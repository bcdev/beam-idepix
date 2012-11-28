package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.pixel.AbstractPixelProperties;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * IDEPIX instrument-specific pixel identification algorithm for GlobAlbedo: abstract superclass
 *
 * @author olafd
 */
public abstract class GlobAlbedoAlgorithm extends AbstractPixelProperties {

    public static final int L1B_F_LAND = 4;

    static final float UNCERTAINTY_VALUE = 0.5f;
    static final float LAND_THRESH = 0.9f;
    static final float WATER_THRESH = 0.9f;

    float[] refl;


    @Override
    public boolean isBrightWhite() {
        return (whiteValue() + brightValue() > getBrightWhiteThreshold());
    }

    // implementation is instrument-dependent:
    public abstract boolean isCloud();

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
        return (!isWater && !isCloud() && landValue > LAND_THRESH);
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
        return (!isInvalid() && isBrightWhite() && ndsiValue() > getNdsiThreshold());
    }

    @Override
    public boolean isBright() {
        return (!isInvalid() && brightValue() > getBrightThreshold());
    }

    @Override
    public boolean isWhite() {
        return (!isInvalid() && whiteValue() > getWhiteThreshold());
    }

    @Override
    public boolean isCold() {
        return (!isInvalid() && temperatureValue() > getTemperatureThreshold());
    }

    @Override
    public boolean isLand() {
        final boolean isLand1 = !usel1bLandWaterFlag && !isWater;
        return (isLand1 || (!isInvalid() && aPrioriLandValue() > LAND_THRESH));
    }

    @Override
    public boolean isL1Water() {
        return (!isInvalid() && aPrioriWaterValue() > WATER_THRESH);
    }

    @Override
    public boolean isVegRisk() {
        return (!isInvalid() && ndviValue() > getNdviThreshold());
    }

    @Override
    public boolean isGlintRisk() {
        return isWater() && isCloud() && (glintRiskValue() > getGlintThreshold());
    }

    @Override
    public boolean isHigh() {
        return (!isInvalid() && pressureValue() > getPressureThreshold());
    }

    @Override
    public boolean isInvalid() {
        return !IdepixUtils.areReflectancesValid(refl);
    }

    public abstract float brightValue();

    public abstract float temperatureValue();

    public abstract float spectralFlatnessValue();

    public abstract float whiteValue();

    public abstract float ndsiValue();

    public abstract float ndviValue();

    public abstract float pressureValue();

    public abstract float glintRiskValue();

    public abstract float aPrioriLandValue();

    public abstract float aPrioriWaterValue();

    public abstract float radiometricLandValue();

    public abstract float radiometricWaterValue();

    public abstract float getBrightWhiteThreshold();
    public abstract float getNdsiThreshold();
    public abstract float getNdviThreshold();
    public abstract float getBrightThreshold();
    public abstract float getWhiteThreshold();
    public abstract float getTemperatureThreshold();
    public abstract float getGlintThreshold();
    public abstract float getPressureThreshold();

    public void setRefl(float[] refl) {
        this.refl = refl;
    }

}
