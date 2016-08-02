package org.esa.beam.idepix.algorithms.cawa;

import org.esa.beam.framework.datamodel.Band;
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
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with CAWA algorithm
 * (merge of GA over land and CC over water).
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.cawa",
        version = "2.2",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Pixel identification with CAWA algorithm (merge of GA over land and CC over water).")
public class CawaOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "MERIS L1b product",
            description = "The MERIS L1b source product.")
    private Product sourceProduct;

    @SourceProduct(optional = true)
    private Product eraInterimProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Parameter(defaultValue = "false",
            description = "Write TOA radiances to the target product.",
            label = " Write TOA radiances to the target product")
    private boolean outputRadiance = false;

    @Parameter(defaultValue = "false",
            description = "Write all TOA reflectances to the target product.",
            label = " Write all TOA Reflectances to the target product")
    private boolean outputAllRad2Refl = false;

    @Parameter(defaultValue = "true",
            description = "Write CAWA TOA reflectances (bands 13-15) to the target product.",
            label = " Write CAWA TOA Reflectances (bands 13-15) to the target product")
    private boolean outputCawaRad2Refl = true;


    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = " NN cloud ambiguous lower boundary (applied on WATER)",
            description = " NN cloud ambiguous lower boundary (applied on WATER)")
    double schillerWaterNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " NN cloud ambiguous/sure separation value (applied on WATER)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (applied on WATER)")
    double schillerWaterNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " NN cloud sure/snow separation value (applied on WATER)",
            description = " NN cloud ambiguous cloud sure/snow separation value (applied on WATER)")
    double schillerWaterNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary (applied on LAND)",
            description = " NN cloud ambiguous lower boundary (applied on LAND)")
    double schillerLandNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value (applied on LAND)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for MERIS L1b products)")
    double schillerLandNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value (applied on LAND)",
            description = " NN cloud ambiguous cloud sure/snow separation value (has only effect for MERIS L1b products)")
    double schillerLandNNCloudSureSnowSeparationValue;


    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;


    private static final int LAND_WATER_MASK_RESOLUTION = 50;
    private static final int OVERSAMPLING_FACTOR_X = 3;
    private static final int OVERSAMPLING_FACTOR_Y = 3;

    private Product waterClassificationProduct;
    private Product landClassificationProduct;
    private Product mergedClassificationProduct;
    private Product postProcessingProduct;

    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product pbaroProduct;
    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> waterClassificationParameters;
    private Map<String, Object> landClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        System.out.println("Running IDEPIX CAWA - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Cawa);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        preProcess();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();
        postProcess();

        targetProduct = postProcessingProduct;

        targetProduct = IdepixUtils.cloneProduct(mergedClassificationProduct);
        targetProduct.setAutoGrouping("radiance:rho_toa");

        Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS).getSourceImage());

        copyOutputBands();
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);   // we need the L1b flag!

//        targetProduct = waterClassificationProduct;
    }

    private boolean isCawaEraInterimProductValid() {
        return eraInterimProduct != null &&
                eraInterimProduct.getSceneRasterWidth() == sourceProduct.getSceneRasterWidth() &&
                eraInterimProduct.containsBand("tcwv") &&
                eraInterimProduct.containsBand("u10") &&
                eraInterimProduct.containsBand("v10");
    }

    private void preProcess() {
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
        pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                                                                        false, false, true, false, false, true);
        pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, false);

        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);
    }

    private void setLandClassificationParameters() {
        landClassificationParameters = new HashMap<>();
        landClassificationParameters.put("copyAllTiePoints", true);
        landClassificationParameters.put("outputSchillerNNValue",
                                         outputSchillerNNValue);
        landClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                                         schillerLandNNCloudAmbiguousLowerBoundaryValue);
        landClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                                         schillerLandNNCloudAmbiguousSureSeparationValue);
        landClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                                         schillerLandNNCloudSureSnowSeparationValue);
    }

    private void setWaterClassificationParameters() {
        waterClassificationParameters = new HashMap<>();
        waterClassificationParameters.put("copyAllTiePoints", true);
        waterClassificationParameters.put("outputSchillerNNValue",
                                          outputSchillerNNValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                                          schillerWaterNNCloudAmbiguousLowerBoundaryValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                                          schillerWaterNNCloudAmbiguousSureSeparationValue);
        waterClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                                          schillerWaterNNCloudSureSnowSeparationValue);
    }

    private void computeWaterCloudProduct() {
        setWaterClassificationParameters();
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        classificationInputProducts.put("pressure", ctpProduct);
        classificationInputProducts.put("pressureLise", pressureLiseProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);

        waterClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaWaterClassificationOp.class),
                                                       waterClassificationParameters, classificationInputProducts);
    }

    private void computeLandCloudProduct() {
        setLandClassificationParameters();
        classificationInputProducts.put("pressure", pbaroProduct);
        landClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaLandClassificationOp.class),
                                                      landClassificationParameters, classificationInputProducts);
    }

    private void mergeLandWater() {
        Map<String, Product> mergeInputProducts = new HashMap<>();
        mergeInputProducts.put("landClassif", landClassificationProduct);
        mergeInputProducts.put("waterClassif", waterClassificationProduct);
        if (eraInterimProduct != null && isCawaEraInterimProductValid()) {
            mergeInputProducts.put("eraInterimProduct", eraInterimProduct);
        } else {
            System.out.println("WARNING: ERA Interim product not available or invalid - will use default values.");
            mergeInputProducts.put("eraInterimProduct", null);
        }

        Map<String, Object> mergeClassificationParameters = new HashMap<>();
        mergeClassificationParameters.put("copyAllTiePoints", true);
        mergedClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaMergeLandWaterOp.class),
                                                        mergeClassificationParameters, mergeInputProducts);
    }

    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("merisCloud", mergedClassificationProduct);
        input.put("ctp", ctpProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudShadow", computeCloudShadow);
        params.put("refineClassificationNearCoastlines", true);  // always an improvement

        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaPostProcessOp.class),
                                                            params, input);

        if (computeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", cloudBufferWidth);
            postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                                      params, input);
        } else {
            postProcessingProduct = classifiedProduct;
        }
    }

    private void copyOutputBands() {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
//        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
//        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        CawaUtils.setupCawaBitmasks(targetProduct);
        if (outputRadiance) {
            IdepixProducts.addRadianceBands(sourceProduct, targetProduct);
        }
        if (outputAllRad2Refl) {
            IdepixProducts.addRadiance2ReflectanceBands(rad2reflProduct, targetProduct);
        }

        if (!outputAllRad2Refl && outputCawaRad2Refl) {
            IdepixProducts.addRadiance2ReflectanceBands(rad2reflProduct, targetProduct, 13, 15);
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CawaOp.class);
        }
    }
}
