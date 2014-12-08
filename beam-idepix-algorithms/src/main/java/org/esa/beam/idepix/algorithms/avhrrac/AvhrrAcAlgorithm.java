package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX instrument-specific pixel identification algorithm for GlobAlbedo: abstract superclass
 *
 * @author olafd
 */
public class AvhrrAcAlgorithm implements AvhrrAcPixelProperties {

    float waterFraction;
    double[] radiance;
    boolean l1FlagLand;
    double[] nnOutput;

    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;
    double avhrracSchillerNNCloudSureSnowSeparationValue;

    double reflCh1;
    double reflCh2;
    double reflCh3;
    double btCh4;
    double btCh5;

    double reflCh1Thresh;
    double reflCh2Thresh;
    double r2r1RatioThresh;
    double r3r1RatioThresh;
    double btCh4Thresh;
    double btCh5Thresh;

    @Override
    public boolean isInvalid() {
        return !IdepixUtils.areAllReflectancesValid(radiance);
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        if (isLand() || isCloudSure()) {   // this check has priority
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
        if (isLand() || isSnowIce()) {   // this check has priority
            return false;
        }

        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] >= avhrracSchillerNNCloudAmbiguousSureSeparationValue &&
                    nnOutput[0] < avhrracSchillerNNCloudSureSnowSeparationValue;   // separation numbers from report HS, 0141112 for NN Nr.2
        } else {
            return false;
        }
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
    public boolean isSnowIce() {
        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] > avhrracSchillerNNCloudSureSnowSeparationValue && nnOutput[0] <= 5.0;    // separation numbers from HS, 20140923
        } else {
            // fallback
            // needs ndsi and brightness
//            return (!isInvalid() && brightValue() > THRESH_BRIGHT_SNOW_ICE && ndsiValue() > THRESH_NDSI_SNOW_ICE);
            return false;  // todo later
        }
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
    public boolean isReflCh1Bright() {
        return reflCh1 > reflCh1Thresh;
    }
    public boolean isReflCh2Bright() {
        return reflCh2 > reflCh2Thresh;
    }
    public boolean isR2R1RatioAboveThresh() {
        return reflCh2/reflCh1 > r2r1RatioThresh;
    }
    public boolean isR3R1RatioAboveThresh() {
        return reflCh3/reflCh1 > r3r1RatioThresh;
    }
    public boolean isCh4BtAboveThresh() {
        return btCh4 > btCh4Thresh;
    }
    public boolean isCh5BtAboveThresh() {
        return btCh5 > btCh5Thresh;
    }

    public void setReflCh1(double reflCh1) {
        this.reflCh1 = reflCh1;
    }

    public void setReflCh2(double reflCh2) {
        this.reflCh2 = reflCh2;
    }

    public void setReflCh3(double reflCh3) {
        this.reflCh3 = reflCh3;
    }

    public void setBtCh4(double btCh4) {
        this.btCh4 = btCh4;
    }

    public void setBtCh5(double btCh5) {
        this.btCh5 = btCh5;
    }

    public void setReflCh1Thresh(double reflCh1Thresh) {
        this.reflCh1Thresh = reflCh1Thresh;
    }

    public void setReflCh2Thresh(double reflCh2Thresh) {
        this.reflCh2Thresh = reflCh2Thresh;
    }

    public void setR2r1RatioThresh(double r2r1RatioThresh) {
        this.r2r1RatioThresh = r2r1RatioThresh;
    }

    public void setR3r1RatioThresh(double r3r1RatioThresh) {
        this.r3r1RatioThresh = r3r1RatioThresh;
    }

    public void setBtCh4Thresh(double btCh4Thresh) {
        this.btCh4Thresh = btCh4Thresh;
    }

    public void setBtCh5Thresh(double btCh5Thresh) {
        this.btCh5Thresh = btCh5Thresh;
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
}
