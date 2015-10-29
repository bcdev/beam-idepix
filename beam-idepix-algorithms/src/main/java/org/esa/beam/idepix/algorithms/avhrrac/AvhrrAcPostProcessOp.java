package org.esa.beam.idepix.algorithms.avhrrac;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.algorithms.CloudBuffer;
import org.esa.beam.idepix.algorithms.CloudShadowFronts;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;

/**
 * Operator used to consolidate cloud flag for AVHRR AC:
 * - coastline refinement
 * - cloud buffer (LC algo as default)
 * - cloud shadow (from Fronts)
 *
 * @author olafd
 * @since Idepix 2.1
 */
@OperatorMetadata(alias = "idepix.avhrrac.postprocess",
                  version = "2.2",
                  internal = true,
                  authors = "Marco Peters, Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2015 by Brockmann Consult",
                  description = "Refines the Landsat cloud classification.")
public class AvhrrAcPostProcessOp extends Operator {
    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    //    @Parameter(defaultValue = "true",
//            label = " Compute cloud shadow",
//            description = " Compute cloud shadow with latest 'fronts' algorithm")
    private boolean computeCloudShadow = false;   // todo: we have no info at all for this (pressure, height, temperature)

    @Parameter(defaultValue = "true",
               label = " Refine pixel classification near coastlines",
               description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "avhrrCloud")
    private Product avhrrCloudProduct;
    @SourceProduct(alias = "waterMask", optional=true)
    private Product waterMaskProduct;

    private Band waterFractionBand;
    private Band origCloudFlagBand;
    private Band rt3Band;
    private Band bt4Band;
    private Band refl1Band;
    private Band refl2Band;
    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        if (!computeCloudBuffer && !computeCloudShadow && !refineClassificationNearCoastlines) {
            setTargetProduct(avhrrCloudProduct);
        } else {
            Product postProcessedCloudProduct = createTargetProduct(avhrrCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");

            waterFractionBand = waterMaskProduct.getBand("land_water_fraction");

            geoCoding = l1bProduct.getGeoCoding();

            origCloudFlagBand = avhrrCloudProduct.getBand(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS);
            rt3Band = avhrrCloudProduct.getBand("rt_3");
            bt4Band = avhrrCloudProduct.getBand("bt_4");
            refl1Band = avhrrCloudProduct.getBand("refl_1");
            refl2Band = avhrrCloudProduct.getBand("refl_2");


            int extendedWidth = 64;
            int extendedHeight = 64; // todo: what do we need?

            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                                 l1bProduct.getSceneRasterHeight()),
                                                   extendedWidth, extendedHeight
            );


            ProductUtils.copyBand(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS, avhrrCloudProduct, postProcessedCloudProduct, false);
            setTargetProduct(postProcessedCloudProduct);
        }
    }

    private Product createTargetProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        return targetProduct;
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile waterFractionTile = getSourceTile(waterFractionBand, srcRectangle);

        final Tile rt3Tile = getSourceTile(rt3Band, srcRectangle);
        final Tile bt4Tile = getSourceTile(bt4Band, srcRectangle);
        final Tile refl1Tile = getSourceTile(refl1Band, srcRectangle);
        final Tile refl2Tile = getSourceTile(refl2Band, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, AvhrrAcConstants.F_CLOUD);
                    boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, AvhrrAcConstants.F_SNOW_ICE);
                    combineFlags(x, y, sourceFlagTile, targetTile);

                    // snow/ice filter refinement for AVHRR (GK 20150922):
                    if (isSnowIce) {
                        refineSnowIceCloudFlagging(x, y, rt3Tile, bt4Tile, refl1Tile, refl2Tile, targetTile);
                    }

                    if (refineClassificationNearCoastlines) {
                        if (isNearCoastline(x, y, waterFractionTile, srcRectangle)) {
                            targetTile.setSample(x, y, AvhrrAcConstants.F_COASTLINE, true);
                            refineSnowIceFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
                            if (isCloud) {
                                refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, waterFractionTile, targetTile, srcRectangle);
                            }
                        }
                    }
                    boolean isCloudAfterRefinement = targetTile.getSampleBit(x, y, AvhrrAcConstants.F_CLOUD);
                    if (isCloudAfterRefinement) {
                        targetTile.setSample(x, y, AvhrrAcConstants.F_SNOW_ICE, false);
                        if ((computeCloudBuffer)) {
                            CloudBuffer.computeSimpleCloudBuffer(x, y,
                                                                 targetTile, targetTile,
                                                                 cloudBufferWidth,
                                                                 AvhrrAcConstants.F_CLOUD,
                                                                 AvhrrAcConstants.F_CLOUD_BUFFER);
                        }
                    }

                }
            }
        }

        if (computeCloudShadow) {
            // todo: we need something modified, as we have no CTP
//            CloudShadowFronts cloudShadowFronts = new CloudShadowFronts(
//                    geoCoding,
//                    srcRectangle,
//                    targetRectangle,
//                    szaTile, saaTile, ctpTile, altTile) {
//
//
//                @Override
//                protected boolean isCloudForShadow(int x, int y) {
//                    final boolean is_cloud_current;
//                    if (!targetTile.getRectangle().contains(x, y)) {
//                        is_cloud_current = sourceFlagTile.getSampleBit(x, y, Landsat8Constants.F_CLOUD);
//                    } else {
//                        is_cloud_current = targetTile.getSampleBit(x, y, Landsat8Constants.F_CLOUD);
//                    }
//                    if (is_cloud_current) {
//                        final boolean isNearCoastline = isNearCoastline(x, y, waterFractionTile, srcRectangle);
//                        if (!isNearCoastline) {
//                            return true;
//                        }
//                    }
//                    return false;
//                }
//
//                @Override
//                protected boolean isCloudFree(int x, int y) {
//                    return !sourceFlagTile.getSampleBit(x, y, Landsat8Constants.F_CLOUD);
//                }
//
//                @Override
//                protected boolean isSurroundedByCloud(int x, int y) {
//                    return isPixelSurrounded(x, y, sourceFlagTile, Landsat8Constants.F_CLOUD);
//                }
//
//                @Override
//                protected void setCloudShadow(int x, int y) {
//                    targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SHADOW, true);
//                }
//            };
//            cloudShadowFronts.computeCloudShadow();
        }
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private boolean isCoastlinePixel(int x, int y, Tile waterFractionTile) {
        boolean isCoastline = false;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            final int waterFraction = waterFractionTile.getSampleInt(x, y);
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                isCoastline = waterFraction < 100 && waterFraction > 0;
            }
        }
        return isCoastline;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private boolean isNearCoastline(int x, int y, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        final int waterFractionCenter = waterFractionTile.getSampleInt(x, y);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (rectangle.contains(i, j)) {
                    if (!(l1bProduct.getGeoCoding() instanceof TiePointGeoCoding) &&
                            !(l1bProduct.getGeoCoding() instanceof CrsGeoCoding)) {
                        if (waterFractionTile.getSampleInt(i, j) != waterFractionCenter) {
                            return true;
                        }
                    } else {
                        if (isCoastlinePixel(i, j, waterFractionTile)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Tile targetTile, Rectangle srcRectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, srcRectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, srcRectangle.x + srcRectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, srcRectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, srcRectangle.y + srcRectangle.height - 1);
        boolean removeCloudFlag = true;
        if (CloudShadowFronts.isPixelSurrounded(x, y, sourceFlagTile, AvhrrAcConstants.F_CLOUD)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, AvhrrAcConstants.F_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, waterFractionTile, srcRectangle)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD, false);
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD_SURE, false);
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD_AMBIGUOUS, false);
        }
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, AvhrrAcConstants.F_SNOW_ICE);
        if (isSnowIce) {
            targetTile.setSample(x, y, AvhrrAcConstants.F_SNOW_ICE, false);
        }
    }

    private void refineSnowIceCloudFlagging(int x, int y,
                                            Tile rt3Tile, Tile bt4Tile, Tile refl1Tile, Tile refl2Tile,
                                            Tile targetTile) {

        final double rt3 = rt3Tile.getSampleDouble(x, y);
        final double bt4 = bt4Tile.getSampleDouble(x, y);
        final double refl1 = refl1Tile.getSampleDouble(x, y);
        final double refl2 = refl2Tile.getSampleDouble(x, y);
        final double ratio21 = refl2/refl1;

        final boolean firstCrit = rt3 > 0.08;
        final boolean secondCrit = (-40.15 < bt4 && bt4 < 1.35) &&
                refl1 > 0.25 && (0.85 < ratio21 && ratio21 < 1.15) && rt3 < 0.02;

        if (firstCrit || !secondCrit) {
            // reset snow_ice to cloud todo: check with a test product from GK if this makes sense at all
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD, true);
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD_SURE, true);
            targetTile.setSample(x, y, AvhrrAcConstants.F_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, AvhrrAcConstants.F_SNOW_ICE, false);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcPostProcessOp.class);
        }
    }
}
