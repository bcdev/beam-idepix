package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX pixel identification algorithm for GlobAlbedo/MERIS
 *
 * @author olafd
 */
public class GlobAlbedoMerisAatsrSynergyAlgorithm extends GlobAlbedoAlgorithm {

    // todo: for further usage in GA (BbdrOp), we need the flags:
    // F_CLEAR_LAND
    // F_CLEAR_SNOW
    // F_CLOUD
    // F_CLOUD_BUFFER
    // F_CLOUD_SHADOW
    // F_WATER
    // F_SEA_ICE // new

    static final float BRIGHTWHITE_THRESH = 1.5f;
    static final float NDSI_THRESH = 0.68f;
    static final float CLOUD_THRESH = 1.65f;
    static final float BRIGHT_THRESH = 0.25f;
    static final float BRIGHT_FOR_WHITE_THRESH = 0.8f;
    static final float WHITE_THRESH = 0.9f;
    static final float NDVI_THRESH = 0.7f;
    static final float TEMPERATURE_THRESH = 0.9f;
    static final float GLINT_THRESH = 0.5f;
    static final float PRESSURE_THRESH = 0.9f;

    // MERIS
    private float[] reflMeris;
    private float[] brrMeris;
    private boolean l1FlagLandMeris;
    private float p1Meris;
    private float pscattMeris;
    private float pbaroMeris;
    private float brr442Meris;
    private float brr442ThreshMeris;
    // AATSR
    private float btemp1200Aatsr;
    private float[] reflAatsr;
    private float[] btempAatsr;
    private boolean l1FlagLandAatsr;
    private boolean useFwardViewForCloudMaskAatsr;

    @Override
    public boolean isCloud() {
        boolean threshTest = whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH;
        boolean bbtest = isBlueDenseCloud();
        return ((threshTest || bbtest) && !isClearSnow());
    }

    @Override
    public boolean isSeaIce() {
        // todo implement
        return isCloud() && btempAatsr[2] < 258.0;
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(reflMeris[0], reflMeris[2],
                                                        IdepixConstants.MERIS_WAVELENGTHS[0],
                                                        IdepixConstants.MERIS_WAVELENGTHS[2]);
        final double slope1 = IdepixUtils.spectralSlope(reflMeris[4], reflMeris[5],
                                                        IdepixConstants.MERIS_WAVELENGTHS[4],
                                                        IdepixConstants.MERIS_WAVELENGTHS[5]);
        final double slope2 = IdepixUtils.spectralSlope(reflMeris[6], reflMeris[9],
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
        if (brr442Meris <= 0.0 || brr442ThreshMeris <= 0.0) {
            return IdepixConstants.NO_DATA_VALUE;
        }
        double value = 0.5 * brr442Meris / brr442ThreshMeris;    // GA delivery, Jan. 2011
        value /= 3.0; // test
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float ndsiValue() {
        double value = (brrMeris[11] - brrMeris[12]) / (brrMeris[11] + brrMeris[12]);
        value = 20.0 * (value + 0.02);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float ndviValue() {
        double value = (brrMeris[9] - brrMeris[4]) / (brrMeris[9] + brrMeris[4]);
        value = 0.5 * (value + 1);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float pressureValue() {
        double value;
        if (isLand()) {
            value = pbaroMeris / 1000.0 - p1Meris / 1000.0;         // we need to take into account the height of the land, e.g. in Himalaya!
        } else if (isWater()) {
            value = pbaroMeris / 1000.0 - pscattMeris / 1000.0;    // we even need to take into account the height of the water, e.g. small lakes in Himalaya!
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
        } else if (l1FlagLandMeris) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (!l1FlagLandMeris) {
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

    public void setBrrMeris(float[] brrMeris) {
        this.brrMeris = brrMeris;
    }

    public void setL1FlagLandMeris(boolean l1FlagLandMeris) {
        this.l1FlagLandMeris = l1FlagLandMeris;
    }

    public void setP1Meris(float p1Meris) {
        this.p1Meris = p1Meris;
    }

    public void setPscattMeris(float pscattMeris) {
        this.pscattMeris = pscattMeris;
    }

    public void setPBaro(float pbaro) {
        this.pbaroMeris = pbaro;
    }

    public void setBrr442Meris(float brr442Meris) {
        this.brr442Meris = brr442Meris;
    }

    public void setBrr442ThreshMeris(float brr442ThreshMeris) {
        this.brr442ThreshMeris = brr442ThreshMeris;
    }

    public void setBtemp1200Aatsr(float btemp1200Aatsr) {
        this.btemp1200Aatsr = btemp1200Aatsr;
    }

    public void setL1FlagLandAatsr(boolean l1FlagLandAatsr) {
        this.l1FlagLandAatsr = l1FlagLandAatsr;
    }

    public void setUseFwardViewForCloudMaskAatsr(boolean useFwardViewForCloudMaskAatsr) {
        this.useFwardViewForCloudMaskAatsr = useFwardViewForCloudMaskAatsr;
    }

    public void setReflMeris(float[] reflMeris) {
        this.reflMeris = reflMeris;
    }

    public void setPbaroMeris(float pbaroMeris) {
        this.pbaroMeris = pbaroMeris;
    }

    public void setReflAatsr(float[] reflAatsr) {
        this.reflAatsr = reflAatsr;
    }

    public void setBtempAatsr(float[] btempAatsr) {
        this.btempAatsr = btempAatsr;
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
    public float getTemperatureThreshold() {
        return TEMPERATURE_THRESH;
    }

    @Override
    public float getGlintThreshold() {
        return GLINT_THRESH;
    }

    @Override
    public float getPressureThreshold() {
        return PRESSURE_THRESH;
    }

    // PRIVATE METHODS

    private boolean isBlueDenseCloud() {
        final float D_BBT = 0.25f;

        final float R1_BBT = -1f;
        final float R2_BBT = 0.01f;
        final float R3_BBT = 0.1f;
        final float R4_BBT = 0.95f;
        final float R5_BBT = 0.05f;
        final float R6_BBT = 0.6f;
        final float R7_BBT = 0.45f;

        if (refl[0] >= D_BBT) {
            final float ndvi = (refl[12] - refl[6]) / (refl[12] + refl[6]);
            final float ndsi = (refl[9] - refl[12]) / (refl[9] + refl[12]);
            final float po2 = refl[10] / refl[9];
            return !(((ndvi <= R1_BBT * ndsi + R2_BBT) || (ndsi >= R3_BBT)) &&
                    (po2 <= R7_BBT)) &&
                    !((refl[12] <= R4_BBT * refl[6] + R5_BBT) &&
                            (refl[12] <= R6_BBT) && (po2 <= R7_BBT));
        }
        return false;
    }

}