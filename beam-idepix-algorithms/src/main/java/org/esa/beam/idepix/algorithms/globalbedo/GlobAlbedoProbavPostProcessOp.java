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
    //JM&GK 20160212 Todo
    private Band blueBand;
    private Band redBand;
    private Band nirBand;
    private Band swirBand;

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
            //JM&GK 20160212 Todo
            blueBand = probavCloudProduct.getBand("TOA_REFL_BLUE");
            redBand = probavCloudProduct.getBand("TOA_REFL_RED");
            nirBand = probavCloudProduct.getBand("TOA_REFL_NIR");
            swirBand = probavCloudProduct.getBand("TOA_REFL_SWIR");

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

    //JM&GK 20160212 Todo
    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile cloudFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile smFlagTile = getSourceTile(origSmFlagBand, srcRectangle);
        //JM&GK 20160212 Todo
        final Tile blueTile = getSourceTile(blueBand, srcRectangle);
        final Tile redTile = getSourceTile(redBand, srcRectangle);
        final Tile nirTile = getSourceTile(nirBand, srcRectangle);
        final Tile swirTile = getSourceTile(swirBand, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    combineFlags(x, y, cloudFlagTile, targetTile);
                    consolidateFlagging(x, y, smFlagTile, targetTile);
                    boolean isCloud = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                    if (isCloud) {
                    // GK 20151201;
//                    if (isCloud || smFlagTile.getSampleBit(x, y, GlobAlbedoProbavClassificationOp.SM_F_CLOUD)) {
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
                    //JM&GK 20160212 Todo
                    refineHaze(x, y, blueTile, redTile, nirTile, swirTile, targetTile);
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
        // GK 20151201;
        final boolean smCloud = smFlagTile.getSampleBit(x, y, GlobAlbedoProbavClassificationOp.SM_F_CLOUD);
        final boolean safeCloud = idepixCloud || (potentialCloudSnow && (!safeSnowIce && !safeClearWater));
        final boolean safeClearWaterFinal = ((!safeClearLand && !safeSnowIce  && !safeCloud && !smCloud) && idepixWater) || safeClearWater;
        final boolean safeClearLandFinal = ((!safeSnowIce  && !idepixCloud && !smCloud && !safeClearWaterFinal) && idepixLand) || safeClearLand;
        final boolean safeCloudFinal = safeCloud && (!safeClearLandFinal && !safeClearWaterFinal);


//        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, safeCloud);
//        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, safeClearLand);
//        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, safeClearWater);
        // GK 20151201;
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, safeClearLandFinal);
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, safeClearWaterFinal);
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, safeCloudFinal);
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

    //JM&GK 20160212 Todo
    private void refineHaze(int x, int y,
                                            Tile blueTile, Tile redTile, Tile nirTile, Tile swirTile,
                                            Tile targetTile) {

        final double blue = blueTile.getSampleDouble(x, y);
        final double red = redTile.getSampleDouble(x, y);
        final double nir = nirTile.getSampleDouble(x, y);
        final double swir = swirTile.getSampleDouble(x, y);
        double [] tcValue = new double[4];
        double [] tcSlopeValue = new double[2];

        tcValue[0] = 0.332* blue+ 0.603* red + 0.676* nir + 0.263* swir;
        tcValue[1] =  0.283* blue+ -0.66* red + 0.577* nir + 0.388* swir;
        tcValue[2] =  0.9* blue+ 0.428* red + 0.0759* nir + -0.041* swir;
        tcValue[3] =  0.016* blue+ 0.428* red + -0.452* nir + 0.882* swir;

        tcSlopeValue[0] = (tcValue[3]- tcValue[2]);
        tcSlopeValue[1] = (tcValue[2]- tcValue[1]);

        final boolean haze = tcSlopeValue[0] < -0.07 && !(tcSlopeValue[1] < -0.01);
        final boolean isCloudBuffer = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD_BUFFER);
        final boolean isLand = targetTile.getSampleBit(x, y, IdepixConstants.F_LAND);

        if (haze && (!isCloudBuffer || isLand)) {
            // set new haze mask todo
            targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, true);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoProbavPostProcessOp.class);
        }
    }
}
