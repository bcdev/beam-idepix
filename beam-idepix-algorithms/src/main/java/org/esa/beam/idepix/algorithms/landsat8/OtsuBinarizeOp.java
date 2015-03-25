package org.esa.beam.idepix.algorithms.landsat8;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 25.03.2015
 * Time: 13:45
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.landsat8.otsu",
                  version = "2.2.1",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2015 by Brockmann Consult",
                  description = "Landsat 8 Otsu binarization: provides product with binarized R, G, and B image.")
public class OtsuBinarizeOp extends Operator {

    @SourceProduct(alias = "l8source", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();

        final Band redBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[3]);
        final Band greenBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[2]);
        final Band blueBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[1]);

        final BufferedImage redImageOriginal = redBand.getSourceImage().getAsBufferedImage();
        final BufferedImage greenImageOriginal = greenBand.getSourceImage().getAsBufferedImage();
        final BufferedImage blueImageOriginal = blueBand.getSourceImage().getAsBufferedImage();

        // todo: the following does not yet work. investigate
        final BufferedImage redImageGray = OtsuBinarize.toGray(redImageOriginal);
        final BufferedImage greenImageGray = OtsuBinarize.toGray(greenImageOriginal);
        final BufferedImage blueImageGray = OtsuBinarize.toGray(blueImageOriginal);

        final BufferedImage redImageBinarized = OtsuBinarize.toGray(redImageGray);
        final BufferedImage greenImageBinarized = OtsuBinarize.toGray(greenImageGray);
        final BufferedImage blueImageBinarized = OtsuBinarize.toGray(blueImageGray);

        Product binarizedProduct = new Product(sourceProduct.getName() + "_bin",
                                               sourceProduct.getProductType() + " (binarized)",
                                               sourceProduct.getSceneRasterWidth(),
                                               sourceProduct.getSceneRasterHeight());

        final Band redTargetBand =
                binarizedProduct.addBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[3], ProductData.TYPE_UINT8);
        final Band greenTargetBand =
                binarizedProduct.addBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[2], ProductData.TYPE_UINT8);
        final Band blueTargetBand =
                binarizedProduct.addBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[1], ProductData.TYPE_UINT8);

        redTargetBand.setSourceImage(redImageBinarized);
        greenTargetBand.setSourceImage(greenImageBinarized);
        blueTargetBand.setSourceImage(blueImageBinarized);

        setTargetProduct(binarizedProduct);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OtsuBinarizeOp.class);
        }
    }

}
