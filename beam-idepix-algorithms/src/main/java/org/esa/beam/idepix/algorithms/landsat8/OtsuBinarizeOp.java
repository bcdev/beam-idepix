package org.esa.beam.idepix.algorithms.landsat8;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.jai.ImageManager;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.jai.SingleBandedSampleModel;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.BandSelectDescriptor;
import javax.media.jai.operator.FormatDescriptor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Operator to generate grey or binary images with Otsu algorithm
 * (see e.g. http://zerocool.is-a-geek.net/java-image-binarization/)
 * Target product will contain just one band with this image
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

    public static final String RGB_BINARY_BAND_NAME = "RGB_TO_BINARY";
    public static final String RGB_GREY_BAND_NAME = "RGB_TO_GREY";

    @SourceProduct(alias = "l8source", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "GREY",
               valueSet = {"GREY","BINARY"},
               description = "OTSU processing mode (grey or binary target image)",
               label = "OTSU processing mode (grey or binary target image)")
    private String otsuMode;


    @Override
    public void initialize() throws OperatorException {
        final Band redBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[3]);
        final Band greenBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[2]);
        final Band blueBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[1]);

        final RasterDataNode[] rgbChannelNodes = new RasterDataNode[]{redBand, greenBand, blueBand};

        try {
            final ImageInfo imageInfo = ProductUtils.createImageInfo(rgbChannelNodes, true, ProgressMonitor.NULL);
            BufferedImage rgbImage = ProductUtils.createRgbImage(rgbChannelNodes, imageInfo, ProgressMonitor.NULL);
            final BufferedImage rgbImageGray = OtsuBinarize.toGray(rgbImage);
            final BufferedImage rgbImageBinarized = OtsuBinarize.binarize(rgbImageGray);

//            File file = new File("test.jpg");
//            ImageIO.write(rgbImageBinarized, "jpg", file);

            Product otsuProduct;
            if (otsuMode.equals("GREY")) {
                otsuProduct = createGreyProduct(rgbImageGray);
            } else {
                otsuProduct = createBinarizedProduct(rgbImageBinarized);
            }
            setTargetProduct(otsuProduct);
        } catch (IOException e) {
            throw new OperatorException("Cannot do OTSU binarization: " + e.getMessage());
        }
    }

    private Product createBinarizedProduct(BufferedImage sourceImage) {

        Product product = new Product(sourceProduct.getName() + "_binary",
                                      sourceProduct.getProductType() + " (binarized)",
                                      sourceProduct.getSceneRasterWidth(),
                                      sourceProduct.getSceneRasterHeight());

        product.setGeoCoding(sourceProduct.getGeoCoding());
        product.setDescription("Product holding RGB Image transformed to binary");

        final PlanarImage planarImage = PlanarImage.wrapRenderedImage(sourceImage);
        RenderedOp bandImage = getBandSourceImage(planarImage, 0);
        Band band = product.addBand(RGB_BINARY_BAND_NAME, ImageManager.getProductDataType(bandImage.getSampleModel().getDataType()));
        band.setSourceImage(bandImage);
        band.setUnit("dl");
        band.setDescription("RGB Image transformed to binary");
        final Band sourceProductReferenceBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_RED_BAND_NAME);
        band.setNoDataValue(sourceProductReferenceBand.getNoDataValue());
        band.setNoDataValueUsed(sourceProductReferenceBand.isNoDataValueUsed());
        product.getBand(RGB_BINARY_BAND_NAME).setValidPixelExpression(sourceProductReferenceBand.getValidPixelExpression());

        return product;
    }

    private Product createGreyProduct(BufferedImage sourceImage) {

        Product product = new Product(sourceProduct.getName() + "_grey",
                                      sourceProduct.getProductType() + " (greyscaled)",
                                      sourceProduct.getSceneRasterWidth(),
                                      sourceProduct.getSceneRasterHeight());

        product.setGeoCoding(sourceProduct.getGeoCoding());
        product.setDescription("Product holding RGB Image transformed to greyscale");

        final PlanarImage planarImage = PlanarImage.wrapRenderedImage(sourceImage);
        RenderedOp bandImage = getBandSourceImage(planarImage, 0);
        Band band = product.addBand(RGB_GREY_BAND_NAME, ImageManager.getProductDataType(bandImage.getSampleModel().getDataType()));
        band.setSourceImage(bandImage);
        band.setUnit("dl");
        band.setDescription("RGB Image transformed to greyscale");
        final Band sourceProductReferenceBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_RED_BAND_NAME);
        band.setNoDataValue(sourceProductReferenceBand.getNoDataValue());
        band.setNoDataValueUsed(sourceProductReferenceBand.isNoDataValueUsed());
        product.getBand(RGB_GREY_BAND_NAME).setValidPixelExpression(sourceProductReferenceBand.getValidPixelExpression());

        return product;
    }

    private RenderedOp getBandSourceImage(PlanarImage planarImage, int i) {
        RenderedOp bandImage = BandSelectDescriptor.create(planarImage, new int[]{i}, null);
        int tileWidth = bandImage.getTileWidth();
        int tileHeight = bandImage.getTileHeight();
        ImageLayout imageLayout = new ImageLayout();
        boolean noSourceImageTiling = tileWidth == bandImage.getWidth() && tileHeight == bandImage.getHeight();
        if (noSourceImageTiling) {
            tileWidth = Math.min(bandImage.getWidth(), 512);
            tileHeight = Math.min(bandImage.getHeight(), 512);
            imageLayout.setTileWidth(tileWidth);
            imageLayout.setTileHeight(tileHeight);
        }
        imageLayout.setSampleModel(new SingleBandedSampleModel(bandImage.getSampleModel().getDataType(), tileWidth, tileHeight));
        bandImage = FormatDescriptor.create(bandImage, bandImage.getSampleModel().getDataType(), new RenderingHints(JAI.KEY_IMAGE_LAYOUT, imageLayout));
        return bandImage;
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
