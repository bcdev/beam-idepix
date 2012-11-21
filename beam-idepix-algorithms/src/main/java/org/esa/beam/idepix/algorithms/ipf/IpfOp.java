package org.esa.beam.idepix.algorithms.ipf;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
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
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.operators.IdepixCloudClassificationOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.meris.cloud.BlueBandOp;
import org.esa.beam.meris.cloud.CloudProbabilityOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.util.ProductUtils;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.ipf",
                  version = "1.4-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with IPF (former MEPIX) algorithm.")
public class IpfOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // IPF parameters
    @Parameter(defaultValue = "true", label = " TOA Reflectances")
    private boolean ipfOutputRad2Refl = true;

    @Parameter(defaultValue = "false", label = " Gas Absorption Corrected Reflectances")
    private boolean ipfOutputGaseous = false;

    @Parameter(defaultValue = "true", label = " Land/Water Reclassification Flags")
    private boolean ipfOutputLandWater = true;

    @Parameter(defaultValue = "true", label = " Rayleigh Corrected Reflectances")
    private boolean ipfOutputRayleigh = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Top Pressure and Surface Pressure")
    private boolean ipfOutputL2Pressures = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Detection Flags")
    private boolean ipfOutputL2CloudDetection = true;

    // QWG specific test options
    @Parameter(label = " P1 Pressure Threshold ", defaultValue = "125.0")
    private double ipfQWGUserDefinedP1PressureThreshold = 125.0;
    @Parameter(label = " PScatt Pressure Threshold ", defaultValue = "700.0")
    private double ipfQWGUserDefinedPScattPressureThreshold = 700.0;

    @Parameter(label = " User Defined Delta RhoTOA442 Threshold ", defaultValue = "0.03")
    private double ipfQWGUserDefinedDeltaRhoToa442Threshold;
    @Parameter(label = " Theoretical Glint Threshold", defaultValue = "0.015")
    public double ipfQWGUserDefinedGlintThreshold;

    @Parameter(label = " RhoTOA753 Threshold ", defaultValue = "0.1")
    private double ipfQWGUserDefinedRhoToa753Threshold = 0.1;
    @Parameter(label = " RhoTOA Ratio 753/775 Threshold ", defaultValue = "0.15")
    private double ipfQWGUserDefinedRhoToaRatio753775Threshold = 0.15;
    @Parameter(label = " MDSI Threshold ", defaultValue = "0.01")
    private double ipfQWGUserDefinedMDSIThreshold = 0.01;
    @Parameter(label = " NDVI Threshold ", defaultValue = "0.1")
    private double userDefinedNDVIThreshold;

    @Parameter(label = " Bright Test Threshold ", defaultValue = "0.03")
    private double ipfQWGUserDefinedRhoToa442Threshold = 0.03;   // default changed from 0.185, 2011/03/25
    @Parameter(label = " Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412",
                       "442",
                       "490",
                       "510",
                       "560",
                       "620",
                       "665",
                       "681",
                       "705",
                       "753",
                       "760",
                       "775",
                       "865",
                       "890",
                       "900"
               })
    private int rhoAgReferenceWavelength;   // default changed from 442, 2011/03/25

    // Pressure product parameters
    @Parameter(defaultValue = "true", label = " Barometric Pressure")
    private boolean pressureOutputPbaro = true;

    @Parameter(defaultValue = "false", label = " Use GETASSE30 DEM for Barometric Pressure Computation")
    private boolean pressurePbaroGetasse = false;

    @Parameter(defaultValue = "true", label = " Surface Pressure (FUB, O2 project)")
    private boolean pressureOutputPsurfFub = true;

    @Parameter(defaultValue = "false", label = " Apply Tropical Atmosphere (instead of USS standard) in FUB algorithm")
    private boolean pressureFubTropicalAtmosphere = false;

    @Parameter(defaultValue = "false",
               label = " L2 Cloud Top Pressure with FUB Straylight Correction (applied to RR products only!)")
    private boolean pressureQWGOutputCtpStraylightCorrFub = false;

    @Parameter(defaultValue = "false", label = " 'P1' (LISE, O2 project, all surfaces)")
    private boolean pressureOutputP1Lise = false;

    @Parameter(defaultValue = "false", label = " Surface Pressure (LISE, O2 project, land)")
    private boolean pressureOutputPSurfLise = false;

    @Parameter(defaultValue = "false", label = " 'P2' (LISE, O2 project, ocean)")
    private boolean pressureOutputP2Lise = false;

    @Parameter(defaultValue = "false", label = " 'PScatt' (LISE, O2 project, ocean)")
    private boolean pressureOutputPScattLise = false;


    // Cloud product parameters
    @Parameter(defaultValue = "false", label = " Blue Band Flags")
    private boolean cloudOutputBlueBand = false;

    @Parameter(defaultValue = "false", label = " Cloud Probability")
    private boolean cloudOutputCloudProbability = false;

    @Parameter(defaultValue = "false", label = " Combined Clouds Flags")
    private boolean cloudOutputCombinedCloud = false;


    private Product merisCloudProduct;
    private Product rayleighProduct;
    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product pbaroProduct;
    private Product ctpProduct;

    private boolean straylightCorr;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.IPF);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processQwg();
        renameL1bMaskNames(targetProduct);
    }

    private void processQwg() {
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
        pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, pressurePbaroGetasse);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);

        Product ctpStraylightProduct = null;
        if (pressureQWGOutputCtpStraylightCorrFub) {
            straylightCorr = sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME);
            // currently, apply straylight correction for RR products only...
            if (straylightCorr) {
                ctpStraylightProduct = IdepixProducts.computeCloudTopPressureStraylightProduct(sourceProduct, straylightCorr);
            }
        }
        pressureLiseProduct = IdepixProducts.
                computePressureLiseProduct(sourceProduct, rad2reflProduct, ipfOutputL2CloudDetection,
                                           straylightCorr,
                                           pressureOutputP1Lise,
                                           pressureOutputPSurfLise,
                                           pressureOutputP2Lise,
                                           pressureOutputPScattLise);

        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous ||
                pressureOutputPsurfFub || ipfOutputL2Pressures || ipfOutputL2CloudDetection) {
            merisCloudProduct = IdepixProducts.computeMerisCloudProduct(sourceProduct, rad2reflProduct, ctpProduct,
                                                    pressureLiseProduct, pbaroProduct,ipfOutputL2Pressures);
        }

        Product gasProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous) {
            gasProduct = IdepixProducts.
                    computeGaseousCorrectionProduct(sourceProduct, rad2reflProduct, merisCloudProduct, true);
        }

        Product landProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater) {
            landProduct = IdepixProducts.computeLandClassificationProduct(sourceProduct, gasProduct);
        }

        if (ipfOutputRayleigh) {
            rayleighProduct = IdepixProducts.computeRayleighCorrectionProduct(sourceProduct,
                                                                              gasProduct,
                                                                              rad2reflProduct,
                                                                              landProduct,
                                                                              merisCloudProduct,
                                                                              ipfOutputRayleigh,
                                                                              LandClassificationOp.LAND_FLAGS + ".F_LANDCONS");
        }

        Product blueBandProduct = null;
        if (cloudOutputBlueBand || cloudOutputCombinedCloud) {
            final Product brrProduct = IdepixProducts.computeBrrProduct(sourceProduct, true, true);
            blueBandProduct = IdepixProducts.computeBlueBandProduct(sourceProduct, brrProduct);
        }

        // Cloud Probability
        Product cloudProbabilityProduct = null;
        if (cloudOutputCloudProbability || cloudOutputCombinedCloud) {
            cloudProbabilityProduct = IdepixProducts.computeCloudProbabilityProduct(sourceProduct);
        }

        // Surface Pressure NN (FUB)
        Product psurfNNProduct = null;
        if (pressureOutputPsurfFub) {
            psurfNNProduct = IdepixProducts.computePsurfNNProduct(sourceProduct, merisCloudProduct,
                                                                  pressureFubTropicalAtmosphere,
                                                                  straylightCorr);
        }

        // Combined Cloud
        Product combinedCloudProduct = null;
        if (cloudOutputCombinedCloud) {
            combinedCloudProduct = IdepixProducts.computeCombinedCloudProduct(blueBandProduct, cloudProbabilityProduct);
        }

        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        if (ipfOutputRad2Refl) {
            addRadiance2ReflectanceBands();
        }

        if (ipfOutputL2CloudDetection) {
            addCloudClassificationFlagBand();
        }

        if (ipfOutputGaseous) {
            addGaseousCorrectionBands(gasProduct);
        }
        if (ipfOutputRayleigh) {
            addRayleighCorrectionBands();
        }
        if (ipfOutputLandWater) {
            addLandClassificationBand(landProduct);
        }
        if (straylightCorr && pressureQWGOutputCtpStraylightCorrFub) {
            addCtpStraylightProductBands(ctpStraylightProduct);
        }
        if (pressureOutputPbaro) {
            addBarometricPressureProductBands();
        }
        if (pressureOutputPsurfFub) {
            addPsurfNNProductBands(psurfNNProduct);
        }
        addPressureLiseProductBands();
        if (cloudOutputBlueBand) {
            addBlueBandProductBands(blueBandProduct);
        }
        if (cloudOutputCloudProbability) {
            addCloudProbabilityProductBands(cloudProbabilityProduct);
        }
        if (cloudOutputCombinedCloud) {
            addCombinedCloudProductBands(combinedCloudProduct);
        }

        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        IdepixCloudClassificationOp.addBitmasks(sourceProduct, targetProduct);
    }

    private void addCombinedCloudProductBands(Product combinedCloudProduct) {
        FlagCoding flagCoding = CombinedCloudOp.createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        Band band = combinedCloudProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME);
        band.setSampleCoding(flagCoding);
        moveBand(combinedCloudProduct, CombinedCloudOp.FLAG_BAND_NAME);
    }

    private void addCloudProbabilityProductBands(Product cloudProbabilityProduct) {
        FlagCoding flagCoding = CloudProbabilityOp.createCloudFlagCoding(targetProduct);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : cloudProbabilityProduct.getBands()) {
            if (!targetProduct.containsBand(band.getName())) {
                if (band.getName().equals(CloudProbabilityOp.CLOUD_FLAG_BAND)) {
                    band.setSampleCoding(flagCoding);
                }
                targetProduct.addBand(band);
            }
        }
    }

    private void addRadiance2ReflectanceBands() {
        for (String bandname : rad2reflProduct.getBandNames()) {
            moveBand(rad2reflProduct, bandname);
        }
    }

    private void addCloudClassificationFlagBand() {
        FlagCoding flagCoding = CoastColourClassificationOp.createFlagCoding(
                CoastColourClassificationOp.CLOUD_FLAGS);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : merisCloudProduct.getBands()) {
            if (band.getName().equals(CoastColourClassificationOp.CLOUD_FLAGS)) {
                band.setSampleCoding(flagCoding);
                targetProduct.addBand(band);
            }
        }
    }

    private void moveBand(Product product, String bandname) {
        if (!targetProduct.containsBand(bandname)) {
            targetProduct.addBand(product.getBand(bandname));
        }
    }

    private void addBlueBandProductBands(Product blueBandProduct) {
        FlagCoding flagCoding = BlueBandOp.createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        Band band = blueBandProduct.getBand(BlueBandOp.BLUE_FLAG_BAND);
        band.setSampleCoding(flagCoding);
        moveBand(blueBandProduct, BlueBandOp.BLUE_FLAG_BAND);
    }

    private void addPressureLiseProductBands() {
        if (pressureOutputP1Lise) {
            addPressureLiseProductBand(LisePressureOp.PRESSURE_LISE_P1);
        }
        if (pressureOutputPSurfLise) {
            addPressureLiseProductBand(LisePressureOp.PRESSURE_LISE_PSURF);
        }
        if (pressureOutputP2Lise) {
            addPressureLiseProductBand(LisePressureOp.PRESSURE_LISE_P2);
        }
        if (pressureOutputPScattLise) {
            addPressureLiseProductBand(LisePressureOp.PRESSURE_LISE_PSCATT);
        }
    }


    private void addPressureLiseProductBand(String bandname) {
        moveBand(pressureLiseProduct, bandname);
    }

    private void addPsurfNNProductBands(Product psurfNNProduct) {
        for (String bandname : psurfNNProduct.getBandNames()) {
            moveBand(psurfNNProduct, bandname);
        }
    }

    private void addBarometricPressureProductBands() {
        for (String bandname : pbaroProduct.getBandNames()) {
            moveBand(pbaroProduct, bandname);
        }
    }


    private void addCtpStraylightProductBands(Product ctpProductStraylight) {
        for (String bandname : ctpProductStraylight.getBandNames()) {
            if (!bandname.equals(IdepixCloudClassificationOp.CLOUD_FLAGS)) {
                moveBand(ctpProductStraylight, bandname);
            }
        }
    }

    private void addLandClassificationBand(Product landProduct) {
        FlagCoding flagCoding = LandClassificationOp.createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : landProduct.getBands()) {
            if (!targetProduct.containsBand(band.getName())) {
                if (band.getName().equals(LandClassificationOp.LAND_FLAGS)) {
                    band.setSampleCoding(flagCoding);
                }
                targetProduct.addBand(band);
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

    private void addGaseousCorrectionBands(Product gasProduct) {
        FlagCoding flagCoding = GaseousCorrectionOp.createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        Band band = gasProduct.getBand(GaseousCorrectionOp.GAS_FLAGS);
        band.setSampleCoding(flagCoding);
        targetProduct.addBand(band);
    }



    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IpfOp.class);
        }
    }
}
