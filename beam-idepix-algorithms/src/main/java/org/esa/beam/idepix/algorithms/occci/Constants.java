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
    public static final String LAND_WATER_FRACTION_BAND_NAME = "land_water_fraction";

    // debug bands:
    public static final String BRIGHTNESS_BAND_NAME = "brightness_value";
    public static final String NDSI_BAND_NAME = "ndsi_value";
    public static final String SCHILLER_NN_OUTPUT_BAND_NAME = "schiller_nn_value";
    public static final String SOLZ_BAND_NAME = "solz";
    public static final String SOLA_BAND_NAME = "sola";
    public static final String SENZ_BAND_NAME = "senz";
    public static final String SENA_BAND_NAME = "sena";

    static final int SRC_SZA = 0;
    static final int SRC_SAA = 1;
    static final int SRC_VZA = 2;
    static final int SRC_VAA = 3;
    static final int SRC_PRESS = 4;
    static final int SRC_OZ = 5;
    static final int SRC_MWIND = 6;
    static final int SRC_ZWIND = 7;
    static final int MODIS_SRC_RAD_OFFSET = 22;
    static final int MODIS_SRC_RAD_1KM_OFFSET = 15;
    static final int MODIS_SRC_RAD_AGGR1KM_OFFSET = 7;
    static final int MODIS_SRC_EMISS_OFFSET = 16;
    static final int MODIS_NN_INPUT_LENGTH = 10;
    static final int SEAWIFS_SRC_RAD_OFFSET = 8;
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


    //    #Planck constant [Js]
    public static final double PLANCK_CONSTANT = 6.62606896e-34;
    //    #c in vacuum [m/s]
    public static final double VACUUM_LIGHT_SPEED = 2.99792458e8;
    //    #Boltzmann constant  [J/K]
    public static final double BOLTZMANN_CONSTANT = 1.3806504e-23;

    //    #temperature correction intercept [K], for band number in [20,36], but not 26
    public static final double[] TCI = {
            4.770532E-01,    // band 20
            9.262664E-02,    // band 21
            9.757996E-02,    // band 22
            8.929242E-02,    // band 23
            7.310901E-02,    // band 24
            7.060415E-02,    // band 25
            0.0,             // band 26
            2.204921E-01,    // band 27
            2.046087E-01,    // band 28
            1.599191E-01,    // band 29
            8.253401E-02,    // band 30
            1.302699E-01,    // band 31
            7.181833E-02,    // band 32
            1.972608E-02,    // band 33
            1.913568E-02,    // band 34
            1.817817E-02,    // band 35
            1.583042E-02};   // band 36

    //    #temperature correction slope [1]
    public static final double[] TCS = {
            9.993411E-01,    // band 20
            9.998646E-01,    // band 21
            9.998584E-01,    // band 22
            9.998682E-01,    // band 23
            9.998819E-01,    // band 24
            9.998845E-01,    // band 25
            0.0,             // band 26
            9.994877E-01,    // band 27
            9.994918E-01,    // band 28
            9.995495E-01,    // band 29
            9.997398E-01,    // band 30
            9.995608E-01,    // band 31
            9.997256E-01,    // band 32
            9.999160E-01,    // band 33
            9.999167E-01,    // band 34
            9.999191E-01,    // band 35
            9.999281E-01};   // band 36

    public static final double[] MODIS_EMISSIVE_WAVELENGTHS = {
            3785.3,   // band 20
            3991.6,   // band 21
            3991.6,   // band 22
            4056.0,   // band 23
            4472.6,   // band 24
            4544.7,   // band 25
            1375.0,   // band 26
            6766.0,   // band 27
            7338.2,   // band 28
            8523.8,   // band 29
            9730.3,   // band 30
            11012.1,  // band 31
            12025.9,  // band 32
            13362.9,  // band 33
            13681.8,  // band 34
            13910.8,  // band 35
            14193.7   // band 36
    };

}
