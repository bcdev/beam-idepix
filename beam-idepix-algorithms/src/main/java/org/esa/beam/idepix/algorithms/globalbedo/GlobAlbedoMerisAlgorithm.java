package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX pixel identification algorithm for GlobAlbedo/MERIS
 *
 * @author olafd
 */
public class GlobAlbedoMerisAlgorithm extends GlobAlbedoAlgorithm {

    static final float BRIGHTWHITE_THRESH = 1.5f;
    static final float NDSI_THRESH = 0.68f;
    static final float CLOUD_THRESH = 1.65f;
    static final float BRIGHT_THRESH = 0.25f;
    static final float BRIGHT_FOR_WHITE_THRESH = 0.8f;
    static final float WHITE_THRESH = 0.9f;
    static final float NDVI_THRESH = 0.7f;
    static final float GLINT_THRESH = 0.5f;
    static final float PRESSURE_THRESH = 0.9f;

    private float[] brr;
    private boolean l1FlagLand;
    private float p1;
    private float pscatt;
    private float pbaro;
    private float brr442;
    private float brr442Thresh;

    @Override
    public boolean isCloud() {
        boolean threshTest = whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH;
        return !isInvalid() && (threshTest && !isClearSnow());
    }

    @Override
    public boolean isSeaIce() {
        // no algorithm available for MERIS only
        return false;
    }

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
        return (float) Math.max(0.0f, flatness);
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
            value = pbaro / 1000.0 - p1 / 1000.0;         // we need to take into account the height of the land, e.g. in Himalaya!
        } else if (isWater()) {
            value = pbaro / 1000.0 - pscatt / 1000.0;    // we even need to take into account the height of the water, e.g. small lakes in Himalaya!
        } else {
            value = UNCERTAINTY_VALUE;
        }
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
    public float temperatureValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricLandValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float radiometricWaterValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float getBrightWhiteThreshold() {
        return BRIGHTWHITE_THRESH;
    }

    @Override
    public float glintRiskValue() {
        return UNCERTAINTY_VALUE;
    }

    // SETTERS

    public void setBrr(float[] brr) {
        this.brr = brr;
    }

    public void setL1FlagLand(boolean l1FlagLand) {
        this.l1FlagLand = l1FlagLand;
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

    public void setBrr442(float brr442) {
        this.brr442 = brr442;
    }

    public void setBrr442Thresh(float brr442Thresh) {
        this.brr442Thresh = brr442Thresh;
    }


    // THRESHOLD GETTERS

    @Override
    public float getNdsiThreshold() {
        return NDSI_THRESH;
    }

    @Override
    public float getNdviThreshold() {
        return NDVI_THRESH;
    }

    @Override
    public float getBrightThreshold() {
        return BRIGHT_THRESH;
    }

    @Override
    public float getWhiteThreshold() {
        return WHITE_THRESH;
    }

    @Override
    public float getGlintThreshold() {
        return GLINT_THRESH;
    }

    @Override
    public float getPressureThreshold() {
        return PRESSURE_THRESH;
    }

}
