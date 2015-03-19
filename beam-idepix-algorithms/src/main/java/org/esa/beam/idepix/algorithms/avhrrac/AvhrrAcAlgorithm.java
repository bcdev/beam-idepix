package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * IDEPIX instrument-specific pixel identification algorithm for GlobAlbedo: abstract superclass
 *
 * @author olafd
 */
public class AvhrrAcAlgorithm implements AvhrrAcPixelProperties {

    float waterFraction;
    double[] radiance;
    double[] nnOutput;

    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;
    double avhrracSchillerNNCloudSureSnowSeparationValue;

    double reflCh1;
    double reflCh2;
    double reflCh3;
    double btCh3;
    double btCh4;
    double btCh5;

    double rho3b;
    double emissivity3b;
    double ndsi;

    String noaaId;
    double distanceCorr;
    double sza;

//    double reflCh1Thresh;
//    double reflCh2Thresh;
//    double r2r1RatioThresh;
//    double r3r1RatioThresh;
//    double btCh4Thresh;
//    double btCh5Thresh;

    private double latitude;
    private double longitude;

    @Override
    public boolean isInvalid() {
        return !IdepixUtils.areAllReflectancesValid(radiance);
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isSnowIce() {

        boolean isSnowIce = isCloudSureSchiller() && emissivity3b < AvhrrAcConstants.EMISSIVITY_THRESH;
        // todo: also consider NDSI?!

        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (!isSnowIce && nnOutput != null) {
            // separation numbers from HS, 20140923
            isSnowIce = nnOutput[0] > avhrracSchillerNNCloudSureSnowSeparationValue && nnOutput[0] <= 5.0;
        }

        return isSnowIce;
    }

    @Override
    public boolean isCloudAmbiguous() {
        if (isCloudSure()) {   // this check has priority
            return false;
        }

        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] >= avhrracSchillerNNCloudAmbiguousLowerBoundaryValue &&
                    nnOutput[0] < avhrracSchillerNNCloudAmbiguousSureSeparationValue;    // separation numbers from report HS, 20141112 for NN Nr.2
//            return nnOutput[0] >= 0.48 && nnOutput[0] < 0.48;      // CB: cloud sure gives enough clouds, no ambiguous needed, 20141111
        } else {
            return false;
        }
    }

    @Override
    public boolean isCloudSure() {
        if (isSnowIce()) {   // this check has priority
            return false;
        }

        boolean isCloudAdditional = isCloudSnowIceFromDecisionTree();
        boolean isCloudSureSchiller = false;
        if (!isCloudAdditional) {
            isCloudSureSchiller = isCloudSureSchiller();
        }

        return isCloudSureSchiller || isCloudAdditional;
    }

    private boolean isCloudSureSchiller() {
        boolean isCloudSureSchiller;
        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (nnOutput != null) {
            isCloudSureSchiller =  nnOutput[0] >= avhrracSchillerNNCloudAmbiguousSureSeparationValue &&
                    nnOutput[0] < avhrracSchillerNNCloudSureSnowSeparationValue;   // separation numbers from report HS, 0141112 for NN Nr.2
        } else {
            isCloudSureSchiller = false;
        }
        return isCloudSureSchiller;
    }

    private boolean isCloudSnowIceFromDecisionTree() {
        // first apply additional tests (GK, 20150313):

        // 1. RGCT test:
        final double ndvi = (reflCh2 - reflCh1)/(reflCh2 + reflCh1);
        final double rgctThresh = getRgctThreshold(ndvi);
        final boolean isCloudRGCT = isLand() && reflCh1/100.0 > rgctThresh;

        // 2. RRCT test:
        final double rrctThresh = 1.1;
        final boolean isCloudRRCT = isLand() && !isDesertArea() && reflCh2/reflCh1 < rrctThresh;

        // 3. C3AT test:
        final double c3atThresh = 0.06;
        final boolean isCloudC3AT = isLand() && !isDesertArea() && rho3b > c3atThresh;

        // 4. TGCT test
        final double tgctThresh = 244.0;
        final boolean isCloudTGCT = btCh4 < tgctThresh;

        // 5. FMFT test
        final double fmftThresh = getFmftThreshold();
        final boolean isCloudFMFT = (btCh4 - btCh5) > fmftThresh;

        // 6. TMFT test
        final double bt34 = btCh3 - btCh4;
        final double tmftMinThresh = getTmftMinThreshold(bt34);
        final double tmftMaxThresh = getTmftMaxThreshold(bt34);
        final boolean isClearTMFT = bt34 > tmftMinThresh && bt34 < tmftMaxThresh;

        // 7. Emissivity test
//        final double emissivityThresh = 0.022; // or 2.2?
        final double emissivityThresh = 2.2; // or 2.2?
        final boolean isCloudEmissivity = emissivity3b > emissivityThresh;

        // now use combinations:
        //
        // if (RGCT AND FMFT) then cloud
        // if [ NOT RGCT BUT desert AND (FMFT OR (TGCT AND lat<latMaxThresh)) ] then cloud
        // if [ NOT RGCT BUT (RRCT AND FMFT) ] then cloud
        // if [ NOT RGCT BUT (RRCT AND C3AT) ] then cloud
        // if TMFT: clear pixels must not become cloud in any case!
        // cloud ONLY if cloudEmissivity
        //
        // apply Schiller AFTER these test for pixels not yet cloud!!
        boolean isCloudAdditional = false;
        if (!isClearTMFT && isCloudEmissivity) {
            // first branch of condition tree:
            if (isCloudRGCT && isCloudFMFT) {
                isCloudAdditional = true;
            }
            // second branch of condition tree:
            if ((isDesertArea() && (isCloudFMFT || Math.abs(latitude) < AvhrrAcConstants.LAT_MAX_THRESH)) ||
                        (isCloudRRCT && isCloudFMFT) ||
                        (isCloudRRCT && isCloudC3AT)) {
                isCloudAdditional = true;
            }
        }
        return isCloudAdditional;
    }

    private double getTmftMinThreshold(double bt34) {
        int tmftMinThresholdIndexRow = (int) ((btCh4 - 190.0)/10.0);
        tmftMinThresholdIndexRow = Math.max(0, tmftMinThresholdIndexRow);
        tmftMinThresholdIndexRow = Math.min(13, tmftMinThresholdIndexRow);
        int tmftMinThresholdIndexColumn = (int) ((bt34 - 7.5)/15.0) + 1;
        tmftMinThresholdIndexColumn = Math.max(0, tmftMinThresholdIndexColumn);
        tmftMinThresholdIndexColumn = Math.min(3, tmftMinThresholdIndexColumn);

        final int tmftMinThresholdIndex = 4*tmftMinThresholdIndexRow + tmftMinThresholdIndexColumn;

        return AvhrrAcConstants.tmftTestMinThresholds[tmftMinThresholdIndex];
    }

    private double getTmftMaxThreshold(double bt34) {
        int tmftMaxThresholdIndexRow = (int) ((btCh4 - 190.0)/10.0);
        tmftMaxThresholdIndexRow = Math.max(0, tmftMaxThresholdIndexRow);
        tmftMaxThresholdIndexRow = Math.min(13, tmftMaxThresholdIndexRow);
        int tmftMaxThresholdIndexColumn = (int) ((bt34 - 7.5)/15.0) + 1;
        tmftMaxThresholdIndexColumn = Math.max(0, tmftMaxThresholdIndexColumn);
        tmftMaxThresholdIndexColumn = Math.min(3, tmftMaxThresholdIndexColumn);

        final int tmftMaxThresholdIndex = 4*tmftMaxThresholdIndexRow + tmftMaxThresholdIndexColumn;

        return AvhrrAcConstants.tmftTestMaxThresholds[tmftMaxThresholdIndex];
    }

    private double getFmftThreshold() {
        int fmftThresholdIndex = (int) (btCh4 - 200.0);
        fmftThresholdIndex = Math.max(0, fmftThresholdIndex);
        fmftThresholdIndex = Math.min(120, fmftThresholdIndex);
        return AvhrrAcConstants.fmftTestThresholds[fmftThresholdIndex];
    }

    private boolean isDesertArea() {
        return (latitude >= 10.0 && latitude < 35.0 && longitude >= 20.0 && longitude < 30.0) ||
                (latitude >= 5.0 && latitude < 50.0 && longitude >= 30.0 && longitude < 60.0) ||
                (latitude >= 25.0 && latitude < 50.0 && longitude >= 60.0 && longitude < 110.0) ||
                (latitude >= -31.0 && latitude < -19.0 && longitude >= 121.0 && longitude < 141.0);
    }

    private double getRgctThreshold(double ndvi) {
        double rgctThresh = Double.MAX_VALUE;
        if (ndvi < -0.05) {
            rgctThresh = 0.8;
        } else if (ndvi >= -0.05 && ndvi < 0.0) {
            rgctThresh = 0.6;
        } else if (ndvi >= 0.0 && ndvi < 0.05) {
            rgctThresh = 0.5;
        } else if (ndvi >= 0.05 && ndvi < 0.1) {
            rgctThresh = 0.4;
        } else if (ndvi >= 0.1 && ndvi < 0.15) {
            rgctThresh = 0.35;
        }  else if (ndvi >= 0.15 && ndvi < 0.25) {
            rgctThresh = 0.3;
        } else if (ndvi >= 0.25) {
            rgctThresh = 0.25;
        }
        return rgctThresh;
    }

    @Override
    public boolean isCloudBuffer() {
        // is applied in post processing!
        return false;
    }

    @Override
    public boolean isCloudShadow() {
        // is applied in post processing!
        return false;
    }

    @Override
    public boolean isCoastline() {
        // NOTE that this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502). If so, get a coastline in post processing approach.
        return waterFraction < 100 && waterFraction > 0;
    }

    @Override
    public boolean isLand() {
        return waterFraction == 0;
    }

    @Override
    public boolean isGlintRisk() {
        return false;
    }

    /// test tests... :-)
//    public boolean isReflCh1Bright() {
//        return reflCh1 > reflCh1Thresh;
//    }
//    public boolean isReflCh2Bright() {
//        return reflCh2 > reflCh2Thresh;
//    }
//    public boolean isR2R1RatioAboveThresh() {
//        return reflCh2/reflCh1 > r2r1RatioThresh;
//    }
//    public boolean isR3R1RatioAboveThresh() {
//        return reflCh3/reflCh1 > r3r1RatioThresh;
//    }
//    public boolean isCh4BtAboveThresh() {
//        return (btCh4-273.15) > btCh4Thresh;
//    }
//    public boolean isCh5BtAboveThresh() {
//        return (btCh5-273.15) > btCh5Thresh;
//    }

//    public void setReflCh1Thresh(double reflCh1Thresh) {
//        this.reflCh1Thresh = reflCh1Thresh;
//    }
//
//    public void setReflCh2Thresh(double reflCh2Thresh) {
//        this.reflCh2Thresh = reflCh2Thresh;
//    }
//
//    public void setR2r1RatioThresh(double r2r1RatioThresh) {
//        this.r2r1RatioThresh = r2r1RatioThresh;
//    }
//
//    public void setR3r1RatioThresh(double r3r1RatioThresh) {
//        this.r3r1RatioThresh = r3r1RatioThresh;
//    }
//
//    public void setBtCh4Thresh(double btCh4Thresh) {
//        this.btCh4Thresh = btCh4Thresh;
//    }
//
//    public void setBtCh5Thresh(double btCh5Thresh) {
//        this.btCh5Thresh = btCh5Thresh;
//    }

    public void setReflCh1(double reflCh1) {
        this.reflCh1 = reflCh1;
    }

    public void setReflCh2(double reflCh2) {
        this.reflCh2 = reflCh2;
    }

    public void setReflCh3(double reflCh3) {
        this.reflCh3 = reflCh3;
    }
    public void setBtCh3(double btCh3) {
        this.btCh3 = btCh3;
    }

    public void setBtCh4(double btCh4) {
        this.btCh4 = btCh4;
    }

    public void setBtCh5(double btCh5) {
        this.btCh5 = btCh5;
    }

    public void setRadiance(double[] rad) {
        this.radiance = rad;
    }
    public void setWaterFraction(float waterFraction) {
        this.waterFraction = waterFraction;
    }
    public void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

    public void setAmbiguousLowerBoundaryValue(double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue) {
       this.avhrracSchillerNNCloudAmbiguousLowerBoundaryValue = avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;
    }

    public void setAmbiguousSureSeparationValue(double avhrracSchillerNNCloudAmbiguousSureSeparationValue) {
       this.avhrracSchillerNNCloudAmbiguousSureSeparationValue = avhrracSchillerNNCloudAmbiguousSureSeparationValue;
    }

    public void setSureSnowSeparationValue(double avhrracSchillerNNCloudSureSnowSeparationValue) {
       this.avhrracSchillerNNCloudSureSnowSeparationValue = avhrracSchillerNNCloudSureSnowSeparationValue;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setSza(double sza) {
        this.sza = sza;
    }

    public void setDistanceCorr(double distanceCorr) {
        this.distanceCorr = distanceCorr;
    }

    public void computeAdditionalSpectralQuantities() {
        final double solar3b = AvhrrAcConstants.SOLAR_3b;
        final double ew3b = noaaId.equals("11") ? AvhrrAcConstants.EW_3b[0] : AvhrrAcConstants.EW_3b[1];
        final double A0 = noaaId.equals("11") ? AvhrrAcConstants.A0[0] : AvhrrAcConstants.A0[1];
        final double B0 = noaaId.equals("11") ? AvhrrAcConstants.B0[0] : AvhrrAcConstants.B0[1];
        final double C0 = noaaId.equals("11") ? AvhrrAcConstants.C0[0] : AvhrrAcConstants.C0[1];
        final double a13b = noaaId.equals("11") ? AvhrrAcConstants.a1_3b[0] : AvhrrAcConstants.a1_3b[1];
        final double a23b = noaaId.equals("11") ? AvhrrAcConstants.a2_3b[0] : AvhrrAcConstants.a2_3b[1];
        final double c1 = AvhrrAcConstants.c1;
        final double c2 = AvhrrAcConstants.c2;

        final double T3bB0 = (btCh4 - btCh5) > 1.0 ? A0 + B0*btCh4 + C0*(btCh4 - btCh5) : btCh4;

        final double expEnumerator = c2*AvhrrAcConstants.NU_CH3;
        final double expDenominator = (T3bB0 - a13b)/a23b;
        final double expTerm = Math.exp(expEnumerator/expDenominator);
//        final double btCh4Celsius = btCh4 - 273.15;
        final double btCh4Celsius = btCh4;  // test!
        final double R3bem = btCh4Celsius > 0.0 ? c1*Math.pow(AvhrrAcConstants.NU_CH3, 3.0)/(expTerm - 1.0) : 0.0;
        final double B03b = 1000.0*solar3b/ew3b;

        emissivity3b = btCh4Celsius > 0.0 ? radiance[2]/R3bem : 0.0;

        if (sza < 90.0 && R3bem > 0.0 && radiance[2] > 0.0) {
            rho3b = Math.PI * (radiance[2] - R3bem) /
                    ((B03b * Math.cos(sza * MathUtils.DTOR) / distanceCorr) - Math.PI * R3bem);
        } else if (sza > 90.0 && emissivity3b > 0.0) {
            rho3b = 1.0 - emissivity3b;
        } else {
            rho3b = Double.NaN;
        }

        ndsi = !Double.isNaN(rho3b) ? (reflCh1 - rho3b)/(reflCh1 + rho3b) : Double.NaN;
    }

    public void setNoaaId(String noaaId) {
        this.noaaId = noaaId;
    }

    public float getWaterFraction() {
        return waterFraction;
    }

    public double getRho3b() {
        return rho3b;
    }

    public double getEmissivity3b() {
        return emissivity3b;
    }

    public double getNdsi() {
        return ndsi;
    }
}
