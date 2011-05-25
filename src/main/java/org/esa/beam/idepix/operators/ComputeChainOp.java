/* 
 * Copyright (C) 2002-2008 by Brockmann Consult
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.idepix.operators;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.BrrOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.meris.cloud.BlueBandOp;
import org.esa.beam.meris.cloud.CloudProbabilityOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.util.BeamConstants;
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * IDEPIX main operator setting up a chain of existing operators.
 *
 * @author Olaf Danne
 * @version $Revision: 7609 $ $Date: 2009-12-18 17:51:22 +0100 (Fr, 18 Dez 2009) $
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.ComputeChain",
                  version = "1.2",
                  authors = "Olaf Danne, Carsten Brockmann",
                  copyright = "(c) 2010 by Brockmann Consult",
                  description = "Pixel identification and classification. This operator just calls a chain of other operators.")
public class ComputeChainOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;


    // Cloud screening parameters
    @Parameter(defaultValue = "GlobAlbedo", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;


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

    // Coastcolour parameters
    @Parameter(defaultValue = "true", label = " TOA Reflectances")
    private boolean ccOutputRad2Refl = true;

    @Parameter(defaultValue = "false", label = " Gas Absorption Corrected Reflectances")
    private boolean ccOutputGaseous = false;

    @Parameter(defaultValue = "true", label = " Land/Water Reclassification Flags")
    private boolean ccOutputLandWater = true;

    @Parameter(defaultValue = "true", label = " Rayleigh Corrected Reflectances")
    private boolean ccOutputRayleigh = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Top Pressure and Surface Pressure")
    private boolean ccOutputL2Pressures = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Detection Flags")
    private boolean ccOutputL2CloudDetection = true;

    @Parameter(defaultValue = "true", label = " Anas cloud optimisation")
    private boolean enableAnasOptimisation;


    @Parameter(label = " PScatt Pressure Threshold ", defaultValue = "700.0")
    private double ccUserDefinedPScattPressureThreshold = 700.0;

    @Parameter(label = " Theoretical Glint Threshold", defaultValue = "0.015")
    public double ccUserDefinedGlintThreshold;

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
    private int ccRhoAgReferenceWavelength;   // default changed from 442, 2011/03/25


    private boolean straylightCorr;
    private Product merisCloudProduct;
    private Product rayleighProduct;
    //    private Product correctedRayleighProduct;
    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product pbaroProduct;
    private Product ctpProduct;

    @Override
    public void initialize() throws OperatorException {

//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // for debugging purpose

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, algorithm);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        if (isQWGAlgo()) {
            processQwg();
        } else if (isCoastColourAlgo()) {
            processCoastColour();
        } else if (isGlobAlbedoAlgo()) {
            processGlobAlbedo();
        }

        renameL1bMaskNames();
    }

    private void renameL1bMaskNames() {
        prefixMask("coastline");
        prefixMask("land");
        prefixMask("water");
        prefixMask("cosmetic");
        prefixMask("duplicated");
        prefixMask("glint_risk");
        prefixMask("suspect");
        prefixMask("bright");
        prefixMask("invalid");
    }

    private void prefixMask(String coastline) {
        Mask coastlineMask = targetProduct.getMaskGroup().get(coastline);
        if (coastlineMask != null) {
            coastlineMask.setName("l1b_" + coastlineMask.getName());
        }
    }

    private void processQwg() {
        // Radiance to Reflectance
        computeRad2reflProduct();

        // Barometric Pressure
        computePbaroProduct();

        // Cloud Top Pressure
        computeCtpProduct();

        // Cloud Top Pressure with FUB Straylight Correction
        Product ctpProductStraylight = computeCtpStraylightProduct();

        // Pressure (LISE)
        computePressureLiseProduct();

        // Cloud Classification
        merisCloudProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous ||
            pressureOutputPsurfFub || ipfOutputL2Pressures || ipfOutputL2CloudDetection
            || isGlobAlbedoAlgo()) {
            computeMerisCloudProduct();
        }

        // Gaseous Correction
        Product gasProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous || isGlobAlbedoAlgo()) {
            gasProduct = computeGaseousCorrectionProduct();
        }

        // Land Water Reclassification
        Product landProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater || isGlobAlbedoAlgo()) {
            landProduct = computeLandClassificationProduct(gasProduct);
        }

        // Rayleigh Correction
        if (ipfOutputRayleigh || isGlobAlbedoAlgo()) {
            computeRayleighCorrectionProduct(gasProduct, landProduct);
        }

        // Blue Band
        Product blueBandProduct = null;
        if (isQWGAlgo() && (cloudOutputBlueBand || cloudOutputCombinedCloud)) {
            // BRR
            blueBandProduct = computeBlueBandProduct();
        }

        // Cloud Probability
        Product cloudProbabilityProduct = null;
        if (isQWGAlgo() && (cloudOutputCloudProbability || cloudOutputCombinedCloud)) {
            cloudProbabilityProduct = computeCloudProbabilityProduct();
        }

        // Surface Pressure NN (FUB)
        Product psurfNNProduct = null;
        if (isQWGAlgo() && pressureOutputPsurfFub) {
            psurfNNProduct = computePsurfNNProduct();
        }

        // Combined Cloud
        Product combinedCloudProduct = null;
        if (isQWGAlgo() && cloudOutputCombinedCloud) {
            combinedCloudProduct = computeCombinedCloudProduct(blueBandProduct, cloudProbabilityProduct);
        }

        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        fillTargetProduct(ctpProductStraylight, gasProduct, landProduct, blueBandProduct, cloudProbabilityProduct,
                          psurfNNProduct, combinedCloudProduct);

        ProductUtils.copyFlagBands(sourceProduct, targetProduct);
        Band l1FlagsSourceBand = sourceProduct.getBand(BeamConstants.MERIS_L1B_FLAGS_DS_NAME);
        Band l1FlagsTargetBand = targetProduct.getBand(BeamConstants.MERIS_L1B_FLAGS_DS_NAME);
        l1FlagsTargetBand.setSourceImage(l1FlagsSourceBand.getSourceImage());

        IdepixCloudClassificationOp.addBitmasks(sourceProduct, targetProduct);
    }

    private void processCoastColour() {
        // Radiance to Reflectance
        computeRad2reflProduct();

        // Cloud Top Pressure
        computeCtpProduct();

        // Pressure (LISE)
        computePressureLiseProduct();

        // Cloud Classification
        computeCoastColourMerisCloudProduct();

        // Gaseous Correction
        Product gasProduct = computeGaseousCorrectionProduct();

        // Land Water Reclassification
        Product landProduct = computeLandClassificationProduct(gasProduct);
//        Product landProduct = computeCoastColourLandClassificationProduct(gasProduct);

        // Rayleigh Correction
        if (ccOutputRayleigh) {
            computeRayleighCorrectionProduct(gasProduct, landProduct);
        }

        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        fillTargetProduct(null, gasProduct, landProduct, null, null, null, null);

        ProductUtils.copyFlagBands(sourceProduct, targetProduct);
        Band l1FlagsSourceBand = sourceProduct.getBand(BeamConstants.MERIS_L1B_FLAGS_DS_NAME);
        Band l1FlagsTargetBand = targetProduct.getBand(BeamConstants.MERIS_L1B_FLAGS_DS_NAME);
        l1FlagsTargetBand.setSourceImage(l1FlagsSourceBand.getSourceImage());

        CoastColourCloudClassificationOp.addBitmasks(sourceProduct, targetProduct);
    }


    private Product computeCombinedCloudProduct(Product blueBandProduct, Product cloudProbabilityProduct) {
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        Product combinedCloudProduct;
        Map<String, Product> combinedCloudInput = new HashMap<String, Product>(2);
        combinedCloudInput.put("cloudProb", cloudProbabilityProduct);
        combinedCloudInput.put("blueBand", blueBandProduct);
        combinedCloudProduct = GPF.createProduct("Meris.CombinedCloud", emptyParams, combinedCloudInput);
        return combinedCloudProduct;
    }

    private Product computePsurfNNProduct() {
        Product psurfNNProduct;
        Map<String, Product> psurfNNInput = new HashMap<String, Product>(2);
        psurfNNInput.put("l1b", sourceProduct);
        psurfNNInput.put("cloud", merisCloudProduct);
        Map<String, Object> psurfNNParameters = new HashMap<String, Object>(2);
        psurfNNParameters.put("tropicalAtmosphere", pressureFubTropicalAtmosphere);
        // mail from RL, 2009/03/19: always apply correction on FUB pressure
        // currently only for RR (FR coefficients still missing)
        psurfNNParameters.put("straylightCorr", straylightCorr);
        psurfNNProduct = GPF.createProduct("Meris.SurfacePressureFub", psurfNNParameters, psurfNNInput);
        return psurfNNProduct;
    }

    private Product computeCloudProbabilityProduct() {
        Product cloudProbabilityProduct;
        Map<String, Product> cloudProbabilityInput = new HashMap<String, Product>(1);
        cloudProbabilityInput.put("input", sourceProduct);
        Map<String, Object> cloudProbabilityParameters = new HashMap<String, Object>(3);
        cloudProbabilityParameters.put("configFile", "cloud_config.txt");
        cloudProbabilityParameters.put("validLandExpression", "not l1_flags.INVALID and dem_alt > -50");
        cloudProbabilityParameters.put("validOceanExpression", "not l1_flags.INVALID and dem_alt <= -50");
        cloudProbabilityProduct = GPF.createProduct("Meris.CloudProbability", cloudProbabilityParameters,
                                                    cloudProbabilityInput);
        return cloudProbabilityProduct;
    }

    private Product computeBlueBandProduct() {
        Product brrProduct = computeBrrProduct();
        Product blueBandProduct;

        Map<String, Object> emptyParams = new HashMap<String, Object>();
        Map<String, Product> blueBandInput = new HashMap<String, Product>(2);
        blueBandInput.put("l1b", sourceProduct);
        blueBandInput.put("toar", brrProduct);
        blueBandProduct = GPF.createProduct("Meris.BlueBand", emptyParams, blueBandInput);
        return blueBandProduct;
    }

    private Product computeBrrProduct() {
        Map<String, Product> brrInput = new HashMap<String, Product>(1);
        brrInput.put("input", sourceProduct);
        Map<String, Object> brrParameters = new HashMap<String, Object>(2);
        brrParameters.put("outputToar", true);
        brrParameters.put("correctWater", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(BrrOp.class), brrParameters, brrInput);
    }

    private void computeRayleighCorrectionProduct(Product gasProduct, Product landProduct) {
        Map<String, Product> rayleighInput = new HashMap<String, Product>(3);
        rayleighInput.put("l1b", sourceProduct);
        rayleighInput.put("input", gasProduct);
        rayleighInput.put("rhotoa", rad2reflProduct);
        rayleighInput.put("land", landProduct);
        rayleighInput.put("cloud", merisCloudProduct);
        Map<String, Object> rayleighParameters = new HashMap<String, Object>(2);
        rayleighParameters.put("correctWater", true);
        rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixRayleighCorrectionOp.class),
                                            rayleighParameters, rayleighInput);
    }

    private Product computeLandClassificationProduct(Product gasProduct) {
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        Product landProduct;
        Map<String, Product> landInput = new HashMap<String, Product>(2);
        landInput.put("l1b", sourceProduct);
        landInput.put("gascor", gasProduct);
        landProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), emptyParams,
                                        landInput);
        return landProduct;
    }

    private Product computeGaseousCorrectionProduct() {
        Product gasProduct;
        Map<String, Product> gasInput = new HashMap<String, Product>(3);
        gasInput.put("l1b", sourceProduct);
        gasInput.put("rhotoa", rad2reflProduct);
        gasInput.put("cloud", merisCloudProduct);
        Map<String, Object> gasParameters = new HashMap<String, Object>(2);
        gasParameters.put("correctWater", true);
        gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), gasParameters,
                                       gasInput);
        return gasProduct;
    }

    private Product computeCtpStraylightProduct() {
        Product ctpProductStraylight = null;
        // currently, apply straylight correction for RR products only...
        straylightCorr = sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME);
        if (isQWGAlgo() && straylightCorr && pressureQWGOutputCtpStraylightCorrFub) {
            Map<String, Object> ctpParameters = new HashMap<String, Object>(1);
            ctpParameters.put("straylightCorr", straylightCorr);
            ctpProductStraylight = GPF.createProduct("Meris.CloudTopPressureOp", ctpParameters, sourceProduct);
        }
        return ctpProductStraylight;
    }

    private void computePbaroProduct() {
        Map<String, Object> pbaroParameters = new HashMap<String, Object>(1);
        pbaroParameters.put("useGetasseDem", pressurePbaroGetasse);
        pbaroProduct = GPF.createProduct("Meris.BarometricPressure", pbaroParameters, sourceProduct);
    }

    private void computeCtpProduct() {
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        ctpProduct = GPF.createProduct("Meris.CloudTopPressureOp", emptyParams, sourceProduct);
    }

    private void computeRad2reflProduct() {
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), emptyParams,
                                            sourceProduct);
    }

    private void computeMerisCloudProduct() {
        if (isQWGAlgo() || isGlobAlbedoAlgo()) {
            Map<String, Product> cloudInput = new HashMap<String, Product>(4);
            cloudInput.put("l1b", sourceProduct);
            cloudInput.put("rhotoa", rad2reflProduct);
            cloudInput.put("ctp", ctpProduct);
            cloudInput.put("pressureOutputLise", pressureLiseProduct);

            Map<String, Object> cloudClassificationParameters = new HashMap<String, Object>(11);
            cloudClassificationParameters.put("l2Pressures", ipfOutputL2Pressures || isGlobAlbedoAlgo());
            cloudClassificationParameters.put("userDefinedP1PressureThreshold", ipfQWGUserDefinedP1PressureThreshold);
            cloudClassificationParameters.put("userDefinedPScattPressureThreshold",
                                              ipfQWGUserDefinedPScattPressureThreshold);
            cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ipfQWGUserDefinedRhoToa442Threshold);
            cloudClassificationParameters.put("userDefinedDeltaRhoToa442Threshold",
                                              ipfQWGUserDefinedDeltaRhoToa442Threshold);
            cloudClassificationParameters.put("userDefinedRhoToa753Threshold", ipfQWGUserDefinedRhoToa753Threshold);
            cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ipfQWGUserDefinedRhoToa442Threshold);
            cloudClassificationParameters.put("userDefinedRhoToaRatio753775Threshold",
                                              ipfQWGUserDefinedRhoToaRatio753775Threshold);
            cloudClassificationParameters.put("userDefinedMDSIThreshold", ipfQWGUserDefinedMDSIThreshold);
            cloudClassificationParameters.put("userDefinedNDVIThreshold", userDefinedNDVIThreshold);

            cloudInput.put("pressureBaro", pbaroProduct);
            merisCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudClassificationOp.class),
                                                  cloudClassificationParameters, cloudInput);
        }
    }

    private void computeCoastColourMerisCloudProduct() {
        Map<String, Product> cloudInput = new HashMap<String, Product>(4);
        cloudInput.put("l1b", sourceProduct);
        cloudInput.put("rhotoa", rad2reflProduct);
        cloudInput.put("ctp", ctpProduct);
        cloudInput.put("pressureOutputLise", pressureLiseProduct);

        Map<String, Object> cloudClassificationParameters = new HashMap<String, Object>(11);
        cloudClassificationParameters.put("l2Pressures", ccOutputL2Pressures);
        cloudClassificationParameters.put("l2CloudDetection", ccOutputL2CloudDetection);
        cloudClassificationParameters.put("userDefinedPScattPressureThreshold",
                                          ccUserDefinedPScattPressureThreshold);
        cloudClassificationParameters.put("userDefinedGlintThreshold", ccUserDefinedGlintThreshold);
        cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ccUserDefinedRhoToa442Threshold);
        cloudClassificationParameters.put("userDefinedRhoToa753Threshold", ccUserDefinedRhoToa753Threshold);
        cloudClassificationParameters.put("userDefinedRhoToa442Threshold", ccUserDefinedRhoToa442Threshold);
        cloudClassificationParameters.put("userDefinedMDSIThreshold", ccUserDefinedMDSIThreshold);
        cloudClassificationParameters.put("userDefinedNDVIThreshold", ccUserDefinedNDVIThreshold);
        cloudClassificationParameters.put("rhoAgReferenceWavelength", ccRhoAgReferenceWavelength);
        cloudClassificationParameters.put("enableAnasOptimisation", enableAnasOptimisation);
        merisCloudProduct = GPF.createProduct(
                OperatorSpi.getOperatorAlias(CoastColourCloudClassificationOp.class),
                cloudClassificationParameters, cloudInput);
    }

    private void computePressureLiseProduct() {
        pressureLiseProduct = null;
        Map<String, Product> pressureLiseInput = new HashMap<String, Product>(2);
        pressureLiseInput.put("l1b", sourceProduct);
        pressureLiseInput.put("rhotoa", rad2reflProduct);
        Map<String, Object> pressureLiseParameters = new HashMap<String, Object>(6);
        pressureLiseParameters.put("straylightCorr",
                                   false);   // mail from RL/RS, 2009/03/19: do not apply correction on LISE pressure
        pressureLiseParameters.put("outputP1", true);
        pressureLiseParameters.put("outputPressureSurface", pressureOutputPSurfLise);
        pressureLiseParameters.put("outputP2", pressureOutputP2Lise);
        pressureLiseParameters.put("outputPScatt", true);
        pressureLiseParameters.put("l2CloudDetection", ipfOutputL2CloudDetection);
        pressureLiseProduct = GPF.createProduct("Meris.LisePressure", pressureLiseParameters, pressureLiseInput);
    }

    private void fillTargetProduct(Product ctpProductStraylight, Product gasProduct, Product landProduct,
                                   Product blueBandProduct, Product cloudProbabilityProduct, Product psurfNNProduct,
                                   Product combinedCloudProduct) {
        addRad2ReflBands();
        addMerisCloudProductBands();
        addCloudClassificationFlagBand();
        addGaseousCorrectionBands(gasProduct);
        addRayleighCorrectionBands();
        addLandWaterClassificationFlagBand(landProduct);
        addCtpProductBands(ctpProductStraylight);
        addPbaroProductBands();
        addPsurfNNProductBands(psurfNNProduct);
        addPressureLiseProductBands();
        addBlueBandProductBands(blueBandProduct);
        addCloudProbabilityProductBands(cloudProbabilityProduct);
        addCombinedCloudProductBands(combinedCloudProduct);
    }

    private void addCombinedCloudProductBands(Product combinedCloudProduct) {
        if (isQWGAlgo() && cloudOutputCombinedCloud) {
            FlagCoding flagCoding = CombinedCloudOp.createFlagCoding();
            targetProduct.getFlagCodingGroup().add(flagCoding);
            for (Band band : combinedCloudProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(CombinedCloudOp.FLAG_BAND_NAME)) {
                        band.setSampleCoding(flagCoding);
                    }
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addCloudProbabilityProductBands(Product cloudProbabilityProduct) {
        if (isQWGAlgo() && cloudOutputCloudProbability) {
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
    }

    private void addBlueBandProductBands(Product blueBandProduct) {
        if (isQWGAlgo() && cloudOutputBlueBand) {
            FlagCoding flagCoding = BlueBandOp.createFlagCoding();
            targetProduct.getFlagCodingGroup().add(flagCoding);
            for (Band band : blueBandProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(BlueBandOp.BLUE_FLAG_BAND)) {
                        band.setSampleCoding(flagCoding);
                    }
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addPressureLiseProductBands() {
        if (isQWGAlgo() && pressureOutputP1Lise) {
            for (Band band : pressureLiseProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(LisePressureOp.PRESSURE_LISE_P1)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }

        if (isQWGAlgo() && pressureOutputPSurfLise) {
            for (Band band : pressureLiseProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(LisePressureOp.PRESSURE_LISE_PSURF)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }

        if (isQWGAlgo() && pressureOutputP2Lise) {
            for (Band band : pressureLiseProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(LisePressureOp.PRESSURE_LISE_P2)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }

        if (isQWGAlgo() && pressureOutputPScattLise) {
            for (Band band : pressureLiseProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(LisePressureOp.PRESSURE_LISE_PSCATT)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }
    }

    private void addPsurfNNProductBands(Product psurfNNProduct) {
        if (isQWGAlgo() && pressureOutputPsurfFub) {
            for (Band band : psurfNNProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addPbaroProductBands() {
        if (isQWGAlgo() && pressureOutputPbaro) {
            for (Band band : pbaroProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addCtpProductBands(Product ctpProductStraylight) {
        if (isQWGAlgo() && straylightCorr && pressureQWGOutputCtpStraylightCorrFub) {
            for (Band band : ctpProductStraylight.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (!band.getName().equals(IdepixCloudClassificationOp.CLOUD_FLAGS)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }
    }

    private void addLandWaterClassificationFlagBand(Product landProduct) {
        if ((isQWGAlgo() && ipfOutputLandWater) || (isCoastColourAlgo() && ccOutputLandWater)) {
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
    }

    private void addRayleighCorrectionBands() {
        if ((isQWGAlgo() && ipfOutputRayleigh) || (isCoastColourAlgo() && ccOutputRayleigh)) {
            int l1_band_num = RayleighCorrectionOp.L1_BAND_NUM;
            FlagCoding flagCoding = RayleighCorrectionOp.createFlagCoding(l1_band_num);
            targetProduct.getFlagCodingGroup().add(flagCoding);
            for (Band band : rayleighProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (band.getName().equals(RayleighCorrectionOp.RAY_CORR_FLAGS)) {
                        band.setSampleCoding(flagCoding);
                    }
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addGaseousCorrectionBands(Product gasProduct) {
        if ((isQWGAlgo() && ipfOutputGaseous) || (isCoastColourAlgo() && ccOutputGaseous)) {
            FlagCoding flagCoding = GaseousCorrectionOp.createFlagCoding();
            targetProduct.getFlagCodingGroup().add(flagCoding);
            for (Band band : gasProduct.getBands()) {
                if (band.getName().equals(GaseousCorrectionOp.GAS_FLAGS)) {
                    band.setSampleCoding(flagCoding);
                }
                targetProduct.addBand(band);
            }
        }
    }

    private void addCloudClassificationFlagBand() {
        if ((isQWGAlgo() && ipfOutputL2CloudDetection) || (isCoastColourAlgo() && ccOutputL2CloudDetection)) {
            FlagCoding flagCoding = IdepixCloudClassificationOp.createFlagCoding(
                    IdepixCloudClassificationOp.CLOUD_FLAGS);
            targetProduct.getFlagCodingGroup().add(flagCoding);
            for (Band band : merisCloudProduct.getBands()) {
                if (band.getName().equals(IdepixCloudClassificationOp.CLOUD_FLAGS)) {
                    band.setSampleCoding(flagCoding);
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private void addMerisCloudProductBands() {
        if (isQWGAlgo() && ipfOutputL2Pressures) {
            for (Band band : merisCloudProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    if (!band.getName().equals(IdepixCloudClassificationOp.CLOUD_FLAGS)) {
                        targetProduct.addBand(band);
                    }
                }
            }
        }
    }

    private void addRad2ReflBands() {
        if ((isQWGAlgo() && ipfOutputRad2Refl) || (isCoastColourAlgo() && ccOutputRad2Refl)) {
            for (Band band : rad2reflProduct.getBands()) {
                if (!targetProduct.containsBand(band.getName())) {
                    targetProduct.addBand(band);
                }
            }
        }
    }

    private boolean isQWGAlgo() {
        return CloudScreeningSelector.QWG.equals(algorithm);
    }

    private boolean isGlobAlbedoAlgo() {
        return CloudScreeningSelector.GlobAlbedo.equals(algorithm);
    }

    private boolean isCoastColourAlgo() {
        return CloudScreeningSelector.CoastColour.equals(algorithm);
    }

    private void processGlobAlbedo() {
        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            processQwg();
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

        gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GACloudScreeningOp.class),
                                           gaCloudClassificationParameters, gaCloudInput);

        // add cloud shadow flag to the cloud product computed above...
        if (gaComputeMerisCloudShadow && IdepixUtils.isValidMerisProduct(sourceProduct)) {
            Map<String, Product> gaFinalCloudInput = new HashMap<String, Product>(4);
            gaFinalCloudInput.put("gal1b", sourceProduct);
            gaFinalCloudInput.put("cloud", gaCloudProduct);
            gaFinalCloudInput.put("ctp", ctpProduct);   // may be null
            Map<String, Object> gaFinalCloudClassificationParameters = new HashMap<String, Object>(1);
            gaFinalCloudClassificationParameters.put("ctpMode", ctpMode);
            gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class),
                                               gaFinalCloudClassificationParameters, gaFinalCloudInput);
        }

        targetProduct = gaCloudProduct;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ComputeChainOp.class);
        }
    }
}
