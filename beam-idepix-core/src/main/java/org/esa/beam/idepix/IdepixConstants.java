package org.esa.beam.idepix;

import org.esa.beam.dataio.envisat.EnvisatConstants;

/**
 * IDEPIX constants
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixConstants {

    public static final String IDEPIX_VERSION = "v1.4-renovation-SNAPSHOT";

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_BUFFER = 2;
    public static final int F_CLOUD_SHADOW = 3;
    public static final int F_CLEAR_LAND = 4;
    public static final int F_CLEAR_WATER = 5;
    public static final int F_CLEAR_SNOW = 6;
    public static final int F_LAND = 7;
    public static final int F_WATER = 8;
    public static final int F_BRIGHT = 9;
    public static final int F_WHITE = 10;
    public static final int F_BRIGHTWHITE = 11;
    public static final int F_COLD = 12;
    public static final int F_HIGH = 13;
    public static final int F_VEG_RISK = 14;
    public static final int F_GLINT_RISK = 15;

    public static final String[] ipfParameterNames = new String[]{
            "ipfOutputRad2Refl", "ipfOutputGaseous", "ipfOutputLandWater", "ipfOutputRayleigh",
            "ipfOutputL2Pressures", "ipfOutputL2CloudDetection",
            "ipfQWGUserDefinedP1PressureThreshold", "ipfQWGUserDefinedPScattPressureThreshold",
            "ipfQWGUserDefinedRhoToa442Threshold", "ipfQWGUserDefinedDeltaRhoToa442Threshold",
            "ipfQWGUserDefinedRhoToa753Threshold", "ipfQWGUserDefinedRhoToaRatio753775Threshold",
            "ipfQWGUserDefinedMDSIThreshold"
    };

    public static final String[] pressureParameterNames = new String[]{
            "pressureOutputPbaro", "pressurePbaroGetasse", "pressureOutputPsurfFub", "pressureFubTropicalAtmosphere",
            "pressureQWGOutputCtpStraylightCorrFub", "pressureOutputP1Lise", "pressureOutputPSurfLise",
            "pressureOutputP2Lise", "pressureOutputPScattLise"
    };

    public static final String[] cloudProductParameterNames = new String[]{
            "cloudOutputBlueBand", "cloudOutputCloudProbability", "cloudOutputCombinedCloud"
    };

    public static final String[] cloudScreeningParameterNames = new String[]{
            "algorithm"
    };

    public static final String[] globalbedoParameterNames = new String[]{
            "gaCopyRadiances",
            "gaComputeFlagsOnly",
            "gaCopyPressure",
            "gaComputeMerisCloudShadow",
            "ctpMode",
            "gaCopyAnnotations",
            "gaUseAatsrFwardForClouds",
            "gaCloudBufferWidth",
            "wmResolution",
            "wmFill",
            "gaUseL1bLandWaterFlag",
            "gaUseWaterMaskFraction"
    };

    public static final String[] coastcolourParameterNames = new String[]{
            "ccOutputRad2Refl", "ccOutputGaseous", "ccOutputRayleigh",
            "ccOutputL2Pressures", "ccOutputL2CloudDetection",
            "ccUserDefinedPScattPressureThreshold",
            "ccUserDefinedGlintThreshold",
            "ccUserDefinedRhoToa753Threshold",
            "ccUserDefinedMDSIThreshold",
            "ccUserDefinedNDVIThreshold",
            "ccUserDefinedRhoToa442Threshold",
            "ccRhoAgReferenceWavelength",
            "ccCloudBufferWidth",
            "ccUseL1bLandFlag",
            "ccLandMaskResolution",
            "ccOversamplingFactorX",
            "ccOversamplingFactorY",
            "ccSeaIceThreshold",
            "ccSchillerAmbiguous",
            "ccSchillerSure"
    };


    public static String SPOT_VGT_PRODUCT_TYPE_PREFIX = "VGT";

    public static final int PRODUCT_TYPE_INVALID = -1;
    public static final int PRODUCT_TYPE_MERIS = 0;
    public static final int PRODUCT_TYPE_AATSR = 1;
    public static final int PRODUCT_TYPE_VGT = 2;

    public static String VGT_RADIANCE_0_BAND_NAME = "B0";
    public static String VGT_RADIANCE_2_BAND_NAME = "B2";
    public static String VGT_RADIANCE_3_BAND_NAME = "B3";
    public static String VGT_RADIANCE_MIR_BAND_NAME = "MIR";

    public static String VGT_SM_FLAG_BAND_NAME = "SM";

    public static final int NO_DATA_VALUE = -1;

    /**
     * The names of the VGT spectral band names.
     */
    public static String[] VGT_RADIANCE_BAND_NAMES = {
            VGT_RADIANCE_0_BAND_NAME, // 0
            VGT_RADIANCE_2_BAND_NAME, // 1
            VGT_RADIANCE_3_BAND_NAME, // 2
            VGT_RADIANCE_MIR_BAND_NAME, // 3
    };

    public static String[] VGT_ANNOTATION_BAND_NAMES = {
            "VZA",
            "SZA",
            "VAA",
            "SAA",
            "WVG",
            "OG",
            "AG",
    };

    public static final float[] VGT_WAVELENGTHS = {450.0f, 645.0f, 835.0f, 1670.0f};

    public static String[] AATSR_REFLECTANCE_BAND_NAMES = {
            EnvisatConstants.AATSR_L1B_REFLEC_NADIR_0550_BAND_NAME, // 0
            EnvisatConstants.AATSR_L1B_REFLEC_NADIR_0670_BAND_NAME, // 1
            EnvisatConstants.AATSR_L1B_REFLEC_NADIR_0870_BAND_NAME, // 2
            EnvisatConstants.AATSR_L1B_REFLEC_NADIR_1600_BAND_NAME, // 3
            EnvisatConstants.AATSR_L1B_REFLEC_FWARD_0550_BAND_NAME, // 4
            EnvisatConstants.AATSR_L1B_REFLEC_FWARD_0670_BAND_NAME, // 5
            EnvisatConstants.AATSR_L1B_REFLEC_FWARD_0870_BAND_NAME, // 6
            EnvisatConstants.AATSR_L1B_REFLEC_FWARD_1600_BAND_NAME  // 7
    };

    public static String[] AATSR_BTEMP_BAND_NAMES = {
            EnvisatConstants.AATSR_L1B_BTEMP_NADIR_0370_BAND_NAME, // 0
            EnvisatConstants.AATSR_L1B_BTEMP_NADIR_1100_BAND_NAME, // 1
            EnvisatConstants.AATSR_L1B_BTEMP_NADIR_1200_BAND_NAME, // 2
            EnvisatConstants.AATSR_L1B_BTEMP_FWARD_0370_BAND_NAME, // 3
            EnvisatConstants.AATSR_L1B_BTEMP_FWARD_1100_BAND_NAME, // 4
            EnvisatConstants.AATSR_L1B_BTEMP_FWARD_1200_BAND_NAME, // 5
    };

    public static final float[] AATSR_REFL_WAVELENGTHS = {
            450.0f,
            645.0f,
            835.0f,
            1670.0f,
            450.0f,
            645.0f,
            835.0f,
            1670.0f
    };
    public static final float[] AATSR_TEMP_WAVELENGTHS = {370.0f, 1100.0f, 1200.0f, 370.0f, 1100.0f, 1200.0f};

    public static String[] AATSR_FLAG_BAND_NAMES = {
            "confid_flags_nadir", // 0
            "cloud_flags_nadir", // 1
            "confid_flags_fward", // 2
            "cloud_flags_fward", // 3
    };

    public static final float[] MERIS_WAVELENGTHS =
            {
                    412.7f, 442.5f, 489.9f, 509.8f, 559.7f, 619.6f, 664.6f, 680.8f,
                    708.3f, 753.3f, 761.5f, 778.4f, 864.9f, 884.9f, 900.0f
            };

    public static String[] MERIS_BRR_BAND_NAMES = {
            "brr_1", // 0
            "brr_2", // 0
            "brr_3", // 0
            "brr_4", // 0
            "brr_5", // 0
            "brr_6", // 0
            "brr_7", // 0
            "brr_8", // 0
            "brr_9", // 0
            "brr_10", // 0
            "brr_12", // 0
            "brr_13", // 0
            "brr_14", // 0
    };

    public static final String inputconsistencyErrorMessage =
            "Selected cloud screening algorithm cannot be used with given input product. \n\n" +
                    "Valid combinations are: \n" +
                    " - QWG for MERIS products \n" +
                    " - GlobColour for MERIS, AATSR, VGT products \n" +
                    " - CoastColour for MERIS products ";


    public static final int IO_TAB_INDEX = 0;
    public static final int CLOUDSCREENING_TAB_INDEX = 1;
    public static final int IPF_TAB_INDEX = 2;
    public static final int PRESSURE_TAB_INDEX = 3;
    public static final int CLOUDS_TAB_INDEX = 4;
    public static final int GLOBALBEDO_TAB_INDEX = 5;
    public static final int COASTCOLOUR_TAB_INDEX = 6;

    public static final String ctpModeDefault = "Derive from Neural Net";

    // constants for spectral unmixing
    public static final String[] SMA_SOURCE_BAND_NAMES = {"brr_5_n", "brr_7_n", "brr_9_n", "brr_10_n", "brr_12_n", "brr_13_n"};
    public static final String[] SMA_ENDMEMBER_NAMES = {"Land", "Water", "Coast", "Cloud"};
    public static final double[] SMA_ENDMEMBER_WAVELENGTHS =
            {559.694, 664.57306, 708.32904, 753.37103, 778.40906, 864.87604};
    public static final double[] SMA_ENDMEMBER_BANDWIDTHS =
            {9.97, 9.985, 9.992, 7.495, 15.01, 20.047};
    public static final double[][] SMA_ENDMEMBER_RADIATIONS =
            {{0.06874453, 0.05234256, 0.10713479, 0.2107095, 0.22287288, 0.24322398},
            {0.026597029, 0.014183232, 0.012450832, 0.011182333, 0.01058279, 0.008555549},
            {0.061452672, 0.03917208, 0.046320472, 0.06117781, 0.06220935, 0.061626144},
            {0.4057965, 0.41043115, 0.43384373, 0.47499827, 0.48148763, 0.49312785}};
    public static final String[] SMA_ABUNDANCE_BAND_NAMES = {"Land_abundance", "Water_abundance", "Cloud_abundance"};
    public static final String SMA_SUMMARY_BAND_NAME = "summary_error";
}
