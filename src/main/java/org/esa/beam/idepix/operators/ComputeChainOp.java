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
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
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
import org.esa.beam.meris.cloud.CloudEdgeOp;
import org.esa.beam.meris.cloud.CloudProbabilityOp;
import org.esa.beam.meris.cloud.CloudShadowOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.unmixing.Endmember;
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
                  version = "1.3.4-SNAPSHOT",
                  authors = "Olaf Danne, Carsten Brockmann",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Pixel identification and classification. This operator just calls a chain of other operators.")
public class ComputeChainOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;


    // Cloud screening parameters
    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour", "GlobCover", "MagicStick"})
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
    @Parameter(defaultValue = "false", label = " Rayleigh Corrected Reflectances")
    private boolean gaOutputRayleigh = false;
    @Parameter(defaultValue = "false", label = " Use the LC cloud buffer algorithm")
    private boolean gaLcCloudBuffer = false;

    // Coastcolour parameters
    @Parameter(defaultValue = "true", label = " TOA Reflectances")
    private boolean ccOutputRad2Refl = true;

    @Parameter(defaultValue = "false", label = " Gas Absorption Corrected Reflectances")
    private boolean ccOutputGaseous = false;

    @Parameter(defaultValue = "true", label = " Rayleigh Corrected Reflectances and Mixed Pixel Flag")
    private boolean ccOutputRayleigh = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Top Pressure and Surface Pressure")
    private boolean ccOutputL2Pressures = true;

    @Parameter(defaultValue = "true", label = " L2 Cloud Detection Flags")
    private boolean ccOutputL2CloudDetection = true;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(label = "GAC Window Width (# of pixels)", defaultValue = "5")
    private int ccGacWindowWidth;

    @Parameter(label = " PScatt Pressure Threshold ", defaultValue = "700.0")
    private double ccUserDefinedPScattPressureThreshold = 700.0;

    @Parameter(label = " Theoretical Glint Threshold", defaultValue = "0.015")
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

    @Parameter(label = "Spatial Cloud Test", description = "Perform the Spatial Cloud Test.", defaultValue = "false")
    private boolean ccSpatialCloudTest;
    @Parameter(label = "Threshold for Spatial Cloud Test",
               description = "Threshold for Spatial Cloud Test.", defaultValue = "0.04",
               interval = "[0.0, 1.0]")
    private double ccSpatialCloudTestThreshold;

    private boolean straylightCorr;
    private Product merisCloudProduct;
    private Product rayleighProduct;
    //    private Product correctedRayleighProduct;
    private Product pressureLiseProduct;
    private Product rad2reflProduct;
    private Product pbaroProduct;
    private Product ctpProduct;
    private Product ccPostProcessingProduct;

    @Override
    public void initialize() throws OperatorException {

//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // for debugging purpose

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, algorithm);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        switch (algorithm) {
            case QWG:
                processQwg();
                break;
            case CoastColour:
                processCoastColour();
                break;
            case GlobAlbedo:
                processGlobAlbedo();
                break;
            case GlobCover:
                processGlobCover();
                break;
            case MagicStick:
                processMagicStick();
                break;

            default:
                throw new OperatorException("Unsupported algorithm selected: " + algorithm);
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

    private void prefixMask(String maskName) {
        Mask mask = targetProduct.getMaskGroup().get(maskName);
        if (mask != null) {
            mask.setName("l1b_" + mask.getName());
        }
    }

    private void processQwg() {
        computeRadiance2ReflectanceProduct();
        computeBarometricPressureProduct();
        computeCloudTopPressureProduct();

        Product ctpStraylightProduct = null;
        if (pressureQWGOutputCtpStraylightCorrFub) {
            straylightCorr = sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME);
            // currently, apply straylight correction for RR products only...
            if (straylightCorr) {
                ctpStraylightProduct = computeCloudTopPressureStraylightProduct();
            }
        }
        computePressureLiseProduct();

        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous ||
                pressureOutputPsurfFub || ipfOutputL2Pressures || ipfOutputL2CloudDetection) {
            computeMerisCloudProduct(ipfOutputL2Pressures);
        }

        Product gasProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater || ipfOutputGaseous) {
            gasProduct = computeGaseousCorrectionProduct();
        }

        Product landProduct = null;
        if (ipfOutputRayleigh || ipfOutputLandWater) {
            landProduct = computeLandClassificationProduct(gasProduct);
        }

        if (ipfOutputRayleigh) {
            computeRayleighCorrectionProduct(gasProduct, landProduct, LandClassificationOp.LAND_FLAGS + ".F_LANDCONS");
        }

        Product blueBandProduct = null;
        if (cloudOutputBlueBand || cloudOutputCombinedCloud) {
            blueBandProduct = computeBlueBandProduct(computeBrrProduct());
        }

        // Cloud Probability
        Product cloudProbabilityProduct = null;
        if (cloudOutputCloudProbability || cloudOutputCombinedCloud) {
            cloudProbabilityProduct = computeCloudProbabilityProduct();
        }

        // Surface Pressure NN (FUB)
        Product psurfNNProduct = null;
        if (pressureOutputPsurfFub) {
            psurfNNProduct = computePsurfNNProduct();
        }

        // Combined Cloud
        Product combinedCloudProduct = null;
        if (cloudOutputCombinedCloud) {
            combinedCloudProduct = computeCombinedCloudProduct(blueBandProduct, cloudProbabilityProduct);
        }
        //=====================================================
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        if (ipfOutputRad2Refl) {
            addRadiance2ReflectanceBands();
        }
        if (ipfOutputL2Pressures) {
            addMerisCloudProductBands();
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

    private void processGlobAlbedo() {
        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            computeRadiance2ReflectanceProduct();
            computeBarometricPressureProduct();
            computeCloudTopPressureProduct();
            computePressureLiseProduct();
            computeMerisCloudProduct(true);
            Product gasProduct = computeGaseousCorrectionProduct();
            Product landProduct = computeLandClassificationProduct(gasProduct);
            computeRayleighCorrectionProduct(gasProduct, landProduct, LandClassificationOp.LAND_FLAGS + ".F_LANDCONS");
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
        gaCloudClassificationParameters.put("gaLcCloudBuffer", gaLcCloudBuffer);

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
            gaFinalCloudClassificationParameters.put("shadowForCloudBuffer", true);
            gaCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class),
                                               gaFinalCloudClassificationParameters, gaFinalCloudInput);
        }

        targetProduct = gaCloudProduct;
        if (gaOutputRayleigh) {
            addRayleighCorrectionBands();
        }
        addPressureLiseProductBands();
    }

    private void processGlobCover() {
        Product brrProduct = computeBrrProduct();
        Product blueBandProduct = computeBlueBandProduct(brrProduct);
        Product cloudProbabilityProduct = computeCloudProbabilityProduct();
        Product combinedCloudProduct = computeCombinedCloudProduct(blueBandProduct, cloudProbabilityProduct);
        computeCloudTopPressureProduct();

        Operator cloudEdgeOp = new CloudEdgeOp();
        cloudEdgeOp.setSourceProduct(combinedCloudProduct);
        Product cloudEdgeProduct = cloudEdgeOp.getTargetProduct();

        Operator cloudShadowOp = new CloudShadowOp();
        cloudShadowOp.setSourceProduct("l1b", sourceProduct);
        cloudShadowOp.setSourceProduct("cloud", cloudEdgeProduct);
        cloudShadowOp.setSourceProduct("ctp", ctpProduct);
        Product cloudShadowProduct = cloudShadowOp.getTargetProduct();

        Operator idepixGlobCoverOp = new IdepixGlobCoverOp();
        idepixGlobCoverOp.setSourceProduct("cloudProduct", cloudShadowProduct);
        idepixGlobCoverOp.setSourceProduct("brrProduct", brrProduct);
        targetProduct = idepixGlobCoverOp.getTargetProduct();
    }

    private void processMagicStick() {
        computeCloudTopPressureProduct();

        Operator operator = new IdepixMagicStickOp();
        operator.setSourceProduct(sourceProduct);
        Product magicProduct = operator.getTargetProduct();
        Map<String, Product> shadowInput = new HashMap<String, Product>(4);
        shadowInput.put("gal1b", sourceProduct);
        shadowInput.put("cloud", magicProduct);
        shadowInput.put("ctp", ctpProduct);   // may be null
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("ctpMode", ctpMode);
        targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class), params, shadowInput);
    }

    private void processCoastColour() {
        computeRadiance2ReflectanceProduct();
        computeCloudTopPressureProduct();
        computePressureLiseProduct();
        computeCoastColourMerisCloudProduct();
//        postCloudProduct = computeCoastColourPostProcessProduct();
        Product gasProduct = null;
        if (ccOutputGaseous || ccOutputRayleigh) {
            gasProduct = computeGaseousCorrectionProduct();
        }

        // Land Water Reclassification
        // Land classification is done CoastColourMerisCloudProduct
//        Product landProduct = computeLandClassificationProduct(gasProduct);

        Product smaProduct = null;
        if (ccOutputRayleigh) {
            computeRayleighCorrectionProduct(gasProduct, merisCloudProduct,
                                             CoastColourCloudClassificationOp.CLOUD_FLAGS + ".F_LAND");
            smaProduct = computeSpectralUnmixingProduct();
        }

        // Post Cloud Classification and computation of Mixed Pixel Flag
        ccPostProcessingProduct = computeCoastColourPostProcessProduct(smaProduct);

        //=====================================================
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        if (ccOutputRad2Refl) {
            addRadiance2ReflectanceBands();
        }
        if (ccOutputL2Pressures) {
            addMerisCloudProductBands();
        }
        if (ccOutputL2CloudDetection) {
            addCloudClassificationFlagBandCoastColour();
        }
        if (ccOutputGaseous) {
            addGaseousCorrectionBands(gasProduct);
        }
        if (ccOutputRayleigh) {
            addRayleighCorrectionBands();
        }

        if (ccOutputL2CloudDetection) {
            Band cloudFlagBand = targetProduct.getBand(CoastColourCloudClassificationOp.CLOUD_FLAGS);
            cloudFlagBand.setSourceImage(
                    ccPostProcessingProduct.getBand(CoastColourCloudClassificationOp.CLOUD_FLAGS).getSourceImage());
        }

        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        CoastColourCloudClassificationOp.addBitmasks(targetProduct);
    }

    private Product computeCombinedCloudProduct(Product blueBandProduct, Product cloudProbabilityProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("cloudProb", cloudProbabilityProduct);
        input.put("blueBand", blueBandProduct);
        return GPF.createProduct("Meris.CombinedCloud", GPF.NO_PARAMS, input);
    }

    private Product computePsurfNNProduct() {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("tropicalAtmosphere", pressureFubTropicalAtmosphere);
        // mail from RL, 2009/03/19: always apply correction on FUB pressure
        // currently only for RR (FR coefficients still missing)
        params.put("straylightCorr", straylightCorr);
        return GPF.createProduct("Meris.SurfacePressureFub", params, input);
    }

    private Product computeCloudProbabilityProduct() {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("input", sourceProduct);
        Map<String, Object> params = new HashMap<String, Object>(3);
        params.put("configFile", "cloud_config.txt");
        params.put("validLandExpression", "not l1_flags.INVALID and dem_alt > -50");
        params.put("validOceanExpression", "not l1_flags.INVALID and dem_alt <= -50");
        return GPF.createProduct("Meris.CloudProbability", params, input);
    }

    private Product computeBlueBandProduct(Product brrProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("toar", brrProduct);
        return GPF.createProduct("Meris.BlueBand", GPF.NO_PARAMS, input);
    }

    private Product computeBrrProduct() {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("input", sourceProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("outputToar", true);
        params.put("correctWater", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(BrrOp.class), params, input);
    }

    private void computeRayleighCorrectionProduct(Product gasProduct, Product landProduct, String landExpression) {
        Map<String, Product> input = new HashMap<String, Product>(3);
        input.put("l1b", sourceProduct);
        input.put("input", gasProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("land", landProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("correctWater", true);
        params.put("landExpression", landExpression);
        params.put("exportBrrNormalized", ccOutputRayleigh);
        rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixRayleighCorrectionOp.class), params, input);
    }

    private Product computeSpectralUnmixingProduct() {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("sourceProduct", rayleighProduct);
        Map<String, Object> params = new HashMap<String, Object>(3);
        // todo: do we need more than one endmember file? do more parameters need to be flexible?
        params.put("sourceBandNames", IdepixConstants.SMA_SOURCE_BAND_NAMES);
        final Endmember[] endmembers = IdepixUtils.setupCCSpectralUnmixingEndmembers();
        params.put("endmembers", endmembers);
        params.put("computeErrorBands", true);
        params.put("minBandwidth", 5.0);
        params.put("unmixingModelName", "Fully Constrained LSU");
        return GPF.createProduct("Unmix", params, input);
    }

    private Product computeLandClassificationProduct(Product gasProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("gascor", gasProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), GPF.NO_PARAMS, input);
    }

    private Product computeGaseousCorrectionProduct() {
        Map<String, Product> input = new HashMap<String, Product>(3);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("correctWater", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), params, input);
    }

    // Cloud Top Pressure with FUB Straylight Correction
    private Product computeCloudTopPressureStraylightProduct() {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("straylightCorr", true);
        return GPF.createProduct("Meris.CloudTopPressureOp", params, sourceProduct);
    }

    private void computeBarometricPressureProduct() {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("useGetasseDem", pressurePbaroGetasse);
        pbaroProduct = GPF.createProduct("Meris.BarometricPressure", params, sourceProduct);
    }

    private void computeCloudTopPressureProduct() {
        ctpProduct = GPF.createProduct("Meris.CloudTopPressureOp", GPF.NO_PARAMS, sourceProduct);
    }

    private void computeRadiance2ReflectanceProduct() {
        rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS, sourceProduct);
    }

    private void computeMerisCloudProduct(boolean computeL2Pressure) {
        Map<String, Product> input = new HashMap<String, Product>(4);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("ctp", ctpProduct);
        input.put("pressureOutputLise", pressureLiseProduct);
        input.put("pressureBaro", pbaroProduct);

        Map<String, Object> params = new HashMap<String, Object>(11);
        params.put("l2Pressures", computeL2Pressure);
        params.put("userDefinedP1PressureThreshold", ipfQWGUserDefinedP1PressureThreshold);
        params.put("userDefinedPScattPressureThreshold",
                   ipfQWGUserDefinedPScattPressureThreshold);
        params.put("userDefinedRhoToa442Threshold", ipfQWGUserDefinedRhoToa442Threshold);
        params.put("userDefinedDeltaRhoToa442Threshold",
                   ipfQWGUserDefinedDeltaRhoToa442Threshold);
        params.put("userDefinedRhoToa753Threshold", ipfQWGUserDefinedRhoToa753Threshold);
        params.put("userDefinedRhoToa442Threshold", ipfQWGUserDefinedRhoToa442Threshold);
        params.put("userDefinedRhoToaRatio753775Threshold",
                   ipfQWGUserDefinedRhoToaRatio753775Threshold);
        params.put("userDefinedMDSIThreshold", ipfQWGUserDefinedMDSIThreshold);
        params.put("userDefinedNDVIThreshold", userDefinedNDVIThreshold);

        merisCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudClassificationOp.class),
                                              params, input);
    }

    private void computeCoastColourMerisCloudProduct() {
        Map<String, Object> glintCorrParameters = new HashMap<String, Object>(11);
        glintCorrParameters.put("glintCorrParameters", false);
        glintCorrParameters.put("outputTosa", false);
        glintCorrParameters.put("outputNormReflec", false);
        glintCorrParameters.put("outputReflec", false);
        glintCorrParameters.put("outputPath", false);
        glintCorrParameters.put("outputTransmittance", false);
        glintCorrParameters.put("deriveRwFromPath", false);
        glintCorrParameters.put("useFlint", false);
        HashMap<String, Product> glintProducts = new HashMap<String, Product>();
        glintProducts.put("merisProduct", sourceProduct);
        Product gacProduct = GPF.createProduct("Meris.GlintCorrection", glintCorrParameters, glintProducts);

        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", ccLandMaskResolution);
        waterParameters.put("subSamplingFactorX", ccOversamplingFactorX);
        waterParameters.put("subSamplingFactorY", ccOversamplingFactorY);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);

        Map<String, Product> cloudInputProducts = new HashMap<String, Product>(4);
        cloudInputProducts.put("l1b", sourceProduct);
        cloudInputProducts.put("rhotoa", rad2reflProduct);
        cloudInputProducts.put("gac", gacProduct);
        cloudInputProducts.put("ctp", ctpProduct);
        cloudInputProducts.put("pressureOutputLise", pressureLiseProduct);
        cloudInputProducts.put("waterMask", waterMaskProduct);

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
        cloudClassificationParameters.put("gacWindowWidth", ccGacWindowWidth);
        cloudClassificationParameters.put("seaIceThreshold", ccSeaIceThreshold);
        cloudClassificationParameters.put("spatialCloudTest", ccSpatialCloudTest);
        cloudClassificationParameters.put("spatialCloudTestThreshold", ccSpatialCloudTestThreshold);
        merisCloudProduct = GPF.createProduct(
                OperatorSpi.getOperatorAlias(CoastColourCloudClassificationOp.class),
                cloudClassificationParameters, cloudInputProducts);
    }

    private Product computeCoastColourPostProcessProduct(Product smaProduct1) {
        HashMap<String, Product> input = new HashMap<String, Product>();
        input.put("l1b", sourceProduct);
        input.put("merisCloud", merisCloudProduct);
        input.put("ctp", ctpProduct);
        input.put("rayleigh", rayleighProduct);
        input.put("sma", smaProduct1);   // may be null

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("cloudBufferWidth", ccCloudBufferWidth);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CoastColourPostProcessOp.class), params, input);
    }

    private void computePressureLiseProduct() {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        Map<String, Object> params = new HashMap<String, Object>(6);
        params.put("straylightCorr", false); // mail from RL/RS, 2009/03/19: do not apply correction on LISE pressure
        params.put("outputP1", true);
        params.put("outputPressureSurface", pressureOutputPSurfLise);
        params.put("outputP2", pressureOutputP2Lise);
        params.put("outputPScatt", true);
        params.put("l2CloudDetection", ipfOutputL2CloudDetection);
        pressureLiseProduct = GPF.createProduct("Meris.LisePressure", params, input);
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

    private void addCloudClassificationFlagBand() {
        FlagCoding flagCoding = CoastColourCloudClassificationOp.createFlagCoding(
                CoastColourCloudClassificationOp.CLOUD_FLAGS, false);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : merisCloudProduct.getBands()) {
            if (band.getName().equals(CoastColourCloudClassificationOp.CLOUD_FLAGS)) {
                band.setSampleCoding(flagCoding);
                targetProduct.addBand(band);
            }
        }
    }

    private void addCloudClassificationFlagBandCoastColour() {
        FlagCoding flagCoding = CoastColourCloudClassificationOp.createFlagCoding(
                CoastColourCloudClassificationOp.CLOUD_FLAGS, ccSpatialCloudTest);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : merisCloudProduct.getBands()) {
            if (band.getName().equals(CoastColourCloudClassificationOp.CLOUD_FLAGS)) {
                Band targetBand = ProductUtils.copyBand(band.getName(), merisCloudProduct, targetProduct, true);
                targetBand.setSampleCoding(flagCoding);
            }
        }
    }

    private void addMerisCloudProductBands() {
        for (String bandname : merisCloudProduct.getBandNames()) {
            if (!bandname.equals(IdepixCloudClassificationOp.CLOUD_FLAGS)) {
                moveBand(merisCloudProduct, bandname);
            }
        }
    }

    private void addRadiance2ReflectanceBands() {
        for (String bandname : rad2reflProduct.getBandNames()) {
            moveBand(rad2reflProduct, bandname);
        }
    }

    private void moveBand(Product product, String bandname) {
        if (!targetProduct.containsBand(bandname)) {
            targetProduct.addBand(product.getBand(bandname));
        }
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
