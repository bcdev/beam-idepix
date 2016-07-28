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

    public static boolean isWetIce(float reflR, float reflG, float reflB, double[] abR, double[] abG, double[] abB,
                                   double wetIceThreshold) {

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
        return wetIceValue > wetIceThreshold;
    }

    public static float getWetIceValue(float reflR, float reflG, float reflB) {
        return reflR*reflR/(reflB*reflG);
    }

    public static float getHistogramNormalizedRefl(float refl, float a, float b) {
        return Math.max(0.0f, Math.min(255.0f, 255.0f * (refl - a)/(b - a)));
    }

    public static double[] computeHistogram95PercentInterval(Band band, boolean antarctic) {
        final String roiExpr = antarctic ? "latitude < -50.0" : "latitude > 50.0";
        Mask highLatMask = Mask.BandMathsType.create("highLatMask",
                                                     "latitudes > 50deg only",
                                                     band.getSceneRasterWidth(),
                                                     band.getSceneRasterHeight(),
                                                     roiExpr,
                                                     Color.cyan, 0.5);

        final Stx stx = new StxFactory().create(new Mask[]{highLatMask},
                                                new RasterDataNode[]{band},
                                                ProgressMonitor.NULL);

        final double highValue = stx.getHistogram().getHighValue()[0];    // stx.getHistogram() is a JAI histogram!
        final double lowValue = stx.getHistogram().getLowValue()[0];

        final Histogram beamHistogram = new Histogram(stx.getHistogram().getBins(0),
                                                      band.scaleInverse(lowValue),
                                                      band.scaleInverse(highValue));
        final Range rangeFor95Percent = beamHistogram.findRangeFor95Percent();
        final double a = rangeFor95Percent.getMin();
        final double b = rangeFor95Percent.getMax();

        return new double[]{a, b};
    }
}
