package org.esa.beam.idepix.algorithms.globalbedo;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 19.11.12
 * Time: 10:26
 *
 * @author olafd
 */
public class GlobAlbedoAatsrAlgorithm extends GlobAlbedoAlgorithm {

    public static final int L1B_F_LAND = 0;
    public static final int L1B_F_GLINT_RISK = 2;

    private float btemp1200;
    private boolean l1FlagLand;
    private boolean l1FlagGlintRisk;
    private boolean useFwardViewForCloudMask;

    @Override
    public boolean isCloud() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float brightValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float temperatureValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float spectralFlatnessValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float whiteValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float ndsiValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float ndviValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float pressureValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float glintRiskValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float aPrioriLandValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float aPrioriWaterValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float radiometricLandValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float radiometricWaterValue() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getBrightWhiteThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getNdsiThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getNdviThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getBrightThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getWhiteThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getTemperatureThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getGlintThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getPressureThreshold() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
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
