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
//    private static final float CLOUD_THRESH = 1.65f;  // = BRIGHTWHITE_THRESH + 2*0.5, because pressureValue, temperatureValue = 0.5
    private static final float CLOUD_THRESH = 1.0f;
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

    public static final int L1B_F_LAND = 0;
    public static final int L1B_F_GLINT_RISK = 2;

    private float[] refl;
    private float btemp1200;
    private boolean l1FlagLand;
    private boolean l1FlagGlintRisk;


    @Override
    public boolean isBrightWhite() {
        return (whiteValue() + brightValue() > BRIGHTWHITE_THRESH);
    }

    @Override
    public boolean isCloud() {
        // with CLOUD_THRESH = 1.0, this fits very nice for investigated high and mid level clouds. Low clouds
        // are a bit underestimated. Checked for products:
        // - ATS_TOA_1PRUPA20050310_093826_subset.dim
        // - 20090702_101450
        // - 20100410_104752
        // - 20100621_112512
        // - 20100721_122242
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
//        return (!isInvalid() && isBrightWhite() && ndsiValue() > NDSI_THRESH);
        
        // this fits very well for example ATS_TOA_1PRUPA20050310_093826_subset.dim
        // for snow pixels over the Alpes, we obviously have refl_645 > refl_835 for most pixels
        return (!isInvalid() && whiteValue() < 0.0 && ndsiValue() > NDSI_THRESH);
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
        final double flatness0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                          IdepixConstants.AATSR_WAVELENGTHS[0],
                                                          IdepixConstants.AATSR_WAVELENGTHS[1]);
        final double flatness1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                          IdepixConstants.AATSR_WAVELENGTHS[1],
                                                          IdepixConstants.AATSR_WAVELENGTHS[2]);

        return (float) ((flatness0 + flatness1) / 200.0);
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
        this.refl = refl;
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
}
