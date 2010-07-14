package org.esa.beam.mepix.operators;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MepixConstants {

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

    public static final float VGT_WAVELENGTH_B0 = 450.0f;
    public static final float VGT_WAVELENGTH_B2 = 645.0f;
    public static final float VGT_WAVELENGTH_B3 = 835.0f;
    public static final float VGT_WAVELENGTH_MIR = 1670.0f;
}
