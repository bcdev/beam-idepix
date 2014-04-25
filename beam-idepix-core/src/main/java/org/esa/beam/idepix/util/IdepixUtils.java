package org.esa.beam.idepix.util;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.unmixing.Endmember;
import org.esa.beam.util.BitSetter;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixUtils {

    private static java.util.logging.Logger logger = java.util.logging.Logger.getLogger("aatsrrecalibration");
    public static final String IDEPIX_CLOUD_FLAGS = "cloud_classif_flags";

    private IdepixUtils() {
    }

    public static boolean validateInputProduct(Product inputProduct, AlgorithmSelector algorithm) {
        return isInputValid(inputProduct) && isInputConsistent(inputProduct, algorithm);
    }

    public static boolean isInputValid(Product inputProduct) {
        if (!isValidMerisProduct(inputProduct) &&
                !isValidVgtProduct(inputProduct)) {
            logErrorMessage("Input product must be either MERIS or VGT L1b!");
        }
        return true;
    }

    public static boolean isValidMerisProduct(Product product) {
        final boolean merisL1TypePatternMatches = EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(product.getProductType()).matches();
        // accept also ICOL L1N products...
        final boolean merisIcolTypePatternMatches = isValidMerisIcolL1NProduct(product);
        final boolean merisCCL1PTypePatternMatches = isValidMerisCCL1PProduct(product);
        return merisL1TypePatternMatches || merisIcolTypePatternMatches || merisCCL1PTypePatternMatches;
    }

    private static boolean isValidMerisIcolL1NProduct(Product product) {
        final String icolProductType = product.getProductType();
        if (icolProductType.endsWith("_1N")) {
            int index = icolProductType.indexOf("_1");
            final String merisProductType = icolProductType.substring(0, index) + "_1P";
            return (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(merisProductType).matches());
        } else {
            return false;
        }
    }

    private static boolean isValidMerisCCL1PProduct(Product product) {
        return IdepixConstants.MERIS_CCL1P_TYPE_PATTERN.matcher(product.getProductType()).matches();
    }

    public static boolean isValidVgtProduct(Product product) {
        return product.getProductType().startsWith(IdepixConstants.SPOT_VGT_PRODUCT_TYPE_PREFIX);
    }

    private static boolean isInputConsistent(Product sourceProduct, AlgorithmSelector algorithm) {
        if (AlgorithmSelector.CoastColour == algorithm) {
            return (isValidMerisProduct(sourceProduct));
        } else  if (AlgorithmSelector.GlobAlbedo == algorithm) {
            return (isValidMerisProduct(sourceProduct) || isValidVgtProduct(sourceProduct));
        } else {
            return false;
        }
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
        if (isNoReflectanceValid(reflectance)) {
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


    public static boolean areAllReflectancesValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (Float.isNaN(aReflectance) || aReflectance <= 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static void setNewBandProperties(Band band, String description, String unit, double noDataValue,
                                            boolean useNoDataValue) {
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(noDataValue);
        band.setNoDataValueUsed(useNoDataValue);
    }

    public static FlagCoding createIdepixFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, IdepixConstants.F_INVALID), null);
        flagCoding.addFlag("F_CLOUD_SURE", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_SURE), null);
        flagCoding.addFlag("F_CLOUD_AMBIGUOUS", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_AMBIGUOUS), null);
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_BUFFER), null);
        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, IdepixConstants.F_CLOUD_SHADOW), null);
        flagCoding.addFlag("F_CLEAR_LAND", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_LAND), null);
        flagCoding.addFlag("F_CLEAR_WATER", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_WATER), null);
        flagCoding.addFlag("F_CLEAR_SNOW", BitSetter.setFlag(0, IdepixConstants.F_CLEAR_SNOW), null);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, IdepixConstants.F_LAND), null);
        flagCoding.addFlag("F_WATER", BitSetter.setFlag(0, IdepixConstants.F_WATER), null);
        flagCoding.addFlag("F_SEAICE", BitSetter.setFlag(0, IdepixConstants.F_SEAICE), null);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, IdepixConstants.F_BRIGHT), null);
        flagCoding.addFlag("F_WHITE", BitSetter.setFlag(0, IdepixConstants.F_WHITE), null);
        flagCoding.addFlag("F_BRIGHTWHITE", BitSetter.setFlag(0, IdepixConstants.F_BRIGHTWHITE), null);
        flagCoding.addFlag("F_HIGH", BitSetter.setFlag(0, IdepixConstants.F_HIGH), null);
        flagCoding.addFlag("F_VEG_RISK", BitSetter.setFlag(0, IdepixConstants.F_VEG_RISK), null);

        return flagCoding;
    }


    public static int setupIdepixCloudscreeningBitmasks(Product gaCloudProduct) {

        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

//        maskGroup.add(Mask.BandMathsType.create("cc_land", "IDEPIX CC land flag", w, h,
//                                                CLOUD_FLAGS + ".F_LAND",
//                                                Color.GREEN.darker(), 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_coastline", "IDEPIX CC coastline flag", w, h,
//                                                CLOUD_FLAGS + ".F_COASTLINE",
//                                                Color.GREEN, 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_cloud", "IDEPIX CC cloud flag", w, h,
//                                                CLOUD_FLAGS + ".F_CLOUD",
//                                                Color.YELLOW.darker(), 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_cloud_ambiguous", "IDEPIX CC cloud ambiguous flag", w, h,
//                                                CLOUD_FLAGS + ".F_CLOUD_AMBIGUOUS",
//                                                Color.YELLOW, 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_cloud_buffer", "IDEPIX CC cloud buffer flag", w, h,
//                                                CLOUD_FLAGS + ".F_CLOUD_BUFFER",
//                                                new Color(204, 255, 204), 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_cloud_shadow", "IDEPIX CC cloud shadow flag", w, h,
//                                                CLOUD_FLAGS + ".F_CLOUD_SHADOW",
//                                                Color.BLUE, 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_snow_ice", "IDEPIX CC snow/ice flag", w, h,
//                                                CLOUD_FLAGS + ".F_SNOW_ICE", Color.CYAN, 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_glint_risk", "IDEPIX CC glint risk flag", w, h,
//                                                CLOUD_FLAGS + ".F_GLINTRISK", Color.PINK, 0.5f));
//        maskGroup.add(Mask.BandMathsType.create("cc_mixed_pixel", "IDEPIX CC mixed pixel flag", w, h,
//                                                CLOUD_FLAGS + ".F_MIXED_PIXEL",
//                                                Color.GREEN.darker().darker(), 0.5f));

        mask = Mask.BandMathsType.create("lc_invalid", "IDEPIX LC invalid flag", w, h, "cloud_classif_flags.F_INVALID",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_cloud", "IDEPIX LC cloud flag", w, h, "cloud_classif_flags.F_CLOUD_SURE",
                                         Color.yellow, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_cloud_ambiguous", "IDEPIX LC cloud ambiguous flag", w, h, "cloud_classif_flags.F_CLOUD_AMBIGUOUS",
                                         Color.yellow, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_cloud_buffer", "IDEPIX LC cloud buffer flag", w, h,
                                         "cloud_classif_flags.F_CLOUD_BUFFER", Color.red, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_cloud_shadow", "IDEPIX LC cloud shadow flag", w, h,
                                         "cloud_classif_flags.F_CLOUD_SHADOW", Color.cyan, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_clear_land", "IDEPIX LC clear land flag", w, h,
                                         "cloud_classif_flags.F_CLEAR_LAND", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_clear_water", "IDEPIX LC clear water flag", w, h,
                                         "cloud_classif_flags.F_CLEAR_WATER", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_clear_snow", "IDEPIX LC clear snow flag ", w, h,
                                         "cloud_classif_flags.F_CLEAR_SNOW", Color.magenta, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_land", "IDEPIX LC land flag", w, h, "cloud_classif_flags.F_LAND",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_water", "IDEPIX LC water flag", w, h, "cloud_classif_flags.F_WATER",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_ice", "IDEPIX LC ice flag", w, h, "cloud_classif_flags.F_SEAICE",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_bright", "IDEPIX LC bright flag", w, h,
                                         "cloud_classif_flags.F_BRIGHT", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_white", "IDEPIX LC white flag", w, h, "cloud_classif_flags.F_WHITE",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_brightwhite", "IDEPIX LC brightwhite flag", w, h,
                                         "cloud_classif_flags.F_BRIGHTWHITE", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_high", "IDEPIX LC 'high' flag", w, h, "cloud_classif_flags.F_HIGH",
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("lc_veg_risk", "IDEPIX LC vegetation risk flag", w, h,
                                         "cloud_classif_flags.F_VEG_RISK", getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);

        return index;
    }

    public static Endmember[] setupCCSpectralUnmixingEndmembers() {
        Endmember[] endmembers = new Endmember[IdepixConstants.SMA_ENDMEMBER_NAMES.length];
        for (int i = 0; i < endmembers.length; i++) {
            endmembers[i] = new Endmember(IdepixConstants.SMA_ENDMEMBER_NAMES[i],
                                          IdepixConstants.SMA_ENDMEMBER_WAVELENGTHS,
                                          IdepixConstants.SMA_ENDMEMBER_RADIATIONS[i]);

        }

        return endmembers;
    }

    public static double convertGeophysicalToMathematicalAngle(double inAngle) {
        if (0.0 <= inAngle && inAngle < 90.0) {
            return (90.0 - inAngle);
        } else if (90.0 <= inAngle && inAngle < 360.0) {
            return (90.0 - inAngle + 360.0);
        } else {
            // invalid
            return Double.NaN;
        }
    }

    public static boolean isNoReflectanceValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (!Float.isNaN(aReflectance) && aReflectance > 0.0f) {
                return false;
            }
        }
        return true;
    }

    private static Color getRandomColour(Random random) {
        int rColor = random.nextInt(256);
        int gColor = random.nextInt(256);
        int bColor = random.nextInt(256);
        return new Color(rColor, gColor, bColor);
    }

}
