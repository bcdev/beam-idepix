package org.esa.beam.mepix.operators;

import org.esa.beam.mepix.util.MepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class AatsrPixelProperties implements PixelProperties{

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
    private static final float GLINT_THRESH =  -3.65E-4f;

    private float[] refl;

    // todo: complete method implementation

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
        if (isInvalid()) {
            return false;
        }
        return (isBrightWhite() && ndsiValue() > NDSI_THRESH);
    }

    @Override
    public boolean isLand() {
        if (isInvalid()) {
            return false;
        }
        return (aPrioriLandValue() > LAND_THRESH);
    }

    @Override
    public boolean isWater() {
        if (isInvalid()) {
            return false;
        }
        return (aPrioriWaterValue() > WATER_THRESH);
    }

    @Override
    public boolean isBright() {
        if (isInvalid()) {
            return false;
        }
        return brightValue() > BRIGHT_THRESH;
    }

    @Override
    public boolean isWhite() {
        if (isInvalid()) {
            return false;
        }
        return whiteValue() > WHITE_THRESH;
    }

    @Override
    public boolean isCold() {
        return false;
    }

    @Override
    public boolean isVegRisk() {
        if (isInvalid()) {
            return false;
        }
        return ndviValue() > NDVI_THRESH;
    }

    @Override
    public boolean isGlintRisk() {
        // todo: define
        return false;
    }

    @Override
    public boolean isHigh() {
        if (isInvalid()) {
            return false;
        }
        return (pressureValue() > PRESSURE_THRESH);
    }

    @Override
    public boolean isInvalid() {
        return !MepixUtils.areReflectancesValid(refl);
    }

    @Override
    public float brightValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float spectralFlatnessValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float whiteValue() {
        if (brightValue()>BRIGHT_FOR_WHITE_THRESH) {
                 return spectralFlatnessValue();
        }  else {
            return 0f;
        }
    }

    @Override
    public float temperatureValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
    }


    @Override
    public float ndsiValue() {
        return (refl[0] - refl[1])/(refl[0] + refl[1]); // test
    }

    @Override
    public float ndviValue() {
        // todo: define
        return UNCERTAINTY_VALUE;
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
}
