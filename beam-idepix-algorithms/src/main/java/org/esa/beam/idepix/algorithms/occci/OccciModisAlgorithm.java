package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.idepix.algorithms.SchillerAlgorithm;

import java.util.Random;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/MODIS
 * todo: implement
 *
 * @author olafd
 */
public class OccciModisAlgorithm extends OccciAlgorithm {

    private float[] refl;
    private SchillerAlgorithm waterNN;
    private SchillerAlgorithm.Accessor accessor;

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public boolean isCloud() {
        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            return (double) waterNN.compute(accessor) > 1.35;
        } else {
            // test
            final double r = new Random().nextDouble();
            return (r > 0.5);
        }
    }

    @Override
    public boolean isCloudAmbiguous() {
        return false;
    }

    @Override
    public boolean isCloudBuffer() {
        return false;
    }

    @Override
    public boolean isCloudShadow() {
        return false;
    }

    @Override
    public boolean isSnowIce() {
        return false;
    }

    @Override
    public boolean isMixedPixel() {
        return false;
    }

    @Override
    public boolean isGlintRisk() {
        return false;
    }

    @Override
    public boolean isCoastline() {
        return false;
    }

    @Override
    public boolean isLand() {
        return false;
    }

    @Override
    public boolean isCloudSure() {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////
    // setters
    public void setRefl(float[] reflectance) {
        refl = reflectance;
    }

    public void setWaterNN(SchillerAlgorithm waterNN) {
        this.waterNN = waterNN;
    }

    public void setAccessor(SchillerAlgorithm.Accessor accessor) {
        this.accessor = accessor;
    }
}
