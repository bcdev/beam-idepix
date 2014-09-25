package org.esa.beam.idepix.algorithms.occci;

/**
 * IDEPIX pixel identification algorithm for OC-CCI/MODIS
 * todo: implement
 *
 * @author olafd
 */
public class OccciModisAlgorithm extends OccciAlgorithm {

    // as long as we have no Schiller, CLOUD thresholds experimentally selected just from A2009125001500.L1B_LAC:
    private static final double THRESH_BRIGHT_CLOUD_AMBIGUOUS = 0.07;
    private static final double THRESH_BRIGHT_CLOUD_SURE = 0.15;
    // SNOW_ICE thresholds experimentally selected just from MOD021KM.A2014121.0155.006.2014121132820.hdf
    // more investigations needed
    private static final double THRESH_BRIGHT_SNOW_ICE = 0.25;
    private static final double THRESH_NDSI_SNOW_ICE = 0.8;

    @Override
    public boolean isSnowIce() {

        // for MODIS ALL NN, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.0 : clear
        // 2.0 < x < 3.35 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.35 < x < 4.2 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] > 4.2 && nnOutput[0] <= 5.0;    // separation numbers from HS, 20140923
        } else {
            // fallback
            // needs ndsi and brightness
            // MERIS: ndsi depends on rho_toa_865,885; brightness depends on rho_ag (bottom of Rayleigh)
            // maybe we can forget the Rayleigh (it's small)
            // MODIS: for slope use bands 16 (869nm) and 7 (2130nm, 500m spatial), threshold to be adjusted
            // for brightness use band 16 (Rayleigh corrected?)
            return (!isInvalid() && brightValue() > THRESH_BRIGHT_SNOW_ICE && ndsiValue() > THRESH_NDSI_SNOW_ICE);
        }
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

        // for MODIS ALL NN, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.0 : clear
        // 2.0 < x < 3.35 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.35 < x < 4.2 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] > 2.0 && nnOutput[0] <= 3.35;    // separation numbers from HS, 20140923
        } else {
            // fallback
            return (brightValue() > THRESH_BRIGHT_CLOUD_AMBIGUOUS);
        }
    }

    @Override
    public boolean isCloudSure() {
        if (isSnowIce()) {   // this has priority
            return false;
        }

        // for MODIS ALL NN, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.0 : clear
        // 2.0 < x < 3.35 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.35 < x < 4.2 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        if (nnOutput != null) {
            return nnOutput[0] > 3.35 && nnOutput[0] <= 4.2;   // ALL NN separation numbers from HS, 20140923
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

    ///////////////////////////////////////////////////////////////////////
    // feature values

    @Override
    public float brightValue() {
        // use EV_250_Aggr1km_RefSB_1
        return (float) refl[0];
    }

    @Override
    public float ndsiValue() {
        // use EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7
        return (float) ((refl[0] - refl[6])/(refl[0] + refl[6]));
    }

}
