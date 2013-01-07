package org.esa.beam.idepix.pixel;

/**
 * Interface for pixel properties.
 * To be used for instrument-specific implementations.
 *
 * @author Olaf Danne
 *
 */
interface PixelProperties {

    // todo: add documentation if set of properties is finally agreed

    boolean isBrightWhite();

    boolean isCloud();
    
    boolean isClearLand();

    boolean isClearWater();

    boolean isClearSnow();

    boolean isSeaIce();

    boolean isLand();

    boolean isWater();

    boolean isL1Water();

    boolean isBright();

    boolean isWhite();

    boolean isVegRisk();

    boolean isGlintRisk();

    boolean isHigh();

    boolean isInvalid();

}
