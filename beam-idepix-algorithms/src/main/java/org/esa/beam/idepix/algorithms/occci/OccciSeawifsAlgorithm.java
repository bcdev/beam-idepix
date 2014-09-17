package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/SeaWiFS
 * todo: implement
 *
 * @author olafd
 */
public class OccciSeawifsAlgorithm extends OccciAlgorithm {

    private static final double GLINT_INCREMENT = 0.1;

    // as long as we have no Schiller, CLOUD thresholds experimentally selected just from A2009125001500.L1B_LAC:
    private static final double THRESH_BRIGHT_CLOUD_AMBIGUOUS = 0.07;
    private static final double THRESH_BRIGHT_CLOUD_SURE = 0.15;

    @Override
    public boolean isSnowIce() {
        // we don't have anything for SeaWiFS...
        return false;
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        if (isLand() || isCloudSure()) {   // this check has priority
            return false;
        }

        // for SeaWiFS, nnOutput has one element:
        // nnOutput[0] =
        // 0 : cloudy
        // 1 : semitransparent cloud
        // 2 : clear water
        if (nnOutput != null) {
            return nnOutput[0] >= 0.48 && nnOutput[0] < 1.47;    // separation numbers from report HS, 20140822
        } else {
            // fallback
            return (brightValue() > THRESH_BRIGHT_CLOUD_AMBIGUOUS);
        }
    }

    @Override
    public boolean isCloudSure() {
        if (isLand() || isSnowIce()) {   // this check has priority
            return false;
        }

        // for SeaWiFS, nnOutput has one element:
        // nnOutput[0] =
        // 0 : cloudy
        // 1 : semitransparent cloud
        // 2 : clear water
        if (nnOutput != null) {
            return nnOutput[0] >= 0.0 && nnOutput[0] < 0.48;   // separation numbers from report HS, 20140822
        } else {
            // fallback
            return (brightValue() > THRESH_BRIGHT_CLOUD_SURE);
        }
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
        // unmixing using MERIS bands 7, 9, 10, 12
        return false;
    }

    @Override
    public boolean isGlintRisk() {
        // todo
        // depends on geometry, windspeed and rho_toa_865
        // MODIS: we have rho_toa_865, wind components are required!
        return false;
    }

    @Override
    public float brightValue() {
        // use L_865
        return (float) refl[7];
    }

    @Override
    public float ndsiValue() {
        return 0;
    }

}
