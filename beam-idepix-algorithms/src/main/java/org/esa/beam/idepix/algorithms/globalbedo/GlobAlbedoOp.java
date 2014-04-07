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
                  version = "2.1-SNAPSHOT",
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

    // Globalbedo user options
    @Parameter(defaultValue = "true",
               label = " Write TOA Radiances to the target product",
               description = " Write TOA Radiances to the target product")
    private boolean gaCopyRadiances = true;

    @Parameter(defaultValue = "true",
               label = " Write TOA Reflectances to the target product",
               description = " Write TOA Reflectances to the target product")
    private boolean gaCopyToaReflectances = true;

    @Parameter(defaultValue = "false",
               label = " Write Rayleigh Corrected Reflectances to the target product",
               description = " Write Rayleigh Corrected Reflectances to the target product")
    private boolean gaCopyRayleigh = false;

    @Parameter(defaultValue = "false",
               label = " Write Feature Values to the target product",
               description = " Write all Feature Values to the target product")
    private boolean gaCopyFeatureValues = false;

    @Parameter(defaultValue = "Derive from Neural Net",
               label = " CTP value to use in MERIS cloud shadow algorithm",
               description = " CTP value to use in MERIS cloud shadow algorithm",
               valueSet = {
                       IdepixConstants.ctpModeDefault,
                       "850 hPa",
                       "700 hPa",
                       "500 hPa",
                       "400 hPa",
                       "300 hPa"
               })
    private String ctpMode;

    @Parameter(defaultValue = "false",
               label = " Use GETASSE30 DEM for Barometric Pressure Computation",
               description = " If selected and installed, use GETASSE30 DEM for Barometric Pressure Computation. " +
                       "Otherwise use tie point altitude.")
    private boolean gaUseGetasse = false;

    @Parameter(defaultValue = "false",
               label = " Write input annotation bands to the target product (VGT only)",
               description = " Write input annotation bands to the target product (has only effect for VGT L1b products)")
    private boolean gaCopyAnnotations;

    @Parameter(defaultValue = "2",
               label = " Width of cloud buffer (# of pixels)",
               description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int gaCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"},
               label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution of the used SRTM land-water mask in m/pixel")
    private int wmResolution;

    @Parameter(defaultValue = "false",
               label = " Use land-water flag from L1b product instead",
               description = "Use land-water flag from L1b product instead of SRTM mask")
    private boolean gaUseL1bLandWaterFlag;

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
        }  else if (IdepixUtils.isValidVgtProduct(sourceProduct)) {
            processGlobAlbedoVgt();
        }
        renameL1bMaskNames(targetProduct);
    }

    private Map<String, Object> createGaCloudClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<String, Object>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", gaCopyRadiances);
        gaCloudClassificationParameters.put("gaCopyToaReflectances", gaCopyToaReflectances);
        gaCloudClassificationParameters.put("gaCopyFeatureValues", gaCopyFeatureValues);
        gaCloudClassificationParameters.put("ctpMode", ctpMode);
        gaCloudClassificationParameters.put("gaUseGetasse", gaUseGetasse);
        gaCloudClassificationParameters.put("gaCopyAnnotations", gaCopyAnnotations);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);

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
                                                                        false, true, false, false, true);
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

    private void processGlobAlbedoVgt() {
        // Cloud Classification
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoVgtClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        targetProduct = gaCloudProduct;
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

//        public Spi() {
//            // this is deprecated:
////            super(GlobAlbedoOp.class, "idepix.globalbedo");
//
//
//            super(new AnnotationOperatorDescriptor(GlobAlbedoOp.class,
//                                                   GlobAlbedoOp.class.getAnnotation(OperatorMetadata.class)));
//        }

        // this is preliminary //todo: to be discussed what to use finally
        public Spi() {
            super(GlobAlbedoOp.class);
        }
    }
}
