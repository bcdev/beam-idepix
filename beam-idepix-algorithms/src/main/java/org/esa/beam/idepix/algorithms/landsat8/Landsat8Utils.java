package org.esa.beam.idepix.algorithms.landsat8;

import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.jai.JAIUtils;
import org.esa.beam.util.math.Histogram;

import javax.media.jai.RenderedOp;
import java.awt.*;

/**
 * Utility class for Idepix Landsat 8
 *
 * @author olafd
 */
public class Landsat8Utils {
    // if possible, put here everything which is common for both land and water parts

    public static int setupLandsat8Bitmasks(Product cloudProduct) {

        int index = 0;
        int w = cloudProduct.getSceneRasterWidth();
        int h = cloudProduct.getSceneRasterHeight();
        Mask mask;

        mask = Mask.BandMathsType.create("landsat8_invalid",
                Landsat8Constants.F_INVALID_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_INVALID",
                Color.red.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_cloud",
                Landsat8Constants.F_CLOUD_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_CLOUD",
                Color.magenta, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
//        mask = Mask.BandMathsType.create("landsat8_cloud_ambiguous",
//                Landsat8Constants.F_CLOUD_AMBIGUOUS_DESCR_TEXT, w, h,
//                "cloud_classif_flags.F_CLOUD_AMBIGUOUS",
//                Color.yellow, 0.5f);
//        cloudProduct.getMaskGroup().add(index++, mask);
//        mask = Mask.BandMathsType.create("landsat8_cloud_sure",
//                Landsat8Constants.F_CLOUD_SURE_DESCR_TEXT, w, h,
//                "cloud_classif_flags.F_CLOUD_SURE",
//                Color.red, 0.5f);
//        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_cloud_buffer",
                Landsat8Constants.F_CLOUD_BUFFER_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_CLOUD_BUFFER",
                Color.orange, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
//        mask = Mask.BandMathsType.create("landsat8_cloud_shadow",
//                Landsat8Constants.F_CLOUD_SHADOW_DESCR_TEXT, w, h,
//                "cloud_classif_flags.F_CLOUD_SHADOW",
//                Color.red.darker(), 0.5f);
//        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_bright",
                Landsat8Constants.F_BRIGHT_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_BRIGHT",
                Color.yellow.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_white",
                Landsat8Constants.F_WHITE_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_WHITE",
                Color.red.brighter(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
//        mask = Mask.BandMathsType.create("landsat8_snow_ice",
//                Landsat8Constants.F_SNOW_ICE_DESCR_TEXT, w, h,
//                "cloud_classif_flags.F_SNOW_ICE",
//                Color.cyan, 0.5f);
//        cloudProduct.getMaskGroup().add(index++, mask);
//        mask = Mask.BandMathsType.create("landsat8_glint_risk",
//                Landsat8Constants.F_GLINTRISK_DESCR_TEXT, w, h,
//                "cloud_classif_flags.F_GLINTRISK",
//                Color.pink, 0.5f);
//        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_coastline",
                Landsat8Constants.F_COASTLINE_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_COASTLINE",
                Color.green.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("landsat8_land",
                Landsat8Constants.F_LAND_DESCR_TEXT, w, h,
                "cloud_classif_flags.F_LAND",
                Color.green.brighter(), 0.5f);
        cloudProduct.getMaskGroup().add(index, mask);

        return index;
    }

    public static FlagCoding createLandsat8FlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, Landsat8Constants.F_INVALID), Landsat8Constants.F_INVALID_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, Landsat8Constants.F_CLOUD), Landsat8Constants.F_CLOUD_DESCR_TEXT);
//        flagCoding.addFlag("F_CLOUD_AMBIGUOUS", BitSetter.setFlag(0, Landsat8Constants.F_CLOUD_AMBIGUOUS), Landsat8Constants.F_CLOUD_AMBIGUOUS_DESCR_TEXT);
//        flagCoding.addFlag("F_CLOUD_SURE", BitSetter.setFlag(0, Landsat8Constants.F_CLOUD_SURE), Landsat8Constants.F_CLOUD_SURE_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, Landsat8Constants.F_CLOUD_BUFFER), Landsat8Constants.F_CLOUD_BUFFER_DESCR_TEXT);
//        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, Landsat8Constants.F_CLOUD_SHADOW), Landsat8Constants.F_CLOUD_SHADOW_DESCR_TEXT);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, Landsat8Constants.F_BRIGHT), Landsat8Constants.F_BRIGHT_DESCR_TEXT);
        flagCoding.addFlag("F_WHITE", BitSetter.setFlag(0, Landsat8Constants.F_WHITE), Landsat8Constants.F_WHITE_DESCR_TEXT);
//        flagCoding.addFlag("F_SNOW_ICE", BitSetter.setFlag(0, Landsat8Constants.F_SNOW_ICE), Landsat8Constants.F_SNOW_ICE_DESCR_TEXT);
//        flagCoding.addFlag("F_GLINTRISK", BitSetter.setFlag(0, Landsat8Constants.F_GLINTRISK), Landsat8Constants.F_GLINTRISK_DESCR_TEXT);
        flagCoding.addFlag("F_COASTLINE", BitSetter.setFlag(0, Landsat8Constants.F_COASTLINE), Landsat8Constants.F_COASTLINE_DESCR_TEXT);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, Landsat8Constants.F_LAND), Landsat8Constants.F_LAND_DESCR_TEXT);
        return flagCoding;
    }

    public static Histogram getBeamHistogram(RenderedOp histogramImage) {
        javax.media.jai.Histogram jaiHistogram = JAIUtils.getHistogramOf(histogramImage);

        int[] bins = jaiHistogram.getBins(0);
        int minIndex = 0;
        int maxIndex = bins.length - 1;
        for (int i = 0; i < bins.length; i++) {
            if (bins[i] > 0) {
                minIndex = i;
                break;
            }
        }
        for (int i = bins.length - 1; i >= 0; i--) {
            if (bins[i] > 0) {
                maxIndex = i;
                break;
            }
        }
        double lowValue = jaiHistogram.getLowValue(0);
        double highValue = jaiHistogram.getHighValue(0);
        int numBins = jaiHistogram.getNumBins(0);
        double binWidth = (highValue - lowValue) / numBins;
        int[] croppedBins = new int[maxIndex - minIndex + 1];
        System.arraycopy(bins, minIndex, croppedBins, 0, croppedBins.length);
        return new Histogram(croppedBins, lowValue + minIndex * binWidth, lowValue
                + (maxIndex + 1.0) * binWidth);
    }

}
