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
import org.esa.beam.idepix.operators.CloudBufferOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.io.FileUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with GlobAlbedo algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Idepix.Land",
        version = "2.2",
        authors = "C.Brockmann, O. Danne",
        copyright = "(c) 2010-2012 by Brockmann Consult",
        description = "Pixel identification and classification over land with an algorithm developed " +
                "within the GlobAlbedo and CCI Land Cover projects.")
public class GlobAlbedoOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "L1b product",
            description = "The MERIS or SPOT-VGT L1b product.")
    private Product sourceProduct;

    @SourceProduct(alias = "urbanProduct", optional = true,
            label = "ProbaV or VGT urban product",
            description = "Urban product (only considered for Proba-V and VGT classification, otherwise ignored).")
    private Product urbanProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product merisCloudProduct;
    private Product rayleighProduct;
    private Product pressureLiseProduct;
    private Product pbaroProduct;
    private Product gaPostProcessingProduct;

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
                label = " Write CTP (cloud-top-pressure) to the target product",
                description = " Write CTP (cloud-top-pressure) to the target product")
    private boolean gaCopyCTP;

    @Parameter(defaultValue = "false",
            label = " Write Feature Values to the target product",
            description = " Write all Feature Values to the target product")
    private boolean gaCopyFeatureValues = false;

//    @Parameter(defaultValue = "true",
//            label = " Use forward view for cloud flagging (AATSR only)",
//            description = " Use forward view for cloud flagging (AATSR only)")
    boolean gaUseAatsrFwardForClouds = true;

    @Parameter(defaultValue = "false",
            label = " Write input annotation bands to the target product (VGT only)",
            description = " Write input annotation bands to the target product (has only effect for VGT L1b products)")
    private boolean gaCopyAnnotations;

    @Parameter(defaultValue = "false",
               label = " Use GETASSE30 DEM for Barometric Pressure Computation",
               description = " If selected and installed, use GETASSE30 DEM for Barometric Pressure Computation. " +
                       "Otherwise use tie point altitude.")
    private boolean gaUseGetasse = false;

    @Parameter(defaultValue = "true",
            label = " Apply alternative NN for MERIS cloud classification",
            description = " Apply NN for MERIS cloud classification (has only effect for MERIS L1b products)")
    private boolean gaApplyMERISAlternativeSchillerNN;

    @Parameter(defaultValue = "false",
               label = " Apply alternative NN for MERIS cloud classification purely (not combined with previous approach)",
               description = " Apply NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean gaApplyMERISAlternativeSchillerNNPure;

    @Parameter(defaultValue = "2.0",
               label = " Alternative NN cloud ambiguous lower boundary (MERIS only)",
               description = " Alternative NN cloud ambiguous lower boundary (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
               label = " Alternative NN cloud ambiguous/sure separation value (MERIS only)",
               description = " Alternative NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
               label = " Alternative NN cloud sure/snow separation value (MERIS only)",
               description = " Alternative NN cloud ambiguous cloud sure/snow separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Apply NN for VGT/Proba-V cloud classification",
            description = " Apply NN for VGT/Proba-V cloud classification (has only effect for VGT/Proba-V L1b products)")
    private boolean gaApplyVGTSchillerNN;

    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary (VGT/Proba-V only)",
            description = " NN cloud ambiguous lower boundary (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value (VGT/Proba-V only)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value (VGT/Proba-V only)",
            description = " NN cloud ambiguous cloud sure/snow separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
               label = " Compute cloud shadow",
               description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean gaComputeCloudShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean gaComputeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int gaCloudBufferWidth;

    @Parameter(defaultValue = "true", label = " Use the LandCover advanced cloud buffer algorithm")
    private boolean gaLcCloudBuffer;

    @Parameter(defaultValue = "true",
               label = " Refine pixel classification near coastlines",
               description = "Refine pixel classification near coastlines. ")
    private boolean gaRefineClassificationNearCoastlines;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"},
            label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution of the used SRTM land-water mask in m/pixel")
    private int wmResolution;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead",
            description = "Use land-water flag from L1b product instead of SRTM mask")
    private boolean gaUseL1bLandWaterFlag;

    private Map<String, Object> gaCloudClassificationParameters;
    private Product gaCloudProduct;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobAlbedo);
        sourceProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 16); // test
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            processGlobAlbedoMeris();
        }  else if (IdepixUtils.isValidAatsrProduct(sourceProduct)) {
            processGlobAlbedoAatsr();
        } else if (IdepixUtils.isValidVgtProduct(sourceProduct)) {
            processGlobAlbedoVgt();
        } else if (IdepixUtils.isValidProbavProduct(sourceProduct)) {
            processGlobAlbedoProbav();
        }
    }

    private Map<String, Object> createGaVgtCloudClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", gaCopyRadiances);
        gaCloudClassificationParameters.put("gaCopyToaReflectances", gaCopyToaReflectances);
        gaCloudClassificationParameters.put("gaCopyFeatureValues", gaCopyFeatureValues);
        gaCloudClassificationParameters.put("gaUseL1bLandWaterFlag", gaUseL1bLandWaterFlag);
        gaCloudClassificationParameters.put("gaUseGetasse", gaUseGetasse);
        gaCloudClassificationParameters.put("gaCopyAnnotations", gaCopyAnnotations);
        gaCloudClassificationParameters.put("gaApplyVGTSchillerNN", gaApplyVGTSchillerNN);
        gaCloudClassificationParameters.put("gaSchillerNNCloudAmbiguousLowerBoundaryValue", gaSchillerNNCloudAmbiguousLowerBoundaryValue);
        gaCloudClassificationParameters.put("gaSchillerNNCloudAmbiguousSureSeparationValue", gaSchillerNNCloudAmbiguousSureSeparationValue);
        gaCloudClassificationParameters.put("gaSchillerNNCloudSureSnowSeparationValue", gaSchillerNNCloudSureSnowSeparationValue);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);

        return gaCloudClassificationParameters;
    }

    private Map<String, Object> createGaProbavCloudClassificationParameters() {
        // actually the same as VGT
        return createGaVgtCloudClassificationParameters();
    }

    private Map<String, Object> createGaMerisAndAatsrCloudClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", gaCopyRadiances);
        gaCloudClassificationParameters.put("gaCopyToaReflectances", gaCopyToaReflectances);
        gaCloudClassificationParameters.put("gaCopyFeatureValues", gaCopyFeatureValues);
        gaCloudClassificationParameters.put("gaUseL1bLandWaterFlag", gaUseL1bLandWaterFlag);
        gaCloudClassificationParameters.put("gaUseGetasse", gaUseGetasse);
        gaCloudClassificationParameters.put("gaCopyAnnotations", gaCopyAnnotations);
        gaCloudClassificationParameters.put("gaUseAatsrFwardForClouds", gaUseAatsrFwardForClouds);
        gaCloudClassificationParameters.put("gaApplyMERISAlternativeSchillerNN", gaApplyMERISAlternativeSchillerNN);
        gaCloudClassificationParameters.put("gaApplyMERISAlternativeSchillerNNPure", gaApplyMERISAlternativeSchillerNNPure);
        gaCloudClassificationParameters.put("gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue", gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue);
        gaCloudClassificationParameters.put("gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue", gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue);
        gaCloudClassificationParameters.put("gaAlternativeSchillerNNCloudSureSnowSeparationValue", gaAlternativeSchillerNNCloudSureSnowSeparationValue);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);

        return gaCloudClassificationParameters;
    }

    private void processGlobAlbedoMeris() {
        Map<String, Product> gaCloudInput = new HashMap<>(4);
        computeMerisAlgorithmInputProducts(gaCloudInput);

        gaCloudClassificationParameters = createGaMerisAndAatsrCloudClassificationParameters();

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoMerisClassificationOp.class),
                gaCloudClassificationParameters, gaCloudInput);

        if (gaRefineClassificationNearCoastlines || gaComputeCloudShadow || gaComputeCloudBuffer) {
            // Post Cloud Classification: coastline refinement, cloud shadow, cloud buffer
            computeGlobAlbedoMerisPostProcessProduct();

            targetProduct = IdepixUtils.cloneProduct(gaCloudProduct);
            targetProduct.setAutoGrouping("radiance:rho_toa:brr");
            if (IdepixUtils.isValidMerisProduct(sourceProduct) && gaCopyRayleigh) {
                addRayleighCorrectionBands();
            }

            Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
            cloudFlagBand.setSourceImage(gaPostProcessingProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS).getSourceImage());
        } else {
            targetProduct = gaCloudProduct;
        }
        if (gaCopyCTP) {
            ProductUtils.copyBand("cloud_top_press", ctpProduct, targetProduct, true);
        }
        renameL1bMaskNames(targetProduct);
    }

    private void computeMerisAlgorithmInputProducts(Map<String, Product> gaCloudInput) {
        gaCloudInput.put("gal1b", sourceProduct);
//        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS, sourceProduct);
        gaCloudInput.put("refl", rad2reflProduct);
        pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, gaUseGetasse);
        gaCloudInput.put("pbaro", pbaroProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
        pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                false, false, true, false, false, true);
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
        gaCloudClassificationParameters = createGaMerisAndAatsrCloudClassificationParameters();
        Product gaCloudProduct;
        Map<String, Product> gaCloudInput = new HashMap<String, Product>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoAatsrClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        targetProduct = gaCloudProduct;
    }

    private void processGlobAlbedoVgt() {
        // Cloud Classification
        Map<String, Product> gaCloudInput = new HashMap<>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudClassificationParameters = createGaVgtCloudClassificationParameters();

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoVgtClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

//        targetProduct = gaCloudProduct;
        // introduce post-processing as for Proba-V (request GK/JM 20160416)
        if (gaComputeCloudBuffer || gaComputeCloudShadow || gaRefineClassificationNearCoastlines) {
            // Post Cloud Classification: coastline refinement, cloud shadow, cloud buffer
            computeGlobAlbedoVgtPostProcessProduct();

            targetProduct = IdepixUtils.cloneProduct(gaCloudProduct);

            Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
            cloudFlagBand.setSourceImage(gaPostProcessingProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS).getSourceImage());
        } else {
            targetProduct = gaCloudProduct;
        }
    }

    private void processGlobAlbedoProbav() {
        // Cloud Classification
        Map<String, Product> gaCloudInput = new HashMap<>(4);
        gaCloudInput.put("gal1b", sourceProduct);

        gaCloudClassificationParameters = createGaProbavCloudClassificationParameters();

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoProbavClassificationOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        if (gaComputeCloudBuffer || gaComputeCloudShadow || gaRefineClassificationNearCoastlines) {
            // Post Cloud Classification: coastline refinement, cloud shadow, cloud buffer
            computeGlobAlbedoProbavPostProcessProduct();

            targetProduct = IdepixUtils.cloneProduct(gaCloudProduct);

            Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
            cloudFlagBand.setSourceImage(gaPostProcessingProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS).getSourceImage());
        } else {
            targetProduct = gaCloudProduct;
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
                band.setUnit("dl");
                targetProduct.addBand(band);
                targetProduct.getBand(band.getName()).setSourceImage(band.getSourceImage());
            }
        }
    }

    private void computeGlobAlbedoMerisPostProcessProduct() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("merisCloud", gaCloudProduct);
        input.put("ctp", ctpProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("cloudBufferWidth", gaCloudBufferWidth);
        params.put("gaLcCloudBuffer", gaLcCloudBuffer);
        params.put("gaComputeCloudBuffer", gaComputeCloudBuffer);
        params.put("gaComputeCloudShadow", gaComputeCloudShadow);
        params.put("gaRefineClassificationNearCoastlines", gaRefineClassificationNearCoastlines);
        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoMerisPostProcessOp.class),
                                                            params, input);

        if (gaComputeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", gaCloudBufferWidth);
            gaPostProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                                        params, input);
        } else {
            gaPostProcessingProduct = classifiedProduct;
        }
    }

    private void computeGlobAlbedoVgtPostProcessProduct() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("vgtCloud", gaCloudProduct);

        final boolean isUrbanProductValid = isVgtUrbanProductValid(sourceProduct, urbanProduct);
        final Product validUrbanProduct = isUrbanProductValid ? urbanProduct : null;
        input.put("urban", validUrbanProduct);

        Map<String, Object> params = new HashMap<>();
        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoVgtPostProcessOp.class),
                                                    params, input);

        if (gaComputeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", gaCloudBufferWidth);
            gaPostProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                                        params, input);
        } else {
            gaPostProcessingProduct = classifiedProduct;
        }
    }

    private void computeGlobAlbedoProbavPostProcessProduct() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("probavCloud", gaCloudProduct);

        final boolean isUrbanProductValid = isProbavUrbanProductValid(sourceProduct, urbanProduct);
        final Product validUrbanProduct = isUrbanProductValid ? urbanProduct : null;
        input.put("urban", validUrbanProduct);

        Map<String, Object> params = new HashMap<>();
        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoProbavPostProcessOp.class),
                                                            params, input);

        if (gaComputeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", gaCloudBufferWidth);
            gaPostProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                                        params, input);
        } else {
            gaPostProcessingProduct = classifiedProduct;
        }
    }

    // package local for testing
    static boolean isVgtUrbanProductValid(Product sourceProduct, Product urbanProduct) {
        // todo: tbd
        return false;
    }

    // package local for testing
    static boolean isProbavUrbanProductValid(Product sourceProduct, Product urbanProduct) {
        if (urbanProduct == null) {
            return false;
        }

        // e.g. urban_mask_X00Y01.nc
        final String name = sourceProduct.getName();
        final int startIndex = name.indexOf("TOA_X") + 4;
        final String tileString = name.substring(startIndex, startIndex+6);

        final String productName = FileUtils.getFilenameWithoutExtension(urbanProduct.getName());
        final boolean valid = productName.matches("urban_mask_" + tileString);
        if (!valid) {
            System.out.println("WARNING: invalid urbanProduct '" + urbanProduct.getName() + "'");
        }

        return valid;
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
