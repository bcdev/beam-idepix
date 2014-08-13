package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.GeoCoding;

/**
 * Interface for usage of an advanced water mask
 *
 * @author olafd
 */
public interface WatermaskStrategy {
    /**
     * Returns a watermask sample value at a given geo-position
     *
     * @param lat
     * @param lon
     *
     * @return the watermask sample
     */
    byte getWatermaskSample(float lat, float lon);

    /**
     * Returns the fraction of water in a region around a given pixel
     *
     * @param geoCoding The geocoding of the product
     * @param x pixel X position
     * @param y pixel Y position
     *
     * @return The fraction of water.
     */
    byte getWatermaskFraction(GeoCoding geoCoding, int x, int y);
}
