package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.operators.CloudBufferOp;
import org.esa.beam.idepix.util.IdepixUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with AVHRR AC algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.avhrrac",
        version = "2.2",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Pixel identification and classification with AVHRR AC algorithm.")
public class AvhrrAcOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (AVHRR AC L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product classificationProduct;
    private Product postProcessingProduct;
    private Product waterMaskProduct;

    // AvhrrAc parameters
    @Parameter(defaultValue = "false", label = " Copy input radiance/reflectance bands")
    private boolean aacCopyRadiances = false;

    @Parameter(defaultValue = "true",
               label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true",
               label = " Refine pixel classification near coastlines",
               description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    private int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean aacUseWaterMaskFraction = true;

    @Parameter(defaultValue = "false", label = " Flip source images (check before if needed!)")
    private boolean flipSourceImages = false;

//    @Parameter(defaultValue = "false",
//               label = " Debug bands",
//               description = "Write further useful bands to target product.")
//    private boolean avhrracOutputDebug = false;

    @Parameter(defaultValue = "2.15",
            label = " NN cloud ambiguous lower boundary ",
            description = " NN cloud ambiguous lower boundary ")
    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " NN cloud ambiguous/sure separation value ",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
            label = " NN cloud sure/snow separation value ",
            description = " NN cloud ambiguous cloud sure/snow separation value ")
    double avhrracSchillerNNCloudSureSnowSeparationValue;


    private Map<String, Object> aacCloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.AvhrrAc);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        aacCloudClassificationParameters = createAacCloudClassificationParameters();
        processAvhrrAc();
    }

    private Map<String, Object> createAacCloudClassificationParameters() {
        Map<String, Object> aacCloudClassificationParameters = new HashMap<>(1);
        aacCloudClassificationParameters.put("aacCopyRadiances", aacCopyRadiances);
        aacCloudClassificationParameters.put("aacCloudBufferWidth", cloudBufferWidth);
        aacCloudClassificationParameters.put("wmResolution", wmResolution);
        aacCloudClassificationParameters.put("aacUseWaterMaskFraction", aacUseWaterMaskFraction);
        aacCloudClassificationParameters.put("flipSourceImages", flipSourceImages);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudAmbiguousLowerBoundaryValue",
                avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudAmbiguousSureSeparationValue",
                avhrracSchillerNNCloudAmbiguousSureSeparationValue);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudSureSnowSeparationValue",
                avhrracSchillerNNCloudSureSnowSeparationValue);

        return aacCloudClassificationParameters;
    }

    private void processAvhrrAc() {
        Map<String, Product> aacCloudInput = new HashMap<>(4);
        computeAvhrrAcAlgorithmInputProducts(aacCloudInput);

        AbstractAvhrrAcClassificationOp acClassificationOp = null;
        if (IdepixUtils.isAvhrrTimelineProduct(sourceProduct)) {
            acClassificationOp = new AvhrrAcTimelineClassificationOp();
        }  else if (IdepixUtils.isAvhrrUsgsProduct(sourceProduct)) {
            acClassificationOp = new AvhrrAcUSGSClassificationOp();
        } else if (IdepixUtils.isAvhrrOldTestProduct(sourceProduct)) {
            acClassificationOp = new AvhrrAcTestClassificationOp();
        } else if (IdepixUtils.isAvhrrNceiProduct(sourceProduct)) {
            acClassificationOp = new AvhrrAcNCEIClassificationOp();
        } else if (IdepixUtils.isAvhrrLtdr02C1Product(sourceProduct)) {
            acClassificationOp = new AvhrrAcLtdr02C1ClassificationOp();
        } else {
            throw new OperatorException("Input product is not a valid AVHRR product.");
        }

        acClassificationOp.setParameterDefaultValues();
        for (String key : aacCloudClassificationParameters.keySet()) {
            acClassificationOp.setParameter(key, aacCloudClassificationParameters.get(key));
        }
        acClassificationOp.setSourceProduct("aacl1b", sourceProduct);
        acClassificationOp.setSourceProduct("waterMask", waterMaskProduct);

        // todo: do we want postprocessing for AVHRR?
        classificationProduct = acClassificationOp.getTargetProduct();
        postProcess();

        targetProduct = IdepixUtils.cloneProduct(classificationProduct);
        targetProduct.setName(sourceProduct.getName()+".idepix");

        Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS).getSourceImage());

    }

    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("avhrrCloud", classificationProduct);
        input.put("waterMask", waterMaskProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudShadow", false);     // todo: we need algo
        params.put("refineClassificationNearCoastlines", refineClassificationNearCoastlines);  // always an improvement, but time consuming

        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AvhrrAcPostProcessOp.class),
                                                            params, input);

        if (computeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", cloudBufferWidth);
            postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                                      params, input);
        } else {
            postProcessingProduct = classifiedProduct;
        }
    }


    private void computeAvhrrAcAlgorithmInputProducts(Map<String, Product> aacCloudInput) {
        createWaterMaskProduct();
        aacCloudInput.put("aacl1b", sourceProduct);
        aacCloudInput.put("waterMask", waterMaskProduct);
    }

    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<>();
        waterParameters.put("resolution", wmResolution);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcOp.class);
        }
    }
}
