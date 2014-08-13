package org.esa.beam.idepix.algorithms.avhrrac;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.util.Map;

/**
 * Operator for GlobAlbedo MERIS cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.classification.default",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "This operator provides cloud screening from AVHRR L1b data.")
public class AvhrrAcDefaultClassificationOp extends AvhrrAcClassificationOp {

    private Band[] avhrrRadianceBands;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        Tile[] avhrrRadianceTiles = new Tile[IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS];
        float[] avhrrRadiance = new float[IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS; i++) {
            avhrrRadianceTiles[i] = getSourceTile(avhrrRadianceBands[i], rectangle);
        }

        GeoPos geoPos = null;
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                    if (geoCoding.canGetGeoPos()) {
                        geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                        waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                        if (aacUseWaterMaskFraction) {
                            waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                        }
                    }

                    // set up pixel properties for given instruments...
                    AvhrrAcAlgorithm avhrrAcAlgorithm = createAvhrrAcAlgorithm(avhrrRadianceTiles,
                                                                               avhrrRadiance,
                                                                               waterMaskSample,
                                                                               waterMaskFraction,
                                                                               y,
                                                                               x);

                    setCloudFlag(cloudFlagTargetTile, y, x, avhrrAcAlgorithm);

                    // for given instrument, compute more pixel properties and write to distinct band
                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, y, x, avhrrAcAlgorithm);
                    }
                }
            }
            // set cloud buffer flags...
            setCloudBuffer(IdepixUtils.IDEPIX_CLOUD_FLAGS, cloudFlagTargetTile, rectangle);

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    @Override
    public void setBands() {
        avhrrRadianceBands = new Band[IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS; i++) {
            avhrrRadianceBands[i] = sourceProduct.getBand(IdepixConstants.AVHRR_RADIANCE_BAND_PREFIX + (i + 1));
        }
    }

    @Override
    public void extendTargetProduct() throws OperatorException {
        if (aacCopyRadiances) {
            copyRadiances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }
    }

    private void copyRadiances() {
        for (int i = 0; i < IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS; i++) {
            ProductUtils.copyBand(avhrrRadianceBands[i].getName(), sourceProduct, targetProduct, true);
        }
    }

    private AvhrrAcAlgorithm createAvhrrAcAlgorithm(Tile[] avhrrRadianceTiles,
                                                    float[] avhrrRadiance,
                                                    byte watermask,
                                                    byte watermaskFraction,
                                                    int y,
                                                    int x) {
        AvhrrAcDefaultAlgorithm aacAlgorithm = new AvhrrAcDefaultAlgorithm();

        for (int i = 0; i < IdepixConstants.AVHRR_L1b_NUM_SPECTRAL_BANDS; i++) {
            avhrrRadiance[i] = avhrrRadianceTiles[i].getSampleFloat(x, y);
        }
        aacAlgorithm.setRadiance(avhrrRadiance);

        if (aacUseWaterMaskFraction) {
            final boolean isLand = watermaskFraction < WATERMASK_FRACTION_THRESH;
            aacAlgorithm.setL1FlagLand(isLand);
            setIsWaterByFraction(watermaskFraction, aacAlgorithm);
        } else {
            final boolean isLand = !(watermask == WatermaskClassifier.WATER_VALUE);
            aacAlgorithm.setL1FlagLand(isLand);
            setIsWater(watermask, aacAlgorithm);
        }

        return aacAlgorithm;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcDefaultClassificationOp.class);
        }
    }

}
