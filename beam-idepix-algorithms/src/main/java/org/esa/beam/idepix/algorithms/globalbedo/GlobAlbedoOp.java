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
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.operators.IdepixCloudShadowOp;
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
                  version = "2.0.3-SNAPSHOT",
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
    @Parameter(defaultValue = "false", label = "Copy subset of input radiance bands (MERIS/AATSR synergy)")
    boolean gaCopySubsetOfRadiances;
    @Parameter(defaultValue = "false", label = "Copy MERIS TOA reflectance bands (MERIS/AATSR synergy)")
    boolean gaCopyMerisToaReflectances;
    @Parameter(defaultValue = "false", label = " Compute only the flag band")
    private boolean gaComputeFlagsOnly;
    @Parameter(defaultValue = "false", label = " Copy pressure bands (MERIS)")
    private boolean gaCopyPressure;
    @Parameter(defaultValue = "false", label = " Copy Rayleigh Corrected Reflectances (MERIS)")
    private boolean gaCopyRayleigh = false;
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
    @Parameter(defaultValue = "false", label = " Use GETASSE30 DEM for Barometric Pressure Computation")
    private boolean gaUseGetasse = false;
    @Parameter(defaultValue = "false", label = " Copy input annotation bands (VGT)")
    private boolean gaCopyAnnotations;
    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int gaCloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;
    @Parameter(defaultValue = "false", label = " Use land-water flag from L1b product instead")
    private boolean gaUseL1bLandWaterFlag;
    @Parameter(defaultValue = "false", label = " Use the LC cloud buffer algorithm")
    private boolean gaLcCloudBuffer = false;
    @Parameter(defaultValue = "false", label = " Apply 'Blue dense' cloud algorithm  (MERIS)")
    boolean gaApplyBlueDenseCloudAlgorithm = false;
    @Parameter(defaultValue = "false", label = " Use the NN based Schiller cloud algorithm (MERIS)")
    private boolean gaComputeSchillerClouds = false;
    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean gaUseWaterMaskFraction = true;
    @Parameter(defaultValue = "false", label = " Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;
    @Parameter(defaultValue = "false", label = " Use Istomena et al. algorithm for sea ice determination (AATSR)")
    boolean gaUseIstomenaSeaIceAlgorithm;
    @Parameter(defaultValue = "true", label = " Use Schiller algorithm for sea ice determination outside AATSR")
    boolean gaUseSchillerSeaIceAlgorithm;
    @Parameter(defaultValue = "2.0", label = " AATSR refl[1600] threshold for sea ice determination (MERIS/AATSR)")
    float gaRefl1600SeaIceThresh;
    @Parameter(defaultValue = "false", label = "Write Schiller Seaice Output bands (MERIS 1600 and Cloud/Seaice prob)")
    boolean gaWriteSchillerSeaiceNetBands;

    @Parameter(defaultValue = "_M", label = "MERIS/AATSR collocation master product band names extension")
    private String bandExtensionMaster;
    @Parameter(defaultValue = "_S", label = "MERIS/AATSR collocation slave product band names extension")
    private String bandExtensionSlave;

    @Parameter(defaultValue = "RR", label = "MERIS resolution")
    private String merisResolution;


    private Map<String, Object> gaCloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobAlbedo);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        gaCloudClassificationParameters = createGaCloudClassificationParameters();
        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            processGlobAlbedoMeris();
        } else if (IdepixUtils.isValidAatsrProduct(sourceProduct)) {
            processGlobAlbedoAatsr();
        } else if (IdepixUtils.isValidVgtProduct(sourceProduct)) {
            processGlobAlbedoVgt();
        } else if (IdepixUtils.isValidMerisAatsrSynergyProduct(sourceProduct)) {
            processGlobAlbedoMerisAatsrSynergy();
        }
//                processGlobAlbedo();
        renameL1bMaskNames(targetProduct);
    }


    private Map<String, Object> createGaCloudClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<String, Object>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", gaCopyRadiances);
        gaCloudClassificationParameters.put("gaCopySubsetOfRadiances", gaCopySubsetOfRadiances);
        gaCloudClassificationParameters.put("gaCopyMerisToaReflectances", gaCopyMerisToaReflectances);
        gaCloudClassificationParameters.put("gaCopyRayleigh", gaCopyRayleigh);
        gaCloudClassificationParameters.put("gaCopyAnnotations", gaCopyAnnotations);
        gaCloudClassificationParameters.put("gaCopyPressure", gaCopyPressure);
        gaCloudClassificationParameters.put("gaComputeFlagsOnly", gaComputeFlagsOnly);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);
        gaCloudClassificationParameters.put("gaUseL1bLandWaterFlag", gaUseL1bLandWaterFlag);
        gaCloudClassificationParameters.put("gaLcCloudBuffer", gaLcCloudBuffer);
        gaCloudClassificationParameters.put("gaApplyBlueDenseCloudAlgorithm", gaApplyBlueDenseCloudAlgorithm);
        gaCloudClassificationParameters.put("gaComputeSchillerClouds", gaComputeSchillerClouds);
        gaCloudClassificationParameters.put("gaUseWaterMaskFraction", gaUseWaterMaskFraction);
        gaCloudClassificationParameters.put("gaUseAatsrFwardForClouds", gaUseAatsrFwardForClouds);
        gaCloudClassificationParameters.put("gaUseIstomenaSeaIceAlgorithm", gaUseIstomenaSeaIceAlgorithm);
        gaCloudClassificationParameters.put("gaUseSchillerSeaIceAlgorithm", gaUseSchillerSeaIceAlgorithm);
        gaCloudClassificationParameters.put("gaRefl1600SeaIceThresh", gaRefl1600SeaIceThresh);
        gaCloudClassificationParameters.put("gaWriteSchillerSeaiceNetBands", gaWriteSchillerSeaiceNetBands);

        return gaCloudClassificationParameters;
    }

    private void processGlobAlbedoMeris() {
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        computeMerisAlgorithmInputProducts(gaCloudInput);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoMerisClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        Map<String, Product> gaFinalCloudInput = new HashMap<String, Product>(4);
        gaFinalCloudInput.put("l1b", sourceProduct);
        gaFinalCloudInput.put("cloud", gaCloudProduct);
        gaFinalCloudInput.put("ctp", ctpProduct);   // may be null
        Map<String, Object> gaFinalCloudClassificationParameters = new HashMap<String, Object>(1);
        gaFinalCloudClassificationParameters.put("ctpMode", ctpMode);
        gaFinalCloudClassificationParameters.put("shadowForCloudBuffer", true);
        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class),
                                           gaFinalCloudClassificationParameters, gaFinalCloudInput);

        targetProduct = gaCloudProduct;
        if (IdepixUtils.isValidMerisProduct(sourceProduct) && gaCopyRayleigh) {
            addRayleighCorrectionBands();
        }
    }

    private void computeMerisAlgorithmInputProducts(Map<String, Product> gaCloudInput) {
        gaCloudInput.put("gal1b", sourceProduct);
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        gaCloudInput.put("refl", rad2reflProduct);
        pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, gaUseGetasse);
        gaCloudInput.put("pbaro", pbaroProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
        pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                                                                        true, false, true, false, false, true);
        gaCloudInput.put("pressure", pressureLiseProduct);
        merisCloudProduct = IdepixProducts.computeMerisCloudProduct(sourceProduct, rad2reflProduct, ctpProduct,
                                                                    pressureLiseProduct, pbaroProduct, true);
        gaCloudInput.put("cloud", merisCloudProduct);
        final Product gasProduct = IdepixProducts.
                computeGaseousCorrectionProduct(sourceProduct, rad2reflProduct, merisCloudProduct, true);
        final Product landProduct = IdepixProducts.computeLandClassificationProduct(sourceProduct, gasProduct);
        rayleighProduct = IdepixProducts.computeRayleighCorrectionProduct(sourceProduct, gasProduct, rad2reflProduct, landProduct,
                                                                          merisCloudProduct, false,
                                                                          LandClassificationOp.LAND_FLAGS + ".F_LANDCONS");
        gaCloudInput.put("rayleigh", rayleighProduct);

    }

    private void processGlobAlbedoAatsr() {
        // Cloud Classification
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoAatsrClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        targetProduct = gaCloudProduct;
    }

    private void processGlobAlbedoVgt() {
        // Cloud Classification
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoVgtClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        targetProduct = gaCloudProduct;
    }

    private void processGlobAlbedoMerisAatsrSynergy() {
        sourceProduct.setProductType("MER_" + merisResolution + "_" + sourceProduct.getProductType());
        removeCollocatedMasterSlaveExtensions();

        // new approach for GA CCN...
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        // MERIS is master!!
        computeMerisAlgorithmInputProducts(gaCloudInput);
        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoMerisAatsrSynergyClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        targetProduct = gaCloudProduct;
    }

    private void removeCollocatedMasterSlaveExtensions() {
        for (Band band : sourceProduct.getBands()) {
            final String name = band.getName();
            if (name.endsWith(bandExtensionMaster)) {
                band.setName(name.substring(0, name.length() - bandExtensionMaster.length()));
            }
            if (name.endsWith(bandExtensionSlave)) {
                band.setName(name.substring(0, name.length() - bandExtensionSlave.length()));
            }
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
            super(GlobAlbedoOp.class, "idepix.globalbedo");
        }
    }
}
