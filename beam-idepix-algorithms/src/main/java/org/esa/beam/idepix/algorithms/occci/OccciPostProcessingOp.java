package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;
import java.util.Map;

/**
 * OC-CCI post processing operator, operating on tiles:
 * - cloud buffer
 * - ...
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification.post",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "OC-CCI post processing operator.",
                  internal = true)
public class OccciPostProcessingOp extends BasisOp {

    @SourceProduct(alias = "classif", description = "MODIS/SeaWiFS pixel classification product")
    private Product classifProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();

        rectCalculator = new RectangleExtender(new Rectangle(classifProduct.getSceneRasterWidth(),
                                                             classifProduct.getSceneRasterHeight()),
                                               cloudBufferWidth, cloudBufferWidth);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Band classifFlagSourceBand = classifProduct.getBand(Constants.CLASSIF_BAND_NAME);
        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);
        Tile classifFlagSourceTile = getSourceTile(classifFlagSourceBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = classifFlagSourceTile.getSampleBit(x, y, Constants.F_CLOUD);
                    combineFlags(x, y, classifFlagSourceTile, targetTile);
                    if (isCloud) {
                        computeCloudBuffer(x, y, classifFlagSourceTile, targetTile);
                    }
                }
            }
        }
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(classifProduct, classifProduct.getName(), classifProduct.getProductType());
        ProductUtils.copyBand(Constants.CLASSIF_BAND_NAME, classifProduct, targetProduct, false);
        ProductUtils.copyFlagCodings(classifProduct, targetProduct);
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
