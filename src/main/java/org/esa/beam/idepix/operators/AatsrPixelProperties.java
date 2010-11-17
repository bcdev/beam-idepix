package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.IdepixUtils;
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
    private static final float CLOUD_THRESH = 1.65f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.2f;
    private static final float WHITE_THRESH = 0.9f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;
    private static final float REFL835_WATER_THRESH = 0.1f;
    private static final float REFL835_LAND_THRESH = 0.15f;
    private static final float REFL1600_UPPER_THRESH = 0.1f;
    private static final float REFL1600_LOWER_THRESH = 0.01f;
    private static final float REFL0670_UPPER_THRESH = 1.0f;
    private static final float REFL0670_LOWER_THRESH = 0.4f;
    private static final float GLINT_THRESH = -3.65E-4f;
    private static final float TEMPERATURE_THRESH = 0.6f;

    public static final int L1B_F_LAND = 0;
    public static final int L1B_F_GLINT_RISK = 2;

    private float[] refl;
    private float btemp1200;
    private boolean l1FlagLand;
    private boolean l1FlagGlintRisk;
    private boolean useFwardViewForCloudMask;


    @Override
    public boolean isBrightWhite() {
        return (whiteValue() + brightValue() > BRIGHTWHITE_THRESH);
    }

    @Override
    public boolean isCloud() {
        // Checked for products:
        // - ATS_TOA_1PRUPA20050310_093826_subset.dim
        // - 20090702_101450
        // - 20100410_104752
        // - 20100621_112512
        // - 20100721_122242
        return (whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH && !isClearSnow());
    }

    @Override
    public boolean isCloudShadow() {
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
        return (!isInvalid() && !isCold() && isBrightWhite() && ndsiValue() > NDSI_THRESH);
        
        // SnowRadiance:
//        if (apply100PercentSnowMask && !(ndsi > ndsiUpperThreshold)) {
//            boolean is1600InInterval = aatsr1610 >= aatsr1610LowerThreshold && aatsr1610 <= aatsr1610UpperThreshold;
//            boolean is0670InInterval = aatsr0670 >= aatsr0670LowerThreshold && aatsr0670 <= aatsr0670UpperThreshold;
//            considerPixelAsSnow = is1600InInterval && is0670InInterval;
//        }
//        with
//        ndsiUpperThreshold = 0.96
//        aatsr1610LowerThreshold = 1.0
//        aatsr1610UpperThreshold = 10.0
//        aatsr0670LowerThreshold = 1.0
//        aatsr0670UpperThreshold = 10.0

//        boolean isNdsiInInterval = (ndsiValue() > NDSI_THRESH);
//        boolean is1600InInterval = (refl[3] / 100.0 >= REFL1600_LOWER_THRESH && refl[3] / 100.0 <= REFL1600_UPPER_THRESH);
//        boolean is0670InInterval = (refl[1] / 100.0 >= REFL0670_LOWER_THRESH && refl[1] / 100.0 <= REFL0670_UPPER_THRESH);
//        boolean isClearSnow = isNdsiInInterval && is1600InInterval && is0670InInterval;
//
//        return !isInvalid() && isClearSnow;

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
        return l1FlagGlintRisk;
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
        return ((refl[0] + refl[1] + refl[2]) / 300.0f);
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                          IdepixConstants.AATSR_REFL_WAVELENGTHS[0],
                                                          IdepixConstants.AATSR_REFL_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                          IdepixConstants.AATSR_REFL_WAVELENGTHS[1],
                                                          IdepixConstants.AATSR_REFL_WAVELENGTHS[2]);

        return (float) (1.0f - Math.abs(1000.0*(slope0 + slope1)/200.0));
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
        float temperature;
        if (btemp1200 < 225f) {
            temperature = 0.9f;
        } else if (225f <= btemp1200 && 270f > btemp1200) {
            temperature = 0.9f - 0.4f * ((btemp1200 - 225f) / (270f - 225f));
        } else if (270f <= btemp1200 && 280f > btemp1200) {
            temperature = 0.5f - 0.4f * ((btemp1200 - 270f) / (280f - 270f));
        } else {
            temperature =  0.1f;
        }

        return temperature;
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
        return UNCERTAINTY_VALUE;
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
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricWaterValue() {
        return UNCERTAINTY_VALUE;
    }

    // setters for AATSR specific quantities

    public void setRefl(float[] refl) {
        if (useFwardViewForCloudMask) {
            this.refl = new float[]{refl[4], refl[5], refl[6], refl[7]};
        } else {
            this.refl = new float[]{refl[0], refl[1], refl[2], refl[3]};
        }
    }

    public void setBtemp1200(float btemp1200) {
        this.btemp1200 = btemp1200;
    }

    public void setL1FlagGlintRisk(boolean l1FlagGlintRisk) {
        this.l1FlagGlintRisk = l1FlagGlintRisk;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
    }

    public void setUseFwardViewForCloudMask(boolean useFwardViewForCloudMask) {
        this.useFwardViewForCloudMask = useFwardViewForCloudMask;
    }
}
