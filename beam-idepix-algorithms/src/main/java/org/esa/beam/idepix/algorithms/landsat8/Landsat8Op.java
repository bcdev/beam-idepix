package org.esa.beam.idepix.algorithms.landsat8;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
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
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.PlanarImage;
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
        description = "Pixel identification for Landsat 8.")
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
    private boolean computeCloudShadow = false;  // todo: later if we find a way how to compute

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "true",
            label = " Refine pixel classification near coastlines",
            description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines;

    @Parameter(defaultValue = "2",
            interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "865",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength for brightness computation br = R(wvl) over land.",
            label = "Wavelength for brightness computation over land")
    private int brightnessBandLand;

    @Parameter(defaultValue = "100.0",
            description = "Threshold T for brightness classification over land: bright if br > T.",
            label = "Threshold for brightness classification over land")
    private float brightnessThreshLand;

    @Parameter(defaultValue = "655",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 1 for brightness computation over water.",
            label = "Wavelength 1 for brightness computation over water")
    private int brightnessBand1Water;

    @Parameter(defaultValue = "1.0",
            description = "Weight A for wavelength 1 for brightness computation (br = A*R(wvl_1) + B*R(wvl_2)) over water.",
            label = "Weight A for wavelength 1 for brightness computation over water")
    private float brightnessWeightBand1Water;

    @Parameter(defaultValue = "865",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 2 for brightness computation over water.",
            label = "Wavelength 1 for brightness computation over water")
    private int brightnessBand2Water;

    @Parameter(defaultValue = "1.0",
            description = "Weight B for wavelength 2 for brightness computation (br = A*R(wvl_1) + B*R(wvl_2)) over water.",
            label = "Weight B for wavelength 2 for brightness computation over water")
    private float brightnessWeightBand2Water;

    @Parameter(defaultValue = "100.0",
            description = "Threshold T for brightness classification over water: bright if br > T.",
            label = "Threshold for brightness classification over water")
    private float brightnessThreshWater;

    @Parameter(defaultValue = "655",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 1 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over land.",
            label = "Wavelength 1 for whiteness computation over land")
    private int whitenessBand1Land;

    @Parameter(defaultValue = "865",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 2 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over land.",
            label = "Wavelength 2 for whiteness computation over land")
    private int whitenessBand2Land;

    @Parameter(defaultValue = "2.0",
            description = "Threshold T for whiteness classification over land: white if wh < T.",
            label = "Threshold for whiteness classification over land")
    private float whitenessThreshLand;

    @Parameter(defaultValue = "655",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 1 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over water.",
            label = "Wavelength 1 for whiteness computation over water")
    private int whitenessBand1Water;

    @Parameter(defaultValue = "865",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 2 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over water.",
            label = "Wavelength 2 for whiteness computation over water")
    private int whitenessBand2Water;

    @Parameter(defaultValue = "2.0",
            description = "Threshold T for whiteness classification over water: white if wh < T.",
            label = "Threshold for whiteness classification over water")
    private float whitenessThreshWater;

    // SHIMEZ parameters:
    @Parameter(defaultValue = "true",
            label = " Apply SHIMEZ cloud test")
    private boolean applyShimezCloudTest;

    @Parameter(defaultValue = "0.1",
            description = "Threshold A for SHIMEZ cloud test: cloud if mean > B AND diff < A.",
            label = "Threshold A for SHIMEZ cloud test")
    private float shimezDiffThresh;

    @Parameter(defaultValue = "0.35",
            description = "Threshold B for SHIMEZ cloud test: cloud if mean > B AND diff < A.",
            label = "Threshold B for SHIMEZ cloud test")
    private float shimezMeanThresh;

    // HOT parameters:
    @Parameter(defaultValue = "true",
            label = " Apply HOT cloud test")
    private boolean applyHotCloudTest;

    @Parameter(defaultValue = "0.1",
            description = "Threshold A for HOT cloud test: cloud if blue - 0.5*red > A.",
            label = "Threshold A for HOT cloud test")
    private float hotThresh;

    // CLOST parameters:
    @Parameter(defaultValue = "false",
            label = " Apply CLOST cloud test")
    private boolean applyClostCloudTest;

    @Parameter(defaultValue = "0.00001",
            description = "Threshold A for CLOST cloud test: cloud if coastal_aerosol*blue*panchromatic*cirrus > A.",
            label = "Threshold A for CLOST cloud test")
    private float clostThresh;

    // OTSU parameters:
    @Parameter(defaultValue = "false",
            label = " Apply OTSU cloud test")
    private boolean applyOtsuCloudTest;

    // todo: maybe activate
//    @Parameter(defaultValue = "GREY",
//               valueSet = {"GREY", "BINARY"},
//               description = "OTSU processing mode (grey or binary target image)",
//               label = "OTSU processing mode (grey or binary target image)")
    private String otsuMode = "BINARY";

    private static final int LAND_WATER_MASK_RESOLUTION = 50;
    private static final int OVERSAMPLING_FACTOR_X = 3;
    private static final int OVERSAMPLING_FACTOR_Y = 3;

    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;
    private Product otsuProduct;

    @Override
    public void initialize() throws OperatorException {
        System.out.println("Running IDEPIX Landsat 8 - source product: " + sourceProduct.getName());

        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Landsat8);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        // todo: discuss and maybe activate
        HashMap<String, Product> clostInput = new HashMap<>();
        clostInput.put("l8source", sourceProduct);
        Product clostProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ClostOp.class), GPF.NO_PARAMS, clostInput);

        HashMap<String, Product> otsuInput = new HashMap<>();
        otsuInput.put("l8source", sourceProduct);
        otsuInput.put("clost", clostProduct);
        HashMap<String, Object> otsuParameters = new HashMap<>();
        otsuParameters.put("otsuMode", otsuMode);
        otsuProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OtsuBinarizeOp.class), otsuParameters, otsuInput);
        setTargetProduct(otsuProduct);

        // todo: check if useful for Clost test, maybe activate
//        final double clostHistogramMean = computeClostHistogramMean();

        preProcess();
        computeCloudProduct();
        postProcess();

        targetProduct = IdepixUtils.cloneProduct(classificationProduct);

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
        classificationParameters.put("brightnessThreshLand", brightnessThreshLand);
        classificationParameters.put("brightnessBandLand", brightnessBandLand);
        classificationParameters.put("brightnessThreshWater", brightnessThreshWater);
        classificationParameters.put("brightnessBand1Water", brightnessBand1Water);
        classificationParameters.put("brightnessBand2Water", brightnessBand2Water);
        classificationParameters.put("brightnessWeightBand1Water", brightnessWeightBand1Water);
        classificationParameters.put("brightnessWeightBand2Water", brightnessWeightBand2Water);

        classificationParameters.put("whitenessThreshLand", whitenessThreshLand);
        classificationParameters.put("whitenessBand1Land", whitenessBand1Land);
        classificationParameters.put("whitenessBand2Land", whitenessBand2Land);
        classificationParameters.put("whitenessThreshWater", whitenessThreshWater);
        classificationParameters.put("whitenessBand1Water", whitenessBand1Water);
        classificationParameters.put("whitenessBand2Water", whitenessBand2Water);

        classificationParameters.put("applyShimezCloudTest", applyShimezCloudTest);
        classificationParameters.put("shimezDiffThresh", shimezDiffThresh);
        classificationParameters.put("shimezMeanThresh", shimezMeanThresh);
        classificationParameters.put("applyHotCloudTest", applyHotCloudTest);
        classificationParameters.put("hotThresh", hotThresh);
        classificationParameters.put("applyClostCloudTest", applyClostCloudTest);
        classificationParameters.put("clostThresh", clostThresh);
        classificationParameters.put("applyOtsuCloudTest", applyOtsuCloudTest);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l8source", sourceProduct);
        classificationInputProducts.put("otsu", otsuProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Landsat8ClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("landsatCloud", classificationProduct);
        input.put("waterMask", waterMaskProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("computeCloudShadow", false);     // todo: we need algo
        params.put("refineClassificationNearCoastlines", refineClassificationNearCoastlines);  // always an improvement, but time consuming
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

    private double computeClostHistogramMean() {
        final String aerosolName = Landsat8Constants.LANDSAT8_COASTAL_AEROSOL_BAND_NAME;
        final String panName = Landsat8Constants.LANDSAT8_PANCHROMATIC_BAND_NAME;
        final String blueName = Landsat8Constants.LANDSAT8_BLUE_BAND_NAME;
        final String cirrusName = Landsat8Constants.LANDSAT8_CIRRUS_BAND_NAME;

        final Band aerosolBand = sourceProduct.getBand(aerosolName);
        final Band panBand = sourceProduct.getBand(panName);
        final Band cirrusBand = sourceProduct.getBand(cirrusName);
        final Band blueBand = sourceProduct.getBand(blueName);
        final String validMaskExpression = aerosolBand.getValidMaskExpression() + " && " +
                panBand.getValidMaskExpression() + " && " +
                blueBand.getValidMaskExpression() + " && " +
                cirrusBand.getValidMaskExpression();

        final String clostExpression =
                aerosolName + " * " + panName + " * " + blueName + " * " + cirrusName;
        final String fullExpression = "(" + validMaskExpression + ") ? (" + clostExpression + ") : NaN";
        final Band clostBand = new VirtualBand("CLOST", ProductData.TYPE_FLOAT32,
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight(),
                fullExpression);

        final PlanarImage clostImage = VirtualBandOpImage.create(fullExpression, ProductData.TYPE_FLOAT32, null,
                sourceProduct, ResolutionLevel.MAXRES);
        clostBand.setSourceImage(clostImage);
        final Stx stx = new StxFactory().create(clostBand, ProgressMonitor.NULL);

        System.out.println("clost stx mean = " + stx.getMean());
        System.out.println("clost stx std = " + stx.getStandardDeviation());
        System.out.println("clost stx low = " + stx.getMinimum());
        System.out.println("clost stx high = " + stx.getMaximum());

        return stx.getMean();
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
