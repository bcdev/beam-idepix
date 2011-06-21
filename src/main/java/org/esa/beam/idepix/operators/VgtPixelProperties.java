package org.esa.beam.idepix.operators;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from SPOT VGT L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class VgtPixelProperties extends AbstractPixelProperties {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.50f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.65f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.3f;
    private static final float WHITE_THRESH = 0.5f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;
    private static final float REFL835_WATER_THRESH = 0.1f;
    private static final float REFL835_LAND_THRESH = 0.15f;
    private static final float GLINT_THRESH = -3.65E-4f;
    private static final float TEMPERATURE_THRESH = 0.9f;

    public static final int SM_F_LAND = 3;

    private float[] refl;
    private boolean smLand;

    private float brightwhiteThresh = BRIGHTWHITE_THRESH;
    private float cloudThresh = CLOUD_THRESH;
    private float ndsiThresh = NDSI_THRESH;

    @Override
    public boolean isBrightWhite() {
        return (!isInvalid() && whiteValue() + brightValue() > brightwhiteThresh);
    }

    @Override
    public boolean isCloud() {
        if (isInvalid()) {
            return false;
        }
        return (!isInvalid() &&
                (whiteValue() + brightValue() + pressureValue() + temperatureValue() > cloudThresh) &&
                !isClearSnow());
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
    public boolean isClearSnow() {
        return (!isInvalid() && isBrightWhite() && ndsiValue() > ndsiThresh);
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
        return isWater() && isCloud() && (glintRiskValue() > GLINT_THRESH);
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
        double value;
        if (isLand()) {
            value = (refl[0] + refl[1]) / 2.0f;
        } else if (isWater()) {
            value = (refl[1] + refl[2]);
        } else {
            value = (refl[0] + refl[1]) / 2.0f;
        }
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[0],
                                                        IdepixConstants.VGT_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                        IdepixConstants.VGT_WAVELENGTHS[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[2]);
        final double flatness = 1.0f - Math.abs(2000.0 * (slope0 + slope1) / 2.0);
        float result = (float) Math.max(0.0f, flatness);
        return result;
    }

    public float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0f;
        }
    }

    @Override
    public float temperatureValue() {
        return UNCERTAINTY_VALUE;
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
        // todo: define conversion onto interval [0,1]
        return IdepixUtils.spectralSlope(refl[0], refl[1], IdepixConstants.VGT_WAVELENGTHS[0],
                                         IdepixConstants.VGT_WAVELENGTHS[1]);
    }

    @Override
    public float aPrioriLandValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (!smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float radiometricLandValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[2] > refl[1] && refl[2] > REFL835_LAND_THRESH) {
            return 1.0f;
        } else if (refl[2] > REFL835_LAND_THRESH) {
            return 0.75f;
        } else {
            return 0.25f;
        }
    }

    @Override
    public float radiometricWaterValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[0] > refl[1] && refl[1] > refl[2] && refl[2] < REFL835_WATER_THRESH) {
            return 1.0f;
        } else {
            return 0.25f;
        }
    }

    // setters for VGT specific quantities

    public void setSmLand(boolean smLand) {
        this.smLand = smLand;
    }

    public void setRefl(float[] refl) {
        if (refl.length != IdepixConstants.VGT_WAVELENGTHS.length) {
            throw new OperatorException("VGT pixel processing: Invalid number of wavelengths [" + refl.length +
                                        "] - must be " + IdepixConstants.VGT_WAVELENGTHS.length);
        }
        this.refl = refl;
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
