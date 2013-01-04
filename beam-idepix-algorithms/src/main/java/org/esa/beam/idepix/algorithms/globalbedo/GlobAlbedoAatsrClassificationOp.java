package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.idepix.operators.BarometricPressureOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.pixel.AbstractPixelProperties;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Operator for GlobAlbedo AATSR cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification.aatsr",
                  version = "1.0",
                  authors = "Olaf Danne",
                  copyright = "(c) 2008 by Brockmann Consult",
                  description = "This operator provides cloud screening from AATSR data.")
public class GlobAlbedoAatsrClassificationOp extends GlobAlbedoClassificationOp {

    @Parameter(defaultValue = "true", label = "Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;

    // AATSR bands:
    private Band[] aatsrReflectanceBands;
    private Band[] aatsrBtempBands;

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();

        // AATSR variables
        final Band aatsrL1bFlagBand = sourceProduct.getBand(EnvisatConstants.AATSR_L1B_CLOUD_FLAGS_NADIR_BAND_NAME);
        final Tile aatsrL1bFlagTile = getSourceTile(aatsrL1bFlagBand, rectangle);

        Tile[] aatsrReflectanceTiles = new Tile[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        float[] aatsrReflectance = new float[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle);
        }

        Tile[] aatsrBtempTiles = new Tile[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        float[] aatsrBtemp = new float[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            aatsrBtempTiles[i] = getSourceTile(aatsrBtempBands[i], rectangle);
        }

        GeoPos geoPos = null;
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    if (!gaUseL1bLandWaterFlag) {
                        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                        if (geoCoding.canGetGeoPos()) {
                            geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                            waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                        }
                    }

                    // set up pixel properties for given instruments...
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createAatsrAlgorithm(band, targetTile, aatsrL1bFlagTile,
                                                                                   aatsrReflectanceTiles, aatsrReflectance,
                                                                                   aatsrBtempTiles,
                                                                                   aatsrBtemp, waterMaskSample, y, x);

                    if (band == cloudFlagBand) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        setCloudFlag(targetTile, y, x, globAlbedoAlgorithm);
                    }
                    setPixelSamples(band, targetTile, null, null, null, y, x, globAlbedoAlgorithm);
                }
            }
            // set cloud buffer flags...
            if (gaLcCloudBuffer) {
                IdepixUtils.setCloudBufferLC(band, targetTile, rectangle);
            } else {
                setCloudBuffer(band, targetTile, rectangle);
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

    private GlobAlbedoAlgorithm createAatsrAlgorithm(Band band, Tile targetTile, Tile aatsrL1bFlagTile,
                                                     Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                     Tile[] aatsrBtempTiles, float[] aatsrBtemp,
                                                     byte watermaskSample, int y, int x) {

        GlobAlbedoAatsrAlgorithm gaAlgorithm = new GlobAlbedoAatsrAlgorithm();

        for (int i = 0; i < IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES.length; i++) {
            aatsrReflectance[i] = aatsrReflectanceTiles[i].getSampleFloat(x, y);
            if (band.getName().equals(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i])) {
                targetTile.setSample(x, y, aatsrReflectance[i]);
            }
        }
        for (int i = 0; i < IdepixConstants.AATSR_BTEMP_BAND_NAMES.length; i++) {
            aatsrBtemp[i] = aatsrBtempTiles[i].getSampleFloat(x, y);
            if (band.getName().equals(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i])) {
                targetTile.setSample(x, y, aatsrBtemp[i]);
            }
        }

        gaAlgorithm.setUseFwardViewForCloudMask(gaUseAatsrFwardForClouds);
        gaAlgorithm.setRefl(aatsrReflectance);
        gaAlgorithm.setBtemp1200(aatsrBtempTiles[2].getSampleFloat(x, y));
        final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, GlobAlbedoAatsrAlgorithm.L1B_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        gaAlgorithm.setL1FlagLand(isLand);
        setIsWater(watermaskSample, gaAlgorithm);

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
