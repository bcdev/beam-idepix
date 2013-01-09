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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;

/**
 * Operator for GlobAlbedo VGT cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification.vgt",
                  version = "1.0",
                  authors = "Olaf Danne",
                  copyright = "(c) 2008, 2012 by Brockmann Consult",
                  description = "This operator provides cloud screening from SPOT VGT data.")
public class GlobAlbedoVgtClassificationOp extends GlobAlbedoClassificationOp {

    @Parameter(defaultValue = "false", label = "Copy input annotation bands (VGT)")
    private boolean gaCopyAnnotations;

    // VGT bands:
    private Band[] vgtReflectanceBands;

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();

        // VGT variables
        final Band smFlagBand = sourceProduct.getBand("SM");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile[] vgtReflectanceTiles = new Tile[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
        float[] vgtReflectance = new float[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle);
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
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createVgtAlgorithm(smFlagTile, vgtReflectanceTiles,
                                                                                 vgtReflectance,
                                                                                 waterMaskSample, y, x);

                    if (band == cloudFlagBand) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        setCloudFlag(targetTile, y, x, globAlbedoAlgorithm);
                    }
                    setPixelSamples(band, targetTile, null, null, null, y, x, globAlbedoAlgorithm);
                }
            }
            // set cloud buffer flags...
            if (gaLcCloudBuffer) {
                IdepixUtils.setCloudBufferLC(band.getName(), targetTile, rectangle);
            } else {
                setCloudBuffer(band.getName(), targetTile, rectangle);
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
                                                   float[] vgtReflectance, byte watermaskSample, int y, int x) {

        GlobAlbedoVgtAlgorithm gaAlgorithm = new GlobAlbedoVgtAlgorithm();

        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        gaAlgorithm.setRefl(vgtReflectanceSaturationCorrected);

        final boolean isLand = smFlagTile.getSampleBit(x, y, GlobAlbedoVgtAlgorithm.SM_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        gaAlgorithm.setSmLand(isLand);
        setIsWater(watermaskSample, gaAlgorithm);

        // specific threshold for polar regions:
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding != null) {
            final PixelPos pixelPos = new PixelPos();
            pixelPos.setLocation(x + 0.5f, y + 0.5f);
            final GeoPos geoPos = new GeoPos();
            geoCoding.getGeoPos(pixelPos, geoPos);
        }

        return gaAlgorithm;
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
