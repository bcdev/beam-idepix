package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * IDEPIX pixel identification algorithm for GlobAlbedo/VGT
 *
 * @author olafd
 */
public class GlobAlbedoVgtAlgorithm extends GlobAlbedoAlgorithm {

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.50f;
    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.65f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float BRIGHT_THRESH = 0.3f;
    private static final float WHITE_THRESH = 0.5f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
    private static final float NDVI_THRESH = 0.4f;
    private static final float REFL835_WATER_THRESH = 0.1f;
    private static final float REFL835_LAND_THRESH = 0.15f;

    private boolean smLand;
    private double[] nnOutput;

    @Override
    public boolean isCloud() {
        if (!isInvalid()) {
            if (((whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH) && !isClearSnow())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSeaIce() {
        // no algorithm available for VGT only
        return false;
    }

    @Override
    public boolean isGlintRisk() {
        return false;
    }

    @Override
    public float brightValue() {
        double value;

        // do not make a difference any more
        // (changed for LC VGT processing because of clouds in rivers with new water mask, 20130227)
        value = (refl[0] + refl[1]) / 2.0f;

        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float temperatureValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[0],
                                                        IdepixConstants.VGT_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                        IdepixConstants.VGT_WAVELENGTHS[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[2]);
        final double flatness = 1.0f - Math.abs(2000.0 * (slope0 + slope1) / 2.0);
        return (float) Math.max(0.0f, flatness);
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
    public float ndsiValue() {
        double value = (refl[2] - refl[3]) / (refl[2] + refl[3]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float ndviValue() {
        double value = (refl[2] - refl[1]) / (refl[2] + refl[1]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    @Override
    public float pressureValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float glintRiskValue() {
        return IdepixUtils.spectralSlope(refl[0], refl[1], IdepixConstants.VGT_WAVELENGTHS[0],
                                         IdepixConstants.VGT_WAVELENGTHS[1]);
    }

    @Override
    public float aPrioriLandValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float aPrioriWaterValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (!smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    @Override
    public float radiometricLandValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[2] > refl[1] && refl[2] > REFL835_LAND_THRESH) {
            return 1.0f;
        } else if (refl[2] > REFL835_LAND_THRESH) {
            return 0.75f;
        } else {
            return 0.25f;
        }
    }

    @Override
    public float radiometricWaterValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[0] > refl[1] && refl[1] > refl[2] && refl[2] < REFL835_WATER_THRESH) {
            return 1.0f;
        } else {
            return 0.25f;
        }
    }

    @Override
    public float getBrightWhiteThreshold() {
        return BRIGHTWHITE_THRESH;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public float getNdsiThreshold() {
        return NDSI_THRESH;  //To change body of implemented methods use File | Settings | File Templates.
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
    public float getPressureThreshold() {
        return PRESSURE_THRESH;
    }

    // setters for VGT specific quantities

    public void setSmLand(boolean smLand) {
        this.smLand = smLand;
    }

    public void setRefl(float[] refl) {
        if (refl.length != IdepixConstants.VGT_WAVELENGTHS.length) {
            throw new OperatorException("VGT pixel processing: Invalid number of wavelengths [" + refl.length +
                                                "] - must be " + IdepixConstants.VGT_WAVELENGTHS.length);
        }
        this.refl = refl;
    }

    public void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

    public double[] getNnOutput() {
        return nnOutput;
    }
}
