package org.esa.beam.idepix.operators;

/**
 * Abstract base class for pixel properties. Provides default implementations for isLand() and isWater(), using the
 * SRTM-shapefile-based land-water-mask.
 *
 * @author Thomas Storm
 */
public abstract class AbstractPixelProperties implements PixelProperties {

    boolean isWater;

    @Override
    public boolean isWater() {
        return isWater;
    }

    public void setIsWater(boolean isWater) {
        this.isWater = isWater;
    }
}
