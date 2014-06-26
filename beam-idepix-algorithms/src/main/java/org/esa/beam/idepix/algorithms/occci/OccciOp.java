package org.esa.beam.idepix.algorithms.occci;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with OC-CCI algorithm.
 * todo: implement (see GlobAlbedoOp)
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.occci",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Pixel identification and classification with Oc-CCI algorithm.")
public class OccciOp extends Operator {

    @SourceProduct(alias = "source", label = "Name (MODIS/SeaWiFS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product rad2reflProduct;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int gaCloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;

    private Map<String, Object> occciCloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobAlbedo);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        occciCloudClassificationParameters = createOccciCloudClassificationParameters();
        if (IdepixUtils.isValidModisProduct(sourceProduct)) {
            processOccciModis();
        } else if (IdepixUtils.isValidSeawifsProduct(sourceProduct)) {
            processOccciSeawifs();
        }

    }

    private void processOccciModis() {
        Map<String, Product> modisClassifInput = new HashMap<String, Product>(4);
        computeModisAlgorithmInputProducts(modisClassifInput);

        targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciModisClassificationOp.class),
                                                               occciCloudClassificationParameters, modisClassifInput);
    }

    private void processOccciSeawifs() {
        Map<String, Product> seawifsClassifInput = new HashMap<String, Product>(4);
        computeModisAlgorithmInputProducts(seawifsClassifInput);

        targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciModisClassificationOp.class),
                                          occciCloudClassificationParameters, seawifsClassifInput);
    }

    private void computeModisAlgorithmInputProducts(Map<String, Product> modisClassifInput) {
        // todo
        modisClassifInput.put("modisl1b", sourceProduct);
        rad2reflProduct = sourceProduct; // seems that inputs are TOA refls
        modisClassifInput.put("refl", rad2reflProduct);
    }

    private void computeSeawifsAlgorithmInputProducts(Map<String, Product> seawifsClassifInput) {
        // todo
        seawifsClassifInput.put("seawifsl1b", sourceProduct);
        rad2reflProduct = computeSewifsRadiance2ReflectanceProduct(sourceProduct);
        seawifsClassifInput.put("refl", rad2reflProduct);
    }


    private Product computeSewifsRadiance2ReflectanceProduct(Product sourceProduct) {
        // todo
        return null;
    }


    private Map<String, Object> createOccciCloudClassificationParameters() {
        Map<String, Object> occciCloudClassificationParameters = new HashMap<String, Object>(1);
        occciCloudClassificationParameters.put("gaCloudBufferWidth", gaCloudBufferWidth);
        occciCloudClassificationParameters.put("wmResolution", wmResolution);

        return occciCloudClassificationParameters;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OccciOp.class);
        }
    }
}
