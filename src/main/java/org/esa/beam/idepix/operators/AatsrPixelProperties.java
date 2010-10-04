package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.MepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from AATSR L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class AatsrPixelProperties implements PixelProperties {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.65f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.65f;  // = BRIGHTWHITE_THRESH + 2*0.5, because pressureValue, temperatureValue = 0.5
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

    private float[] refl;
    private float btemp1200;


    @Override
    public boolean isBrightWhite() {
        return (whiteValue() + brightValue() > BRIGHTWHITE_THRESH);
    }

    @Override
    public boolean isCloud() {
        return (whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH && !isClearSnow());
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
        return false;
    }

    @Override
    public boolean isVegRisk() {
        return (!isInvalid() && ndviValue() > NDVI_THRESH);
    }

    @Override
    public boolean isGlintRisk() {
        // todo: define
        return false;
    }

    @Override
    public boolean isHigh() {
        return (!isInvalid() && pressureValue() > PRESSURE_THRESH);
    }

    @Override
    public boolean isInvalid() {
        return !MepixUtils.areReflectancesValid(refl);
    }

    @Override
    public float brightValue() {
        return ((refl[0] + refl[1] + refl[2]) / 3.0f);
    }

    @Override
    public float spectralFlatnessValue() {
        final double flatness0 = MepixUtils.spectralSlope(refl[0], refl[1],
                                                          MepixConstants.AATSR_WAVELENGTHS[0],
                                                          MepixConstants.AATSR_WAVELENGTHS[1]);
        final double flatness1 = MepixUtils.spectralSlope(refl[1], refl[2],
                                                          MepixConstants.AATSR_WAVELENGTHS[1],
                                                          MepixConstants.AATSR_WAVELENGTHS[2]);

        return (float) ((flatness0 + flatness1) / 2.0);
    }

    @Override
    public float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0f;
        }
    }

    @Override
    public float temperatureValue() {
        return btemp1200;
    }

    @Override
    public float ndsiValue() {
        return (refl[2] - refl[4]) / (refl[2] + refl[4]);
    }

    @Override
    public float ndviValue() {
        return (refl[2] - refl[3]) / (refl[2] + refl[3]);
    }

    @Override
    public float pressureValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float aPrioriLandValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float aPrioriWaterValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricLandValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricWaterValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    // setters for AATSR specific quantities

    public void setRefl(float[] refl) {
        this.refl = refl;
    }

    public void setBtemp1200(float btemp1200) {
        this.btemp1200 = btemp1200;
    }
}
