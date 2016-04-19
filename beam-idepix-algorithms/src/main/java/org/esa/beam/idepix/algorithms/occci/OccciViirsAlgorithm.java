package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/VIIRS
 * todo: complete
 *
 * @author olafd
 */
public class OccciViirsAlgorithm extends OccciAlgorithm {

    private static final double THRESH_BRIGHT = 0.15;

    @Override
    public boolean isSnowIce() {
        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 4.15 && nnOutput[0] <= 5.0;
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        if (isCloudSure() || isSnowIce()) {   // this check has priority
            return false;
        }

        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 2.15 && nnOutput[0] <= 3.7;
    }

    @Override
    public boolean isCloudSure() {
        if (isSnowIce()) {   // this has priority
            return false;
        }

        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 3.7 && nnOutput[0] <= 4.2;
    }

    @Override
    public boolean isCloudBuffer() {
        // is applied in post processing!
        return false;
    }

    @Override
    public boolean isCloudShadow() {
        // will be applied in post processing once we have an appropriate algorithm
        return false;
    }

    @Override
    public boolean isMixedPixel() {
        // todo
        return false;
    }

    @Override
    public boolean isGlintRisk() {
        // todo
        return false;
    }

    @Override
    public boolean isBright() {
        return brightValue() > THRESH_BRIGHT;
    }

    ///////////////////////////////////////////////////////////////////////

    @Override
    public float brightValue() {
        return (float) refl[4];   //  rhot_671 (671nm)
    }

    @Override
    public float whiteValue(int numeratorIndex, int denominatorIndex) {
        return 0.5f; // not yet needed
    }

    @Override
    public float ndsiValue() {
        return 0.5f; // not yet needed
    }

}
