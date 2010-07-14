package org.esa.beam.mepix.operators;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class AatsrPixelProperties implements PixelProperties{

    private static final float BRIGHTWHITE_THRESH = 1.0f;
    private static final float NDSI_THRESH = 0.7f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 2.0f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.9f;
    private static final float WHITE_THRESH = 0.9f;
    private static final float NDVI_THRESH = 0.4f;

    private float reflecNadir0550;
    private float reflecNadir0670;
    private float reflecNadir0870;
    private float reflecNadir1600;

    // todo: complete method implementation

    @Override
    public boolean isBrightWhite() {
        return false;
    }

    @Override
    public boolean isCloud() {
        return false;
    }

    @Override
    public boolean isClearLand() {
        return false;
    }

    @Override
    public boolean isClearWater() {
        return false;
    }

    @Override
    public boolean isClearSnow() {
        return false;
    }

    @Override
    public boolean isLand() {
        return false;
    }

    @Override
    public boolean isWater() {
        return false;
    }

    @Override
    public boolean isBright() {
        return false;
    }

    @Override
    public boolean isWhite() {
        return false;
    }

    @Override
    public boolean isVegRisk() {
        return false;
    }

    @Override
    public boolean isHigh() {
        return false;
    }

    @Override
    public float brightValue() {
        return 0;
    }

    @Override
    public float whiteValue() {
        return 0;
    }

    @Override
    public float ndsiValue() {
        return (reflecNadir0550 - reflecNadir0670)/(reflecNadir0550 + reflecNadir0670); // test
    }

    @Override
    public float ndviValue() {
        return 0;
    }

    @Override
    public float pressureValue() {
        return 0;
    }

    @Override
    public float aPrioriLandValue() {
        return 0;
    }

    @Override
    public float aPrioriWaterValue() {
        return 0;
    }

    @Override
    public float radiometricLandValue() {
        return 0;
    }

    @Override
    public float radiometricWaterValue() {
        return 0;
    }

    // setters for AATSR specific quantities

    public void setReflecNadir0550(float reflecNadir0550) {
        this.reflecNadir0550 = reflecNadir0550;
    }

    public void setReflecNadir0670(float reflecNadir0670) {
        this.reflecNadir0670 = reflecNadir0670;
    }

    public void setReflecNadir0870(float reflecNadir0870) {
        this.reflecNadir0870 = reflecNadir0870;
    }

    public void setReflecNadir1600(float reflecNadir1600) {
        this.reflecNadir1600 = reflecNadir1600;
    }
}
