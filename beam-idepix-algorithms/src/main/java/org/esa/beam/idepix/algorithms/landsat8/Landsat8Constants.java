package org.esa.beam.idepix.algorithms.landsat8;

import java.util.HashMap;
import java.util.Map;

/**
 * Landsat 8 constants
 *
 * @author olafd
 */
public class Landsat8Constants {

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_AMBIGUOUS = 2;
    public static final int F_CLOUD_SURE = 3;
    public static final int F_CLOUD_BUFFER = 4;
    public static final int F_CLOUD_SHADOW = 5;
    public static final int F_BRIGHT = 6;
    public static final int F_WHITE = 7;
    public static final int F_SNOW_ICE = 8;
    public static final int F_GLINTRISK = 9;
    public static final int F_COASTLINE = 10;
    public static final int F_LAND = 11;

    public static final String F_INVALID_DESCR_TEXT = "Invalid pixel";
    public static final String F_CLOUD_DESCR_TEXT = "Cloudy pixel";
    public static final String F_CLOUD_AMBIGUOUS_DESCR_TEXT = "Semi transparent clouds, or clouds where the detection level is uncertain";
    public static final String F_CLOUD_SURE_DESCR_TEXT = "Fully opaque clouds with full confidence of their detection";
    public static final String F_CLOUD_BUFFER_DESCR_TEXT = "A buffer of n pixels around a cloud. n is a user supplied parameter. Applied to pixels masked as 'cloud'";
    public static final String F_CLOUD_SHADOW_DESCR_TEXT = "Pixel is affect by a cloud shadow";
    public static final String F_BRIGHT_DESCR_TEXT = "Bright pixel";
    public static final String F_WHITE_DESCR_TEXT = "White pixel";
    public static final String F_SNOW_ICE_DESCR_TEXT = "Snow/ice pixel";
    public static final String F_GLINTRISK_DESCR_TEXT = "Pixel with glint risk";
    public static final String F_COASTLINE_DESCR_TEXT = "Pixel at a coastline";
    public static final String F_LAND_DESCR_TEXT = "Land pixel";

    public static final String Landsat8_FLAGS_NAME = "flags";

    public static final Map<Integer, Integer> LANDSAT8_SPECTRAL_WAVELENGTH_MAP;
    static
    {
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP = new HashMap<Integer, Integer>();
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(440, 0);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(480, 1);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(560, 2);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(655, 3);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(865, 4);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(1610, 5);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(2200, 6);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(590, 7);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(1370, 8);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(10895, 9);
        LANDSAT8_SPECTRAL_WAVELENGTH_MAP.put(12005, 10);
    }

    public static final String[] LANDSAT8_SPECTRAL_BAND_NAMES = {
            "coastal_aerosol",           // 0  (440nm)
            "blue",                      // 1  (480nm)
            "green",                     // 2  (560nm)
            "red",                       // 3  (655nm)
            "near_infrared",             // 4  (865nm)
            "swir_1",                    // 5  (1610nm)
            "swir_2",                    // 6  (2200nm)
            "panchromatic",              // 7  (590nm)
            "cirrus",                    // 8  (1370nm)
            "thermal_infrared_(tirs)_1", // 9  (10895nm)
            "thermal_infrared_(tirs)_2", // 10 (12005nm)
    };
    public static final int LANDSAT8_NUM_SPECTRAL_BANDS = LANDSAT8_SPECTRAL_BAND_NAMES.length;
}