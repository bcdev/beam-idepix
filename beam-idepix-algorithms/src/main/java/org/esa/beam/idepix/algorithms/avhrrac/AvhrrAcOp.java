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

    @Parameter(defaultValue = "false",
               label = " Debug bands",
               description = "Write further useful bands to target product.")
    private boolean avhrracOutputDebug = false;

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
        aacCloudClassificationParameters.put("avhrracOutputDebug", avhrracOutputDebug);
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

        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AvhrrAcClassificationOp.class),
                                           aacCloudClassificationParameters, aacCloudInput);

        setTargetProduct(classifProduct);
//        addBandsToTargetProduct(classifProduct);
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
