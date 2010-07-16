package org.esa.beam.mepix.operators;

import org.esa.beam.mepix.util.MepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MerisPixelProperties implements PixelProperties {

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

    public static final int L1B_F_LAND = 4;
    
    private float brr442;
    private float brr442Thresh;
    private float p1;
    private float pscatt;
    private boolean l1FlagLand;

    private float[] refl;
    private float[] brr;


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
        if (brr442 <= 0.0 || brr442Thresh <= 0.0) {
            return MepixConstants.NO_DATA_VALUE;
        }
        return brr442 / brr442Thresh;
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
        return UNCERTAINTY_VALUE; 
    }

    @Override
    public float ndsiValue() {
        return (brr[11]-brr[12])/(brr[11]+brr[12]);
    }

    @Override
    public float ndviValue() {
        return (brr[9]-brr[4])/(brr[9]+brr[4]);
    }

    @Override
    public float pressureValue() {
        if (isLand()) {
            return 1.0f - p1/1000.0f;
        } else if (isWater()) {
            return 1.0f - pscatt/1000.0f;
        } else {
            return UNCERTAINTY_VALUE;
        }
    }

    @Override
    public float aPrioriLandValue() {
        if (isInvalid()) {
            return 0.5f;
        } else if (l1FlagLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return 0.5f;
        } else if (!l1FlagLand) {
            return 1.0f;
        } else return 0.0f;
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

    // setters for MERIS specific quantities
    public void setBrr442(float brr442) {
        this.brr442 = brr442;
    }

    public void setBrr442Thresh(float brr442Thresh) {
        this.brr442Thresh = brr442Thresh;
    }

    public void setRefl(float[] refl) {
        this.refl = refl;
    }

    public void setBrr(float[] brr) {
        this.brr = brr;
    }

    public void setP1(float p1) {
        this.p1 = p1;
    }

    public void setPscatt(float pscatt) {
        this.pscatt = pscatt;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
    }
}
