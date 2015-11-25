package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.CloudBuffer;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;

/**
 * Operator used to consolidate cloud flag for PROBA-V:
 * - cloud buffer (LC algo as default)
 *
 * @author olafd
 * @since Idepix 2.2
 */
@OperatorMetadata(alias = "idepix.probav.postprocess",
        version = "2.2",
        internal = true,
        authors = "Marco Peters, Marco Zuehlke, Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "Refines the Proba-V pixel classification.")
public class GlobAlbedoProbavPostProcessOp extends Operator {

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    //    @Parameter(defaultValue = "true",
//            label = " Compute cloud shadow",
//            description = " Compute cloud shadow with latest 'fronts' algorithm")
    private boolean computeCloudShadow = false;   // todo: we have no info at all for this (pressure, height, temperature)

    //    @Parameter(defaultValue = "true",
//               label = " Refine pixel classification near coastlines",
//               description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines = false; // not yet required

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "probavCloud")
    private Product probavCloudProduct;

    private Band origCloudFlagBand;
    private Band origSmFlagBand;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        if (!computeCloudBuffer && !computeCloudShadow && !refineClassificationNearCoastlines) {
            setTargetProduct(probavCloudProduct);
        } else {
            Product postProcessedCloudProduct = createTargetProduct(probavCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");

            origCloudFlagBand = probavCloudProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
            origSmFlagBand = l1bProduct.getBand("SM_FLAGS");

            int extendedWidth = 64;
            int extendedHeight = 64; // todo: what do we need?

            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                                 l1bProduct.getSceneRasterHeight()),
                                                   extendedWidth, extendedHeight
            );

            ProductUtils.copyBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, probavCloudProduct, postProcessedCloudProduct, false);
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

        final Tile cloudFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile smFlagTile = getSourceTile(origSmFlagBand, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = cloudFlagTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                    combineFlags(x, y, cloudFlagTile, targetTile);

                    consolidateFlagging(x, y, smFlagTile, targetTile);
                    isCloud = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                    if (isCloud) {
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
                        if ((computeCloudBuffer)) {
                            CloudBuffer.computeSimpleCloudBuffer(x, y,
                                                                 targetTile, targetTile,
                                                                 cloudBufferWidth,
                                                                 IdepixConstants.F_CLOUD,
                                                                 IdepixConstants.F_CLOUD_BUFFER);
                        }
                    }

                    consolidateFlaggingWithCloudBuffer(x, y, smFlagTile, targetTile);
                }
            }
        }
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private void consolidateFlagging(int x, int y, Tile smFlagTile, Tile targetTile) {
        final boolean smClear = smFlagTile.getSampleBit(x, y, GlobAlbedoProbavClassificationOp.SM_F_CLEAR);
        final boolean idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.F_LAND);
        final boolean idepixClearLand = targetTile.getSampleBit(x, y, IdepixConstants.F_CLEAR_LAND);
        final boolean idepixWater = targetTile.getSampleBit(x, y, IdepixConstants.F_WATER);
        final boolean idepixClearWater = targetTile.getSampleBit(x, y, IdepixConstants.F_CLEAR_WATER);
        final boolean idepixClearSnow = targetTile.getSampleBit(x, y, IdepixConstants.F_CLEAR_SNOW);
        final boolean idepixCloud = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);

        final boolean safeClearLand = smClear && idepixLand && idepixClearLand && !idepixClearSnow;
        final boolean safeClearWater = smClear && idepixWater && idepixClearWater && !idepixClearSnow;
        final boolean potentialCloudSnow = !safeClearLand && idepixLand;
        final boolean safeSnowIce = potentialCloudSnow && idepixClearSnow;

        final boolean safeCloud = idepixCloud && ((potentialCloudSnow && !safeSnowIce) || !safeClearWater);

        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, safeCloud);
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, safeClearLand);
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, safeClearWater);
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, safeSnowIce);
    }

    private void consolidateFlaggingWithCloudBuffer(int x, int y, Tile smFlagTile, Tile targetTile) {
        final boolean isCloudBuffer = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD_BUFFER);
        if (isCloudBuffer) {
            targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, false);
            targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, false);
            targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
        }
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoProbavPostProcessOp.class);
        }
    }
}
