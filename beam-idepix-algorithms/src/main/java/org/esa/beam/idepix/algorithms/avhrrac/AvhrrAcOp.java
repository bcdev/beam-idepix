package org.esa.beam.idepix.algorithms.avhrrac;

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
import org.esa.beam.idepix.util.IdepixUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Idepix operator for pixel identification and classification with AVHRR AC algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.avhrrac",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Pixel identification and classification with AVHRR AC algorithm.")
public class AvhrrAcOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (AVHRR AC L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product classifProduct;
    private Product waterMaskProduct;

    // AvhrrAc parameters
    @Parameter(defaultValue = "true", label = " Copy input radiance/reflectance bands")
    private boolean aacCopyRadiances = true;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int aacCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean aacUseWaterMaskFraction = true;

//    @Parameter(defaultValue = "false",
//               label = " Debug bands",
//               description = "Write further useful bands to target product.")
//    private boolean avhrracOutputDebug = false;

    @Parameter(defaultValue = "2.15",
               label = " Schiller NN cloud ambiguous lower boundary ",
               description = " Schiller NN cloud ambiguous lower boundary ")
    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
               label = " Schiller NN cloud ambiguous/sure separation value ",
               description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
               label = " Schiller NN cloud sure/snow separation value ",
               description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double avhrracSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "20.0",
               label = " Reflectance 1 'brightness' threshold ",
               description = " Reflectance 1 'brightness' threshold ")
    double reflCh1Thresh;

    @Parameter(defaultValue = "20.0",
               label = " Reflectance 2 'brightness' threshold ",
               description = " Reflectance 2 'brightness' threshold ")
    double reflCh2Thresh;

    @Parameter(defaultValue = "1.0",
               label = " Reflectance 2/1 ratio threshold ",
               description = " Reflectance 2/1 ratio threshold ")
    double r2r1RatioThresh;

    @Parameter(defaultValue = "1.0",
               label = " Reflectance 3/1 ratio threshold ",
               description = " Reflectance 3/1 ratio threshold ")
    double r3r1RatioThresh;

    @Parameter(defaultValue = "-30.0",
               label = " Channel 4 brightness temperature threshold (C)",
               description = " Channel 4 brightness temperature threshold (C)")
    double btCh4Thresh;

    @Parameter(defaultValue = "-30.0",
               label = " Channel 5 brightness temperature threshold (C)",
               description = " Channel 5 brightness temperature threshold (C)")
    double btCh5Thresh;


    private Map<String, Object> aacCloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.AvhrrAc);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        aacCloudClassificationParameters = createAacCloudClassificationParameters();
        processAvhrrAc();
    }

    private Map<String, Object> createAacCloudClassificationParameters() {
        Map<String, Object> aacCloudClassificationParameters = new HashMap<>(1);
        aacCloudClassificationParameters.put("aacCopyRadiances", aacCopyRadiances);
        aacCloudClassificationParameters.put("aacCloudBufferWidth", aacCloudBufferWidth);
        aacCloudClassificationParameters.put("wmResolution", wmResolution);
        aacCloudClassificationParameters.put("aacUseWaterMaskFraction", aacUseWaterMaskFraction);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudAmbiguousLowerBoundaryValue",
                                             avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudAmbiguousSureSeparationValue",
                                             avhrracSchillerNNCloudAmbiguousSureSeparationValue);
        aacCloudClassificationParameters.put("avhrracSchillerNNCloudSureSnowSeparationValue",
                                             avhrracSchillerNNCloudSureSnowSeparationValue);

        aacCloudClassificationParameters.put("reflCh1Thresh", reflCh1Thresh);
        aacCloudClassificationParameters.put("reflCh2Thresh", reflCh2Thresh);
        aacCloudClassificationParameters.put("r2r1RatioThresh", r2r1RatioThresh);
        aacCloudClassificationParameters.put("r3r1RatioThresh", r3r1RatioThresh);
        aacCloudClassificationParameters.put("btCh4Thresh", btCh4Thresh);
        aacCloudClassificationParameters.put("btCh5Thresh", btCh5Thresh);

        return aacCloudClassificationParameters;
    }

    private void processAvhrrAc() {
        Map<String, Product> aacCloudInput = new HashMap<>(4);
        computeAvhrrAcAlgorithmInputProducts(aacCloudInput);

        AvhrrAcClassificationOp acClassificationOp = new AvhrrAcClassificationOp();
        // test operator for older products which contain all inputs for Schiller NN:
//        AvhrrAcClassification2Op acClassificationOp = new AvhrrAcClassification2Op();
//        AvhrrAcClassification3Op acClassificationOp = new AvhrrAcClassification3Op();
        acClassificationOp.setParameterDefaultValues();
        for (String key : aacCloudClassificationParameters.keySet()) {
            acClassificationOp.setParameter(key, aacCloudClassificationParameters.get(key));
        }
        acClassificationOp.setSourceProduct("aacl1b", sourceProduct);
        acClassificationOp.setSourceProduct("waterMask", waterMaskProduct);

        setTargetProduct(acClassificationOp.getTargetProduct());
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
