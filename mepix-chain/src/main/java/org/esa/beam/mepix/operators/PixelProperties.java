package org.esa.beam.mepix.operators;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public interface PixelProperties {

    // todo: add documentation

    public boolean isBrightWhite();

    public boolean isCloud();

    public boolean isClearLand();

    public boolean isClearWater();

    public boolean isClearSnow();

    public boolean isLand();

    public boolean isWater();

    public boolean isBright();

    public boolean isWhite();

    public boolean isVegRisk();

    public boolean isHigh();

    public boolean isInvalid();

    public float brightValue();

    public float spectralFlatnessValue();
    
    public float whiteValue();

    public float ndsiValue();

    public float ndviValue();

    public float pressureValue();

    public float aPrioriLandValue();

    public float aPrioriWaterValue();

    public float radiometricLandValue();

    public float radiometricWaterValue();
}
