package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.AlgorithmSelector;
import org.esa.beam.idepix.operators.*;
import org.esa.beam.idepix.util.IdepixProducts;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with GlobAlbedo algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.globalbedo",
                  version = "1.4-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with GlobAlbedo algorithm.")
public class GlobAlbedoOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product merisCloudProduct;
    private Product rayleighProduct;
    private Product pressureLiseProduct;
    private Product pbaroProduct;

    // Globalbedo parameters
    @Parameter(defaultValue = "true", label = " Copy input radiance/reflectance bands")
    private boolean gaCopyRadiances = true;
    @Parameter(defaultValue = "false", label = " Compute only the flag band")
    private boolean gaComputeFlagsOnly;
    @Parameter(defaultValue = "false", label = " Copy pressure bands (MERIS)")
    private boolean gaCopyPressure;
    @Parameter(defaultValue = "true", label = " Compute cloud shadow (MERIS)")
    private boolean gaComputeMerisCloudShadow;
    @Parameter(label = " CTP value to use in MERIS cloud shadow algorithm", defaultValue = "Derive from Neural Net",
               valueSet = {
                       IdepixConstants.ctpModeDefault,
                       "850 hPa",
                       "700 hPa",
                       "500 hPa",
                       "400 hPa",
                       "300 hPa"
               })
    private String ctpMode;
    @Parameter(defaultValue = "false", label = " Copy input annotation bands (VGT)")
    private boolean gaCopyAnnotations;
    @Parameter(defaultValue = "true", label = " Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;
    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int gaCloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;
    @Parameter(defaultValue = "true", label = " Use land-water flag from L1b product instead")
    private boolean gaUseL1bLandWaterFlag;
    @Parameter(defaultValue = "false", label = " Copy Rayleigh Corrected Reflectances")
    private boolean gaOutputRayleigh = false;
    @Parameter(defaultValue = "false", label = " Use the LC cloud buffer algorithm")
    private boolean gaLcCloudBuffer = false;
    @Parameter(defaultValue = "false", label = " Use the NN based Schiller cloud algorithm")
    private boolean gaComputeSchillerClouds = false;
    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean gaUseWaterMaskFraction = true;


    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobAlbedo);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processGlobAlbedo();
        renameL1bMaskNames(targetProduct);
    }

    private void processGlobAlbedo() {
        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
            pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct);
            ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
            pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct, true);
            merisCloudProduct = IdepixProducts.computeMerisCloudProduct(sourceProduct, rad2reflProduct, ctpProduct,
                                                                        pressureLiseProduct, pbaroProduct, true);
            final Product gasProduct = IdepixProducts.computeGaseousCorrectionProduct(sourceProduct, rad2reflProduct, merisCloudProduct);
            final Product landProduct = IdepixProducts.computeLandClassificationProduct(sourceProduct, gasProduct);
            rayleighProduct = IdepixProducts.computeRayleighCorrectionProduct(sourceProduct, gasProduct, rad2reflProduct, landProduct,
                                                            merisCloudProduct, false, LandClassificationOp.LAND_FLAGS + ".F_LANDCONS");
        }

        // Cloud Classification
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        gaCloudInput.put("gal1b", sourceProduct);
        gaCloudInput.put("cloud", merisCloudProduct);   // may be null
        gaCloudInput.put("rayleigh", rayleighProduct);  // may be null
        gaCloudInput.put("pressure", pressureLiseProduct);   // may be null
        gaCloudInput.put("pbaro", pbaroProduct);   // may be null
        gaCloudInput.put("refl", rad2reflProduct);   // may be null
        Map<String, Object> gaCloudClassificationParameters = new HashMap<String, Object>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", gaCopyRadiances);
        gaCloudClassificationParameters.put("gaCopyAnnotations", gaCopyAnnotations);
        gaCloudClassificationParameters.put("gaCopyPressure", gaCopyPressure);
        gaCloudClassificationParameters.put("gaComputeFlagsOnly", gaComputeFlagsOnly);
        gaCloudClassificationParameters.put("gaUseAatsrFwardForClouds", gaUseAatsrFwardForClouds);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);
        gaCloudClassificationParameters.put("gaUseL1bLandWaterFlag", gaUseL1bLandWaterFlag);
        gaCloudClassificationParameters.put("gaLcCloudBuffer", gaLcCloudBuffer);
        gaCloudClassificationParameters.put("gaComputeSchillerClouds", gaComputeSchillerClouds);
        gaCloudClassificationParameters.put("gaUseWaterMaskFraction", gaUseWaterMaskFraction);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoCloudScreeningOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        // add cloud shadow flag to the cloud product computed above...
        if (gaComputeMerisCloudShadow && IdepixUtils.isValidMerisProduct(sourceProduct)) {
            Map<String, Product> gaFinalCloudInput = new HashMap<String, Product>(4);
            gaFinalCloudInput.put("gal1b", sourceProduct);
            gaFinalCloudInput.put("cloud", gaCloudProduct);
            gaFinalCloudInput.put("ctp", ctpProduct);   // may be null
            Map<String, Object> gaFinalCloudClassificationParameters = new HashMap<String, Object>(1);
            gaFinalCloudClassificationParameters.put("ctpMode", ctpMode);
            gaFinalCloudClassificationParameters.put("shadowForCloudBuffer", true);
            gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class),
                                               gaFinalCloudClassificationParameters, gaFinalCloudInput);
        }

        targetProduct = gaCloudProduct;
        if (gaOutputRayleigh) {
            addRayleighCorrectionBands();
        }
    }

    private void addRayleighCorrectionBands() {
        int l1_band_num = RayleighCorrectionOp.L1_BAND_NUM;
        FlagCoding flagCoding = RayleighCorrectionOp.createFlagCoding(l1_band_num);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : rayleighProduct.getBands()) {
            // do not add the normalized bands
            if (!targetProduct.containsBand(band.getName()) && !band.getName().endsWith("_n")) {
                if (band.getName().equals(RayleighCorrectionOp.RAY_CORR_FLAGS)) {
                    band.setSampleCoding(flagCoding);
                }
                targetProduct.addBand(band);
                targetProduct.getBand(band.getName()).setSourceImage(band.getSourceImage());
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoOp.class);
        }
    }
}
