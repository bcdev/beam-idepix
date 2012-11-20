package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.idepix.IdepixConstants;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 19.11.12
 * Time: 10:24
 *
 * @author olafd
 */
public class GlobAlbedoVgtAlgorithm extends GlobAlbedoAlgorithm {

    public static final int SM_F_LAND = 3;

    private boolean smLand;
    private float ndsiThresh;

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

    // setters for VGT specific quantities

    public void setSmLand(boolean smLand) {
        this.smLand = smLand;
    }

    public void setNdsiThresh(float ndsiThresh) {
        this.ndsiThresh = ndsiThresh;
    }

    public void setRefl(float[] refl) {
        if (refl.length != IdepixConstants.VGT_WAVELENGTHS.length) {
            throw new OperatorException("VGT pixel processing: Invalid number of wavelengths [" + refl.length +
                                                "] - must be " + IdepixConstants.VGT_WAVELENGTHS.length);
        }
        this.refl = refl;
    }

}
