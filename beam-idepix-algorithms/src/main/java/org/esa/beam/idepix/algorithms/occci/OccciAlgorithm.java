package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX instrument-specific pixel identification algorithm for OC-CCI: abstract superclass
 *
 * @author olafd
 */
public abstract class OccciAlgorithm extends OccciAbstractPixelProperties {

    // in general, implementations are instrument-dependent:
    public abstract boolean isInvalid();
    public abstract boolean isCloud();
    public abstract boolean isCloudAmbiguous();
    public abstract boolean isCloudBuffer();
    public abstract boolean isCloudShadow();
    public abstract boolean isSnowIce();
    public abstract boolean isMixedPixel();
    public abstract boolean isGlintRisk();
    public abstract boolean isCoastline();
    public abstract boolean isLand();
}
