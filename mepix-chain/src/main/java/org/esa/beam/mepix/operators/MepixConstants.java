package org.esa.beam.mepix.operators;

import org.esa.beam.dataio.envisat.EnvisatConstants;

/**
 * MEPIX constants
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MepixConstants {

    public static final String MEPIX_VERSION = "v1.2-SNAPSHOT";

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
            "gaCopyRadiances"
    };

    public static final String[] coastcolourParameterNames = new String[]{
            "ccCopyRadiances"
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

    public static final float[] AATSR_WAVELENGTHS = {450.0f, 645.0f, 835.0f, 1670.0f, 450.0f, 645.0f, 835.0f, 1670.0f};

    public static String[] AATSR_FLAG_BAND_NAMES = {
            "confid_flags_nadir", // 0
            "cloud_flags_nadir", // 1
            "confid_flags_fward", // 2
            "cloud_flags_fward", // 3
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
}
