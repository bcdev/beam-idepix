package org.esa.beam.idepix.algorithms.globalbedo;

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
 * Operator for GlobAlbedo VGT cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification.vgt",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2008, 2012 by Brockmann Consult",
                  description = "This operator provides cloud screening from SPOT VGT data.")
public class GlobAlbedoVgtClassificationOp extends GlobAlbedoClassificationOp {

    // VGT bands:
    private Band[] vgtReflectanceBands;

    private static final int SM_F_LAND = 3;
    private static final int SM_F_MIR_GOOD = 4;
    private static final int SM_F_B3_GOOD = 5;
    private static final int SM_F_B2_GOOD = 6;
    private static final int SM_F_B0_GOOD = 7;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // VGT variables
        final Band smFlagBand = sourceProduct.getBand("SM");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile[] vgtReflectanceTiles = new Tile[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
        float[] vgtReflectance = new float[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle);
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
                    if (!gaUseL1bLandWaterFlag) {
                        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                        if (geoCoding.canGetGeoPos()) {
                            geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                            waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                            if (gaUseWaterMaskFraction) {
                                waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                            }
                        }
                    }

                    // set up pixel properties for given instruments...
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createVgtAlgorithm(smFlagTile, vgtReflectanceTiles,
                                                                                 vgtReflectance,
                                                                                 waterMaskSample,
                                                                                 waterMaskFraction,
                                                                                 y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);
                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, null, null, null, y, x, globAlbedoAlgorithm);
                    }
                }
            }
            // set cloud buffer flags...
            if (gaLcCloudBuffer) {
                IdepixUtils.setCloudBufferLC(IdepixUtils.IDEPIX_CLOUD_FLAGS, cloudFlagTargetTile, rectangle);
            } else {
                setCloudBuffer(IdepixUtils.IDEPIX_CLOUD_FLAGS, cloudFlagTargetTile, rectangle);
            }

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    @Override
    public void setBands() {
        vgtReflectanceBands = new Band[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i]);
        }
    }

    @Override
    public void extendTargetProduct() throws OperatorException {
        if (gaCopyRadiances) {
            copyRadiances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (gaCopyAnnotations) {
            copyAnnotations();
        }
    }

    private void copyAnnotations() {
        for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
        }
    }

    private void copyRadiances() {
        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            // write the original reflectance bands:
            ProductUtils.copyBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private GlobAlbedoAlgorithm createVgtAlgorithm(Tile smFlagTile, Tile[] vgtReflectanceTiles,
                                                   float[] vgtReflectance,
                                                   byte watermask, byte watermaskFraction,
                                                   int y, int x) {

        GlobAlbedoVgtAlgorithm gaAlgorithm = new GlobAlbedoVgtAlgorithm();

        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }

        checkVgtReflectanceQuality(vgtReflectance, smFlagTile, x, y);
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        gaAlgorithm.setRefl(vgtReflectanceSaturationCorrected);


        if (gaUseL1bLandWaterFlag) {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
            gaAlgorithm.setSmLand(isLand);
            setIsWater(watermask, gaAlgorithm);
        } else {
            if (gaUseWaterMaskFraction) {
                final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                        watermaskFraction < WATERMASK_FRACTION_THRESH;
                gaAlgorithm.setSmLand(isLand);
                setIsWaterByFraction(watermaskFraction, gaAlgorithm);
            } else {
                final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                        !(watermask == WatermaskClassifier.WATER_VALUE);
                gaAlgorithm.setSmLand(isLand);
                setIsWater(watermask, gaAlgorithm);
            }
        }


        return gaAlgorithm;
    }

    private void checkVgtReflectanceQuality(float[] vgtReflectance, Tile smFlagTile, int x, int y) {
        final boolean isB0Good = smFlagTile.getSampleBit(x, y, SM_F_B0_GOOD);
        final boolean isB2Good = smFlagTile.getSampleBit(x, y, SM_F_B2_GOOD);
        final boolean isB3Good = smFlagTile.getSampleBit(x, y, SM_F_B3_GOOD);
        final boolean isMirGood = smFlagTile.getSampleBit(x, y, SM_F_MIR_GOOD);
        if (!isB0Good || !isB2Good || !isB3Good || !isMirGood) {
            for (int i = 0; i < vgtReflectance.length; i++) {
                vgtReflectance[i] = Float.NaN;
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoVgtClassificationOp.class, "idepix.globalbedo.classification.vgt");
        }
    }

}
