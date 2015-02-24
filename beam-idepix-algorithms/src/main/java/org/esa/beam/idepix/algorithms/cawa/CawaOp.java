package org.esa.beam.idepix.algorithms.cawa;

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

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Parameter(defaultValue = "true",
            description = "Write TOA radiances to the target product.",
            label = " Write TOA radiances to the target product")
    private boolean outputRadiance = true;

    @Parameter(defaultValue = "false",
            description = "Write TOA reflectances to the target product.",
            label = " Write TOA Reflectances to the target product")
    private boolean outputRad2Refl = false;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "1.4",
            description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as still ambiguous.",
            label = "Cloud screening 'ambiguous' threshold")
    private double cloudScreeningAmbiguous = 1.4;      // Schiller

    @Parameter(defaultValue = "1.8",
            description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as sure.",
            label = "Cloud screening 'sure' threshold")
    private double cloudScreeningSure = 1.8;       // Schiller

    @Parameter(defaultValue = "true",
            label = " Write Schiller NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = " Schiller NN cloud ambiguous lower boundary ",
            description = " Schiller NN cloud ambiguous lower boundary ")
    double schillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " Schiller NN cloud ambiguous/sure separation value ",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double schillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " Schiller NN cloud sure/snow separation value ",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double schillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Use GETASSE30 DEM for Barometric Pressure Computation",
            description = " If selected and installed, use GETASSE30 DEM for Barometric Pressure Computation. " +
                    "Otherwise use tie point altitude.")
    private boolean useGetasse = false;



    private static final int LAND_WATER_MASK_RESOLUTION = 50;
    private static final int OVERSAMPLING_FACTOR_X = 3;
    private static final int OVERSAMPLING_FACTOR_Y = 3;

    private static final double CC_GLINT_THRESHOLD_ADDITION = 0.1;


    private Product waterClassificationProduct;
    private Product landClassificationProduct;
    private Product mergedClassificationProduct;
    private Product postProcessingClassificationProduct;

    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product pbaroProduct;
    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;

    @Override
    public void initialize() throws OperatorException {
        System.out.println("Running IDEPIX - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Cawa);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        preProcess();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();
        postProcess();

        targetProduct = postProcessingClassificationProduct;
//        CawaUtils.setupCawaBitmasks(landClassificationProduct);
//        CawaUtils.setupCawaBitmasks(waterClassificationProduct);
//        targetProduct = waterClassificationProduct;
//        targetProduct = landClassificationProduct;
        copyOutputBands();
    }

    private void copyOutputBands() {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        CawaUtils.setupCawaBitmasks(targetProduct);
        if (outputRadiance) {
            IdepixProducts.addRadianceBands(sourceProduct, targetProduct);
        }
        if (outputRad2Refl) {
            IdepixProducts.addRadiance2ReflectanceBands(rad2reflProduct, targetProduct);
        }
    }

    private void postProcess() {
        postProcessingClassificationProduct = mergedClassificationProduct;
        // todo!
    }

    private void mergeLandWater() {
        Map<String, Product> mergeInputProducts = new HashMap<>();
        mergeInputProducts.put("landClassif", landClassificationProduct);
        mergeInputProducts.put("waterClassif", waterClassificationProduct);

        mergedClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaMergeLandWaterOp.class),
                                                       GPF.NO_PARAMS, mergeInputProducts);
    }

    private void preProcess() {
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
        pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                                                                        false, false, true, false, false, true);
        pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, useGetasse);

        HashMap<String, Object> waterMaskParameters = new HashMap<String, Object>();
        waterMaskParameters.put("resolution", LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);
    }

    private void computeWaterCloudProduct() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        classificationInputProducts.put("pressure", ctpProduct);
        classificationInputProducts.put("pressureLise", pressureLiseProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);

        Map<String, Object> waterClassificationParameters = new HashMap<String, Object>(11);
        waterClassificationParameters.put("cloudScreeningAmbiguous",
                                          cloudScreeningAmbiguous);
        waterClassificationParameters.put("cloudScreeningSure",
                                          cloudScreeningSure);
        waterClassificationParameters.put("ccGlintCloudThresholdAddition",
                                               CC_GLINT_THRESHOLD_ADDITION);
        waterClassificationParameters.put("outputSchillerNNValue",
                                          outputSchillerNNValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                                          schillerNNCloudAmbiguousLowerBoundaryValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                                          schillerNNCloudAmbiguousSureSeparationValue);
        waterClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                                          schillerNNCloudSureSnowSeparationValue);

        waterClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaWaterClassificationOp.class),
                                              waterClassificationParameters, classificationInputProducts);
    }

    private void computeLandCloudProduct() {
        Map<String, Object> landClassificationParameters = new HashMap<>(1);
        landClassificationParameters.put("outputSchillerNNValue",
                                          outputSchillerNNValue);
        landClassificationParameters.put("gaSchillerNNCloudAmbiguousLowerBoundaryValue",
                                         schillerNNCloudAmbiguousLowerBoundaryValue);
        landClassificationParameters.put("gaSchillerNNCloudAmbiguousSureSeparationValue",
                                         schillerNNCloudAmbiguousSureSeparationValue);
        landClassificationParameters.put("gaSchillerNNCloudSureSnowSeparationValue",
                                         schillerNNCloudSureSnowSeparationValue);
        landClassificationParameters.put("gaCloudBufferWidth",
                                         cloudBufferWidth);

        classificationInputProducts.put("pressure", pbaroProduct);
        landClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CawaLandClassificationOp.class),
                                                      landClassificationParameters, classificationInputProducts);
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
