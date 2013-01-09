package org.esa.beam.idepix.util;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.unmixing.Endmember;
import org.esa.beam.util.BitSetter;

import javax.swing.JOptionPane;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
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
                !isValidAatsrProduct(inputProduct) &&
                !isValidVgtProduct(inputProduct) &&
                !isValidMerisAatsrSynergyProduct(inputProduct)) {
            logErrorMessage("Input product must be either MERIS, AATSR, colocated MERIS/AATSR, or VGT L1b!");
        }
        return true;
    }

    public static boolean isValidMerisProduct(Product product) {
        final boolean merisL1TypePatternMatches = EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(product.getProductType()).matches();
        // accept also ICOL L1N products...
        final boolean merisIcolTypePatternMatches = isValidMerisIcolL1NProduct(product);
        return merisL1TypePatternMatches || merisIcolTypePatternMatches;
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

    public static boolean isValidAatsrProduct(Product product) {
        return product.getProductType().startsWith(EnvisatConstants.AATSR_L1B_TOA_PRODUCT_TYPE_NAME);
    }

    public static boolean isValidVgtProduct(Product product) {
        return product.getProductType().startsWith(IdepixConstants.SPOT_VGT_PRODUCT_TYPE_PREFIX);
    }

    public static boolean isValidMerisAatsrSynergyProduct(Product product) {
        // todo: needs to be more strict, but for the moment we assume this is enough...
        return product.getProductType().contains("COLLOCATED");
    }

    private static boolean isInputConsistent(Product sourceProduct, AlgorithmSelector algorithm) {
        if (AlgorithmSelector.IPF == algorithm ||
                AlgorithmSelector.CoastColour == algorithm ||
                AlgorithmSelector.GlobCover == algorithm ||
                AlgorithmSelector.MagicStick == algorithm ||
                AlgorithmSelector.Schiller == algorithm) {
            return (isValidMerisProduct(sourceProduct));
        }
        return AlgorithmSelector.GlobAlbedo == algorithm &&
                (isValidMerisProduct(sourceProduct) ||
                        isValidAatsrProduct(sourceProduct) ||
                        isValidVgtProduct(sourceProduct) ||
                        isValidMerisAatsrSynergyProduct(sourceProduct));
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

    public static Map<Integer, Integer> setupMerisWavelengthIndexMap() {
        Map<Integer, Integer> merisWavelengthIndexMap = new HashMap<Integer, Integer>();
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

    public static FlagCoding createIdepixFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, IdepixConstants.F_INVALID), null);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, IdepixConstants.F_CLOUD), null);
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
        flagCoding.addFlag("F_GLINT_RISK", BitSetter.setFlag(0, IdepixConstants.F_GLINT_RISK), null);

        return flagCoding;
    }


    public static int setupIdepixCloudscreeningBitmasks(Product gaCloudProduct) {

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
        mask = Mask.BandMathsType.create("F_SEAICE", "Sea ice pixels", w, h, "cloud_classif_flags.F_SEAICE",
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

    private static Color getRandomColour(Random random) {
        int rColor = random.nextInt(256);
        int gColor = random.nextInt(256);
        int bColor = random.nextInt(256);
        return new Color(rColor, gColor, bColor);
    }

    public static void setCloudBufferLC(String bandName, Tile targetTile, Rectangle rectangle) {
        //  set alternative cloud buffer flag as used in LC-CCI project:
        // 1. use 2x2 square with reference pixel in upper left
        // 2. move this square row-by-row over the tile
        // 3. if reference pixel is not clouds, don't do anything
        // 4. if reference pixel is cloudy:
        //    - if 2x2 square only has cloud pixels, then set cloud buffer of two pixels
        //      in both x and y direction of reference pixel.
        //    - if 2x2 square also has non-cloudy pixels, do the same but with cloud buffer of only 1

        if (bandName.equals(IDEPIX_CLOUD_FLAGS)) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height - 1; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                    if (targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD)) {
                        // reference pixel is upper left (x, y)
                        // first set buffer of 1 in each direction
                        int bufferWidth = 1;
                        int LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                        int RIGHT_BORDER = Math.min(x + bufferWidth, rectangle.x + rectangle.width - 1);
                        int TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                        int BOTTOM_BORDER = Math.min(y + bufferWidth, rectangle.y + rectangle.height - 1);
                        // now check if whole 2x2 square (x+1,y), (x, y+1), (x+1, y+1) is cloudy
                        if (targetTile.getSampleBit(x + 1, y, IdepixConstants.F_CLOUD) &&
                                targetTile.getSampleBit(x, y + 1, IdepixConstants.F_CLOUD) &&
                                targetTile.getSampleBit(x + 1, y + 1, IdepixConstants.F_CLOUD)) {
                            // set buffer of 2 in each direction
                            bufferWidth = 2;
                            LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                            RIGHT_BORDER = Math.min(x + 1 + bufferWidth, rectangle.x + rectangle.width - 1);
                            TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                            BOTTOM_BORDER = Math.min(y + 1 + bufferWidth, rectangle.y + rectangle.height - 1);
                        }
                        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                                targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                            }
                        }

                    }
                }
            }
            int bufferWidth = 1;

            // south tile boundary...
            final int ySouth = rectangle.y + rectangle.height - 1;
            for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                int LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                int RIGHT_BORDER = Math.min(x + bufferWidth, rectangle.x + rectangle.width - 1);
//                int TOP_BORDER = ySouth - bufferWidth;
                int TOP_BORDER = Math.max(rectangle.y, ySouth - bufferWidth);
                if (targetTile.getSampleBit(x, ySouth, IdepixConstants.F_CLOUD)) {
                    for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                        for (int j = TOP_BORDER; j <= ySouth; j++) {
                            targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                        }
                    }
                }
            }

            // east tile boundary...
            final int xEast = rectangle.x + rectangle.width - 1;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height - 1; y++) {
//                int LEFT_BORDER = xEast - bufferWidth;
                int LEFT_BORDER = Math.max(rectangle.x, xEast - bufferWidth);
                int TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                int BOTTOM_BORDER = Math.min(y + bufferWidth, rectangle.y + rectangle.height - 1);
                if (targetTile.getSampleBit(xEast, y, IdepixConstants.F_CLOUD)) {
                    for (int i = LEFT_BORDER; i <= xEast; i++) {
                        for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                            targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                        }
                    }
                }
            }
            // pixel in lower right corner...
            if (targetTile.getSampleBit(xEast, ySouth, IdepixConstants.F_CLOUD)) {
                for (int i = Math.max(rectangle.x, xEast - 1); i <= xEast; i++) {
                    for (int j = Math.max(rectangle.y, ySouth - 1); j <= ySouth; j++) {
                        targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                    }
                }
            }
        }
    }

}
