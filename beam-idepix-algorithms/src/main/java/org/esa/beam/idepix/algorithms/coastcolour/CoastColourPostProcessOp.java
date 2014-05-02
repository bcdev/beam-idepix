package org.esa.beam.idepix.algorithms.coastcolour;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.Bresenham;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

/**
 * Operator used to consolidate cloud flag for CoastColour:
 * - cloud shadow
 * - cloud buffer
 * - mixed pixel
 * - coastline refinement
 *
 * @author Marco
 * @since Idepix 1.3.1
 */
@OperatorMetadata(alias = "idepix.coastcolour.postprocess",
                  version = "2.1-SNAPSHOT",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Refines the cloud classification of Meris.CoastColourCloudClassification operator.")
public class CoastColourPostProcessOp extends MerisBasisOp {

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

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


    private static final int MEAN_EARTH_RADIUS = 6372000;

    private Band origCloudFlagBand;
    private TiePointGrid szaTPG;
    private TiePointGrid saaTPG;
    private Band ctpBand;

    private Band landAbundanceBand;
    private Band waterAbundanceBand;
    private Band cloudAbundanceBand;
    private Band summaryErrorBand;
    private Band brr7nBand;
    private Band brr9nBand;
    private Band brr10nBand;
    private Band brr12nBand;
    private Band landWaterBand;

    private RectangleExtender rectCalculator;

    private GeoCoding geoCoding;


    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = createCompatibleProduct(merisCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");

        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", 50);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, l1bProduct);
        landWaterBand = waterMaskProduct.getBand("land_water_fraction");

        origCloudFlagBand = merisCloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS);
        szaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        saaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        ctpBand = ctpProduct.getBand("cloud_top_press");
        int shadowWidth;
        int shadowHeight;
        if (l1bProduct.getProductType().equals(EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME)) {
            shadowWidth = 64;
            shadowHeight = 64;
        } else {
            shadowWidth = 16;
            shadowHeight = 16;
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
        geoCoding = l1bProduct.getGeoCoding();

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               shadowWidth, shadowHeight
        );


        ProductUtils.copyBand(CloudClassificationOp.CLOUD_FLAGS, merisCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        Tile sourceFlagTile = getSourceTile(origCloudFlagBand, extendedRectangle);
        Tile szaTile = getSourceTile(szaTPG, extendedRectangle);
        Tile saaTile = getSourceTile(saaTPG, extendedRectangle);
        Tile ctpTile = getSourceTile(ctpBand, extendedRectangle);
        final Tile waterFractionTile = getSourceTile(landWaterBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {
                if (targetRectangle.contains(x, y)) {
                    if (targetRectangle.contains(x, y)) {
                        boolean isCloud = sourceFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                        combineFlags(x, y, sourceFlagTile, targetTile);

                        if (isNearCoastline(x, y, sourceFlagTile, waterFractionTile, extendedRectangle)) {
                            targetTile.setSample(x, y, CoastColourClassificationOp.F_COASTLINE, true);
                            refineSnowIceFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
                            if (isCloud) {
                                refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, waterFractionTile, targetTile, extendedRectangle);
                            }
                        }
                        boolean isCloudAfterRefinement = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                        if (isCloudAfterRefinement) {
                            computeCloudBuffer(x, y, sourceFlagTile, targetTile);
                        }
                    }
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

        // compute cloud shadow as proposed by Michael (as in 'Fronts' project)
        computeCloudShadowMichael(targetTile, extendedRectangle, sourceFlagTile, waterFractionTile, szaTile, saaTile, ctpTile);

    }

    private void computeCloudShadowMichael(Tile targetTile, Rectangle extendedRectangle, Tile sourceFlagTile,
                                           Tile waterFractionTile, Tile szaTile, Tile saaTile,
                                           Tile ctpTile) {
        Rectangle targetRectangle = targetTile.getRectangle();

        final int h = targetRectangle.height;
        final int w = targetRectangle.width;
        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;

        boolean[][] isCloudShadow = new boolean[w][h];
        for (int y = y0; y < y0 + h; y++) {
            checkForCancellation();
            for (int x = x0; x < x0 + w; x++) {
                final boolean isCloud = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                if (!isCloud) {
                    isCloudShadow[x - x0][y - y0] = getCloudShadow(x, y, sourceFlagTile, waterFractionTile, ctpTile, szaTile, saaTile,
                                                                   extendedRectangle);
                    targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD_SHADOW, isCloudShadow[x - x0][y - y0]);
                }
            }
        }

        // first 'post-correction': fill gaps surrounded by other cloud or cloud shadow pixels
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                final boolean is_cloud = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                if (!is_cloud) {
                    final boolean pixelSurroundedByClouds = isPixelSurrounded(x, y, targetTile, targetRectangle, CoastColourClassificationOp.F_CLOUD);
                    final boolean pixelSurroundedByCloudShadow = isPixelSurroundedByCloudShadow(x, y, targetRectangle, isCloudShadow);

                    if (pixelSurroundedByClouds || pixelSurroundedByCloudShadow) {
                        targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD_SHADOW, true);
                    }
                }
            }
        }

        // second post-correction, called 'belt' (why??): flag a pixel as cloud shadow if neighbour pixel is shadow
        for (int y = y0; y < y0 + h; y++) {
            checkForCancellation();
            for (int x = x0; x < x0 + w; x++) {
                final boolean is_cloud = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                if (!is_cloud) {
                    performCloudShadowBeltCorrection(x, y, targetTile, isCloudShadow);
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
                boolean is_already_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourClassificationOp.F_CLOUD);
                if (!is_already_cloud && rectangle.contains(i, j)) {
                    targetTile.setSample(i, j, CoastColourClassificationOp.F_CLOUD_BUFFER, true);
                }
            }
        }
    }

    private boolean isNearCoastline(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);

        if (!(l1bProduct.getGeoCoding() instanceof TiePointGeoCoding) &&
                !(l1bProduct.getGeoCoding() instanceof CrsGeoCoding)) {
            final int waterFractionCenter = waterFractionTile.getSampleInt(x, y);
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    if (waterFractionTile.getSampleInt(i, j) != waterFractionCenter) {
                        return true;
                    }
                }
            }
        } else {
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    final boolean isAlreadyCoastline = sourceFlagTile.getSampleBit(i, j, CoastColourClassificationOp.F_COASTLINE);
                    if (isAlreadyCoastline) {
                        return true;
                    }
                }
            }
        }


        return false;
    }

    private boolean refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Tile targetTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        boolean removeCloudFlag = true;
        if (isPixelSurrounded(x, y, sourceFlagTile, rectangle, CoastColourClassificationOp.F_CLOUD)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourClassificationOp.F_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, sourceFlagTile, waterFractionTile, rectangle)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD, false);
            targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD_SURE, false);
            targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD_AMBIGUOUS, false);
            boolean is_land = sourceFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_LAND);
            targetTile.setSample(x, y, CoastColourClassificationOp.F_MIXED_PIXEL, !is_land);
        }
        // return whether this is still a cloud
        return !removeCloudFlag;
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_SNOW_ICE);
        if (isSnowIce) {
            targetTile.setSample(x, y, CoastColourClassificationOp.F_SNOW_ICE, false);
        }
    }

    private boolean isPixelSurrounded(int x, int y, Tile sourceFlagTile, Rectangle targetRectangle, int pixelFlag) {
        // check if pixel is surrounded by other pixels flagged as 'pixelFlag'
        int surroundingPixelCount = 0;
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (sourceFlagTile.getRectangle().contains(i, j)) {
                    boolean is_flagged = sourceFlagTile.getSampleBit(i, j, pixelFlag);
                    if (is_flagged && targetRectangle.contains(i, j)) {
                        surroundingPixelCount++;
                    }
                }
            }
        }

        return (surroundingPixelCount * 1.0 / 9 >= 0.7);  // at least 6 pixel in a 3x3 box
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
        final boolean isLand = cloudClassifFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_LAND);
        final boolean isCloud = cloudClassifFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
        final boolean isAlreadyMixedPixel = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_MIXED_PIXEL);

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

        final boolean isMixedPixel = isAlreadyMixedPixel ||
                (((b1 && b2) && (b3 && b4 && b2)) || (b5 && b6 && b7) || b8 && b9) && b10 && b11;
        targetTile.setSample(x, y, CoastColourClassificationOp.F_MIXED_PIXEL, isMixedPixel && !isLand);
    }

    private boolean isPixelSurroundedByCloudShadow(int x, int y, Rectangle targetRectangle, boolean[][] isCloudShadow) {
        // check if pixel is surrounded by other cloud shadow pixels
        int surroundingPixelCount = 0;
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (targetRectangle.contains(i, j)) {
                    if (isCloudShadow[i - targetRectangle.x][j - targetRectangle.y]) {
                        surroundingPixelCount++;
                    }
                }
            }
        }
        return surroundingPixelCount * 1.0 / 9 >= 0.7; // at least 6 pixel in a 3x3 box
    }

    private void performCloudShadowBeltCorrection(int x, int y, Tile targetTile, boolean[][] isCloudShadow) {
        // flag a pixel as cloud shadow if neighbour pixel is shadow
        final Rectangle rectangle = targetTile.getRectangle();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (rectangle.contains(i, j) && isCloudShadow[i - rectangle.x][j - rectangle.y]) {
                    targetTile.setSample(x, y, CoastColourClassificationOp.F_CLOUD_SHADOW, true);
                    break;
                }
            }
        }
    }

    // used by Michael's aproach
    private float getCloudBase(int x, int y, Tile ctpTile, Tile cloudTile, Rectangle sourceRectangle) {
        float cb;

        // computes the cloud base in metres
        int cloudFlag = cloudTile.getSampleInt(x, y);
        if (!BitSetter.isFlagSet(cloudFlag, CoastColourClassificationOp.F_CLOUD) || ctpTile == null) {
            cb = 0.0f;
        } else {
            cb = computeHeightFromPressure(ctpTile.getSampleFloat(x, y));
            for (int i = x - 1; i <= x + 1; i++) {
                for (int j = y - 1; j <= y + 1; j++) {
                    PixelPos pixelPos = new PixelPos(i, j);
                    if (sourceRectangle.contains(pixelPos)) {
                        final float neighbourCloudBase = computeHeightFromPressure(ctpTile.getSampleFloat(i, j));
                        cb = Math.min(cb, neighbourCloudBase);
                    }
                }
            }
        }
        return cb;
    }

    // used by MP's aproach
    private boolean getCloudShadow(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Tile ctpTile, Tile szaTile, Tile saaTile,
                                   Rectangle sourceRectangle) {
        final double sza = szaTile.getSampleDouble(x, y);
        final double saa = saaTile.getSampleDouble(x, y);

        final GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x, y), null);

        final double angle = IdepixUtils.convertGeophysicalToMathematicalAngle(saa);
        PixelPos borderPixel = Bresenham.findBorderPixel(x, y, sourceRectangle, angle);
        List<PixelPos> pathPixels = Bresenham.getPathPixels(x, y, (int) borderPixel.getX(), (int) borderPixel.getY(), sourceRectangle);

        for (PixelPos pathPixel : pathPixels) {

            final int xCurrent = (int) pathPixel.getX();
            final int yCurrent = (int) pathPixel.getY();

            if (sourceRectangle.contains(xCurrent, yCurrent)) {
                final boolean is_cloud_current = sourceFlagTile.getSampleBit(xCurrent, yCurrent, CoastColourClassificationOp.F_CLOUD);
                final boolean is_mixed_current = sourceFlagTile.getSampleBit(xCurrent, yCurrent, CoastColourClassificationOp.F_MIXED_PIXEL);
                final boolean isNearCoastline = isNearCoastline(xCurrent, yCurrent, sourceFlagTile, waterFractionTile, sourceRectangle);
                if (is_cloud_current && !is_mixed_current && !isNearCoastline) {
                    final GeoPos geoPosCurrent = geoCoding.getGeoPos(new PixelPos(xCurrent, yCurrent), null);
                    final double dist = computeDistance(geoPos, geoPosCurrent);
                    final double sunHeight = dist * Math.tan(MathUtils.DTOR * (90.0 - sza));
                    final float cloudHeight = computeHeightFromPressure(ctpTile.getSampleFloat(xCurrent, yCurrent));
                    float cloudBase = getCloudBase(xCurrent, yCurrent, ctpTile, sourceFlagTile, sourceRectangle);
                    // cloud base should be at least at 300m, cloud thickness should also be at least 300m (OD, 2012/08/02)
                    cloudBase = (float) Math.min(cloudHeight - 300.0, cloudBase);
                    cloudBase = (float) Math.max(300.0, cloudBase);
                    if (sunHeight >= cloudBase + 300.0 && sunHeight <= cloudHeight + 300) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    private double computeDistance(GeoPos geoPos1, GeoPos geoPos2) {
        final float lon1 = geoPos1.getLon();
        final float lon2 = geoPos2.getLon();
        final float lat1 = geoPos1.getLat();
        final float lat2 = geoPos2.getLat();

        final double cosLat1 = Math.cos(MathUtils.DTOR * lat1);
        final double cosLat2 = Math.cos(MathUtils.DTOR * lat2);
        final double sinLat1 = Math.sin(MathUtils.DTOR * lat1);
        final double sinLat2 = Math.sin(MathUtils.DTOR * lat2);

        final double delta = MathUtils.DTOR * (lon2 - lon1);
        final double cosDelta = Math.cos(delta);
        final double sinDelta = Math.sin(delta);

        final double y = Math.sqrt(Math.pow(cosLat2 * sinDelta, 2) + Math.pow(cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDelta, 2));
        final double x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDelta;

        final double ad = Math.atan2(y, x);

        return ad * MEAN_EARTH_RADIUS;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourPostProcessOp.class);
        }
    }
}
