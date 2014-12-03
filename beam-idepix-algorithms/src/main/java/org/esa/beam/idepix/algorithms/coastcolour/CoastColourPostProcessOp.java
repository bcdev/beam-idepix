package org.esa.beam.idepix.algorithms.coastcolour;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGeoCoding;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.CloudBuffer;
import org.esa.beam.idepix.algorithms.CloudShadowFronts;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.Rectangle;
import java.util.HashMap;

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
                  version = "2.1",
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

        HashMap<String, Object> waterParameters = new HashMap<>();
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
        if (l1bProduct.getProductType().startsWith("MER_F")) {
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
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, extendedRectangle);
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
                            CloudBuffer.computeSimpleCloudBuffer(x, y, targetTile, targetTile, cloudBufferWidth,
                                                                 CoastColourClassificationOp.F_CLOUD,
                                                                 CoastColourClassificationOp.F_CLOUD_BUFFER);
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

        final CloudShadowFronts cloudShadowFronts = new CloudShadowFronts(
                CoastColourClassificationOp.F_CLOUD,
                CoastColourClassificationOp.F_CLOUD_SHADOW,
                geoCoding,
                targetTile,
                extendedRectangle,
                szaTile, saaTile, ctpTile, null) {

            @Override
            protected boolean isCloudForShadow(int x, int y) {
                final boolean is_cloud_current;
                if (!targetRectangle.contains(x, y)) {
                    is_cloud_current = sourceFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                } else {
                    is_cloud_current = targetTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD);
                }
                if (is_cloud_current) {
                    final boolean is_mixed_current = sourceFlagTile.getSampleBit(x, y, CoastColourClassificationOp.F_MIXED_PIXEL);
                    final boolean isNearCoastline = isNearCoastline(x, y, sourceFlagTile, waterFractionTile, extendedRectangle);
                    if (!is_mixed_current && !isNearCoastline) {
                        return true;
                    }
                }
                return false;
            }
        };
        cloudShadowFronts.computeCloudShadow();
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
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
        if (CloudShadowFronts.isPixelSurrounded(x, y, sourceFlagTile, rectangle, CoastColourClassificationOp.F_CLOUD)) {
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

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourPostProcessOp.class);
        }
    }
}
