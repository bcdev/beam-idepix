package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/MODIS
 * todo: implement
 *
 * @author olafd
 */
public class OccciModisAlgorithm extends OccciAlgorithm {

    private static final double GLINT_INCREMENT = 0.1;

    // as long as we have no Schiller, CLOUD thresholds experimentally selected just from A2009125001500.L1B_LAC:
    private static final double THRESH_BRIGHT_CLOUD_AMBIGUOUS = 0.07;
    private static final double THRESH_BRIGHT_CLOUD_SURE = 0.15;
    // SNOW_ICE thresholds experimentally selected just from MOD021KM.A2014121.0155.006.2014121132820.hdf
    // more investigations needed
    private static final double THRESH_BRIGHT_SNOW_ICE = 0.25;
    private static final double THRESH_NDSI_SNOW_ICE = 0.8;

    @Override
    public boolean isSnowIce() {
        // needs ndsi and brightness
        // MERIS: ndsi depends on rho_toa_865,885; brightness depends on rho_ag (bottom of Rayleigh)
        // maybe we can forget the Rayleigh (it's small)
        // MODIS: for slope use bands 16 (869nm) and 7 (2130nm, 500m spatial), threshold to be adjusted
        // for brightness use band 16 (Rayleigh corrected?)

        return (!isInvalid() && brightValue() > THRESH_BRIGHT_SNOW_ICE && ndsiValue() > THRESH_NDSI_SNOW_ICE);
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        if (isLand() || isCloudSure() || isSnowIce()) {   // this check has priority
            return false;
        }

        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            final double schillerValue = (double) waterNN.compute(accessor);
            final double thresh = isGlintRisk() ? ambiguousThresh : ambiguousThresh + GLINT_INCREMENT;
            return schillerValue > thresh;
        } else {
            // test (as long as we have no Schiller)
            return (brightValue() > THRESH_BRIGHT_CLOUD_AMBIGUOUS);
        }
    }

    @Override
    public boolean isCloudSure() {
        if (isLand() || isSnowIce()) {   // this has priority
            return false;
        }

        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            final double schillerValue = (double) waterNN.compute(accessor);
            final double thresh = isGlintRisk() ? sureThresh : sureThresh + GLINT_INCREMENT;
            return schillerValue > thresh;
        } else {
            // test (as long as we have no Schiller)
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

    ///////////////////////////////////////////////////////////////////////
    // feature values

    @Override
    public float brightValue() {
        // use EV_250_Aggr1km_RefSB_1
        return (float) refl[10];
    }

    @Override
    public float ndsiValue() {
        // use EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7
        return (float) ((refl[10] - refl[15])/(refl[10] + refl[15]));
    }

}
