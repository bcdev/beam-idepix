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

    private static final double GLINT_INCREMENT = 0.1;

    private float[] refl;
    private SchillerAlgorithm waterNN;
    private SchillerAlgorithm.Accessor accessor;
    private double ambiguousThresh;
    private double sureThresh;

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public boolean isCloud() {
        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            final double schillerValue = (double) waterNN.compute(accessor);
            return schillerValue > 1.35;
        } else {
            // test
            final double r = new Random().nextDouble();
            return (r > 0.5);
        }
    }

    // this is from CC MERIS:
//    double ambiguousThresh = schillerAmbiguous;
//    double sureThresh = schillerSure;
//    // this seems to avoid false cloud flagging in glint regions:
//    if (is_glint_risk) {
//        ambiguousThresh += 0.1;
//        sureThresh += 0.1;
//    }
//
//    float schillerValue = computeSchillerCloud(landWaterNN, sd, pixelInfo);
//
//    boolean isCloud = schillerValue > ambiguousThresh;
//    boolean isCloudAmbiguous = schillerValue > ambiguousThresh && schillerValue < sureThresh;
//
//    targetTile.setSample(pixelInfo.x, pixelInfo.y, F_GLINTRISK, is_glint_risk && !isCloud);
//    targetTile.setSample(pixelInfo.x, pixelInfo.y, F_CLOUD, isCloud);
//    targetTile.setSample(pixelInfo.x, pixelInfo.y, F_CLOUD_AMBIGUOUS, isCloudAmbiguous);


    @Override
    public boolean isCloudAmbiguous() {
        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            final double schillerValue = (double) waterNN.compute(accessor);
            final double thresh = isGlintRisk() ? ambiguousThresh : ambiguousThresh + GLINT_INCREMENT;
            return schillerValue > thresh;
        } else {
            // test (as long as we have no Schiller)
            final double r = new Random().nextDouble();
            return (r > 0.3);
        }
    }

    @Override
    public boolean isCloudSure() {

        if (waterNN != null && accessor != null) {
            // taken from SchillerClassificationOp, shall become active once we have a MODIS NN...
            final double schillerValue = (double) waterNN.compute(accessor);
            final double thresh = isGlintRisk() ? sureThresh : sureThresh + GLINT_INCREMENT;
            return schillerValue > thresh;
        } else {
            // test (as long as we have no Schiller)
            final double r = new Random().nextDouble();
            return (r > 0.7);
        }
    }

    @Override
    public boolean isCloudBuffer() {
        // will be applied in post processing!
        return false;
    }

    @Override
    public boolean isCloudShadow() {
        // will be applied in post processing!
        return false;
    }

    @Override
    public boolean isSnowIce() {
        // todo
        // needs mdsi and brightness
        // MERIS: mdsi depends on rho_toa_865,885; brightness depends on rho_ag (bottom of Rayleigh)
        // maybe we can forget the Rayleigh (it's small)
        // MODIS: for slope use bands 16 (869nm) and 7 (2130nm, 500m spatial), threshold to be adjusted
        // for brightness use band 16 (Rayleigh corrected?)
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
    public boolean isCoastline() {
        return false;
    }

    @Override
    public boolean isLand() {
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

    public void setAmbiguousThresh(double ambiguousThresh) {
        this.ambiguousThresh = ambiguousThresh;
    }

    public void setSureThresh(double sureThresh) {
        this.sureThresh = sureThresh;
    }
}
