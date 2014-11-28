package org.esa.beam.idepix.algorithms.avhrrac;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 28.11.2014
 * Time: 11:17
 *
 * @author olafd
 */
public class Constants {
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

    // debug bands:
    public static final String BRIGHTNESS_BAND_NAME = "brightness_value";
    public static final String NDSI_BAND_NAME = "ndsi_value";
    public static final String SCHILLER_NN_OUTPUT_BAND_NAME = "schiller_nn_value";

    static final int SRC_SZA = 0;
    static final int SRC_LAT = 1;
    static final int SRC_LON = 2;

    static final int SRC_ALBEDO_1 = 3;
    static final int SRC_ALBEDO_2 = 4;
    static final int SRC_RADIANCE_3 = 5;
    static final int SRC_RADIANCE_4 = 6;
    static final int SRC_RADIANCE_5 = 7;

    static final int SRC_WATERFRACTION = 8;

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

}
