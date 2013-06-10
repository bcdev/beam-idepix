package org.esa.beam.classif.algorithm;

public class Constants {

    public static final int NUM_RADIANCE_BANDS = 15;

    public static final int CLEAR_MASK = 0x01;
    public static final int SPAMX_MASK = 0x02;
    public static final int NONCL_MASK = 0x04;
    public static final int CLOUD_MASK = 0x08;
    public static final int UNPROCESSD_MASK = 0x10;
    public static final int SPAMX_OR_NONCL_MASK = 0x02;
    public static final String[] TIE_POINT_GRID_NAMES = new String[]{"latitude", "longitude", "dem_alt", "dem_rough", "lat_corr", "lon_corr", "sun_zenith",
            "sun_azimuth", "view_zenith", "view_azimuth", "zonal_wind", "merid_wind", "atm_press", "ozone", "rel_hum"};
}
