package org.esa.beam.idepix.algorithms.occci;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 27.06.2014
 * Time: 10:29
 *
 * @author olafd
 */
class Constants {

    public static final String CLASSIF_BAND_NAME = "pixel_classif_flags";

    static final int SRC_SZA = 0;
    static final int SRC_SAA = 1;
    static final int SRC_VZA = 2;
    static final int SRC_VAA = 3;
    static final int SRC_PRESS = 4;
    static final int SRC_OZ = 5;
    static final int SRC_MWIND = 6;
    static final int SRC_ZWIND = 7;
    static final int MODIS_SRC_RAD_OFFSET = 8;
    static final int SEAWIFS_SRC_RAD_OFFSET = 10;
    static final int SRC_DETECTOR = 23;
    static final int SRC_MASK = 24;
    static final int SRC_SOL_FLUX_OFFSET = 25;
    static final int SRC_LAT = 40;
    static final int SRC_LON = 41;

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
