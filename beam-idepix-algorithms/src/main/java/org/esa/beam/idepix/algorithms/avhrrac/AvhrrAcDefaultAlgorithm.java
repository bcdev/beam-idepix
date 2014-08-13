package org.esa.beam.idepix.algorithms.avhrrac;

/**
 * IDEPIX pixel identification algorithm for AVHRR AC
 *
 * @author olafd
 */
public class AvhrrAcDefaultAlgorithm extends AvhrrAcAlgorithm {

    static final float BRIGHT_THRESH = 0.4f;

    @Override
    public boolean isCloud() {
       return brightValue() > getBrightThreshold();
    }

    @Override
    public boolean isSeaIce() {
        // no algorithm available
        return false;
    }

    @Override
    public float spectralFlatnessValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float whiteValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float brightValue() {
        return radiance[0]/100.0f;
    }

    @Override
    public float ndsiValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float ndviValue() {
        return UNCERTAINTY_VALUE;
    }

    @Override
    public float pressureValue() {
        return UNCERTAINTY_VALUE;
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
    public float glintRiskValue() {
        return UNCERTAINTY_VALUE;
    }

    // THRESHOLD GETTERS

    @Override
    public float getBrightThreshold() {
        return BRIGHT_THRESH;
    }

    // PRIVATE METHODS

}
