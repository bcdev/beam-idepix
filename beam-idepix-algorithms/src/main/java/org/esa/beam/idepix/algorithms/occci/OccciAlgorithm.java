package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.idepix.algorithms.SchillerAlgorithm;

/**
 * IDEPIX instrument-specific pixel identification algorithm for OC-CCI: abstract superclass
 *
 * @author olafd
 */
public abstract class OccciAlgorithm extends OccciAbstractPixelProperties {

    // in general, implementations are instrument-dependent:
    public abstract boolean isCloud();
    public abstract boolean isCloudAmbiguous();
    public abstract boolean isCloudBuffer();
    public abstract boolean isCloudShadow();
    public abstract boolean isSnowIce();
    public abstract boolean isMixedPixel();
    public abstract boolean isGlintRisk();

    public abstract float brightValue();
    public abstract float ndsiValue();

    float waterFraction;
    double[] refl;
    SchillerAlgorithm waterNN;
    SchillerAlgorithm.Accessor accessor;
    double ambiguousThresh;
    double sureThresh;


    @Override
    public boolean isInvalid() {
        // todo: define if needed
        return false;
    }

    @Override
    public boolean isCoastline() {
        // NOTE that this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return waterFraction < 100 && waterFraction > 0;
    }

    @Override
    public boolean isLand() {
        return waterFraction == 0;
    }

    ///////////////////////////////////////////////////////////////////////
    // setters
    public void setWaterFraction(float waterFraction) {
        this.waterFraction = waterFraction;
    }

    public void setRefl(double[] reflectance) {
        refl = reflectance;
    }

    public void setWaterNN(SchillerAlgorithm waterNN) {
        this.waterNN = waterNN;
    }

    public void setAccessor(SchillerAlgorithm.Accessor accessor) {
        this.accessor = accessor;
    }

    public void setAmbiguousThresh(double ambiguousThresh) {
        this.ambiguousThresh = ambiguousThresh;
    }

    public void setSureThresh(double sureThresh) {
        this.sureThresh = sureThresh;
    }
}
