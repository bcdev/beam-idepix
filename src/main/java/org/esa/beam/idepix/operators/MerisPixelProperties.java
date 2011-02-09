package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from MERIS L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class MerisPixelProperties implements PixelProperties {

    protected static final float BRIGHTWHITE_THRESH = 1.5f;
    protected static final float NDSI_THRESH = 0.68f;
    protected static final float PRESSURE_THRESH = 0.9f;
    protected static final float CLOUD_THRESH = 1.15f;
    protected static final float UNCERTAINTY_VALUE = 0.5f;
    protected static final float LAND_THRESH = 0.9f;
    protected static final float WATER_THRESH = 0.9f;
    protected static final float BRIGHT_THRESH = 0.25f;
    protected static final float WHITE_THRESH = 0.9f;
    protected static final float BRIGHT_FOR_WHITE_THRESH = 0.4f;
    protected static final float NDVI_THRESH = 0.7f;
    protected static final float TEMPERATURE_THRESH = 0.9f;

    protected static final float GLINT_THRESH =  0.9f;

    protected static final int L1B_F_LAND = 4;
    protected float brr442;
    protected float brr442Thresh;
    protected float p1;
    protected float pscatt;
    protected float pbaro;

    protected boolean l1FlagLand;
    protected float[] refl;
    protected float[] brr;


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
        return (!isInvalid() && temperatureValue() > TEMPERATURE_THRESH);
    }

    @Override
    public boolean isVegRisk() {
        return (!isInvalid() && ndviValue() > NDVI_THRESH);
    }

    @Override
    public boolean isGlintRisk() {
        return (isWater() && isCloud() && (glintRiskValue() > GLINT_THRESH));
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
        if (brr442 <= 0.0 || brr442Thresh <= 0.0) {
            return IdepixConstants.NO_DATA_VALUE;
        }
        double value =  0.5 * brr442 / brr442Thresh;
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float)value;
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[2],
                                                          IdepixConstants. MERIS_WAVELENGTHS[0], IdepixConstants. MERIS_WAVELENGTHS[2]);
        final double slope1 = IdepixUtils.spectralSlope(refl[4], refl[5],
                                                                  IdepixConstants. MERIS_WAVELENGTHS[4], IdepixConstants. MERIS_WAVELENGTHS[5]);
        final double slope2 = IdepixUtils.spectralSlope(refl[6], refl[9],
                                                                  IdepixConstants. MERIS_WAVELENGTHS[6], IdepixConstants. MERIS_WAVELENGTHS[9]);


        final double flatness = 1.0f - Math.abs(1000.0 * (slope0 + slope1 + slope2) / 3.0);
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
        return UNCERTAINTY_VALUE; 
    }

    @Override
    public float ndsiValue() {
        double value =  (brr[11]-brr[12])/(brr[11]+brr[12]);
        value = 20.0*(value + 0.02);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float)value;
    }

    @Override
    public float ndviValue() {
        double value = (brr[9]-brr[4])/(brr[9]+brr[4]);
        value = 0.5*(value + 1);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float)value;
    }

    @Override
    public float pressureValue() {
        double value;
        if (isLand()) {
            value = 1.0 - p1 / 1000.0;
        } else if (isWater()) {
            value = 1.0 - pscatt / 1000.0;
        } else {
            value = UNCERTAINTY_VALUE;
        }
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float)value;
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

        // todo: clarify value for REFL620_LAND_THRESH (not yet in ATBD) then implement the following
//        if (isCloud()) {
//            return UNCERTAINTY_VALUE;
//        }
//        if (refl[9] >= refl[6] && refl[6] > REFL620_LAND_THRESH) {
//            return 1.0f;
//        } else {
//            return 0.5f;
//        }
    }

    @Override
    public float radiometricWaterValue() {
         return UNCERTAINTY_VALUE;

        // todo: clarify value for REFL620_WATER_THRESH (not yet in ATBD) then implement the following
//        if (isCloud()) {
//            return UNCERTAINTY_VALUE;
//        }
//        if (refl[9] >= refl[6] && refl[6] > REFL620_WATER_THRESH) {
//            return 1.0f;
//        } else {
//            return 0.5f;
//        }
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

    public void setPBaro(float pbaro) {
        this.pbaro = pbaro;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
    }
}
