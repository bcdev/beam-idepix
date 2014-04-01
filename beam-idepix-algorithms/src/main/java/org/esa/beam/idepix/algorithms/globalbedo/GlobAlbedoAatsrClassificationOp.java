package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
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

import java.awt.Rectangle;
import java.util.Map;

/**
 * Operator for GlobAlbedo AATSR cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification.aatsr",
                  version = "2.1-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "This operator provides cloud screening from AATSR data.")
public class GlobAlbedoAatsrClassificationOp extends GlobAlbedoClassificationOp {

    // AATSR bands:
    private Band[] aatsrReflectanceBands;
    private Band[] aatsrBtempBands;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // AATSR variables
        final Band aatsrL1bFlagBand = sourceProduct.getBand(EnvisatConstants.AATSR_L1B_CLOUD_FLAGS_NADIR_BAND_NAME);
        final Tile aatsrL1bFlagTile = getSourceTile(aatsrL1bFlagBand, rectangle);

        Tile[] aatsrReflectanceTiles = new Tile[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        float[] aatsrReflectance = new float[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle);
        }

        Tile[] aatsrBtempTiles = new Tile[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            aatsrBtempTiles[i] = getSourceTile(aatsrBtempBands[i], rectangle);
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
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createAatsrAlgorithm(aatsrL1bFlagTile,
                                                                                   aatsrReflectanceTiles, aatsrReflectance,
                                                                                   aatsrBtempTiles,
                                                                                   waterMaskSample,
                                                                                   waterMaskFraction,
                                                                                   y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);

                    // for given instrument, compute more pixel properties and write to distinct band
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
        aatsrReflectanceBands = new Band[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            aatsrReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]);
        }
        aatsrBtempBands = new Band[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            aatsrBtempBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i]);
            if (aatsrBtempBands[i] == null) {
                throw new OperatorException
                        ("AATSR temperature bands missing or incomplete in source product - cannot proceed.");
            }
        }
    }

    @Override
    public void extendTargetProduct() throws OperatorException {
        if (gaCopyRadiances) {
            copyRadiances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }
    }

    private void copyRadiances() {
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            ProductUtils.copyBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private GlobAlbedoAlgorithm createAatsrAlgorithm(Tile aatsrL1bFlagTile,
                                                     Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                     Tile[] aatsrBtempTiles,
                                                     byte watermask,
                                                     byte watermaskFraction, int y, int x) {

        GlobAlbedoAatsrAlgorithm gaAlgorithm = new GlobAlbedoAatsrAlgorithm();

        for (int i = 0; i < IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES.length; i++) {
            aatsrReflectance[i] = aatsrReflectanceTiles[i].getSampleFloat(x, y);
        }

        final int length = aatsrReflectance.length / 2;
        float[] refl = new float[length];
        if (gaUseAatsrFwardForClouds) {
            System.arraycopy(aatsrReflectance, length, refl, 0, length);
        } else {
            System.arraycopy(aatsrReflectance, 0, refl, 0, length);
        }
        gaAlgorithm.setRefl(refl);
        gaAlgorithm.setBtemp1200(aatsrBtempTiles[2].getSampleFloat(x, y));

        if (gaUseL1bLandWaterFlag) {
            final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, AATSR_L1B_F_LAND);
            gaAlgorithm.setL1FlagLand(isLand);
            setIsWater(watermask, gaAlgorithm);
        } else {
            if (gaUseWaterMaskFraction) {
                final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, AATSR_L1B_F_LAND) &&
                                       watermaskFraction < WATERMASK_FRACTION_THRESH;
                gaAlgorithm.setL1FlagLand(isLand);
                setIsWaterByFraction(watermaskFraction, gaAlgorithm);
            } else {
                final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, AATSR_L1B_F_LAND) &&
                                       !(watermask == WatermaskClassifier.WATER_VALUE);
                gaAlgorithm.setL1FlagLand(isLand);
                setIsWater(watermask, gaAlgorithm);
            }
        }


        return gaAlgorithm;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoAatsrClassificationOp.class, "idepix.globalbedo.classification.aatsr");
        }
    }

}
