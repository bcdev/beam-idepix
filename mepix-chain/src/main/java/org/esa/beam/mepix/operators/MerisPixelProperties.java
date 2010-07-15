package org.esa.beam.mepix.operators;

import org.esa.beam.mepix.util.MepixUtils;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MerisPixelProperties implements PixelProperties {

    private static final float BRIGHTWHITE_THRESH = 1.0f;
    private static float NDSI_THRESH = 0.7f;
    private static float PRESSURE_THRESH = 0.9f;
    private static float CLOUD_THRESH = 2.0f;
    private static float UNCERTAINTY_VALUE = 0.5f;
    private static float LAND_THRESH = 0.9f;
    private static float WATER_THRESH = 0.9f;
    private static float BRIGHT_THRESH = 0.9f;
    private static float WHITE_THRESH = 0.9f;
    private static float NDVI_THRESH = 0.4f;

    private float brr442;
    private float brr442Thresh;

    private float[] refl;

    // todo: complete method implementation

    @Override
    public boolean isBrightWhite() {
        return (spectralFlatnessValue() + brightValue() > BRIGHTWHITE_THRESH);
    }

    @Override
    public boolean isCloud() {
        return (spectralFlatnessValue() + brightValue() + pressureValue() > CLOUD_THRESH && !isClearSnow());
    }

    @Override
    public boolean isClearLand() {
        float landValue;
        if (radiometricLandValue() > UNCERTAINTY_VALUE) {
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
        float waterValue;
        if (radiometricWaterValue() > UNCERTAINTY_VALUE) {
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
        return (isBrightWhite() && ndsiValue() > NDSI_THRESH);
    }

    @Override
    public boolean isLand() {
        return (aPrioriLandValue() > LAND_THRESH);
    }

    @Override
    public boolean isWater() {
        return (aPrioriWaterValue() > WATER_THRESH);
    }

    @Override
    public boolean isBright() {
        return brightValue() > BRIGHT_THRESH;
    }

    @Override
    public boolean isWhite() {
        return spectralFlatnessValue() > WHITE_THRESH;
    }

    @Override
    public boolean isVegRisk() {
        return ndviValue() > NDVI_THRESH;
    }

    @Override
    public boolean isHigh() {
        return (pressureValue() > PRESSURE_THRESH);
    }

    @Override
    public boolean isInvalid() {
        return MepixUtils.areReflectancesValid(refl);
    }

    @Override
    public float brightValue() {
        return brr442 / brr442Thresh;
    }

    @Override
    public float spectralFlatnessValue() {
        return 1.0f; 
    }

    @Override
    public float whiteValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float ndsiValue() {
        return 0.0f;
    }

    @Override
    public float ndviValue() {
        return 0.0f;
    }

    @Override
    public float pressureValue() {
        return 1.0f;
    }

    @Override
    public float aPrioriLandValue() {
        return 1.0f;
    }

    @Override
    public float aPrioriWaterValue() {
        return 0.0f;
    }

    @Override
    public float radiometricLandValue() {
        return 1.0f;
    }

    @Override
    public float radiometricWaterValue() {
        return 0.0f;
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
}
