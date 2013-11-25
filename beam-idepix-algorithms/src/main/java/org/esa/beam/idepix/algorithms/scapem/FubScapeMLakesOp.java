package org.esa.beam.idepix.algorithms.scapem;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.scapem.lakes",
                  version = "2.0.2-SNAPSHOT",
                  authors = "Tonio Fincke",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Lake identification with Scape-M from L. Guanter, FUB.")
public class FubScapeMLakesOp extends Operator {
    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;
    private final static String water_flags = "water_flags";
    public static final double refl_water_threshold = 0.08;
    private float thicknessOfCoast = 20;
    private float minimumOceanSize = 160;
    private GeoCoding geoCoding;
    private Product landWaterMaskProduct;
    private BufferedImage lakeRegionImage;
    private float kmxpix;
    private int minimumOceanSizeInPixels;
    private BufferedImage coastRegionImage;
    private int thicknessOfCoastInPixels;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.FubScapeM);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding == null) {
            throw new OperatorException("Source product has no geocoding");
        }
        if (!geoCoding.canGetGeoPos()) {
            throw new OperatorException("Source product has no usable geocoding");
        }
        Product targetProduct = new Product(sourceProduct.getName(),
                                            sourceProduct.getProductType(),
                                            sourceProduct.getSceneRasterWidth(),
                                            sourceProduct.getSceneRasterHeight());

        Band flagBand = targetProduct.addBand(water_flags, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createScapeMLakesFlagCoding(water_flags);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        setupCloudScreeningBitmasks(targetProduct);

        landWaterMaskProduct = GPF.createProduct("LandWaterMask", GPF.NO_PARAMS, sourceProduct);
        lakeRegionImage = new BufferedImage(sourceProduct.getSceneRasterWidth(),
                                        sourceProduct.getSceneRasterHeight(), BufferedImage.TYPE_INT_RGB);
        coastRegionImage = new BufferedImage(sourceProduct.getSceneRasterWidth(),
                                                           sourceProduct.getSceneRasterHeight(), BufferedImage.TYPE_INT_RGB);
        kmxpix = 0.3f;
        if (sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
            kmxpix = 1.2f;
        }
        minimumOceanSizeInPixels = (int)(minimumOceanSize / kmxpix);
        thicknessOfCoastInPixels = (int)(thicknessOfCoast / kmxpix)/2;
        identifyLakeRegions();
        identifyCoastRegions();
        setTargetProduct(targetProduct);
    }

    private static void setupCloudScreeningBitmasks(Product lakesProduct) {
        int index = 0;
        int w = lakesProduct.getSceneRasterWidth();
        int h = lakesProduct.getSceneRasterHeight();
        Mask mask;
        mask = Mask.BandMathsType.create("F_LAKES", "pixels over lakes", w, h,
                                         "cloud_classif_flags.F_LAKES", Color.blue.brighter(), 0.5f);
        lakesProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_COASTLINE_BUFFER", "pixels around the coastline", w, h,
                                         "cloud_classif_flags.F_COASTLINE_BUFFER", Color.blue.darker(), 0.5f);
        lakesProduct.getMaskGroup().add(index, mask);
    }

    private static FlagCoding createScapeMLakesFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_LAKES", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("F_COASTLINE_BUFFER", BitSetter.setFlag(0, 1), null);
        return flagCoding;
    }

    private void identifyCoastRegions() {
        final Mask coastlineMask = sourceProduct.getMaskGroup().getByDisplayName("coastline");
        final Band l1FlagsBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        for (int y = 0; y < l1FlagsBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < l1FlagsBand.getSceneRasterWidth(); x++) {
                final int isCoastline = coastlineMask.getSampleInt(x, y);
                if(isCoastline != 0) {
                    for(int i = -thicknessOfCoastInPixels; i < thicknessOfCoastInPixels; i++) {
                        int offset = -Math.abs(i) + thicknessOfCoastInPixels;
                        for(int j = -offset; j < offset; j++) {
                            if(isInBounds(x + j, y + i)) {
                                coastRegionImage.getRaster().setSample(x + j, y + i, 0, 1);
                            }
                        }
                    }
                }
            }
        }
    }

    private void identifyLakeRegions() {
        final Band landWaterFractionBand = landWaterMaskProduct.getBand("land_water_fraction");
        final WritableRaster raster = lakeRegionImage.getRaster();
        for (int y = 0; y < landWaterFractionBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < landWaterFractionBand.getSceneRasterWidth(); x++) {
                if (landWaterFractionBand.getSampleFloat(x, y) >= 50.0) {
                    checkPixel(raster, y, x, y, x - 1);
                    checkPixel(raster, y, x, y - 1, x);
                } else {
                    raster.setSample(x, y, 0, 0);
                }
            }
        }
    }

    private void checkPixel(WritableRaster raster, int origY, int origX, int y, int x) {
        if (isInBounds(x, y)) {
            final int pixelValue = raster.getSample(x, y, 0);
            if (pixelValue > 0) {
                if (pixelValue == minimumOceanSizeInPixels) {
                    raster.setSample(origX, origY, 0, minimumOceanSizeInPixels);
                } else if (pixelValue == minimumOceanSizeInPixels - 1) {
                    setPixelsAsOceans(raster, origX, origY);
//                    raster.setSample(origX, origY, 0, minimumOceanSizeInPixels);
                } else {
                    raster.setSample(origX, origY, 0, pixelValue + 1);
                }
            } else {
                raster.setSample(origX, origY, 0, 1);
            }
        }
    }

    private void setPixelsAsOceans(WritableRaster raster, int x, int y) {
        final int pixelValue = raster.getSample(x, y, 0);
        if (pixelValue > 0 && pixelValue < minimumOceanSizeInPixels) {
            raster.setSample(x, y, 0, minimumOceanSizeInPixels);
            if (isInBounds(x - 1, y)) {
                setPixelsAsOceans(raster, x - 1, y);
            }
            if (isInBounds(x, y - 1)) {
                setPixelsAsOceans(raster, x, y - 1);
            }
        }
    }

    private boolean isInBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < sourceProduct.getSceneRasterWidth() && y < sourceProduct.getSceneRasterHeight();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Raster lakeRegionData = lakeRegionImage.getData(targetTile.getRectangle());
        Raster coastRegionData = coastRegionImage.getData(targetTile.getRectangle());
        for (Tile.Pos pos : targetTile) {
            int waterRegionFlag = 0;
            waterRegionFlag = BitSetter.setFlag(waterRegionFlag, 0,
                    lakeRegionData.getSample(pos.x, pos.y, 0) == minimumOceanSizeInPixels);
            waterRegionFlag = BitSetter.setFlag(waterRegionFlag, 1,
                    coastRegionData.getSample(pos.x, pos.y, 0) == 1);
            targetTile.setSample(pos.x, pos.y, waterRegionFlag);
        }

    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FubScapeMLakesOp.class, "idepix.scapem.lakes");
        }
    }
}
