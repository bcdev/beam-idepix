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
    //    private WatermaskClassifier watermaskClassifier;
    private final static String water_flags = "water_flags";
    public static final double refl_water_threshold = 0.08;
    private float thicknessOfCoast = 20;
    private float minimumOceanSize = 1600;
    private GeoCoding geoCoding;
    private Product landWaterMaskProduct;
    private Band regionBand;
    private BufferedImage lakeRegionImage;
    private float kmxpix;
    private float minimumOceanSizeInPixels;
    private BufferedImage coastRegionImage;
    private float thicknessOfCoastInPixels;

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
        regionBand = new Band("waterRegions", ProductData.TYPE_INT32, sourceProduct.getSceneRasterWidth(),
                              sourceProduct.getSceneRasterHeight());

//        regionBand.setData();

        lakeRegionImage = new BufferedImage(sourceProduct.getSceneRasterWidth(),
                                        sourceProduct.getSceneRasterHeight(), BufferedImage.TYPE_INT_RGB);
        coastRegionImage = new BufferedImage(sourceProduct.getSceneRasterWidth(),
                                                           sourceProduct.getSceneRasterHeight(), BufferedImage.TYPE_INT_RGB);

        kmxpix = 0.3f;
        if (sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
            kmxpix = 1.2f;
        }
        minimumOceanSizeInPixels = minimumOceanSize / kmxpix;
        thicknessOfCoastInPixels = thicknessOfCoast / kmxpix;
        identifyLakeRegions();
        identifyCoastRegions();
//        try {
//            watermaskClassifier = new WatermaskClassifier(50);
//        } catch (IOException e) {
//            throw new OperatorException("Failed to init water mask", e);
//        }
    }

    private static void setupCloudScreeningBitmasks(Product lakesProduct) {
        int index = 0;
        int w = lakesProduct.getSceneRasterWidth();
        int h = lakesProduct.getSceneRasterHeight();
        Mask mask;
        mask = Mask.BandMathsType.create("F_LAKES", "pixels over lakes", w, h,
                                         "cloud_classif_flags.F_LAKES", Color.blue.brighter(), 0.5f);
        lakesProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_OCEAN", "pixels over ocean", w, h,
                                         "cloud_classif_flags.F_OCEAN", Color.blue.darker(), 0.5f);
        lakesProduct.getMaskGroup().add(index, mask);
    }

    private static FlagCoding createScapeMLakesFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_LAKES", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("F_OCEAN", BitSetter.setFlag(0, 1), null);
        return flagCoding;
    }

    private void identifyCoastRegions() {
        final Mask coastlineMask = sourceProduct.getMaskGroup().getByDisplayName("coastline");
//        coastlineMask.getSampleInt()
        final Band l1FlagsBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        for (int y = 0; y < l1FlagsBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < l1FlagsBand.getSceneRasterWidth(); x++) {
                final int isCoastline = coastlineMask.getSampleInt(x, y);
// todo continue working here
//                if(isCoastline == 1) {
//                    for(int i = (int)(-thicknessOfCoastInPixels / 2); i < thicknessOfCoastInPixels / 2; i++) {
//                        for(int i = (int)(-thicknessOfCoastInPixels / 2); i < thicknessOfCoastInPixels / 2; i++) {
//                        }
//                    }
//                }
//                final int l1Flag = l1FlagsBand.getSampleInt(x, y);
//                if()

            }
        }
    }

    private void identifyLakeRegions() {
        final Band landWaterFractionBand = landWaterMaskProduct.getBand("land_water_fraction");
        final WritableRaster raster = lakeRegionImage.getRaster();
        for (int y = 0; y < landWaterFractionBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < landWaterFractionBand.getSceneRasterWidth(); x++) {
                if (landWaterFractionBand.getSampleFloat(x, y) >= 50.0) {
                    if (isInBounds(x - 1, y)) {
                        final int leftPixelValue = raster.getSample(x - 1, y, 0);
                        if (leftPixelValue > 0) {
                            if (leftPixelValue == minimumOceanSizeInPixels) {
                                raster.setSample(x, y, 0, minimumOceanSizeInPixels);
                            } else if (leftPixelValue == minimumOceanSizeInPixels - 1) {
                                raster.setSample(x, y, 0, minimumOceanSizeInPixels);
                                setPixelsAsOceans(raster, x, y);
                            } else {
                                raster.setSample(x, y, 0, leftPixelValue + 1);
                            }
                        }
                    }
                    if (isInBounds(x, y - 1)) {
                        final int upperPixelValue = raster.getSample(x, y - 1, 0);
                        if (upperPixelValue == minimumOceanSizeInPixels) {
                            raster.setSample(x, y, 0, minimumOceanSizeInPixels);
                        } else if (upperPixelValue == minimumOceanSizeInPixels - 1) {
                            raster.setSample(x, y, 0, minimumOceanSizeInPixels);
                            setPixelsAsOceans(raster, x, y);
                        } else {
                            raster.setSample(x, y, 0, upperPixelValue + 1);
                        }
                    }
                } else {
                    raster.setSample(x, y, 0, 0);
                }
            }
        }
    }



    private void setPixelsAsOceans(WritableRaster raster, int x, int y) {
        final int pixelValue = raster.getSample(x, y, 0);
        if (pixelValue > 0) {
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
        float kmxpix = 0.3f;
        if (sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
            kmxpix = 1.2f;
        }
        int sizeOfCoastInPixels = (int) (thicknessOfCoast / kmxpix);

        final Band landWaterFractionBand = landWaterMaskProduct.getBand("land_water_fraction");
        final Tile landWaterFractionTile = getSourceTile(landWaterFractionBand, targetTile.getRectangle());
        for (Tile.Pos pos : targetTile) {
            if (landWaterFractionTile.getSampleFloat(pos.x, pos.y) >= 50.0) {

            }
//            GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pos.x + 0.5f, pos.y + 0.5f), null);
//            boolean isWater = true;
//            try {
//                isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
//            } catch (IOException ignore) {
//            }
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
