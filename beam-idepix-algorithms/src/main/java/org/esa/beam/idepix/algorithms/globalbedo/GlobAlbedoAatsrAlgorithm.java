package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX pixel identification algorithm for GlobAlbedo/AATSR
 *
 * @author olafd
 */
public class GlobAlbedoAatsrAlgorithm extends GlobAlbedoAlgorithm {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.50f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.3f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
//    private static final float BRIGHT_THRESH = 0.2f;
    private static final float BRIGHT_THRESH = 0.1f;
    private static final float WHITE_THRESH = 0.9f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;

    public static final int L1B_F_LAND = 0;

    private float btemp1200;
    private boolean l1FlagLand;

    @Override
    public boolean isCloud() {
        if (!isInvalid()) {
            boolean noSnowOrIce;
            if (isLand()) {
                noSnowOrIce = !isClearSnow();
            } else {
                noSnowOrIce = !isSeaIce();
            }
            if (((whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH) && noSnowOrIce)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSeaIce() {
        // new approach for GA CCN
        return !isInvalid() && isWater() && isBright() && refl[3] < 2.0;
    }

    @Override
    public boolean isGlintRisk() {
        return false;
    }

    @Override
    public float brightValue() {
        double value = (refl[0] + refl[1] + refl[2]) / 300.0f;
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
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
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                        IdepixConstants.AATSR_REFL_WAVELENGTHS[0],
                                                        IdepixConstants.AATSR_REFL_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                        IdepixConstants.AATSR_REFL_WAVELENGTHS[1],
                                                        IdepixConstants.AATSR_REFL_WAVELENGTHS[2]);

        final double flatness = (1.0f - Math.abs(20.0 * (slope0 + slope1) / 2.0));
        return (float) Math.max(0.0f, flatness);
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

    @Override
    public float getBrightWhiteThreshold() {
        return BRIGHTWHITE_THRESH;
    }

    @Override
    public float getNdsiThreshold() {
        return NDSI_THRESH;
    }

    @Override
    public float getNdviThreshold() {
        return NDVI_THRESH;
    }

    @Override
    public float getBrightThreshold() {
        return BRIGHT_THRESH;
    }

    @Override
    public float getWhiteThreshold() {
        return WHITE_THRESH;
    }

    @Override
    public float getPressureThreshold() {
        return PRESSURE_THRESH;
    }

    // setters for AATSR specific quantities

    public void setBtemp1200(float btemp1200) {
        this.btemp1200 = btemp1200;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
    }

}
