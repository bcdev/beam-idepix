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

    // AvhrrAc parameters
    @Parameter(defaultValue = "true", label = " Copy input radiance/reflectance bands")
    private boolean aacCopyRadiances = true;
    @Parameter(defaultValue = "false", label = " Compute only the flag band")
    private boolean aacComputeFlagsOnly;
    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int aacCloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;
    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean aacUseWaterMaskFraction = true;

    private Map<String, Object> aacCloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.AvhrrAc);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        aacCloudClassificationParameters = createAacCloudClassificationParameters();
            processAvhrrAc();

        renameL1bMaskNames(targetProduct);
    }

    private Map<String, Object> createAacCloudClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<>(1);
        gaCloudClassificationParameters.put("gaCopyRadiances", aacCopyRadiances);
        gaCloudClassificationParameters.put("gaComputeFlagsOnly", aacComputeFlagsOnly);
        gaCloudClassificationParameters.put("gaCloudBufferWidth", aacCloudBufferWidth);
        gaCloudClassificationParameters.put("wmResolution", wmResolution);
        gaCloudClassificationParameters.put("gaUseWaterMaskFraction", aacUseWaterMaskFraction);

        return gaCloudClassificationParameters;
    }

    private void processAvhrrAc() {
        Product aacCloudProduct;
        Map<String, Product> aacCloudInput = new HashMap<>(4);
        computeAvhrrAcAlgorithmInputProducts(aacCloudInput);

        aacCloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AvhrrAcDefaultClassificationOp.class),
                                           aacCloudClassificationParameters, aacCloudInput);

        targetProduct = aacCloudProduct;
    }

    private void computeAvhrrAcAlgorithmInputProducts(Map<String, Product> aacCloudInput) {
        aacCloudInput.put("aacl1b", sourceProduct);
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
