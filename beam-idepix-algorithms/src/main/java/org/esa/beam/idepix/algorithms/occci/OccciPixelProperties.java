package org.esa.beam.idepix.algorithms.occci;

/**
 * Interface for OC-CCI pixel properties.
 * To be used for instrument-specific (MODIS, SeaWiFS, ...) implementations.
 *
 * @author olafd
 */
interface OccciPixelProperties {

    /**
     * returns a boolean indicating if a pixel is cloudy (surely or ambiguous)
     *
     * @return isCloud
     */
    boolean isCloud();

    /**
     * returns a boolean indicating if a pixel is cloudy but ambiguous
     *
     * @return isCloudAmbiguous
     */
    boolean isCloudAmbiguous();

    /**
     * returns a boolean indicating if a pixel is surely cloudy
     *
     * @return isCloudSure
     */
    boolean isCloudSure();

    /**
     * returns a boolean indicating if a pixel is a cloud buffer pixel
     *
     * @return isCloudBuffer
     */
    boolean isCloudBuffer();

    /**
     * returns a boolean indicating if a pixel is a cloud shadow pixel
     *
     * @return isCloudShadow
     */
    boolean isCloudShadow();

    /**
     * returns a boolean indicating if a pixel is snow or ice
     *
     * @return isSnowIce
     */
    boolean isSnowIce();

    /**
     * returns a boolean indicating if a pixel is a mixed pixel
     *
     * @return isMixedPixel
     */
    boolean isMixedPixel();

    /**
     * returns a boolean indicating if a pixel has risk for glint
     *
     * @return isGlintRisk
     */
    boolean isGlintRisk();

    /**
     * returns a boolean indicating if a pixel is a coastline pixel
     *
     * @return isCoastline
     */
    boolean isCoastline();

    /**
     * returns a boolean indicating if a pixel is over land
     *
     * @return isLand
     */
    boolean isLand();

}
