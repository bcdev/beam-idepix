package org.esa.beam.idepix.operators;

//import org.esa.beam.framework.gpf.annotations.SourceProduct
import org.esa.beam.idepix.util.IdepixUtils;



/**
 * This class represents pixel properties as derived from MERIS L1b data
 *
 * @author Ana Ruescas
 * @version $Revision: $ $Date: 10.02.11  $
 */
class AnasMerisPixelProperties extends MerisPixelProperties {

    //protected static final float BRIGHT_THRESH = 0.5f;

    protected float p1;

    protected static final float P1_THRESH = 0.15f;
    protected static final float GLINT_THRESH = 0.5f;
    protected static final float CLOUD_THRESH = 1.15f;

    private float sza;
    //protected static final float PScatt_THRESH = 0.2f;

    //Booleans
    @Override
    public boolean isGlintRisk() {
        return (isWater() && glintRiskValue() > GLINT_THRESH);
    }

    @Override
    public boolean isCloud() {
        return (whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH && !isClearSnow());
    }
    //Modified values

    //Use the sza to mask the area from the beginning od the image to NADIR
    //Sun zenith extraction for glint
    /*private float sza() {
        double value:
        value = sza
    }*/

    //P1 to calculate pressure value for glint
    private float p1Value() {
        double value;
        value = 1.0 - p1 / 1000.0;
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    //Glint_risk_value calculated with P1 (derived areas with glint)
    @Override
    public float glintRiskValue() {
        if (p1Value() < P1_THRESH) {
            return p1Value() * (1.0f/0.15f);
        } else {
            return 0.0f;
        }
    }

    //Pressure value over glint and no glint areas
    @Override
    public float pressureValue() {
        double value;
        if (isGlintRisk()) {
            value = 1.00 - p1 / 1000.0;
        } else if (isLand()) {
            value = 1.0 - p1 / 1000.0;
        } else if (isWater()) {
            value = 1.25 - pscatt / 800.0;
        } else {
            value = UNCERTAINTY_VALUE;
        }
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    //Bright_test over glint and no glint areas
    @Override
    public float brightValue() {
        if (isGlintRisk()) {
            if (qwgCloudClassifFlagBrightRc) {
                return 1.0f;
            } else {
                return 0.0f;
            }
        } else {
            if (brr442 <= 0.0 || brr442Thresh <= 0.0) {
                return IdepixConstants.NO_DATA_VALUE;
            }
            double value = 0.5 * brr442 / brr442Thresh;
            value = Math.min(value, 1.0);
            value = Math.max(value, 0.0);
            return (float) value;
        }
    }

    public void setP1(float p1) {
        this.p1 = p1;
    }

    public float getP1() {
        return p1;
    }

    public void setSza(float sza) {
        this.sza = sza;
    }

    //public float getsza () {
        //return sza;


    /*
////Bright_test_1

@Override
public float brightValue() {
if (qwgCloudClassifFlagBrightRc) {
return 1.0f;
} else
return 0.0f;
}

//Glint_risk_value calculated with PScatt(or Band 11)
/*
@Override
public float glintRiskValue() {
 if (pressureValue () < pscatt_THRESH) {
     return pressureValue();
 } else {
     return 0.0f;
 }   */

    /*

     */
    //Spectral flatness using NIR bands 9, 12, 13 & 14         (not working)
  /*  here I tried to include a bright value over NIR, not finished (KS)
    @Override
    public float spectralFlatnessValue() {  //wrong name, don't know, how to change this into brightNIR

        final double brightIR = 1.0f - Math.abs(1000.0 * (slope3 + slope4) / 2.0);
        float result = (float) Math.max(0.0f, flatness);
        return result;
    }
    */
      /*
        public float spectralFlatnessValue() {  //wrong name, don't know, how to change this into brightNIR
        final double slope3 = IdepixUtils.spectralSlope(refl[8], refl[11],
                                                          IdepixConstants. MERIS_WAVELENGTHS[8], IdepixConstants. MERIS_WAVELENGTHS[11]);
        final double slope4 = IdepixUtils.spectralSlope(refl[12], refl[13],
                                                                  IdepixConstants. MERIS_WAVELENGTHS[12], IdepixConstants. MERIS_WAVELENGTHS[13]);



        final double flatness = 1.0f - Math.abs(1000.0 * (slope3 + slope4) / 2.0);
        float result = (float) Math.max(0.0f, flatness);
        return result;
    }
    //White calculated with flatness_NIR
    @Override
    public float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0.0f;
        }
    }
    */
}
