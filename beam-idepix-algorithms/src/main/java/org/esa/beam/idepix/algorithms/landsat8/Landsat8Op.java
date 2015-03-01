package org.esa.beam.idepix.algorithms.landsat8;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification for Landsat 8
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.landsat8",
        version = "2.2",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Pixel identification for Landsat .")
public class Landsat8Op extends Operator {

    @SourceProduct(alias = "sourceProduct",
            label = "Landsat 8 product",
            description = "The Landsat 8 source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Parameter(defaultValue = "false",
            description = "Write source bands to the target product.",
            label = " Write source bands to the target product")
    private boolean outputSourceBands = false;

    //    @Parameter(defaultValue = "true",
//            label = " Compute cloud shadow",
//            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow = false;  // todo: later when specified how to compute

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2",
            interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "100.0",
            description = "Brightness threshold at 865nm.",
            label = "Brightness threshold at 865nm")
    private float brightnessThresh;

    @Parameter(defaultValue = "1.0",  // todo: find reasonable default value
            description = "Brightness coefficient for band 4 (865nm).",
            label = "Brightness coefficient for band 4 (865nm)")
    private float brightnessCoeffBand4;

    @Parameter(defaultValue = "1.0",  // todo: find reasonable default value
            description = "Brightness coefficient for band 5 (1610nm).",
            label = "Brightness coefficient for band 5 (1610nm)")
    private float brightnessCoeffBand5;

    @Parameter(defaultValue = "100.0",  // todo: find reasonable default value
            description = "Whiteness threshold.",
            label = "Whiteness threshold")
    private float whitenessThresh;

    @Parameter(defaultValue = "4",   // todo: find reasonable default value
            interval = "[0,10]",
            description = "Index of numerator band for whiteness computation.",
            label = "Index of numerator band for whiteness computation")
    private int whitenessNumeratorBandIndex;

    @Parameter(defaultValue = "5",  // todo: find reasonable default value
            interval = "[0,10]",
            description = "Index of denominator band for whiteness computation.",
            label = "Index of denominator band for whiteness computation")
    private int whitenessDenominatorBandIndex;


    private static final int LAND_WATER_MASK_RESOLUTION = 50;
    private static final int OVERSAMPLING_FACTOR_X = 3;
    private static final int OVERSAMPLING_FACTOR_Y = 3;

    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;

    @Override
    public void initialize() throws OperatorException {
        System.out.println("Running IDEPIX Landsat 8 - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Landsat8);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        preProcess();
        computeCloudProduct();
        postProcess();

        targetProduct = postProcessingProduct;

        targetProduct = IdepixUtils.cloneProduct(classificationProduct);
        targetProduct.setAutoGrouping("radiance:rho_toa");

        Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS).getSourceImage());

        copyOutputBands();
    }

    private void preProcess() {
        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("brightnessThresh", brightnessThresh);
        classificationParameters.put("brightnessCoeffBand4", brightnessCoeffBand4);
        classificationParameters.put("brightnessCoeffBand5", brightnessCoeffBand5);
        classificationParameters.put("whitenessThresh", whitenessThresh);
        classificationParameters.put("whitenessNumeratorBandIndex", whitenessNumeratorBandIndex);
        classificationParameters.put("whitenessDenominatorBandIndex", whitenessDenominatorBandIndex);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l8source", sourceProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Landsat8ClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("landsatCloud", classificationProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("computeCloudShadow", false);
        params.put("refineClassificationNearCoastlines", true);  // always an improvement
        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Landsat8PostProcessOp.class), params, input);
    }

    private void copyOutputBands() {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        Landsat8Utils.setupLandsat8Bitmasks(targetProduct);
        if (outputSourceBands) {
            for (Band b : sourceProduct.getBands()) {
                ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
                ProductUtils.copyBand(b.getName(), sourceProduct, targetProduct, true);
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(Landsat8Op.class);
        }
    }
}
