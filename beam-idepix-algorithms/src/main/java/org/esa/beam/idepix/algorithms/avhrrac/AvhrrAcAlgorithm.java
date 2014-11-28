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

    public void setRadiance(double[] rad) {
        this.radiance = rad;
    }
    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
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
