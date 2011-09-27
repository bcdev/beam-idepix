package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from MERIS L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class MerisPixelProperties extends AbstractPixelProperties {

    // todo: test changes introduced on 18/07/2011 by AR and activate if ok

    static final float BRIGHTWHITE_THRESH = 1.5f;
    static final float NDSI_THRESH = 0.68f;
    static final float PRESSURE_THRESH = 0.9f;
        static final float CLOUD_THRESH = 1.65f;  // test, 20110328
//    static final float CLOUD_THRESH = 0.2875f;   // AR, 18/05/11
//        static final float CLOUD_THRESH = 1.15f;  // GA delivery, Jan. 2011
    static final float UNCERTAINTY_VALUE = 0.5f;
    static final float LAND_THRESH = 0.9f;
    static final float WATER_THRESH = 0.9f;
    static final float BRIGHT_THRESH = 0.25f;
    static final float WHITE_THRESH = 0.9f;
//        static final float BRIGHT_FOR_WHITE_THRESH = 0.4f;   // GA delivery, Jan. 2011
    static final float BRIGHT_FOR_WHITE_THRESH = 0.8f;   // test, 20110328
    static final float NDVI_THRESH = 0.7f;
    static final float TEMPERATURE_THRESH = 0.9f;

    //    private static final float GLINT_THRESH =  0.9f;  // GA delivery, Jan. 2011
    protected static final float GLINT_THRESH = 0.5f;        // AR, 18/05/11

    protected static final float P1_THRESH = 0.15f;


    public static final int F_BRIGHT_RC = 2;
    public static final int L1B_F_LAND = 4;
    private float brr442;
    private float brr442Thresh;
    private float p1;
    private float pscatt;
    private float pbaro;

    protected boolean qwgCloudClassifFlagBrightRc;
    private boolean l1FlagLand;
    private float[] refl;
    private float[] brr;

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
    // AR, 18/05/11:
//    public boolean isCloud() {
//        return ((whiteValue() + brightValue() + pressureValue() + temperatureValue()) / 4.0f > CLOUD_THRESH && !isClearSnow());
//    }

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
        return (!isInvalid() && isBrightWhite() && ndsiValue() > ndsiThresh);
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
    // AR, 18/05/11:
//    public boolean isGlintRisk() {
//        return (isWater() && glintRiskValue() > GLINT_THRESH);
//    }


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
        double value = 0.5 * brr442 / brr442Thresh;    // GA delivery, Jan. 2011
        value /= 3.0; // test
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    // AR, 18/05/11:
//    public float brightValue() {
//        if (isGlintRisk()) {
//            if (qwgCloudClassifFlagBrightRc) {
//                return 1.0f;
//            } else {
//                return 0.0f;
//            }
//        } else {
//            if (brr442 <= 0.0 || brr442Thresh <= 0.0) {
//                return IdepixConstants.NO_DATA_VALUE;
//            }
//            double value = 0.5 * brr442 / brr442Thresh;
//            value /= 3.0; // test
//            value = Math.min(value, 1.0);
//            value = Math.max(value, 0.0);
//            return (float) value;
//        }
//    }


    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[2],
                                                        IdepixConstants.MERIS_WAVELENGTHS[0],
                                                        IdepixConstants.MERIS_WAVELENGTHS[2]);
        final double slope1 = IdepixUtils.spectralSlope(refl[4], refl[5],
                                                        IdepixConstants.MERIS_WAVELENGTHS[4],
                                                        IdepixConstants.MERIS_WAVELENGTHS[5]);
        final double slope2 = IdepixUtils.spectralSlope(refl[6], refl[9],
                                                        IdepixConstants.MERIS_WAVELENGTHS[6],
                                                        IdepixConstants.MERIS_WAVELENGTHS[9]);


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
        double value = (brr[11] - brr[12]) / (brr[11] + brr[12]);
        value = 20.0 * (value + 0.02);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float ndviValue() {
        double value = (brr[9] - brr[4]) / (brr[9] + brr[4]);
        value = 0.5 * (value + 1);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float pressureValue() {
        double value;
        if (isLand()) {
//            value = 1.0 - p1 / 1000.0;
            value = pbaro / 1000.0 - p1 / 1000.0;         // we need to take into account the height of the land, e.g. in Himalaya!
        } else if (isWater()) {
//            value = 1.0 - pscatt / 1000.0;
            value = pbaro / 1000.0 - pscatt / 1000.0;    // we even need to take into account the height of the water, e.g. small lakes in Himalaya!
        } else {
            value = UNCERTAINTY_VALUE;
        }
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }
    // AR, 18/05/11:
//    public float pressureValue() {
//        double value;
//        if (isGlintRisk()) {
//            value = 1.00 - p1 / 1000.0;
//        } else if (isLand()) {
//            value = 1.0 - p1 / 1000.0;
//        } else if (isWater()) {
//            value = 1.25 - pscatt / 800.0;
//        } else {
//            value = UNCERTAINTY_VALUE;
//        }
//        value = Math.min(value, 1.0);
//        value = Math.max(value, 0.0);
//        return (float) value;
//    }


    @Override
    public float glintRiskValue() {
        return UNCERTAINTY_VALUE;
    }
    // AR, 18/05/11:
//    public float glintRiskValue() {
//        if (p1Value() < P1_THRESH) {
//            return p1Value() * (1.0f / 0.15f);
//        } else {
//            return 0.0f;
//        }
//    }


    //P1 to calculate pressure value for glint
    // AR, 18/05/11:
    private float p1Value() {
        double value;
        value = 1.0 - p1 / 1000.0;
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
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
        } else {
            return 0.0f;
        }
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

    @Override
    public boolean isLand() {
        final boolean isLand1 = !usel1bLandWaterFlag && !isWater;
        return (isLand1 || (!isInvalid() && aPrioriLandValue() > LAND_THRESH));
    }

    @Override
    public boolean isL1Water() {
        return (!isInvalid() && aPrioriWaterValue() > WATER_THRESH);
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

    public void setQwgCloudClassifFlagBrightRc(boolean qwgCloudClassifFlagBrightRc) {
        this.qwgCloudClassifFlagBrightRc = qwgCloudClassifFlagBrightRc;
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
