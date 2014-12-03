package org.esa.beam.idepix.algorithms.coastcolour;

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
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Idepix.Water",
                  version = "2.1",
                  authors = "R. Doerffer, H. Schiller, C. Brockmann, O. Danne, M.Peters",
                  copyright = "(c) 2010-2013 Brockmann Consult",
                  description = "Pixel identification and classification over water with an algorithm developed " +
                          "within the CoastColour and CCI Ocean Colour projects.")
public class CoastColourOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
                   label = "MERIS L1b product",
                   description = "The MERIS L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product merisCloudProduct;
    private Product gasProduct;
    private Product rayleighProduct;
    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product ccPostProcessingProduct;


    // Coastcolour parameters
    @Parameter(defaultValue = "true",
               description = "Write TOA radiances to the target product.",
               label = " Write TOA radiances to the target product")
    private boolean ccOutputRadiance = true;

    @Parameter(defaultValue = "false",
               description = "Write TOA reflectances to the target product.",
               label = " Write TOA Reflectances to the target product")
    private boolean ccOutputRad2Refl = false;

    @Parameter(defaultValue = "false",
               description = "Write Rayleigh Corrected Reflectances to the target product.",
               label = " Write Rayleigh Corrected Reflectances  to the target product")
    private boolean ccOutputRayleigh = false;           // but always compute!!

    @Parameter(defaultValue = "false",
               description = "Write Spectral Unmixing Abundance Bands to the target product.",
               label = " Write Spectral Unmixing Abundance Bands to the target product")
    private boolean ccOutputSma = false;

    @Parameter(defaultValue = "false",
               description = "Write Cloud Probability Feature Value to the target product.",
               label = " Write Cloud Probability Feature Value to the target product")
    private boolean ccOutputCloudProbabilityFeatureValue = false;

    @Parameter(defaultValue = "false",
               description = "Write Sea Ice Climatology Max Value to the target product.",
               label = "Write Sea Ice Climatology Max Value to the target product")
    private boolean ccOutputSeaIceClimatologyValue;

    @Parameter(defaultValue = "false",
               description = "Check for sea/lake ice also outside Sea Ice Climatology area.",
               label = "Check for sea/lake ice also outside sea ice climatology area")
    private boolean ccIgnoreSeaIceClimatology;

    @Parameter(defaultValue = "2", interval = "[0,100]",
               description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
               label = "Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(defaultValue = "1.4",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as still ambiguous.",
               label = "Cloud screening 'ambiguous' threshold")
    private double ccCloudScreeningAmbiguous = 1.4;      // Schiller

    @Parameter(defaultValue = "1.8",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as sure.",
               label = "Cloud screening 'sure' threshold")
    private double ccCloudScreeningSure = 1.8;       // Schiller

    @Parameter(defaultValue = "0.1",
               description = "Value added to cloud screening ambiguous/sure thresholds in case of glint",
               label = "Cloud screening threshold addition in case of glint")
    private double ccGlintCloudThresholdAddition;

    @Parameter(defaultValue = "true",
               label = " Apply alternative Schiller NN for cloud classification",
               description = " Apply Schiller NN for cloud classification ")
    private boolean ccApplyMERISAlternativeSchillerNN;

    @Parameter(defaultValue = "true",
               label = " Use alternative Schiller 'ALL' NN ",
               description = " Use Schiller 'ALL' NN (instead of 'WATER' NN) ")
    private boolean ccUseMERISAlternativeSchillerAllNN;

    @Parameter(defaultValue = "2.0",
               label = " Alternative Schiller NN cloud ambiguous lower boundary ",
               description = " Alternative Schiller NN cloud ambiguous lower boundary ")
    double ccAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
               label = " Alternative Schiller NN cloud ambiguous/sure separation value ",
               description = " Alternative Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double ccAlternativeSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
               label = " Alternative Schiller NN cloud sure/snow separation value ",
               description = " Alternative Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double ccAlternativeSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "true",
               label = " Apply alternative Schiller NN for MERIS cloud classification purely (not combined with previous approach)",
               description = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean ccApplyMERISAlternativeSchillerNNPure;


    private static final int CC_LAND_MASK_RESOLUTION = 50;
    private static final int CC_OVERSAMPLING_FACTOR_X = 3;
    private static final int CC_OVERSAMPLING_FACTOR_Y = 3;

    private Product smaProduct;


    @Override
    public void initialize() throws OperatorException {

        System.out.println("Running IDEPIX - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.CoastColour);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processCoastColour();
    }

    private void processCoastColour() {
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
        pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                                                                        false, false,
                                                                        true, false, false, true);

        computeCoastColourMerisCloudProduct();

        gasProduct = IdepixProducts.computeGaseousCorrectionProduct(sourceProduct, rad2reflProduct, merisCloudProduct, true);

        rayleighProduct = IdepixProducts.computeRayleighCorrectionProduct(sourceProduct, gasProduct, rad2reflProduct,
                                                                          merisCloudProduct, merisCloudProduct,
                                                                          true,
                                                                          CoastColourClassificationOp.CLOUD_FLAGS + ".F_LAND");

        smaProduct = null;
        if (ccOutputSma) {
            smaProduct = IdepixProducts.computeSpectralUnmixingProduct(rayleighProduct, true);
        }

        // Post Cloud Classification and computation of Mixed Pixel Flag
        computeCoastColourPostProcessProduct(smaProduct);

        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        targetProduct.setAutoGrouping("radiance:rho_toa:brr:spec_unmix");
        addBandsToTargetProduct();

        CoastColourClassificationOp.addBitmasks(targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        renameL1bMaskNames(targetProduct);
    }

    private void computeCoastColourMerisCloudProduct() {
        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", CC_LAND_MASK_RESOLUTION);
        waterParameters.put("subSamplingFactorX", CC_OVERSAMPLING_FACTOR_X);
        waterParameters.put("subSamplingFactorY", CC_OVERSAMPLING_FACTOR_Y);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);

        Map<String, Product> cloudInputProducts = new HashMap<String, Product>(4);
        cloudInputProducts.put("l1b", sourceProduct);
        cloudInputProducts.put("rhotoa", rad2reflProduct);
        cloudInputProducts.put("ctp", ctpProduct);
        cloudInputProducts.put("pressureOutputLise", pressureLiseProduct);
        cloudInputProducts.put("waterMask", waterMaskProduct);

        Map<String, Object> cloudClassificationParameters = new HashMap<String, Object>(11);
        cloudClassificationParameters.put("cloudScreeningAmbiguous", ccCloudScreeningAmbiguous);
        cloudClassificationParameters.put("cloudScreeningSure", ccCloudScreeningSure);
        cloudClassificationParameters.put("ccGlintCloudThresholdAddition", ccGlintCloudThresholdAddition);
        cloudClassificationParameters.put("ccOutputSeaIceClimatologyValue", ccOutputSeaIceClimatologyValue);
        cloudClassificationParameters.put("ccIgnoreSeaIceClimatology", ccIgnoreSeaIceClimatology);
        cloudClassificationParameters.put("ccOutputCloudProbabilityFeatureValue", ccOutputCloudProbabilityFeatureValue);
        cloudClassificationParameters.put("ccApplyMERISAlternativeSchillerNN", ccApplyMERISAlternativeSchillerNN);
        cloudClassificationParameters.put("ccUseMERISAlternativeSchillerAllNN", ccUseMERISAlternativeSchillerAllNN);
        cloudClassificationParameters.put("ccAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue", ccAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue);
        cloudClassificationParameters.put("ccAlternativeSchillerNNCloudAmbiguousSureSeparationValue", ccAlternativeSchillerNNCloudAmbiguousSureSeparationValue);
        cloudClassificationParameters.put("ccAlternativeSchillerNNCloudSureSnowSeparationValue", ccAlternativeSchillerNNCloudSureSnowSeparationValue);
        cloudClassificationParameters.put("ccApplyMERISAlternativeSchillerNNPure", ccApplyMERISAlternativeSchillerNNPure);
        merisCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CoastColourClassificationOp.class),
                                              cloudClassificationParameters, cloudInputProducts);
    }

    private void computeCoastColourPostProcessProduct(Product smaProduct1) {
        HashMap<String, Product> input = new HashMap<String, Product>();
        input.put("l1b", sourceProduct);
        input.put("merisCloud", merisCloudProduct);
        input.put("ctp", ctpProduct);
        input.put("rayleigh", rayleighProduct);
        input.put("sma", smaProduct1);   // may be null

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("cloudBufferWidth", ccCloudBufferWidth);
        ccPostProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CoastColourPostProcessOp.class), params, input);
    }

    private void addBandsToTargetProduct() {
        if (ccOutputRadiance) {
            IdepixProducts.addRadianceBands(sourceProduct, targetProduct);
        }
        if (ccOutputRad2Refl) {
            IdepixProducts.addRadiance2ReflectanceBands(rad2reflProduct, targetProduct);
        }
        if (ccOutputRayleigh) {
            IdepixProducts.addRayleighCorrectionBands(rayleighProduct, targetProduct);
        }
        if (ccOutputSma) {
            IdepixProducts.addSpectralUnmixingBands(smaProduct, targetProduct);
        }
        if (ccOutputSeaIceClimatologyValue) {
            IdepixProducts.addCCSeaiceClimatologyValueBand(merisCloudProduct, targetProduct);
        }
        if (ccOutputCloudProbabilityFeatureValue) {
            IdepixProducts.addCCCloudProbabilityValueBand(merisCloudProduct, targetProduct);
        }

        if (ccApplyMERISAlternativeSchillerNN) {
            //IdepixProducts.addMERISAlternativeNNOutputBand(merisCloudProduct, targetProduct);
        }

        addCloudClassificationFlagBandCoastColour();
        Band cloudFlagBand = targetProduct.getBand(CoastColourClassificationOp.CLOUD_FLAGS);
        cloudFlagBand.setSourceImage(ccPostProcessingProduct.getBand(CoastColourClassificationOp.CLOUD_FLAGS).getSourceImage());
    }

    private void addCloudClassificationFlagBandCoastColour() {
        FlagCoding flagCoding = CoastColourClassificationOp.createFlagCoding(
                CoastColourClassificationOp.CLOUD_FLAGS);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : merisCloudProduct.getBands()) {
            if (band.getName().equals(CoastColourClassificationOp.CLOUD_FLAGS)) {
                System.out.println("adding band: " + band.getName());
                Band targetBand = ProductUtils.copyBand(band.getName(), merisCloudProduct, targetProduct, true);
                targetBand.setSampleCoding(flagCoding);
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourOp.class);
        }
    }
}
