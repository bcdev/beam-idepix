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
class VgtPixelProperties implements PixelProperties {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.72f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.3f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.5f;
    private static final float WHITE_THRESH = 0.5f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;
    private static final float REFL835_WATER_THRESH = 0.1f;
    private static final float REFL835_LAND_THRESH = 0.15f;
    private static final float GLINT_THRESH = -3.65E-4f;
    private static final float TEMPERATURE_THRESH = 0.9f;

    public static final int SM_F_B0_GOOD = 7;
    public static final int SM_F_B2_GOOD = 6;
    public static final int SM_F_B3_GOOD = 5;
    public static final int SM_F_MIR_GOOD = 4;
    public static final int SM_F_LAND = 3;
    public static final int SM_F_ICE_SNOW = 2;
    public static final int SM_F_CLOUD_2 = 1;
    public static final int SM_F_CLOUD_1 = 0;

    private float[] refl;
    private boolean smLand;

    @Override
    public boolean isBrightWhite() {
        return (!isInvalid() && whiteValue() + brightValue() > BRIGHTWHITE_THRESH);
    }

    @Override
    public boolean isCloud() {
        if (isInvalid()) {
            return false;
        }
        return (!isInvalid() && (whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH) &&
                !isClearSnow());
    }

    @Override
    public boolean isCloudBuffer() {
        // todo: define
        return false;
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
        return (!isInvalid() && isBrightWhite() && ndsiValue() > NDSI_THRESH);
    }

    @Override
    public boolean isLand() {
        return (!isInvalid() && aPrioriLandValue() > LAND_THRESH);
    }

    @Override
    public boolean isWater() {
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
        if (isLand()) {
            return (refl[0] + refl[1]) / 2.0f;
        } else if (isWater()) {
            return (refl[1] + refl[2]);
        } else {
            return (refl[0] + refl[1]) / 2.0f;
        }
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.scaleVgtSlope(refl[0], refl[1], IdepixConstants.VGT_WAVELENGTHS[0],
                                                           IdepixConstants.VGT_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.scaleVgtSlope(refl[1], refl[2], IdepixConstants.VGT_WAVELENGTHS[1],
                                                           IdepixConstants.VGT_WAVELENGTHS[2]);

        // maybe distinguish between water and land?
        if (isLand()) {
            return (float) ((slope0 + slope1) / 2.0);
        } else if (isWater()) {
            return (float) ((slope0 + slope1) / 2.0);     // currently all the same
        } else {
            return (float) ((slope0 + slope1) / 2.0);     // currently all the same
        }
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
        return (refl[2] - refl[3]) / (refl[2] + refl[3]);
    }

    @Override
    public float ndviValue() {
        return (refl[2] - refl[1]) / (refl[2] + refl[1]);
    }

    @Override
    public float pressureValue() {
        return 0.5f;
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
            return 0.5f;
        } else if (smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return 0.5f;
        } else if (!smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float radiometricLandValue() {
        if (isInvalid() || isCloud()) {
            return 0.5f;
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
            return 0.5f;
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
}
