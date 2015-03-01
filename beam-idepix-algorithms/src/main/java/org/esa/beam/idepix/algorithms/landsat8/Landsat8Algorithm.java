package org.esa.beam.idepix.algorithms.landsat8;

/**
 * IDEPIX instrument-specific pixel identification algorithm for Landsat
 *
 * @author olafd
 */
public class Landsat8Algorithm implements Landsat8PixelProperties {

    float[] l8Radiance;

    float brightnessThresh;
    float brightnessCoeffBand4;
    float brightnessCoeffBand5;

    float whitenessThresh;
    int whitenessNumeratorBandIndex;
    int whitenessDenominatorBandIndex;

    boolean isWater;

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public boolean isCloud() {
        return isBright();  // start with this
//        return isBright() && isWhite();  // todo: play with whiteness parameters first
    }

    @Override
    public boolean isCloudAmbiguous() {
        return isCloud(); // todo: define if needed
    }

    @Override
    public boolean isCloudSure() {
        return isCloud(); // todo: define if needed
    }

    @Override
    public boolean isCloudBuffer() {
        return false;   // in post-processing
    }

    @Override
    public boolean isCloudShadow() {
        return false;  // in post-processing when defined
    }

    @Override
    public boolean isSnowIce() {
        return false; // no way to compute?!
    }

    @Override
    public boolean isGlintRisk() {
        return false;  // no way to compute?!
    }

    @Override
    public boolean isCoastline() {
        return false;
    }

    @Override
    public boolean isLand() {
        return !isWater;
    }     // todo: this is weird

    @Override
    public boolean isBright() {
        if (isLand()) {
            return l8Radiance[4] > brightnessThresh;
        } else {
            final float brightnessWaterValue =
                    brightnessCoeffBand4 * l8Radiance[4] + brightnessCoeffBand5 * l8Radiance[5];
            return brightnessWaterValue > brightnessThresh;
        }
    }

    @Override
    public boolean isWhite() {
        final float whiteness =
                l8Radiance[whitenessNumeratorBandIndex] / l8Radiance[whitenessDenominatorBandIndex];
        return whiteness > whitenessThresh;
    }

    // setter methods

    public void setL8Radiance(float[] l8Radiance) {
        this.l8Radiance = l8Radiance;
    }

    public void setIsWater(boolean isWater) {
        this.isWater = isWater;
    }

    public void setBrightnessThresh(float brightnessThresh) {
        this.brightnessThresh= brightnessThresh;
    }

    public void setBrightnessCoeffBand4(float brightnessCoeffBand4) {
        this.brightnessCoeffBand4 = brightnessCoeffBand4;
    }

    public void setBrightnessCoeffBand5(float brightnessCoeffBand5) {
        this.brightnessCoeffBand5 = brightnessCoeffBand5;
    }

    public void setWhitenessThresh(float whitenessThresh) {
        this.whitenessThresh = whitenessThresh;
    }

    public void setWhitenessNumeratorBandIndex(int whitenessNumeratorBandIndex) {
        this.whitenessNumeratorBandIndex = whitenessNumeratorBandIndex;
    }

    public void setWhitenessDenominatorBandIndex(int whitenessDenominatorBandIndex) {
        this.whitenessDenominatorBandIndex = whitenessDenominatorBandIndex;
    }
}
