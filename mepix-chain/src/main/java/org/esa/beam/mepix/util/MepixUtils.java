package org.esa.beam.mepix.util;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;

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
}
