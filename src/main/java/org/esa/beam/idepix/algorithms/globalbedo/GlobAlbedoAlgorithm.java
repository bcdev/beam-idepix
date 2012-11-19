package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.operators.AbstractPixelProperties;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 19.11.12
 * Time: 10:18
 *
 * @author olafd
 */
public abstract class GlobAlbedoAlgorithm extends AbstractPixelProperties {

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

    abstract float brightValue();

    abstract float temperatureValue();

    abstract float spectralFlatnessValue();

    abstract float whiteValue();

    abstract float ndsiValue();

    abstract float ndviValue();

    abstract float pressureValue();

    abstract float glintRiskValue();

    abstract float aPrioriLandValue();

    abstract float aPrioriWaterValue();

    abstract float radiometricLandValue();

    abstract float radiometricWaterValue();


    abstract float getBrightWhiteThreshold();
    abstract float getNdsiThreshold();
    abstract float getNdviThreshold();
    abstract float getBrightThreshold();
    abstract float getWhiteThreshold();
    abstract float getTemperatureThreshold();
    abstract float getGlintThreshold();
    abstract float getPressureThreshold();

    public void setRefl(float[] refl) {
        this.refl = refl;
    }

}
