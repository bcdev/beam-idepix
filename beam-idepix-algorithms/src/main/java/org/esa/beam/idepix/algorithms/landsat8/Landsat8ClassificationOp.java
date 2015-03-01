package org.esa.beam.idepix.algorithms.landsat8;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.*;
import java.util.Map;

/**
 * Landsat 8 pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.landsat8.classification",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "Landsat 8 water pixel classification operator.")
public class Landsat8ClassificationOp extends Operator {

    @Parameter(defaultValue = "100.0",
            description = "Brightness threshold at 865nm.",
            label = "Brightness threshold at 865nm")
    private float brightnessThresh;

    @Parameter(defaultValue = "1.0",  // todo: find sth. reasonable
            description = "Brightness coefficient for band 4 (865nm).",
            label = "Brightness coefficient for band 4 (865nm)")
    private float brightnessCoeffBand4;

    @Parameter(defaultValue = "1.0",  // todo: find sth. reasonable
            description = "Brightness coefficient for band 5 (1610nm).",
            label = "Brightness coefficient for band 5 (1610nm)")
    private float brightnessCoeffBand5;

    @Parameter(defaultValue = "100.0",  // todo: find reasonable default value
            description = "Whiteness threshold.",
            label = "Whiteness threshold")
    private float whitenessThresh;

    @Parameter(defaultValue = "4",   // todo: find reasonable default value
            interval = "[0,10]",
            description = "Index of numerator band for whiteness computation.",
            label = "Index of numerator band for whiteness computation")
    private int whitenessNumeratorBandIndex;

    @Parameter(defaultValue = "4",  // todo: find reasonable default value
            interval = "[0,10]",
            description = "Index of denominator band for whiteness computation.",
            label = "Index of denominator band for whiteness computation")
    private int whitenessDenominatorBandIndex;


    @SourceProduct(alias = "l8source", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    Band cloudFlagBand;

    private Band[] l8RadianceBands;
    private Band landWaterBand;

    static final int L1B_F_WATER_CONFIDENCE_HIGH = 0;  // todo

    @Override
    public void initialize() throws OperatorException {
        setBands();

        createTargetProduct();

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    public void setBands() {
        l8RadianceBands = new Band[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8RadianceBands[i] = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[i]);
        }
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        // shall be the only target band!!
        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = Landsat8Utils.createLandsat8FlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // MERIS variables
        final Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        final Band l8FlagBand = sourceProduct.getBand(Landsat8Constants.L1B_FLAGS_NAME);
        final Tile l8FlagTile = getSourceTile(l8FlagBand, rectangle);

        Tile[] l8RadianceTiles = new Tile[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        float[] l8Radiance = new float[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8RadianceTiles[i] = getSourceTile(l8RadianceBands[i], rectangle);
        }

        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    // set up pixel properties for given instruments...
                    Landsat8Algorithm landsat8Algorithm = createLandsat8Algorithm(
                            l8RadianceTiles,
                            l8Radiance,
                            waterFractionTile,
                            l8FlagTile,
                            y,
                            x);

                    setCloudFlag(cloudFlagTargetTile, y, x, landsat8Algorithm);
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean isLandPixel(int x, int y, Tile l8FlagTile, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return !l8FlagTile.getSampleBit(x, y, L1B_F_WATER_CONFIDENCE_HIGH); // todo: check!
            }
        } else {
            return !l8FlagTile.getSampleBit(x, y, L1B_F_WATER_CONFIDENCE_HIGH);  // todo
        }
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    void setCloudFlag(Tile targetTile, int y, int x, Landsat8Algorithm l8Algorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, Landsat8Constants.F_INVALID, l8Algorithm.isInvalid());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD, l8Algorithm.isCloud());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SURE, l8Algorithm.isCloud());   // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_AMBIGUOUS, l8Algorithm.isCloud());  // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_SNOW_ICE, l8Algorithm.isSnowIce()); // todo
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_BUFFER, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_GLINTRISK, false);   // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_COASTLINE, false);   // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_LAND, true);         // TODO
    }

    private Landsat8Algorithm createLandsat8Algorithm(Tile[] l8RadianceTiles,
                                                      float[] l8Radiance,
                                                      Tile l8FlagTile,
                                                      Tile waterFractionTile,
                                                      int y,
                                                      int x) {
        Landsat8Algorithm l8Algorithm = new Landsat8Algorithm();

        final int waterFraction = waterFractionTile.getSampleInt(x, y);
        final boolean isLand = isLandPixel(x, y, l8FlagTile, waterFraction);


        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8Radiance[i] = l8RadianceTiles[i].getSampleFloat(x, y);
        }

        l8Algorithm.setL8Radiance(l8Radiance);
        l8Algorithm.setIsWater(!isLand);
        l8Algorithm.setBrightnessThresh(brightnessThresh);
        l8Algorithm.setBrightnessCoeffBand4(brightnessCoeffBand4);
        l8Algorithm.setBrightnessCoeffBand5(brightnessCoeffBand5);
        l8Algorithm.setWhitenessThresh(whitenessThresh);
        l8Algorithm.setWhitenessNumeratorBandIndex(whitenessNumeratorBandIndex);
        l8Algorithm.setWhitenessDenominatorBandIndex(whitenessDenominatorBandIndex);

        return l8Algorithm;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(Landsat8ClassificationOp.class);
        }
    }

}
