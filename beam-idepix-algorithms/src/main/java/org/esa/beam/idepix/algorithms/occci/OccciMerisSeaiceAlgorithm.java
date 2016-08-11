package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.math.Histogram;
import org.esa.beam.util.math.Range;

import java.awt.*;

/**
 * Class providing methods for advanced sea ice classification, i.e. wet ice identification
 * (following MPa, 21.07.2016)
 *
 * @author olafd
 */
public class OccciMerisSeaiceAlgorithm {

    /**
     * Provides a 'wet ice' value from R,G,B reflectances as described by M.Paperin (nn5.pdf, 20160721)
     *
     * @param reflR - red reflectance
     * @param reflG - green reflectance
     * @param reflB - blue reflectance
     * @param abR - red reflectance histogram 95% lower and upper boundary
     * @param abG - green reflectance histogram 95% lower and upper boundary
     * @param abB - blue reflectance histogram 95% lower and upper boundary
     *
     * @return wetIceValue
     */
    public static float getWetIceValue(float reflR, float reflG, float reflB, double[] abR, double[] abG, double[] abB) {
        final float aR = (float) abR[0];
        final float bR = (float) abR[1];
        final float aG = (float) abG[0];
        final float bG = (float) abG[1];
        final float aB = (float) abB[0];
        final float bB = (float) abB[1];

        final float virtReflR = getHistogramNormalizedRefl(reflR, aR, bR);
        final float virtReflG = getHistogramNormalizedRefl(reflG, aG, bG);
        final float virtReflB = getHistogramNormalizedRefl(reflB, aB, bB);

        return getWetIceValue(virtReflR, virtReflG, virtReflB);
    }

    public static boolean isWetIce(float[][] rhoToaArray, int pixelIndex,
                                   double[] abR, double[] abG, double[] abB, boolean applyBlueFilter) {
        final float reflR = rhoToaArray[2][pixelIndex];
        final float reflG = rhoToaArray[13][pixelIndex];
        final float reflB = rhoToaArray[14][pixelIndex];

        final float aR = (float) abR[0];
        final float bR = (float) abR[1];
        final float aG = (float) abG[0];
        final float bG = (float) abG[1];
        final float aB = (float) abB[0];
        final float bB = (float) abB[1];

        final float virtReflR = getHistogramNormalizedRefl(reflR, aR, bR);
        final float virtReflG = getHistogramNormalizedRefl(reflG, aG, bG);
        final float virtReflB = getHistogramNormalizedRefl(reflB, aB, bB);

        final float wetIceValue = getWetIceValue(virtReflR, virtReflG, virtReflB);

//        if (virtReflR < 1.0 && virtReflR > 0.5 && wetIceValue > 1.5 && virtReflR > 1.3*virtReflG &&
//                virtReflR > 1.3*virtReflB && virtReflG > 0.0 && virtReflB > 0.0 && wetIceValue < 100.0) {
//            return true;
//        } else if (virtReflR > 1.0 && wetIceValue > 1.2 && virtReflR > 1.1*virtReflG
//                && virtReflR > 1.1*virtReflB && virtReflG > 0.0 && virtReflB > 0.0 && wetIceValue < 100) {
//            return true;
//        }
        if (virtReflR < 100.0 && virtReflR > 50.0 && wetIceValue > 1.5 && virtReflR > 1.3*virtReflG &&
                virtReflR > 1.3*virtReflB && virtReflG > 0.0 && virtReflB > 0.0 && wetIceValue < 100.0) {
            return true;
        } else if (virtReflR > 100.0 && wetIceValue > 1.2 && virtReflR > 1.1*virtReflG
                && virtReflR > 1.1*virtReflB && virtReflG > 0.0 && virtReflB > 0.0 && wetIceValue < 100) {
            return true;
        }

        return false;
    }

    public static boolean isBlueIce(float[][] rhoToaArray, int pixelIndex) {
        final float refl1 = rhoToaArray[0][pixelIndex];
        final float refl4 = rhoToaArray[3][pixelIndex];
        final float refl5 = rhoToaArray[4][pixelIndex];
        final float refl3 = rhoToaArray[12][pixelIndex];

        return refl5*refl5/(refl1*refl3) < 1.4 && refl5*refl4/(refl1* refl3) > 1.0 &&
                refl1 > 0.12 && refl5 > 0.12 && refl3 > 0.20 && refl1 < 0.8 && refl5 < 0.8 && refl3 < 0.8;
    }


    /**
     * Provides a reflectance mapping into [0, 255] range, with normalization to histogram boundaries
     *
     * @param refl - input reflectance
     * @param a - histogram lower boundary
     * @param b - histogram upper boundary
     *
     * @return  mapped reflectance in [0, 255]
     */
    public static float getHistogramNormalizedRefl(float refl, float a, float b) {
        return Math.max(0.0f, Math.min(255.0f, 255.0f * (refl - a)/(b - a)));
    }

    /**
     * Provides lower and upper boundaries a and b of 95% histogram for a given input band
     *
     * @param band - input band
     * @param roiExpr - a ROI expression (e.g. consider latitudes in polar regions only)
     *
     * @return double[]{a, b}
     */
    public static double[] computeHistogram95PercentInterval(Band band, String roiExpr) {
        final Mask highLatMask =
                band.getProduct().addMask("highLatMask", roiExpr, "latitudes > 50deg only", Color.gray, Double.NaN);
        final Stx stx = new StxFactory().create(new Mask[]{highLatMask},
                                                new RasterDataNode[]{band},
                                                ProgressMonitor.NULL);

        final Histogram beamHistogram = new Histogram(stx.getHistogram().getBins(0),   // stx.getHistogram() is a JAI histogram!
                                                      stx.getMinimum(),
                                                      stx.getMaximum());
        final Range rangeFor95Percent = beamHistogram.findRangeFor95Percent();
        final double a = rangeFor95Percent.getMin();
        final double b = rangeFor95Percent.getMax();

        return new double[]{a, b};
    }

    private static float getWetIceValue(float reflR, float reflG, float reflB) {
        if (reflR > 0.0 && reflG > 0.0 && reflB > 0.0) {
            return reflR * reflR / (reflB * reflG);
        } else {
            return Float.NaN;
        }
    }
}
