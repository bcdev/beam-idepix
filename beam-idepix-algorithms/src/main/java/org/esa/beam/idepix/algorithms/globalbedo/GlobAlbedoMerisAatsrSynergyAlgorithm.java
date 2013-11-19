package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX pixel identification algorithm for GlobAlbedo/MERIS
 *
 * @author olafd
 */
public class GlobAlbedoMerisAatsrSynergyAlgorithm extends GlobAlbedoAlgorithm {

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
    private double schillerRefl1600Meris;
    private double schillerSeaiceCloudProb;

    // AATSR
    private float btemp1200Aatsr;
    private float[] reflAatsr;              // 550, 670, 870, 1600
    private float[] btempAatsr;             // 370, 1100, 1200
    private boolean l1FlagLandAatsr;
    private boolean isIstomena;
    private boolean isSchillerSeaice;
    private float refl1600ThreshAatsr;

    // todo: raise flags only if we have MERIS/AATSR overlap, and/or add flags such as 'NO_MERIS' or 'NO_AATSR'

    @Override
    public boolean isInvalid() {
        return !IdepixUtils.areAllReflectancesValid(reflMeris);
    }

    @Override
    public boolean isCloud() {
        boolean threshTest = whiteValue() + 5.0 * brightValue() + 0.5 * pressureValue() + temperatureValue() > CLOUD_THRESH;
        boolean bbtest = isBlueDenseCloud();
        if (isLand()) {
            return (!isInvalid() && (threshTest || bbtest) && !isClearSnow());
        } else {
            return !isInvalid() && threshTest && !isSeaIce();
        }
    }

    @Override
    public boolean isSeaIce() {
        if (isIstomena) {
            // use Istomena et al algorithm
            final boolean mask1 = Math.abs((btempAatsr[0] - btempAatsr[1]) / btempAatsr[0]) < 0.03;
            final boolean mask2 = Math.abs((btempAatsr[0] - btempAatsr[2]) / btempAatsr[0]) < 0.03;
            final boolean mask3 = Math.abs((reflAatsr[2] - reflAatsr[3]) / reflAatsr[2]) > 0.8;
            final boolean mask4 = Math.abs((reflAatsr[2] - reflAatsr[1]) / reflAatsr[2]) < 0.1;
            final boolean mask5 = Math.abs((reflAatsr[1] - reflAatsr[0]) / reflAatsr[1]) < 0.4;

            return isWater() && mask1 && mask2 && mask3 && mask4 && mask5;
        } else {
            if (reflAatsr[3] > 0.0) {
                return isWater() && isBright() && reflAatsr[3] < refl1600ThreshAatsr;
            } else {
                // outside AATSR swath
                if (isSchillerSeaice) {
                    return isWater() && isBright() &&
                            schillerSeaiceCloudProb < 0.5;
                } else {
                    return false;
                }
            }
        }
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
//        value /= 3.0; // test
        value /= 15.0; // test
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
        value *= 2.0;
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
        if (!IdepixUtils.areAllReflectancesValid(reflAatsr)) {
           // i.e., the region outside AATSR swath
           return UNCERTAINTY_VALUE;
        }

        float temperature;

        if (btempAatsr[2] < 225f) {
            temperature = 0.99f;
        } else if (225f <= btempAatsr[2] && 290f > btempAatsr[2]) {
            temperature = 0.9f - 0.49f * ((btempAatsr[2] - 225f) / (290f - 225f));
        } else if (290f <= btempAatsr[2] && 300f > btempAatsr[2]) {
            temperature = 0.5f - 0.49f * ((btempAatsr[2] - 290f) / (300f - 290f));
        } else {
            temperature = 0.01f;
        }

        return temperature;
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

    public void setIstomena(boolean istomena) {
        isIstomena = istomena;
    }

    public void setSchillerSeaice(boolean schillerSeaice) {
        isSchillerSeaice = schillerSeaice;
    }

    public void setRefl1600ThreshAatsr(float refl1600ThreshAatsr) {
        this.refl1600ThreshAatsr = refl1600ThreshAatsr;
    }

    public void setSchillerSeaiceCloudProb(double schillerSeaiceCloudProb) {
        this.schillerSeaiceCloudProb = schillerSeaiceCloudProb;
    }

    public void setSchillerRefl1600Meris(double schillerRefl1600Meris) {
        this.schillerRefl1600Meris = schillerRefl1600Meris;
    }

    public double getSchillerRefl1600Meris() {
        return schillerRefl1600Meris;
    }

    public double getSchillerSeaiceCloudProb() {
        return schillerSeaiceCloudProb;
    }

    public float getBrr442ThreshMeris() {
        return brr442ThreshMeris;
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
