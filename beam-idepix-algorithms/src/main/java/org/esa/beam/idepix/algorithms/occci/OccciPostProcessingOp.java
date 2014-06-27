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

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        final Band cloudFlagBand = targetProduct.getBand(Constants.CLASSIF_BAND_NAME);
        final Band cloudFlagSourceBand = classifProduct.getBand(Constants.CLASSIF_BAND_NAME);
        final MultiLevelImage classifImage = classifProduct.getBand(Constants.CLASSIF_BAND_NAME).getSourceImage();
        cloudFlagBand.setSourceImage(classifImage);
        final Tile cloudFlagTile = targetTiles.get(cloudFlagBand);
        final Tile cloudFlagSourceTile = getSourceTile(cloudFlagSourceBand, rectangle);
        setCloudBuffer(Constants.CLASSIF_BAND_NAME, cloudFlagTile, cloudFlagSourceTile, rectangle);
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(classifProduct, classifProduct.getName(), classifProduct.getProductType());
        ProductUtils.copyFlagCodings(classifProduct, targetProduct);
        OccciUtils.setupOccciClassifBitmask(targetProduct);

        Band cloudFlagBand = targetProduct.addBand(Constants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
//        cloudFlagBand.setSampleCoding(targetProduct.getFlagCodingGroup().get(Constants.CLASSIF_BAND_NAME));

    }

    void setCloudBuffer(String bandName, Tile targetTile, Tile sourceTile, Rectangle rectangle) {
        if (bandName.equals(Constants.CLASSIF_BAND_NAME)) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
//                    if (targetTile.getSampleBit(x, y, Constants.F_CLOUD)) {
                    if (sourceTile.getSampleBit(x, y, Constants.F_CLOUD)) {
                        int LEFT_BORDER = Math.max(x - cloudBufferWidth, rectangle.x);
                        int RIGHT_BORDER = Math.min(x + cloudBufferWidth, rectangle.x + rectangle.width - 1);
                        int TOP_BORDER = Math.max(y - cloudBufferWidth, rectangle.y);
                        int BOTTOM_BORDER = Math.min(y + cloudBufferWidth, rectangle.y + rectangle.height - 1);
                        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                                if (!targetTile.getSampleBit(i, j, Constants.F_INVALID)) {
                                    targetTile.setSample(i, j, Constants.F_CLOUD_BUFFER, true);
                                }
                            }
                        }
                    }
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
