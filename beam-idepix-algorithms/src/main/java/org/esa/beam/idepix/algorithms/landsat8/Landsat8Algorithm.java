package org.esa.beam.idepix.algorithms.landsat8;

/**
 * IDEPIX instrument-specific pixel identification algorithm for Landsat 8
 *
 * @author olafd
 */
public class Landsat8Algorithm implements Landsat8PixelProperties {

    float[] l8Radiance;

    int brightnessBandLand;
    float brightnessThreshLand;
    int brightnessBand1Water;
    float brightnessWeightBand1Water;
    int brightnessBand2Water;
    float brightnessWeightBand2Water;
    float brightnessThreshWater;
    int whitenessBand1Land;
    int whitenessBand2Land;
    float whitenessThreshLand;
    int whitenessBand1Water;
    int whitenessBand2Water;
    float whitenessThreshWater;

    boolean isLand;
    boolean isInvalid;

    @Override
    public boolean isInvalid() {
        return isInvalid;
    }

    @Override
    public boolean isCloud() {
        return !isInvalid() && (isCloudSure() || isCloudAmbiguous());
    }

    @Override
    public boolean isCloudAmbiguous() {
        return !isInvalid() && isCloudSure(); // todo: define if needed
    }

    @Override
    public boolean isCloudSure() {
        return !isInvalid() && isBright() && isWhite();
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
        return isLand;
    }

    @Override
    public boolean isBright() {
        if (isLand()) {
            final Integer landBandIndex = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBandLand);
            return !isInvalid() && (l8Radiance[landBandIndex] > brightnessThreshLand);
        } else {
            final Integer waterBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBand1Water);
            final Integer waterBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBand2Water);
            final float brightnessWaterValue = brightnessWeightBand1Water* l8Radiance[waterBand1Index] +
                    brightnessWeightBand2Water* l8Radiance[waterBand2Index];
            return !isInvalid() && (brightnessWaterValue > brightnessThreshWater);
        }
    }

    @Override
    public boolean isWhite() {
        if (isLand()) {
            final Integer whitenessBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand1Land);
            final Integer whitenessBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand2Land);
            final float whiteness = l8Radiance[whitenessBand1Index] / l8Radiance[whitenessBand2Index];
            return !isInvalid() && (whiteness < whitenessThreshLand);
        } else {
            final Integer whitenessBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand1Water);
            final Integer whitenessBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand2Water);
            final float whiteness = l8Radiance[whitenessBand1Index] / l8Radiance[whitenessBand2Index];
            return !isInvalid() && (whiteness < whitenessThreshWater);
        }
    }

    // setter methods

    public void setL8Radiance(float[] l8Radiance) {
        this.l8Radiance = l8Radiance;
    }

    public void setIsLand(boolean isLand) {
        this.isLand = isLand;
    }

    public void setInvalid(boolean isInvalid) {
        this.isInvalid = isInvalid;
    }

    public void setBrightnessBandLand(int brightnessBandLand) {
        this.brightnessBandLand = brightnessBandLand;
    }

    public void setBrightnessThreshLand(float brightnessThreshLand) {
        this.brightnessThreshLand = brightnessThreshLand;
    }

    public void setBrightnessBand1Water(int brightnessBand1Water) {
        this.brightnessBand1Water = brightnessBand1Water;
    }

    public void setBrightnessWeightBand1Water(float brightnessWeightBand1Water) {
        this.brightnessWeightBand1Water = brightnessWeightBand1Water;
    }

    public void setBrightnessBand2Water(int brightnessBand2Water) {
        this.brightnessBand2Water = brightnessBand2Water;
    }

    public void setBrightnessWeightBand2Water(float brightnessWeightBand2Water) {
        this.brightnessWeightBand2Water = brightnessWeightBand2Water;
    }

    public void setBrightnessThreshWater(float brightnessThreshWater) {
        this.brightnessThreshWater = brightnessThreshWater;
    }

    public void setWhitenessBand1Land(int whitenessBand1Land) {
        this.whitenessBand1Land = whitenessBand1Land;
    }

    public void setWhitenessBand2Land(int whitenessBand2Land) {
        this.whitenessBand2Land = whitenessBand2Land;
    }

    public void setWhitenessThreshLand(float whitenessThreshLand) {
        this.whitenessThreshLand = whitenessThreshLand;
    }

    public void setWhitenessThreshWater(float whitenessThreshWater) {
        this.whitenessThreshWater = whitenessThreshWater;
    }

    public void setWhitenessBand1Water(int whitenessBand1Water) {
        this.whitenessBand1Water = whitenessBand1Water;
    }

    public void setWhitenessBand2Water(int whitenessBand2Water) {
        this.whitenessBand2Water = whitenessBand2Water;
    }

}
