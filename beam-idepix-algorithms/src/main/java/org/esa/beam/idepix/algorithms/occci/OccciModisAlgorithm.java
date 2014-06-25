package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/MODIS
 * todo: implement
 *
 * @author olafd
 */
public class OccciModisAlgorithm extends OccciAlgorithm {

    @Override
    public boolean isCloud() {
        return false;
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
}
