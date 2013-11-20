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

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.coastcolour",
                  version = "2.0.2-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with CoastColour algorithm.")
public class CoastColourOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
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
    @Parameter(defaultValue = "false", label = " TOA Reflectances")
    private boolean ccOutputRad2Refl = false;

    @Parameter(defaultValue = "false", label = " Gas Absorption Correction Flag")
    private boolean ccOutputGaseous = false;

    @Parameter(defaultValue = "false", label = " Rayleigh Corrected Reflectances (requires L2 Pressures for computation of cloudy pixels!)")
    private boolean ccOutputRayleigh = false;

    @Parameter(defaultValue = "false", label = " Mixed Pixel Flag (requires Rayleigh corrected reflectances!)")
    private boolean ccMixedPixel = false;

    @Parameter(defaultValue = "false", label = " Spectral Unmixing Abundance Bands (requires Rayleigh corrected reflectances!)")
    private boolean ccOutputSma = false;

    @Parameter(defaultValue = "false", label = " L2 Cloud Top Pressure and Surface Pressure")
    private boolean ccOutputL2Pressures = false;

    @Parameter(defaultValue = "false", label = " L2 Cloud Detection Flags")
    private boolean ccOutputL2CloudDetection = false;

    @Parameter(defaultValue = "true", label = " Schiller Cloud Value")
    private boolean ccOutputSchillerCloudValue = true;

    @Parameter(label = " Sea Ice Climatology Max Value", defaultValue = "false")
    private boolean ccOutputSeaIceClimatologyValue;

    @Parameter(label = " RhoGlint Debug Values", defaultValue = "false")
    private boolean ccOutputRhoglintDebugValues;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(label = " P1 Scaled Pressure Threshold ", defaultValue = "1000.0")
    private double ccUserDefinedP1ScaledThreshold = 1000.0;

    @Parameter(label = " PScatt Pressure Threshold ", defaultValue = "700.0")
    private double ccUserDefinedPScattPressureThreshold = 700.0;

    //    @Parameter(label = " Theoretical Glint Threshold", defaultValue = "0.015")
    @Parameter(label = " Theoretical Glint Threshold", defaultValue = "0.2")  // 20130702
    private double ccUserDefinedGlintThreshold;

    @Parameter(label = " RhoTOA753 Threshold ", defaultValue = "0.1")
    private double ccUserDefinedRhoToa753Threshold = 0.1;

    @Parameter(label = " MDSI Threshold ", defaultValue = "0.01")
    private double ccUserDefinedMDSIThreshold = 0.01;

    @Parameter(label = " NDVI Threshold ", defaultValue = "0.1")
    private double ccUserDefinedNDVIThreshold;

    @Parameter(label = " Bright Test Threshold ", defaultValue = "0.03")
    private double ccUserDefinedRhoToa442Threshold = 0.03;

    @Parameter(label = " Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560", "620", "665",
                       "681", "705", "753", "760", "775", "865", "890", "900"
               })
    private int ccRhoAgReferenceWavelength;   // default changed from 442, 2011/03/25

    @Parameter(label = "Resolution of land mask", defaultValue = "50",
               description = "The resolution of the land mask in meter.", valueSet = {"50", "150"})
    private int ccLandMaskResolution;
    @Parameter(label = "Source pixel over-sampling (X)", defaultValue = "3",
               description = "The factor used to over-sample the source pixels in X-direction.")
    private int ccOversamplingFactorX;
    @Parameter(label = "Source pixel over-sampling (Y)", defaultValue = "3",
               description = "The factor used to over-sample the source pixels in Y-direction.")
    private int ccOversamplingFactorY;

    @Parameter(label = "Sea Ice Threshold on Climatology", defaultValue = "10.0")
    private double ccSeaIceThreshold;

    @Parameter(label = "Schiller cloud Threshold ambiguous clouds", defaultValue = "1.4")
    private double ccSchillerAmbiguous;
    @Parameter(label = "Schiller cloud Threshold sure clouds", defaultValue = "1.8")
    private double ccSchillerSure;
    private Product smaProduct;


    @Override
    public void initialize() throws OperatorException {

        System.out.println("Running IDEPIX renovation, v2.0-SNAPSHOT...");

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
                                                                        ccOutputL2CloudDetection,
                                                                        false,
                                                                        true, false, false, true);

        computeCoastColourMerisCloudProduct();

        gasProduct = IdepixProducts.computeGaseousCorrectionProduct(sourceProduct, rad2reflProduct, merisCloudProduct, true);

        // todo: check if it is ok to use merisCloudProduct as 'land product' (as implemented in old Idepix)
        rayleighProduct = IdepixProducts.computeRayleighCorrectionProduct(sourceProduct, gasProduct, rad2reflProduct,
                                                                          merisCloudProduct, merisCloudProduct,
                                                                          ccOutputRayleigh,
                                                                          CoastColourClassificationOp.CLOUD_FLAGS + ".F_LAND");

        smaProduct = null;
        if (ccMixedPixel || ccOutputSma) {
            smaProduct = IdepixProducts.computeSpectralUnmixingProduct(rayleighProduct, true);
        }

        // Post Cloud Classification and computation of Mixed Pixel Flag
        computeCoastColourPostProcessProduct(smaProduct);

        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        addBandsToTargetProduct();

        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        CoastColourClassificationOp.addBitmasks(targetProduct);
        renameL1bMaskNames(targetProduct);
    }

    private void computeCoastColourMerisCloudProduct() {
        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", ccLandMaskResolution);
        waterParameters.put("subSamplingFactorX", ccOversamplingFactorX);
        waterParameters.put("subSamplingFactorY", ccOversamplingFactorY);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);

        Map<String, Product> cloudInputProducts = new HashMap<String, Product>(4);
        cloudInputProducts.put("l1b", sourceProduct);
        cloudInputProducts.put("rhotoa", rad2reflProduct);
        cloudInputProducts.put("ctp", ctpProduct);
        cloudInputProducts.put("pressureOutputLise", pressureLiseProduct);
        cloudInputProducts.put("waterMask", waterMaskProduct);

        Map<String, Object> cloudClassificationParameters = new HashMap<String, Object>(11);
        cloudClassificationParameters.put("l2Pressures", ccOutputL2Pressures);
        cloudClassificationParameters.put("l2CloudDetection", ccOutputL2CloudDetection);
        cloudClassificationParameters.put("ccUserDefinedP1ScaledThreshold", ccUserDefinedP1ScaledThreshold);
        cloudClassificationParameters.put("userDefinedPScattPressureThreshold", ccUserDefinedPScattPressureThreshold);
        cloudClassificationParameters.put("userDefinedGlintThreshold", ccUserDefinedGlintThreshold);
        cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ccUserDefinedRhoToa442Threshold);
        cloudClassificationParameters.put("userDefinedRhoToa753Threshold", ccUserDefinedRhoToa753Threshold);
        cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ccUserDefinedRhoToa442Threshold);
        cloudClassificationParameters.put("userDefinedMDSIThreshold", ccUserDefinedMDSIThreshold);
        cloudClassificationParameters.put("userDefinedNDVIThreshold", ccUserDefinedNDVIThreshold);
        cloudClassificationParameters.put("rhoAgReferenceWavelength", ccRhoAgReferenceWavelength);
        cloudClassificationParameters.put("seaIceThreshold", ccSeaIceThreshold);
        cloudClassificationParameters.put("schillerAmbiguous", ccSchillerAmbiguous);
        cloudClassificationParameters.put("schillerSure", ccSchillerSure);
        cloudClassificationParameters.put("ccOutputSeaIceClimatologyValue", ccOutputSeaIceClimatologyValue);
        cloudClassificationParameters.put("ccOutputRhoglintDebugValues", ccOutputRhoglintDebugValues);
        cloudClassificationParameters.put("ccOutputSchillerCloudValue", ccOutputSchillerCloudValue);
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
        if (ccOutputRad2Refl) {
            IdepixProducts.addRadiance2ReflectanceBands(rad2reflProduct, targetProduct);
        }
        if (ccOutputL2Pressures) {
            IdepixProducts.addL2PressureBands(merisCloudProduct, targetProduct);
        }
        if (ccOutputGaseous) {
            IdepixProducts.addGaseousCorrectionBands(gasProduct, targetProduct);
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
        if (ccOutputRhoglintDebugValues) {
            IdepixProducts.addCCRhoGlintDebugValueBands(merisCloudProduct, targetProduct);
        }
        if (ccOutputSchillerCloudValue) {
            IdepixProducts.addCCSchillerCloudValueBand(merisCloudProduct, targetProduct);
        }

        if (ccOutputL2CloudDetection) {
            addCloudClassificationFlagBandCoastColour();
            Band cloudFlagBand = targetProduct.getBand(CoastColourClassificationOp.CLOUD_FLAGS);
            cloudFlagBand.setSourceImage(ccPostProcessingProduct.getBand(CoastColourClassificationOp.CLOUD_FLAGS).getSourceImage());
        }
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
            super(CoastColourOp.class, "idepix.coastcolour");
        }
    }
}
