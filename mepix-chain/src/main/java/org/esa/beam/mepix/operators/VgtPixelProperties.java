package org.esa.beam.mepix.operators;

import org.esa.beam.mepix.util.MepixUtils;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class VgtPixelProperties implements PixelProperties {

    private static final float BRIGHTWHITE_THRESH = 0.8f;
    private static final float NDSI_THRESH = 0.7f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 2.0f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;
    private static final float BRIGHT_THRESH = 0.8f;
    private static final float WHITE_THRESH = 0.8f;
    private static final float NDVI_THRESH = 0.4f;

    public static final int SM_F_B0_GOOD = 7;
    public static final int SM_F_B2_GOOD = 6;
    public static final int SM_F_B3_GOOD = 5;
    public static final int SM_F_MIR_GOOD = 4;
    public static final int SM_F_LAND = 3;
    public static final int SM_F_ICE_SNOW = 2;
    public static final int SM_F_CLOUD_2 = 1;
    public static final int SM_F_CLOUD_1 = 0;

    private float b0;
    private float b2;
    private float b3;
    private float mir;

    private boolean smLand;

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
        return smLand;  // test
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
        if (isLand()) {
            return (b0 + b2)/2.0f;
        } else if (isWater()) {
            return (b0 + b2 + b3)/3.0f;
        } else {
            return (b0 + b2)/2.0f;
        }
    }

    @Override
    public float whiteValue() {
        // todo: check for NaN values. this problem affects most of these methods. think about what to do.
        // these slopes are multiplied by 1000 to scale them to approx. [0.0, 1.0]
        final float slope0 = Math.abs(1000.0f*MepixUtils.spectralSlope(b0, b2, MepixConstants.VGT_WAVELENGTH_B0,
                                                      MepixConstants.VGT_WAVELENGTH_B2));
        final float slope2 = Math.abs(1000.0f*MepixUtils.spectralSlope(b2, b3, MepixConstants.VGT_WAVELENGTH_B2,
                                                      MepixConstants.VGT_WAVELENGTH_B3));
        final float slope3 = Math.abs(1000.0f*MepixUtils.spectralSlope(b3, mir, MepixConstants.VGT_WAVELENGTH_B3,
                                                      MepixConstants.VGT_WAVELENGTH_MIR));
        if (isLand()) {
            return 1.0f - (slope0 + slope2)/2.0f;
        } else if (isWater()) {
            return 1.0f -  (slope0 + slope2)/2.0f;
        } else {
            return 1.0f -  (slope0 + slope2)/2.0f;
        }
    }

    @Override
    public float ndsiValue() {
        return (b3 - mir)/(b3 + mir); 
    }

    @Override
    public float ndviValue() {
        return (b3 - b2)/(b3 + b2);
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

    // setters for VGT specific quantities

    public void setB0(float b0) {
        this.b0 = b0;
    }

    public void setB2(float b2) {
        this.b2 = b2;
    }

    public void setB3(float b3) {
        this.b3 = b3;
    }

    public void setMir(float mir) {
        this.mir = mir;
    }

    public void setSmLand(boolean smLand) {
        this.smLand = smLand;
    }
}
