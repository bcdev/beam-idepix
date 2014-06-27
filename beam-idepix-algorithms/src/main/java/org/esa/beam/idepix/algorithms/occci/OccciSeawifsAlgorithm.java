package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/SeaWiFS
 * todo: implement
 *
 * @author olafd
 */
public class OccciSeawifsAlgorithm extends OccciAlgorithm {

    private float[] refl;

    @Override
    public boolean isInvalid() {
        return false;
    }

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

    // setters
    public void setRefl(float[] reflectance) {
        refl = reflectance;
    }
}
