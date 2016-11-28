package org.esa.beam.idepix.algorithms.occci;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 27.06.2014
 * Time: 10:29
 *
 * @author olafd
 */
class OccciConstants {

    public static final String CLASSIF_BAND_NAME = "pixel_classif_flags";
    public static final String LAND_WATER_FRACTION_BAND_NAME = "land_water_fraction";

    // debug bands:
    public static final String BRIGHTNESS_BAND_NAME = "brightness_value";
    public static final String NDSI_BAND_NAME = "ndsi_value";
    public static final String SCHILLER_NN_OUTPUT_BAND_NAME = "nn_value";

    static final int SRC_SZA = 0;
    static final int SRC_SAA = 1;
    static final int SRC_VZA = 2;
    static final int SRC_VAA = 3;
    static final int MODIS_SRC_RAD_OFFSET = 22;
//    static final int MODIS_NN_INPUT_LENGTH = 10;
    static final int SEAWIFS_SRC_RAD_OFFSET = 8;
    static final int VIIRS_SRC_RAD_OFFSET = 0;

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_AMBIGUOUS = 2;
    public static final int F_CLOUD_SURE = 3;
    public static final int F_CLOUD_B_NIR = 4;
    public static final int F_CLOUD_BUFFER = 5;
    public static final int F_CLOUD_SHADOW = 6;
    public static final int F_SNOW_ICE = 7;
    public static final int F_WHITE_ICE = 8;
    public static final int F_WET_ICE = 9;
    public static final int F_MIXED_PIXEL = 10;
    public static final int F_GLINT_RISK = 11;
    public static final int F_COASTLINE = 12;
    public static final int F_LAND = 13;
    public static final int F_BRIGHT = 14;

}
