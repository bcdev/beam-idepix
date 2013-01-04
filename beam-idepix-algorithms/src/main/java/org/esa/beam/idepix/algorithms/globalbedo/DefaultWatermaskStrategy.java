package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.watermask.operator.WatermaskClassifier;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 04.01.13
 * Time: 15:21
 *
 * @author olafd
 */
public class DefaultWatermaskStrategy implements WatermaskStrategy {

    private WatermaskClassifier classifier;

    public DefaultWatermaskStrategy(WatermaskClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public byte getWatermaskSample(float lat, float lon) {
        int waterMaskSample = WatermaskClassifier.INVALID_VALUE;
        if (classifier != null && lat > -60f) {
            //TODO the watermask does not work below -60 degree (mz, 2012-03-06)
            waterMaskSample = classifier.getWaterMaskSample(lat, lon);
        }
        return (byte) waterMaskSample;
    }

    @Override
    public byte getWatermaskFraction(GeoCoding geoCoding, int x, int y) {
        int waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
        final GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x, y), null);
        if (classifier != null && geoPos.getLat() > -60f) {
            waterMaskFraction = classifier.getWaterMaskFraction(geoCoding, x, y);
        }
        return (byte) waterMaskFraction;
    }
}
