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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Parameter(description = "The thickness of the coastline in kilometers.", defaultValue = "20")
    private float thicknessOfCoast;

    @Parameter(description = "The minimal size for a water region to be acknowledged as an ocean in km².",
               defaultValue = "1600")
    private float minimumOceanSize;

    @Parameter(description = "Reflectance Threshold for reflectance 12", defaultValue = "0.08")
    private float refl_water_threshold;

    @Parameter(description = "Whether or not to calculate a lake mask", defaultValue = "true")
    private boolean calculateLakes;

    private final static String water_flags = "water_flags";
    private GeoCoding geoCoding;
    private Product landWaterMaskProduct;
    private int[][] lakeRegionMatrix;
    private float kmxpix;
    private int minimumOceanSizeInPixels;
    private BufferedImage coastRegionImage;
    private int thicknessOfCoastInPixels;
    private int regionCounter;
    private Map<Integer, Integer> regionSizes;
    private List<Region> regions;

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
        coastRegionImage = new BufferedImage(sourceProduct.getSceneRasterWidth(),
                                             sourceProduct.getSceneRasterHeight(), BufferedImage.TYPE_INT_RGB);
        kmxpix = 0.3f;
        if (sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
            kmxpix = 1.2f;
        }
        if (calculateLakes) {
            minimumOceanSizeInPixels = (int) (minimumOceanSize / kmxpix);
            identifyLakeRegions();
        }
        thicknessOfCoastInPixels = (int) (thicknessOfCoast / kmxpix) / 2;
        identifyCoastRegions();
        setTargetProduct(targetProduct);
    }

    private void setupCloudScreeningBitmasks(Product lakesProduct) {
        int index = 0;
        int w = lakesProduct.getSceneRasterWidth();
        int h = lakesProduct.getSceneRasterHeight();
        Mask mask;
        mask = Mask.BandMathsType.create("COASTLINE_BUFFER", "Pixels along the coastline", w, h,
                                         water_flags + ".COASTLINE_BUFFER", Color.gray, 0.5f);
        lakesProduct.getMaskGroup().add(index, mask);
        mask = Mask.BandMathsType.create("OCEAN", "Water pixels which are neither in lakes nor close to the coast", w, h,
                                         water_flags + ".OCEAN", Color.blue.darker(), 0.5f);
        lakesProduct.getMaskGroup().add(index++, mask);
        if (calculateLakes) {
            mask = Mask.BandMathsType.create("LAKES", "Pixels over lakes", w, h,
                                             water_flags + ".LAKES", Color.blue.brighter(), 0.5f);
            lakesProduct.getMaskGroup().add(index, mask);
        }
    }

    private FlagCoding createScapeMLakesFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("COASTLINE_BUFFER", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("OCEAN", BitSetter.setFlag(0, 1), null);
        if (calculateLakes) {
            flagCoding.addFlag("LAKES", BitSetter.setFlag(0, 2), null);
        }
        return flagCoding;
    }

    private void identifyCoastRegions() {
        final Mask coastlineMask = sourceProduct.getMaskGroup().getByDisplayName("coastline");
        final Band l1FlagsBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        for (int y = 0; y < l1FlagsBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < l1FlagsBand.getSceneRasterWidth(); x++) {
                if (coastlineMask != null) {   // todo: how to handle products without coastlines, or if this mask
                    // is not available, e.g. in CC products it is named 'l1p_coastline'!
                    final int isCoastline = coastlineMask.getSampleInt(x, y);
                    if (isCoastline != 0) {
                        for (int i = -thicknessOfCoastInPixels; i < thicknessOfCoastInPixels; i++) {
                            int offset = -Math.abs(i) + thicknessOfCoastInPixels;
                            for (int j = -offset; j < offset; j++) {
                                if (isInBounds(x + j, y + i)) {
                                    coastRegionImage.getRaster().setSample(x + j, y + i, 0, 1);
                                }
                            }
                        }
                    }
                } else {
                    coastRegionImage.getRaster().setSample(x, y, 0, 0);
                }
            }
        }
    }

    private void identifyLakeRegions() {
        regionCounter = 0;
        regionSizes = new HashMap<Integer, Integer>();
        regions = new ArrayList<Region>();
        lakeRegionMatrix = new int[sourceProduct.getSceneRasterWidth()][sourceProduct.getSceneRasterHeight()];
        for (int i = 0; i < sourceProduct.getSceneRasterWidth(); i++) {
            Arrays.fill(lakeRegionMatrix[i], 0);
        }
        final Band landWaterFractionBand = landWaterMaskProduct.getBand("land_water_fraction");
        for (int y = 0; y < landWaterFractionBand.getSceneRasterHeight(); y++) {
            for (int x = 0; x < landWaterFractionBand.getSceneRasterWidth(); x++) {
                if (landWaterFractionBand.getSampleFloat(x, y) >= 50.0) {
                    checkPixel(x, y);
                }
            }
        }
    }

    private void checkPixel(int x, int y) {
        int leftPixelRegionID = (isInBounds(x - 1, y)) ? lakeRegionMatrix[x - 1][y] : 0;
        int upperPixelRegionID = (isInBounds(x, y - 1)) ? lakeRegionMatrix[x][y - 1] : 0;
        if (leftPixelRegionID == 0 && upperPixelRegionID == 0) {
            int regionID = ++regionCounter;
            lakeRegionMatrix[x][y] = regionID;
            regionSizes.put(regionID, 1);
            regions.add(new Region(regionID));
        } else {
            if (leftPixelRegionID == 0) {
                lakeRegionMatrix[x][y] = upperPixelRegionID;
                regionSizes.put(upperPixelRegionID, regionSizes.get(upperPixelRegionID) + 1);
            } else if (upperPixelRegionID == 0) {
                lakeRegionMatrix[x][y] = leftPixelRegionID;
                regionSizes.put(leftPixelRegionID, regionSizes.get(leftPixelRegionID) + 1);
            } else {
                if (leftPixelRegionID == upperPixelRegionID) {
                    lakeRegionMatrix[x][y] = leftPixelRegionID;
                    regionSizes.put(leftPixelRegionID, regionSizes.get(leftPixelRegionID) + 1);
                } else {
                    lakeRegionMatrix[x][y] = upperPixelRegionID;
                    Region firstRegion = null;
                    Region secondRegion = null;
                    for (Region region : regions) {
                        if (region.isResponsibleRegionFor(upperPixelRegionID)) {
                            firstRegion = region;
                        }
                        if (region.isResponsibleRegionFor(leftPixelRegionID)) {
                            secondRegion = region;
                        }
                        if (firstRegion != null && secondRegion != null) {
                            break;
                        }
                    }
                    if (firstRegion != null && secondRegion != null && firstRegion != secondRegion) {
                        firstRegion.merge(secondRegion);
                        regions.remove(secondRegion);
                    }
                }
            }
        }
    }

    private boolean isInBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < sourceProduct.getSceneRasterWidth() && y < sourceProduct.getSceneRasterHeight();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();
        Raster coastRegionData = coastRegionImage.getData(rectangle);
        Tile waterFractionTile = getSourceTile(landWaterMaskProduct.getRasterDataNode("land_water_fraction"), rectangle);
        for (Tile.Pos pos : targetTile) {
            final boolean isAlongCoastline = coastRegionData.getSample(pos.x, pos.y, 0) == 1;
            boolean isOcean;
            boolean isLake = false;
            if (calculateLakes) {
                int regionID = lakeRegionMatrix[pos.x][pos.y];
                if (regionID != 0) {
                    for (Region region : regions) {
                        if (region.isResponsibleRegionFor(regionID)) {
                            isLake = !region.isOfMinimalSize();
                            break;
                        }
                    }
                }
                isOcean = regionID != 0 && !isLake && !isAlongCoastline;
            } else {
                isOcean = waterFractionTile.getSampleFloat(pos.x, pos.y) > 50 && !isAlongCoastline;
            }
            int waterRegionFlag = 0;
            waterRegionFlag = BitSetter.setFlag(waterRegionFlag, 0, isAlongCoastline);
            waterRegionFlag = BitSetter.setFlag(waterRegionFlag, 1, isOcean);
            if (calculateLakes) {
                waterRegionFlag = BitSetter.setFlag(waterRegionFlag, 2, isLake);
            }
            targetTile.setSample(pos.x, pos.y, waterRegionFlag);
        }
    }

    private class Region {
        private List<Integer> subRegions;
        private int size;

        Region(int regionID) {
            subRegions = new ArrayList<Integer>();
            subRegions.add(regionID);
            size = -1;
        }

        void merge(Region region) {
            subRegions.addAll(region.getSubRegions());
        }

        List<Integer> getSubRegions() {
            return subRegions;
        }

        public boolean isResponsibleRegionFor(int pixelID) {
            for (Integer subRegion : subRegions) {
                if (subRegion == pixelID) {
                    return true;
                }
            }
            return false;
        }

        boolean isOfMinimalSize() {
            if (size == -1) {
                evaluateSize();
            }
            return size >= minimumOceanSizeInPixels;
        }

        private void evaluateSize() {
            size = 0;
            for (int i = 0; i < subRegions.size(); i++) {
                size += regionSizes.get(subRegions.get(i));
            }
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
