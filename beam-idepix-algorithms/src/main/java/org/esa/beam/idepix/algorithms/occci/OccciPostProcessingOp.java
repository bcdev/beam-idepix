package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGeoCoding;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
//import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;

/**
 * OC-CCI post processing operator, operating on tiles:
 * - cloud buffer
 * - ...
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification.post",
                  version = "2.2",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "OC-CCI post processing operator.",
                  internal = true)
public class OccciPostProcessingOp extends BasisOp {

    @SourceProduct(alias = "refl", description = "MODIS/SeaWiFS L1b reflectance product")
    private Product reflProduct;

    @SourceProduct(alias = "classif", description = "MODIS/SeaWiFS pixel classification product")
    private Product classifProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private RectangleExtender rectCalculator;

    private Band landWaterBand;

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();

        rectCalculator = new RectangleExtender(new Rectangle(classifProduct.getSceneRasterWidth(),
                                                             classifProduct.getSceneRasterHeight()),
                                               cloudBufferWidth, cloudBufferWidth
        );

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Band classifFlagSourceBand = classifProduct.getBand(Constants.CLASSIF_BAND_NAME);
        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);
        Tile classifFlagSourceTile = getSourceTile(classifFlagSourceBand, extendedRectangle);
        final Tile waterFractionTile = getSourceTile(landWaterBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = classifFlagSourceTile.getSampleBit(x, y, Constants.F_CLOUD);
                    combineFlags(x, y, classifFlagSourceTile, targetTile);

                    if (!(classifProduct.getGeoCoding() instanceof TiePointGeoCoding) &&
                            !(classifProduct.getGeoCoding() instanceof CrsGeoCoding)) {
                        // in this case, coastline could not be determined per pixel earlier
                        if (isCoastline(x, y, classifFlagSourceTile, extendedRectangle)) {
                            targetTile.setSample(x, y, Constants.F_COASTLINE, true);
                        }
                    }

                    if (isNearCoastline(x, y, targetTile, waterFractionTile, extendedRectangle)) {
                        refineSnowIceFlaggingForCoastlines(x, y, classifFlagSourceTile, targetTile);
                        if (isCloud) {
                            refineCloudFlaggingForCoastlines(x, y, classifFlagSourceTile, waterFractionTile, targetTile, extendedRectangle);
                        }
                    }

                    if (isCloud) {
                        computeCloudBuffer(x, y, classifFlagSourceTile, targetTile);
                    }
                }
            }
        }
    }

    private boolean isCoastline(int x, int y, Tile sourceFlagTile, Rectangle rectangle) {
        // idea:
        // - consider 3x3 box
        // - consider the 8 pixels surrounding center
        // - assume center pixel as coastline if it is land and number of surrounding land/water pixels is
        //   almost the same: either 3/5, 4/4, or 5/3
        // --> very simple approach, works fairly well except for weird land edges
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);

        final boolean isLandCenter = sourceFlagTile.getSampleBit(x, y, Constants.F_LAND);
        if (isLandCenter) {
            int landCount = 0;
            int count = 0;
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    final boolean isCenter = (i == x && j == y);
                    final boolean isLand = sourceFlagTile.getSampleBit(i, j, Constants.F_LAND);
                    if (!isCenter) {
                        count++;
                    }
                    if (isLand && !isCenter) {
                        landCount++;
                    }
                }
            }
            // also consider reduced boxes at product edge
            return (count >= 4 && landCount >= Math.max(count-6, 1) && landCount <= count - 3);
        }

        return false;
    }

    private boolean isNearCoastline(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);

        if (!(classifProduct.getGeoCoding() instanceof TiePointGeoCoding) &&
                !(classifProduct.getGeoCoding() instanceof CrsGeoCoding)) {
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
                    final boolean isAlreadyCoastline = sourceFlagTile.getSampleBit(i, j, Constants.F_COASTLINE);
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
        if (isPixelSurrounded(x, y, sourceFlagTile, rectangle, Constants.F_CLOUD)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, Constants.F_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, sourceFlagTile, waterFractionTile, rectangle)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, Constants.F_CLOUD, false);
            targetTile.setSample(x, y, Constants.F_CLOUD_SURE, false);
            targetTile.setSample(x, y, Constants.F_CLOUD_AMBIGUOUS, false);
            boolean is_land = sourceFlagTile.getSampleBit(x, y, Constants.F_LAND);
            targetTile.setSample(x, y, Constants.F_MIXED_PIXEL, !is_land);
        }
        // return whether this is still a cloud
        return !removeCloudFlag;
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, Constants.F_SNOW_ICE);
        if (isSnowIce) {
            targetTile.setSample(x, y, Constants.F_SNOW_ICE, false);
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

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(classifProduct, classifProduct.getName(), classifProduct.getProductType());
        ProductUtils.copyBand(Constants.CLASSIF_BAND_NAME, classifProduct, targetProduct, false);
        ProductUtils.copyFlagCodings(classifProduct, targetProduct);
//        ProductUtils.copyTiePointGrids(reflProduct, targetProduct);      todo: check if needed here
//        targetProduct.setGeoCoding(reflProduct.getGeoCoding());
        ProductUtils.copyGeoCoding(reflProduct, targetProduct);

        OccciUtils.setupOccciClassifBitmask(targetProduct);
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
                boolean is_already_cloud = sourceFlagTile.getSampleBit(i, j, Constants.F_CLOUD);
                boolean is_land = sourceFlagTile.getSampleBit(i, j, Constants.F_LAND);
                if (!is_already_cloud && !is_land && rectangle.contains(i, j)) {
                    targetTile.setSample(i, j, Constants.F_CLOUD_BUFFER, true);
                }
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OccciPostProcessingOp.class);
        }
    }

}
