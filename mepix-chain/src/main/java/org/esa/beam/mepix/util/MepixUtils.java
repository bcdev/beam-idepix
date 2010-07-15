package org.esa.beam.mepix.util;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.mepix.operators.MepixConstants;

import javax.swing.JOptionPane;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MepixUtils {

    public static void validateInputProduct(Product inputProduct) {
        if (!inputProduct.getName().startsWith(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME) &&
                            !inputProduct.getName().startsWith(EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME) &&
                            !inputProduct.getName().startsWith(EnvisatConstants.MERIS_FRS_L1B_PRODUCT_TYPE_NAME) &&
                           !inputProduct.getProductType().startsWith("ATS_TOA_1") &&
                           !inputProduct.getProductType().startsWith("VGT")) {
             throw new OperatorException("Input product must be either MERIS, AATSR or VGT L1b!");
        }
    }

    private static java.util.logging.Logger logger = java.util.logging.Logger.getLogger("aatsrrecalibration");

    public static void logInfoMessage(String msg) {
        if (System.getProperty("gpfMode") != null && System.getProperty("gpfMode").equals("GUI")) {
            JOptionPane.showOptionDialog(null, msg, "MEPIX - Info Message", JOptionPane.DEFAULT_OPTION,
                                         JOptionPane.INFORMATION_MESSAGE, null, null, null);
        } else {
            info(msg);
        }
    }

    public static void logErrorMessage(String msg) {
        if (System.getProperty("gpfMode") != null && System.getProperty("gpfMode").equals("GUI")) {
            JOptionPane.showOptionDialog(null, msg, "MEPIX - Error Message", JOptionPane.DEFAULT_OPTION,
                                         JOptionPane.ERROR_MESSAGE, null, null, null);
        } else {
            info(msg);
        }
    }


    public static void info(final String msg) {
        logger.info(msg);
        System.out.println(msg);
    }


    public static float spectralSlope(float ch1, float ch2, float wl1, float wl2) {
        return (ch2 - ch1)/(wl2 - wl1);
    }

    public static double scaleVgtSlope(float refl0, float refl1, float wl0, float wl1) {
        float scaleValue = 0.5f;
        float slope = 1.0f - Math.abs(1000.0f*MepixUtils.spectralSlope(refl0, refl1, wl0,wl1));
        return Math.max((slope - scaleValue)/(1.0-scaleValue), 0);
    }

    public static float[] correctSaturatedReflectances(float[] reflectance) {

        // if all reflectances are NaN, do not correct
        if (!areReflectancesValid(reflectance)) {
            return reflectance;
        }

        float[] correctedReflectance = new float[reflectance.length];

        // search for first non-NaN value from end of spectrum...
        correctedReflectance[reflectance.length-1] = Float.NaN;
        for (int i=reflectance.length-1; i>=0; i--) {
            if (!Float.isNaN(reflectance[i])) {
                correctedReflectance[reflectance.length-1] = reflectance[i];
                break;
            }
        }

        // correct NaN values from end of spectrum, start with first non-NaN value found above...
        for (int i=reflectance.length-1; i>0; i--) {
            if (Float.isNaN(reflectance[i-1])) {
                correctedReflectance[i-1] = correctedReflectance[i];
            } else {
                correctedReflectance[i-1] = reflectance[i-1];    
            }
        }
        return correctedReflectance; 
    }


    public static boolean areReflectancesValid(float[] reflectance) {
        for (int i=0; i<reflectance.length; i++) {
            if (!Float.isNaN(reflectance[i])) {
                return true;
            }
        }
        return false;
    }
}
