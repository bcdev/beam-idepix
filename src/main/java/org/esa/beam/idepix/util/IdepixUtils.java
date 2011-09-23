package org.esa.beam.idepix.util;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.idepix.operators.CloudScreeningSelector;
import org.esa.beam.idepix.operators.IdepixConstants;
import org.esa.beam.util.BitSetter;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.util.HashMap;
import java.util.Random;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixUtils {

    private static java.util.logging.Logger logger = java.util.logging.Logger.getLogger("aatsrrecalibration");

    private IdepixUtils() {
    }

    public static boolean validateInputProduct(Product inputProduct, CloudScreeningSelector algorithm) {
        return isInputValid(inputProduct) && isInputConsistent(inputProduct, algorithm);
    }

    public static boolean isInputValid(Product inputProduct) {
        if (!isValidMerisProduct(inputProduct) &&
            !isValidAatsrProduct(inputProduct) &&
            !isValidVgtProduct(inputProduct)) {
            logErrorMessage("Input product must be either MERIS, AATSR or VGT L1b!");
        }
        return true;
    }

    public static boolean isValidMerisProduct(Product product) {
        return EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(product.getProductType()).matches();
    }

    public static boolean isValidAatsrProduct(Product product) {
        return product.getProductType().startsWith(EnvisatConstants.AATSR_L1B_TOA_PRODUCT_TYPE_NAME);
    }

    public static boolean isValidVgtProduct(Product product) {
        return product.getProductType().startsWith(IdepixConstants.SPOT_VGT_PRODUCT_TYPE_PREFIX);
    }


    private static boolean isInputConsistent(Product sourceProduct, CloudScreeningSelector algorithm) {
        if (CloudScreeningSelector.QWG.equals(algorithm) || CloudScreeningSelector.CoastColour.equals(algorithm)) {
            return (isValidMerisProduct(sourceProduct));
        }
        if (CloudScreeningSelector.GlobAlbedo.equals(algorithm)) {
            return (isValidMerisProduct(sourceProduct) ||
                    isValidAatsrProduct(sourceProduct) ||
                    isValidVgtProduct(sourceProduct));
        }
        return false;
    }

    public static void logErrorMessage(String msg) {
        if (System.getProperty("gpfMode") != null && "GUI".equals(System.getProperty("gpfMode"))) {
            JOptionPane.showOptionDialog(null, msg, "IDEPIX - Error Message", JOptionPane.DEFAULT_OPTION,
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
        return (ch2 - ch1) / (wl2 - wl1);
    }

    public static float[] correctSaturatedReflectances(float[] reflectance) {

        // if all reflectances are NaN, do not correct
        if (!areReflectancesValid(reflectance)) {
            return reflectance;
        }

        float[] correctedReflectance = new float[reflectance.length];

        // search for first non-NaN value from end of spectrum...
        correctedReflectance[reflectance.length - 1] = Float.NaN;
        for (int i = reflectance.length - 1; i >= 0; i--) {
            if (!Float.isNaN(reflectance[i])) {
                correctedReflectance[reflectance.length - 1] = reflectance[i];
                break;
            }
        }

        // correct NaN values from end of spectrum, start with first non-NaN value found above...
        for (int i = reflectance.length - 1; i > 0; i--) {
            if (Float.isNaN(reflectance[i - 1])) {
                correctedReflectance[i - 1] = correctedReflectance[i];
            } else {
                correctedReflectance[i - 1] = reflectance[i - 1];
            }
        }
        return correctedReflectance;
    }


    public static boolean areReflectancesValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (!Float.isNaN(aReflectance)) {
                return true;
            }
        }
        return false;
    }

    public static void setNewBandProperties(Band band, String description, String unit, double noDataValue,
                                            boolean useNoDataValue) {
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(noDataValue);
        band.setNoDataValueUsed(useNoDataValue);
    }

    public static HashMap setupMerisWavelengthIndexMap() {
        HashMap<Integer, Integer> merisWavelengthIndexMap = new HashMap<Integer, Integer>();
        merisWavelengthIndexMap.put(412, 0);
        merisWavelengthIndexMap.put(442, 1);
        merisWavelengthIndexMap.put(490, 2);
        merisWavelengthIndexMap.put(510, 3);
        merisWavelengthIndexMap.put(560, 4);
        merisWavelengthIndexMap.put(620, 5);
        merisWavelengthIndexMap.put(665, 6);
        merisWavelengthIndexMap.put(681, 7);
        merisWavelengthIndexMap.put(705, 8);
        merisWavelengthIndexMap.put(753, 9);
        merisWavelengthIndexMap.put(760, 10);
        merisWavelengthIndexMap.put(775, 11);
        merisWavelengthIndexMap.put(865, 12);
        merisWavelengthIndexMap.put(890, 13);
        merisWavelengthIndexMap.put(900, 14);

        return merisWavelengthIndexMap;
    }

    public static FlagCoding createGAFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, IdepixConstants.F_INVALID), null);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, IdepixConstants.F_CLOUD), null);
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_BUFFER), null);
        flagCoding.addFlag("F_CLOUD_BUFFER_LC", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_BUFFER_LC), null);
        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_SHADOW), null);
        flagCoding.addFlag("F_CLEAR_LAND", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_LAND), null);
        flagCoding.addFlag("F_CLEAR_WATER", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_WATER), null);
        flagCoding.addFlag("F_CLEAR_SNOW", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_SNOW), null);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, IdepixConstants.F_LAND), null);
        flagCoding.addFlag("F_WATER", BitSetter.setFlag(0, IdepixConstants.F_WATER), null);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, IdepixConstants.F_BRIGHT), null);
        flagCoding.addFlag("F_WHITE", BitSetter.setFlag(0, IdepixConstants.F_WHITE), null);
        flagCoding.addFlag("F_BRIGHTWHITE", BitSetter.setFlag(0, IdepixConstants.F_BRIGHTWHITE), null);
        flagCoding.addFlag("F_COLD", BitSetter.setFlag(0, IdepixConstants.F_COLD), null);
        flagCoding.addFlag("F_HIGH", BitSetter.setFlag(0, IdepixConstants.F_HIGH), null);
        flagCoding.addFlag("F_VEG_RISK", BitSetter.setFlag(0, IdepixConstants.F_VEG_RISK), null);
        flagCoding.addFlag("F_GLINT_RISK", BitSetter.setFlag(0, IdepixConstants.F_GLINT_RISK), null);

        return flagCoding;
    }


    public static int setupGlobAlbedoCloudscreeningBitmasks(Product gaCloudProduct) {

        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

        mask = Mask.BandMathsType.create("F_INVALID", "Invalid pixels", w, h, "cloud_classif_flags.F_INVALID",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD", "Cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD",
                                         Color.yellow, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_BUFFER", "Cloud + cloud buffer pixels", w, h,
                                         "cloud_classif_flags.F_CLOUD_BUFFER", Color.red, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_BUFFER_LC", "Cloud + LC cloud buffer pixels", w, h,
                                         "cloud_classif_flags.F_CLOUD_BUFFER_LC", Color.blue, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_SHADOW", "Cloud shadow pixels", w, h,
                                         "cloud_classif_flags.F_CLOUD_SHADOW", Color.cyan, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_LAND", "Clear sky pixels over land", w, h,
                                         "cloud_classif_flags.F_CLEAR_LAND", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_WATER", "Clear sky pixels over water", w, h,
                                         "cloud_classif_flags.F_CLEAR_WATER", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_SNOW", "Clear sky pixels, snow covered ", w, h,
                                         "cloud_classif_flags.F_CLEAR_SNOW", Color.magenta, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_LAND", "Pixels over land", w, h, "cloud_classif_flags.F_LAND",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_WATER", "Pixels over water", w, h, "cloud_classif_flags.F_WATER",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_BRIGHT", "Pixels classified as bright", w, h,
                                         "cloud_classif_flags.F_BRIGHT", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_WHITE", "Pixels classified as white", w, h, "cloud_classif_flags.F_WHITE",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_BRIGHTWHITE", "Pixels classified as 'brightwhite'", w, h,
                                         "cloud_classif_flags.F_BRIGHTWHITE", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_COLD", "Cold pixels", w, h, "cloud_classif_flags.F_COLD",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_HIGH", "High pixels", w, h, "cloud_classif_flags.F_HIGH",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_VEG_RISK", "Pixels may contain vegetation", w, h,
                                         "cloud_classif_flags.F_VEG_RISK", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_GLINT_RISK", "Pixels may contain glint", w, h,
                                         "cloud_classif_flags.F_GLINT_RISK", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);

        return index;
    }

    private static Color getRandomColour(Random random) {
        int rColor = random.nextInt(256);
        int gColor = random.nextInt(256);
        int bColor = random.nextInt(256);
        return new Color(rColor, gColor, bColor);
    }

}
