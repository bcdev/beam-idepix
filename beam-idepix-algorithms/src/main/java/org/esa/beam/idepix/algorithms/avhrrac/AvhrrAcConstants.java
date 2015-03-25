package org.esa.beam.idepix.algorithms.avhrrac;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 28.11.2014
 * Time: 11:17
 *
 * @author olafd
 */
public class AvhrrAcConstants {
    public static final String CLASSIF_BAND_NAME = "pixel_classif_flags";
    public static final String LAND_WATER_FRACTION_BAND_NAME = "land_water_fraction";

    private static final String AVHRR_AC_ALBEDO_1_BAND_NAME = "albedo_1";
    private static final String AVHRR_AC_ALBEDO_2_BAND_NAME = "albedo_2";

    public static final String[] AVHRR_AC_ALBEDO_BAND_NAMES = {
            AVHRR_AC_ALBEDO_1_BAND_NAME,
            AVHRR_AC_ALBEDO_2_BAND_NAME,
    };

    private static final String AVHRR_AC_RADIANCE_1_BAND_NAME = "radiance_1";
    private static final String AVHRR_AC_RADIANCE_2_BAND_NAME = "radiance_2";
    private static final String AVHRR_AC_RADIANCE_3_BAND_NAME = "radiance_3";
    private static final String AVHRR_AC_RADIANCE_4_BAND_NAME = "radiance_4";
    private static final String AVHRR_AC_RADIANCE_5_BAND_NAME = "radiance_5";

    public static final String[] AVHRR_AC_RADIANCE_BAND_NAMES = {
            AVHRR_AC_RADIANCE_1_BAND_NAME,
            AVHRR_AC_RADIANCE_2_BAND_NAME,
            AVHRR_AC_RADIANCE_3_BAND_NAME,
            AVHRR_AC_RADIANCE_4_BAND_NAME,
            AVHRR_AC_RADIANCE_5_BAND_NAME
    };

    private static final String AVHRR_AC_RADIANCE_1_OLD_BAND_NAME = "avhrr_ch1";
    private static final String AVHRR_AC_RADIANCE_2_OLD_BAND_NAME = "avhrr_ch2";
    private static final String AVHRR_AC_RADIANCE_3_OLD_BAND_NAME = "avhrr_ch3";
    private static final String AVHRR_AC_RADIANCE_4_OLD_BAND_NAME = "avhrr_ch4";
    private static final String AVHRR_AC_RADIANCE_5_OLD_BAND_NAME = "avhrr_ch5";

    public static final String[] AVHRR_AC_RADIANCE_OLD_BAND_NAMES = {
            AVHRR_AC_RADIANCE_1_OLD_BAND_NAME,
            AVHRR_AC_RADIANCE_2_OLD_BAND_NAME,
            AVHRR_AC_RADIANCE_3_OLD_BAND_NAME,
            AVHRR_AC_RADIANCE_4_OLD_BAND_NAME,
            AVHRR_AC_RADIANCE_5_OLD_BAND_NAME
    };

    private static final String AVHRR_AC_RADIANCE_1_AVISA_BAND_NAME = "radiance_1";
    private static final String AVHRR_AC_RADIANCE_2_AVISA_BAND_NAME = "radiance_2";
    private static final String AVHRR_AC_RADIANCE_3_AVISA_BAND_NAME = "radiance_3a";
    private static final String AVHRR_AC_RADIANCE_4_AVISA_BAND_NAME = "radiance_4";
    private static final String AVHRR_AC_RADIANCE_5_AVISA_BAND_NAME = "radiance_5";

    public static final String[] AVHRR_AC_RADIANCE_AVISA_BAND_NAMES = {
            AVHRR_AC_RADIANCE_1_AVISA_BAND_NAME,
            AVHRR_AC_RADIANCE_2_AVISA_BAND_NAME,
            AVHRR_AC_RADIANCE_3_AVISA_BAND_NAME,
            AVHRR_AC_RADIANCE_4_AVISA_BAND_NAME,
            AVHRR_AC_RADIANCE_5_AVISA_BAND_NAME,
    };

    public static final String AVHRR_AC_SZA_TL_BAND_NAME = "sun_zenith";
    public static final String AVHRR_AC_SAA_TL_BAND_NAME = "sun_azimuth";
    public static final String AVHRR_AC_VZA_TL_BAND_NAME = "sat_zenith";
    public static final String AVHRR_AC_VAA_TL_BAND_NAME = "satellite_azimuth";
    public static final String AVHRR_AC_GROUND_HEIGHT_TL_BAND_NAME = "ground_height";

    private static final String AVHRR_AC_REFL_1_TL_BAND_NAME = "avhrr_b1";
    private static final String AVHRR_AC_REFL_2_TL_BAND_NAME = "avhrr_b2";
    private static final String AVHRR_AC_REFL_3_TL_BAND_NAME = "avhrr_b3b";
    private static final String AVHRR_AC_RAD_3_TL_BAND_NAME  = "avhrr_b3b_radiance";
    private static final String AVHRR_AC_REFL_4_TL_BAND_NAME = "avhrr_b4";
    private static final String AVHRR_AC_REFL_5_TL_BAND_NAME = "avhrr_b5";

    public static final String[] AVHRR_AC_REFL_TL_BAND_NAMES = {
            AVHRR_AC_REFL_1_TL_BAND_NAME,
            AVHRR_AC_REFL_2_TL_BAND_NAME,
            AVHRR_AC_REFL_3_TL_BAND_NAME,
            AVHRR_AC_RAD_3_TL_BAND_NAME,
            AVHRR_AC_REFL_4_TL_BAND_NAME,
            AVHRR_AC_REFL_5_TL_BAND_NAME
    };



    // debug bands:
    public static final String SCHILLER_NN_OUTPUT_BAND_NAME = "schiller_nn_value";
    public static final String EMISSIVITY3B_OUTPUT_BAND_NAME = "emissivity3b";
    public static final String RHO3B_OUTPUT_BAND_NAME = "rho3b";
    public static final String NDSI_OUTPUT_BAND_NAME = "ndsi";

    static final int SRC_USGS_SZA = 0;
    static final int SRC_USGS_LAT = 1;
    static final int SRC_USGS_LON = 2;
    static final int SRC_USGS_ALBEDO_1 = 3;
    static final int SRC_USGS_ALBEDO_2 = 4;
    static final int SRC_USGS_RADIANCE_3 = 5;
    static final int SRC_USGS_RADIANCE_4 = 6;
    static final int SRC_USGS_RADIANCE_5 = 7;
    static final int SRC_USGS_WATERFRACTION = 8;

    static final int SRC_TL_LAT = 0;
    static final int SRC_TL_LON = 1;
    static final int SRC_TL_SZA = 2;
    static final int SRC_TL_VZA = 3;
    static final int SRC_TL_SAA = 4;
    static final int SRC_TL_VAA = 5;
    static final int SRC_TL_GROUND_HEIGHT = 6;
    static final int SRC_TL_REFL_1 = 7;
    static final int SRC_TL_REFL_2 = 8;
    static final int SRC_TL_REFL_3 = 9;
    static final int SRC_TL_RAD_3 = 10;
    static final int SRC_TL_REFL_4 = 11;
    static final int SRC_TL_REFL_5 = 12;
    static final int SRC_TL_WATERFRACTION = 13;

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_AMBIGUOUS = 2;
    public static final int F_CLOUD_SURE = 3;
    public static final int F_CLOUD_BUFFER = 4;
    public static final int F_CLOUD_SHADOW = 5;
    public static final int F_SNOW_ICE = 6;
    public static final int F_MIXED_PIXEL = 7;
    public static final int F_GLINT_RISK = 8;
    public static final int F_COASTLINE = 9;
    public static final int F_LAND = 10;

    public static final double NU_CH3 = 2694.0;
    public static final double NU_CH4 = 925.0;
    public static final double NU_CH5 = 839.0;

    public static final double SOLAR_3b = 4.448;
    // first value of the following constants is for NOAA11, second value for NOAA14
    public static final double[] EW_3b = {278.85792,284.69366};
    public static final double[] A0 = {6.34384,4.00162};
    public static final double[] B0 = {2.68468,0.98107};
    public static final double[] C0 = {-1.70931,1.9789};
    public static final double[] a1_3b = {-1.738973,-1.88533};
    public static final double[] a2_3b = {1.003354,1.003839};
    public static final double c1 = 1.1910659*1.E-5; // mW/(m^2 sr cm^-4)
    public static final double c2 = 1.438833;

//    public static final double TGCT_THRESH = 244.0;
    public static final double TGCT_THRESH = 260.0;

    public static final double EMISSIVITY_THRESH = 0.022;
    public static final double LAT_MAX_THRESH = 60.0;

    public static double[] fmftTestThresholds = new double[] {
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.01, 0.03, 0.05, 0.08, 0.11, 0.14, 0.18, 0.23, 0.28,
        0.34, 0.41, 0.48, 0.57, 0.66, 0.76, 0.87, 1.0, 1.13, 1.27,
        1.42, 1.59, 1.76, 1.94, 2.14, 2.34, 2.55, 2.77, 3.0, 3.24,
        3.48, 3.73, 3.99, 4.26, 4.52, 4.80, 5.0, 5.35, 5.64, 5.92,
        6.20, 6.48, 6.76, 7.03, 7.30, 7.8, 7.8, 7.8, 7.8, 7.8,
        7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8,
        7.8
    };

    public static double[] tmftTestMaxThresholds = new double[] {
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.615, 2.655, 2.685, 2.505,
            1.865, 1.835, 1.845, 1.915,
            1.815, 1.785, 1.815, 1.795,
            1.885, 1.885, 1.875, 1.875,
            2.135, 2.115, 2.095, 2.105,
            6.825, 7.445, 8.305, 7.125,
            19.055, 18.485, 17.795, 17.025,
            20.625, 19.775, 19.355, 19.895,
            18.115, 15.935, 20.395, 16.025,
            18.115, 15.935, 20.395, 16.025
    };

    public static double[] tmftTestMinThresholds = new double[] {
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            -0.805, -0.975, -0.795, -1.045,
            -1.195, -1.065, -1.125, -1.175,
            -1.225, -1.285, -1.285, -1.285,
            -2.425, -1.325, -2.105, -1.975,
            -1.685, -1.595, -1.535, -2.045,
            -4.205, -4.145, -3.645, -3.585,
            -2.425, -1.715, -2.275, -2.105,
            0.585, -0.585, 0.825, 0.345,
            0.655, 1.905, 0.475, 1.385,
            0.655, 1.905, 0.475, 1.385
    };
}
