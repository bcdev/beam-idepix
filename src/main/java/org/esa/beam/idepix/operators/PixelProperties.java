package org.esa.beam.idepix.operators;

/**
 * Interface for pixel properties.
 * To be used for instrument-specific implementations.
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
interface PixelProperties {

    // todo: add documentation if set of properties is finally agreed

    boolean isBrightWhite();

    boolean isCloud();
    
    boolean isClearLand();

    boolean isClearWater();

    boolean isClearSnow();

    boolean isLand();

    boolean isWater();

    boolean isL1Water();

    boolean isBright();

    boolean isWhite();

    boolean isCold();

    boolean isVegRisk();

    boolean isGlintRisk();

    boolean isHigh();

    boolean isInvalid();

    float brightValue();

    float temperatureValue();

    float spectralFlatnessValue();
    
    float whiteValue();

    float ndsiValue();

    float ndviValue();

    float pressureValue();

    float glintRiskValue();

    float aPrioriLandValue();

    float aPrioriWaterValue();

    float radiometricLandValue();

    float radiometricWaterValue();


}
