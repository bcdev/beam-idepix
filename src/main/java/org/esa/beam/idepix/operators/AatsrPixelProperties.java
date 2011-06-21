package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from AATSR L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class AatsrPixelProperties extends AbstractPixelProperties {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.50f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.3f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.2f;
    private static final float WHITE_THRESH = 0.9f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;
    private static final float GLINT_THRESH = 0.9f;
    private static final float TEMPERATURE_THRESH = 0.6f;

    public static final int L1B_F_LAND = 0;
    public static final int L1B_F_GLINT_RISK = 2;

    private float[] refl;
    private float btemp1200;
    private boolean l1FlagLand;
    private boolean l1FlagGlintRisk;
    private boolean useFwardViewForCloudMask;

    private float brightwhiteThresh = BRIGHTWHITE_THRESH;
    private float cloudThresh = CLOUD_THRESH;
    private float ndsiThresh = NDSI_THRESH;


    @Override
    public boolean isBrightWhite() {
        return (whiteValue() + brightValue() > brightwhiteThresh);
    }

    @Override
    public boolean isCloud() {
        return (whiteValue() + brightValue() + pressureValue() + temperatureValue() > cloudThresh && !isClearSnow());
    }

    @Override
    public boolean isClearLand() {
        if (isInvalid()) {
            return false;
        }
        float landValue;

        if (!MathUtils.equalValues(radiometricLandValue(), UNCERTAINTY_VALUE)) {
            landValue = radiometricLandValue();
        } else if (aPrioriLandValue() > UNCERTAINTY_VALUE) {
            landValue = aPrioriLandValue();
        } else {
            return false; // this means: if we have no information about land, we return isClearLand = false
        }
        return (!isCloud() && landValue > LAND_THRESH);
    }

    @Override
    public boolean isClearWater() {
        if (isInvalid()) {
            return false;
        }
        float waterValue;
        if (!MathUtils.equalValues(radiometricWaterValue(), UNCERTAINTY_VALUE)) {
            waterValue = radiometricWaterValue();
        } else if (aPrioriWaterValue() > UNCERTAINTY_VALUE) {
            waterValue = aPrioriWaterValue();
        } else {
            return false; // this means: if we have no information about water, we return isClearWater = false
        }
        return (!isCloud() && waterValue > WATER_THRESH);
    }

    @Override
    public boolean isLand() {
        return (!isInvalid() && aPrioriLandValue() > LAND_THRESH);
    }

    @Override
    public boolean isL1Water() {
        return (!isInvalid() && aPrioriWaterValue() > WATER_THRESH);
    }

    @Override
    public boolean isClearSnow() {
        return (!isInvalid() && !isCold() && isBrightWhite() && ndsiValue() > ndsiThresh);
    }

    @Override
    public boolean isBright() {
        return (!isInvalid() && brightValue() > BRIGHT_THRESH);
    }

    @Override
    public boolean isWhite() {
        return (!isInvalid() && whiteValue() > WHITE_THRESH);
    }

    @Override
    public boolean isCold() {
        return (!isInvalid() && temperatureValue() > TEMPERATURE_THRESH);
    }

    @Override
    public boolean isVegRisk() {
        return (!isInvalid() && ndviValue() > NDVI_THRESH);
    }

    @Override
    public boolean isGlintRisk() {
        return l1FlagGlintRisk ||
                (isWater() && isCloud() && (glintRiskValue() > GLINT_THRESH));
    }

    @Override
    public boolean isHigh() {
        return (!isInvalid() && pressureValue() > PRESSURE_THRESH);
    }

    @Override
    public boolean isInvalid() {
        return !IdepixUtils.areReflectancesValid(refl);
    }

    @Override
    public float brightValue() {
        double value = (refl[0] + refl[1] + refl[2]) / 300.0f;
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                IdepixConstants.AATSR_REFL_WAVELENGTHS[0],
                IdepixConstants.AATSR_REFL_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                IdepixConstants.AATSR_REFL_WAVELENGTHS[1],
                IdepixConstants.AATSR_REFL_WAVELENGTHS[2]);

        final double flatness = (1.0f - Math.abs(20.0 * (slope0 + slope1) / 2.0));
        float result = (float) Math.max(0.0f, flatness);
        return result;
    }

    @Override
    public float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0.0f;
        }
    }

    @Override
    public float temperatureValue() {
        float temperature;

        if (btemp1200 < 225f) {
            temperature = 0.99f;
        } else if (225f <= btemp1200 && 290f > btemp1200) {
            temperature = 0.9f - 0.49f * ((btemp1200 - 225f) / (290f - 225f));
        } else if (290f <= btemp1200 && 300f > btemp1200) {
            temperature = 0.5f - 0.49f * ((btemp1200 - 290f) / (300f - 290f));
        } else {
            temperature = 0.01f;
        }

        return temperature;
    }

    @Override
    public float ndsiValue() {
        double value = (refl[2] - refl[3]) / (refl[2] + refl[3]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float ndviValue() {
        double value = (refl[2] - refl[1]) / (refl[2] + refl[1]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float pressureValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float glintRiskValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float aPrioriLandValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (l1FlagLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (!l1FlagLand) {
            return 1.0f;
        } else return 0.0f;
    }

    @Override
    public float radiometricLandValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricWaterValue() {
        return UNCERTAINTY_VALUE;
    }

    // setters for AATSR specific quantities

    public void setRefl(float[] refl) {
        if (useFwardViewForCloudMask) {
            this.refl = new float[]{refl[4], refl[5], refl[6], refl[7]};
        } else {
            this.refl = new float[]{refl[0], refl[1], refl[2], refl[3]};
        }
    }

    public void setBtemp1200(float btemp1200) {
        this.btemp1200 = btemp1200;
    }

    public void setL1FlagGlintRisk(boolean l1FlagGlintRisk) {
        this.l1FlagGlintRisk = l1FlagGlintRisk;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
    }

    public void setUseFwardViewForCloudMask(boolean useFwardViewForCloudMask) {
        this.useFwardViewForCloudMask = useFwardViewForCloudMask;
    }

    /**
     * TEST: these methods allow to change thresholds externally, e.g. for specific regions
     *
     * @param brightwhiteThresh
     */
    public void setBrightwhiteThresh(float brightwhiteThresh) {
        this.brightwhiteThresh = brightwhiteThresh;
    }

    public void setNdsiThresh(float ndsiThresh) {
        this.ndsiThresh = ndsiThresh;
    }

    public void setCloudThresh(float cloudThresh) {
        this.cloudThresh = cloudThresh;
    }

}
