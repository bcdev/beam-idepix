package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * Operator used to consolidate cloud flag for CoastColour
 *
 * @author Marco
 * @since Idepix 1.3.1
 */
@OperatorMetadata(alias = "Meris.CoastColourPostProcessCloud",
                  version = "1.1",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Refines the cloud classification of Meris.CoastColourCloudClassification operator.")
public class CoastColourPostProcessOp extends MerisBasisOp {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "merisCloud")
    private Product merisCloudProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;
    @SourceProduct(alias = "rayleigh", optional = true)
    private Product rayleighProduct;
    @SourceProduct(alias = "sma", optional = true)
    private Product smaProduct;


    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private Band origCloudFlagBand;
    private TiePointGrid szaTPG;
    private TiePointGrid vzaTPG;
    private TiePointGrid saaTPG;
    private TiePointGrid vaaTPG;
    private Band ctpBand;
    private RasterDataNode altitudeRDN;

    private Band landAbundanceBand;
    private Band waterAbundanceBand;
    private Band cloudAbundanceBand;
    private Band summaryErrorBand;
    private Band brr7nBand;
    private Band brr9nBand;
    private Band brr10nBand;
    private Band brr12nBand;

    private RectangleExtender rectCalculator;


    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = createCompatibleProduct(merisCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");
        origCloudFlagBand = merisCloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS);
        szaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        vzaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
        saaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        vaaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME);
        ctpBand = ctpProduct.getBand("cloud_top_press");
        int shadowWidth;
        if (l1bProduct.getProductType().equals(EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME)) {
            altitudeRDN = l1bProduct.getBand("altitude");
            shadowWidth = 16;
        } else {
            altitudeRDN = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
            shadowWidth = 64;

        }

        if (smaProduct != null) {
            landAbundanceBand = smaProduct.getBand(IdepixConstants.SMA_ABUNDANCE_BAND_NAMES[0]);
            waterAbundanceBand = smaProduct.getBand(IdepixConstants.SMA_ABUNDANCE_BAND_NAMES[1]);
            cloudAbundanceBand = smaProduct.getBand(IdepixConstants.SMA_ABUNDANCE_BAND_NAMES[2]);
            summaryErrorBand = smaProduct.getBand(IdepixConstants.SMA_SUMMARY_BAND_NAME);
            brr7nBand = rayleighProduct.getBand(IdepixConstants.SMA_SOURCE_BAND_NAMES[1]);
            brr9nBand = rayleighProduct.getBand(IdepixConstants.SMA_SOURCE_BAND_NAMES[2]);
            brr10nBand = rayleighProduct.getBand(IdepixConstants.SMA_SOURCE_BAND_NAMES[3]);
            brr12nBand = rayleighProduct.getBand(IdepixConstants.SMA_SOURCE_BAND_NAMES[4]);
        }

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               shadowWidth, shadowWidth);


        ProductUtils.copyBand(CloudClassificationOp.CLOUD_FLAGS, merisCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        Tile sourceFlagTile = getSourceTile(origCloudFlagBand, extendedRectangle);
        Tile szaTile = getSourceTile(szaTPG, extendedRectangle);
        Tile vzaTile = getSourceTile(vzaTPG, extendedRectangle);
        Tile saaTile = getSourceTile(saaTPG, extendedRectangle);
        Tile vaaTile = getSourceTile(vaaTPG, extendedRectangle);
        Tile altitudeTile = getSourceTile(altitudeRDN, extendedRectangle);
        Tile ctpTile = getSourceTile(ctpBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {
                computeCloudShadow(x, y, szaTile, vzaTile, saaTile, vaaTile, altitudeTile, ctpTile,
                                   sourceFlagTile, targetTile);

                if (targetRectangle.contains(x, y)) {
                    final boolean is_cloud = sourceFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD);
                    if (is_cloud) {
                        computeCloudBuffer(x, y, sourceFlagTile, targetTile);
                    }
                    combineFlags(x, y, sourceFlagTile, targetTile);
                    if (is_cloud) {
                        if (isNearCoastline(x, y, sourceFlagTile, targetTile)) {
                            refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
                        }
                    }
                    // todo: setting to 'false' seems to be ignored if previously set to true - check this!
//                    targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_CLOUD, false);  // todo test - remove!
//                    targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_CLOUD, true);  // todo test - remove!
                }
            }
        }

        if (smaProduct != null) {
            Tile landAbundanceTile = getSourceTile(landAbundanceBand, targetRectangle);
            Tile waterAbundanceTile = getSourceTile(waterAbundanceBand, targetRectangle);
            Tile cloudAbundanceTile = getSourceTile(cloudAbundanceBand, targetRectangle);
            Tile summaryErrorTile = getSourceTile(summaryErrorBand, targetRectangle);
            Tile brr7nTile = getSourceTile(brr7nBand, targetRectangle);
            Tile brr9nTile = getSourceTile(brr9nBand, targetRectangle);
            Tile brr10nTile = getSourceTile(brr10nBand, targetRectangle);
            Tile brr12nTile = getSourceTile(brr12nBand, targetRectangle);

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    computeMixedPixelFlag(x, y, landAbundanceTile, waterAbundanceTile,
                                          cloudAbundanceTile, summaryErrorTile,
                                          brr7nTile, brr9nTile, brr10nTile, brr12nTile, sourceFlagTile, targetTile);
                }
            }
        }

    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private void computeCloudBuffer(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        Rectangle rectangle = targetTile.getRectangle();
        final int LEFT_BORDER = Math.max(x - cloudBufferWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + cloudBufferWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - cloudBufferWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + cloudBufferWidth, rectangle.y + rectangle.height - 1);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                boolean is_already_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_CLOUD);
                boolean is_land = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_LAND);
                if (!is_already_cloud && !is_land && targetTile.getRectangle().contains(i, j)) {
                    targetTile.setSample(i, j, CoastColourCloudClassificationOp.F_CLOUD_BUFFER, true);
                }
            }
        }
    }

    private boolean isNearCoastline(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        Rectangle rectangle = targetTile.getRectangle();
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                final boolean is_coastline = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_COASTLINE);
                if (is_coastline && targetTile.getRectangle().contains(i, j)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        Rectangle rectangle = targetTile.getRectangle();
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        boolean removeCloudFlag = true;
        if (isPixelSurroundedByClouds(x, y, sourceFlagTile, targetTile)) {
            removeCloudFlag = false;
        } else {
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_CLOUD);
                    if (is_cloud && targetTile.getRectangle().contains(i, j) && !isNearCoastline(i, j, sourceFlagTile, targetTile)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            final int sourceSample = sourceFlagTile.getSampleInt(x, y);
            targetTile.setSample(x, y, sourceSample - 1);
            targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_MIXED_PIXEL, true);
            // todo (s.a.)
//            targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_CLOUD, false);
        }
    }

    private boolean isPixelSurroundedByClouds(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        Rectangle rectangle = targetTile.getRectangle();
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        final int pixelsInWindow = (RIGHT_BORDER - LEFT_BORDER + 1) * (BOTTOM_BORDER - TOP_BORDER + 1);
        int cloudPixelCount = 0;
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                boolean is_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_CLOUD);
                if (is_cloud && targetTile.getRectangle().contains(i, j)) {
                    cloudPixelCount++;
                }
            }
        }

        return (cloudPixelCount * 1.0 / pixelsInWindow >= 0.7);  // at least 6 pixel in a 3x3 box
    }

    private void computeCloudShadow(int x, int y, Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile,
                                    Tile altitudeTile, Tile ctpTile, Tile sourceFlagTile, Tile targetTile) {
        float ctp = ctpTile.getSampleFloat(x, y);
        if (ctp > 0) {
            boolean is_cloud = sourceFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD);
            if (is_cloud) {
                GeoCoding geoCoding = l1bProduct.getGeoCoding();
                GeoPos cloudGeoPos = geoCoding.getGeoPos(new PixelPos(x + 0.5f, y + 0.5f), null);

                float cloudHeight = computeHeightFromPressure(ctp);
                GeoPos shadowGeoPos = getCloudShadowPosition(x, y, szaTile, vzaTile, saaTile, vaaTile, altitudeTile,
                                                             cloudHeight, geoCoding, cloudGeoPos);
                if (shadowGeoPos != null) {
                    PixelPos shadowPixelPos = geoCoding.getPixelPos(shadowGeoPos, null);
                    final int shadowPixelX = MathUtils.floorInt(shadowPixelPos.x);
                    final int shadowPixelY = MathUtils.floorInt(shadowPixelPos.y);
                    Rectangle rectangle = targetTile.getRectangle();
                    boolean isAlreadyCloud = isPixelAlreadyMarkedAsCloud(shadowPixelX, shadowPixelY, sourceFlagTile);
                    if (!isAlreadyCloud && rectangle.contains(shadowPixelX, shadowPixelY)) {
                        targetTile.setSample(shadowPixelX, shadowPixelY,
                                             CoastColourCloudClassificationOp.F_CLOUD_SHADOW, true);
                    }
                }
            }
        }
    }

    private boolean isPixelAlreadyMarkedAsCloud(int pixelX, int pixelY, Tile sourceFlagTile) {
        boolean is_already_cloud = false;
        if (sourceFlagTile.getRectangle().contains(pixelX, pixelY)) {
            is_already_cloud = sourceFlagTile.getSampleBit(pixelX, pixelY, CoastColourCloudClassificationOp.F_CLOUD);
        }
        return is_already_cloud;
    }

    private GeoPos getCloudShadowPosition(int x, int y, Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile,
                                          Tile altitudeTile, float cloudHeight, GeoCoding geoCoding, GeoPos geoPos) {
        float sza = szaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float vza = vzaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float saa = saaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float vaa = vaaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        return IdepixCloudShadowOp.getCloudShadow(altitudeTile, geoCoding, sza, saa, vza, vaa, cloudHeight, geoPos);
    }

    private float computeHeightFromPressure(float pressure) {
        return (float) (-8000 * Math.log(pressure / 1013.0f));
    }

    private void computeMixedPixelFlag(int x, int y, Tile landAbundanceTile, Tile waterAbundanceTile,
                                       Tile cloudAbundanceTile, Tile summaryErrorTile,
                                       Tile brr7nTile, Tile brr9nTile,
                                       Tile brr10nTile, Tile brr12nTile,
                                       Tile cloudClassifFlagTile, Tile targetTile) {

        final float landAbundance = landAbundanceTile.getSampleFloat(x, y);
        final float waterAbundance = waterAbundanceTile.getSampleFloat(x, y);
        final float cloudAbundance = cloudAbundanceTile.getSampleFloat(x, y);
        final float summaryError = summaryErrorTile.getSampleFloat(x, y);
        final float brr7n = brr7nTile.getSampleFloat(x, y);
        final float brr9n = brr9nTile.getSampleFloat(x, y);
        final float brr10n = brr10nTile.getSampleFloat(x, y);
        final float brr12n = brr12nTile.getSampleFloat(x, y);
        final float diffb9b7 = brr9n - brr7n;
        final float diffb10b7 = brr10n - brr7n;
        final boolean isLand = cloudClassifFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_LAND);
        final boolean isCloud = cloudClassifFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD);

        final boolean b1 = (landAbundance > 0.002);
        final boolean b2 = (summaryError < 0.0075);
        final boolean b3 = (waterAbundance > 0.2);
        final boolean b4 = (waterAbundance < 0.93);
        final boolean b5 = (landAbundance > 0.4);
        final boolean b6 = (cloudAbundance > 0.045);
        final boolean b7 = (brr7n <= brr12n);
        final boolean b8 = (diffb9b7 > 0.01);
        final boolean b9 = (diffb10b7 > 0.01);
        final boolean b10 = !isLand;
        final boolean b11 = !isCloud;

        final boolean isMixedPixel = (((b1 && b2) && (b3 && b4 && b2)) || (b5 && b6 && b7) || b8 && b9) && b10 && b11;
        targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_MIXED_PIXEL, isMixedPixel);

        // former expression used by AR - currently not used any more
//        final boolean isMixedPixelOld = (((b1 && b2) && (b3 && waterAbundance < 0.9 && b2)) ||
//                b5 && b6 && b7) && b10;
//        targetTile.setSample(x, y, CoastColourCloudClassificationOp.F_MIXED_PIXEL, isMixedPixelOld);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourPostProcessOp.class);
        }
    }
}
