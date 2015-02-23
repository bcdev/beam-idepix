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
import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;
import org.esa.beam.idepix.algorithms.globalbedo.GlobAlbedoMerisClassificationOp;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.util.IdepixUtils;

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
            label = " Apply alternative Schiller NN for cloud classification",
            description = " Apply Schiller NN for cloud classification ")
    private boolean applyMERISAlternativeSchillerNN;

    @Parameter(defaultValue = "true",
            label = " Use alternative Schiller 'ALL' NN ",
            description = " Use Schiller 'ALL' NN (instead of 'WATER' NN) ")
    private boolean useMERISAlternativeSchillerAllNN;

    @Parameter(defaultValue = "2.0",
            label = " Alternative Schiller NN cloud ambiguous lower boundary ",
            description = " Alternative Schiller NN cloud ambiguous lower boundary ")
    double alternativeSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " Alternative Schiller NN cloud ambiguous/sure separation value ",
            description = " Alternative Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double alternativeSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " Alternative Schiller NN cloud sure/snow separation value ",
            description = " Alternative Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double alternativeSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "true",
            label = " Apply alternative Schiller NN for MERIS cloud classification purely (not combined with previous approach)",
            description = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean applyMERISAlternativeSchillerNNPure;

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
    private Product postProcessingClassificationProduct;

    private Product gasProduct;
    private Product rayleighProduct;
    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product pbaroProduct;
    private Product waterMaskProduct;
    private Map<String, Product> classificationInputProducts;

    @Override
    public void initialize() throws OperatorException {
        // todo: for MERIS, we need a 'merge' operator using Globalbedo algorithm over land, and Coastcolour algorithm over water

        System.out.println("Running IDEPIX - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Cawa);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        preProcess();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();
        copyOutputBands();
    }

    private void copyOutputBands() {
        // todo
    }

    private void mergeLandWater() {
        // todo
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
        classificationInputProducts.put("ctp", ctpProduct);
        classificationInputProducts.put("pressureOutputLise", pressureLiseProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);

        Map<String, Object> waterClassificationParameters = new HashMap<String, Object>(11);
        waterClassificationParameters.put("cloudScreeningAmbiguous",
                                          cloudScreeningAmbiguous);
        waterClassificationParameters.put("cloudScreeningSure",
                                          cloudScreeningSure);
        waterClassificationParameters.put("ccGlintCloudThresholdAddition",
                                               CC_GLINT_THRESHOLD_ADDITION);
        waterClassificationParameters.put("ccApplyMERISAlternativeSchillerNN",
                                          applyMERISAlternativeSchillerNN);
        waterClassificationParameters.put("ccUseMERISAlternativeSchillerAllNN",
                                               useMERISAlternativeSchillerAllNN);
        waterClassificationParameters.put("ccAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue",
                                          alternativeSchillerNNCloudAmbiguousLowerBoundaryValue);
        waterClassificationParameters.put("ccAlternativeSchillerNNCloudAmbiguousSureSeparationValue",
                                          alternativeSchillerNNCloudAmbiguousSureSeparationValue);
        waterClassificationParameters.put("ccAlternativeSchillerNNCloudSureSnowSeparationValue",
                                          alternativeSchillerNNCloudSureSnowSeparationValue);
        waterClassificationParameters.put("ccApplyMERISAlternativeSchillerNNPure",
                                          applyMERISAlternativeSchillerNNPure);

        waterClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CoastColourClassificationOp.class),
                                              waterClassificationParameters, classificationInputProducts);
    }

    private void computeLandCloudProduct() {
        Map<String, Object> landClassificationParameters = new HashMap<>(1);
        landClassificationParameters.put("gaCopyRadiances", false);         // todo: remove from Cawa land op
        landClassificationParameters.put("gaCopyToaReflectances", false);
        landClassificationParameters.put("gaUseGetasse", useGetasse);
        landClassificationParameters.put("gaApplyMERISAlternativeSchillerNN",
                                         applyMERISAlternativeSchillerNN);
        landClassificationParameters.put("gaApplyMERISAlternativeSchillerNNPure",
                                         applyMERISAlternativeSchillerNNPure);
        landClassificationParameters.put("gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue",
                                         alternativeSchillerNNCloudAmbiguousLowerBoundaryValue);
        landClassificationParameters.put("gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue",
                                         alternativeSchillerNNCloudAmbiguousSureSeparationValue);
        landClassificationParameters.put("gaAlternativeSchillerNNCloudSureSnowSeparationValue",
                                         alternativeSchillerNNCloudSureSnowSeparationValue);
        landClassificationParameters.put("gaSchillerNNCloudAmbiguousLowerBoundaryValue",
                                         alternativeSchillerNNCloudAmbiguousLowerBoundaryValue);
        landClassificationParameters.put("gaSchillerNNCloudAmbiguousSureSeparationValue",
                                         alternativeSchillerNNCloudAmbiguousSureSeparationValue);
        landClassificationParameters.put("gaSchillerNNCloudSureSnowSeparationValue",
                                         alternativeSchillerNNCloudSureSnowSeparationValue);
        landClassificationParameters.put("gaCloudBufferWidth",
                                         cloudBufferWidth);

        classificationInputProducts.put("pbaro", pbaroProduct);
        landClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GlobAlbedoMerisClassificationOp.class),
                                                      landClassificationParameters, classificationInputProducts);
    }

}
