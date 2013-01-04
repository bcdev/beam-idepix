package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.datamodel.GeoCoding;

/**
 * Interface for water mask usage
 *
 * @author olafd
 */
public interface WatermaskStrategy {
    // todo: add comments
    byte getWatermaskSample(float lat, float lon);

    byte getWatermaskFraction(GeoCoding geoCoding, int x, int y);
}
