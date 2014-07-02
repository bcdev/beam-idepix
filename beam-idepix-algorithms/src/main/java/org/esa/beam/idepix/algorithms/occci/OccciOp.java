package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;

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
                  description = "Pixel identification and classification with OC-CCI algorithm.")
public class OccciOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MODIS/SeaWiFS L1b product)", description = "The source product.")
    private Product sourceProduct;


    private Product rad2reflProduct;


    @Parameter(defaultValue = "true",
               label = " Reflective solar bands",
               description = "Write TOA reflective solar bands (RefSB) to target product (MODIS).")
    private boolean ocOutputRad2Refl = true;

    @Parameter(defaultValue = "false",
               label = " Emissive bands",
               description = "Write 'Emissive' to target product (MODIS).")
    private boolean ocOutputEmissive = false;

    @Parameter(defaultValue = "true",
               label = " Radiance bands",
               description = "Write TOA radiance to target product (SeaWiFS).")
    private boolean ocOutputRadiance = true;

    @Parameter(defaultValue = "false",
               label = " Debug bands",
               description = "Write further useful bands to target product.")
    private boolean ocOutputDebug = false;

    @Parameter(description = "Defines the sensor type to use. If the parameter is not set, the product type defined by the input file is used.")
    String sensorTypeString;

    @Parameter(label = "Schiller cloud Threshold ambiguous clouds", defaultValue = "1.4")   // todo: adjust default?
    private double ocSchillerAmbiguous;
    @Parameter(label = "Schiller cloud Threshold sure clouds", defaultValue = "1.8")
    private double ocSchillerSure;

    @Parameter(defaultValue = "1", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int ocWaterMaskResolution;

    private Map<String, Object> occciCloudClassificationParameters;
    private Product classifProduct;
    private Product waterMaskProduct;


    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Occci);
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

        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciClassificationOp.class),
                                           occciCloudClassificationParameters, modisClassifInput);

        // post processing:
        // - cloud buffer
        // - cloud shadow todo
        Map<String, Object> postProcessParameters = new HashMap<String, Object>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        Map<String, Product> postProcessInput = new HashMap<String, Product>();
        postProcessInput.put("classif", classifProduct);
        Product postProcessProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciPostProcessingOp.class),
                                                       postProcessParameters, postProcessInput);
        setTargetProduct(postProcessProduct);
        addBandsToTargetProduct(postProcessProduct);
    }

    private void processOccciSeawifs() {
        Map<String, Product> seawifsClassifInput = new HashMap<String, Product>(4);
        computeModisAlgorithmInputProducts(seawifsClassifInput);

        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciClassificationOp.class),
                                          occciCloudClassificationParameters, seawifsClassifInput);

        // post processing:
        // - cloud buffer
        // - cloud shadow todo
        Map<String, Object> postProcessParameters = new HashMap<String, Object>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        Map<String, Product> postProcessInput = new HashMap<String, Product>();
        postProcessInput.put("classif", classifProduct);
        Product postProcessProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciPostProcessingOp.class),
                                                       postProcessParameters, postProcessInput);
        setTargetProduct(postProcessProduct);
        addBandsToTargetProduct(postProcessProduct);
    }

    private void computeModisAlgorithmInputProducts(Map<String, Product> modisClassifInput) {
        createWaterMaskProduct();
        modisClassifInput.put("waterMask", waterMaskProduct);

        rad2reflProduct = sourceProduct; // we will convert pixelwise later, for MODIS inputs are TOA reflectances anyway
        modisClassifInput.put("refl", rad2reflProduct);
    }

    private void computeSeawifsAlgorithmInputProducts(Map<String, Product> seawifsClassifInput) {
        createWaterMaskProduct();
        seawifsClassifInput.put("waterMask", waterMaskProduct);

        rad2reflProduct = sourceProduct; // we will convert pixelwise later
        seawifsClassifInput.put("refl", rad2reflProduct);
    }

    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", ocWaterMaskResolution);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }

    private Map<String, Object> createOccciCloudClassificationParameters() {
        Map<String, Object> occciCloudClassificationParameters = new HashMap<String, Object>(1);
        occciCloudClassificationParameters.put("sensorTypeString", sensorTypeString);
        occciCloudClassificationParameters.put("schillerAmbiguous", ocSchillerAmbiguous);
        occciCloudClassificationParameters.put("schillerSure", ocSchillerSure);
        occciCloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        occciCloudClassificationParameters.put("wmResolution", ocWaterMaskResolution);
        occciCloudClassificationParameters.put("ocOutputDebug", ocOutputDebug);

        return occciCloudClassificationParameters;
    }

    private void addBandsToTargetProduct(Product targetProduct) {
        if (ocOutputRad2Refl) {
            copySourceBands(rad2reflProduct, targetProduct, "RefSB");
        }
        if (ocOutputEmissive) {
            copySourceBands(rad2reflProduct, targetProduct, "Emissive");
        }
        if (ocOutputRadiance) {
            copySourceBands(rad2reflProduct, targetProduct, "L_");
        }

        if (ocOutputDebug) {
            copySourceBands(classifProduct, targetProduct, "_value");
        }
    }

    private static void copySourceBands(Product rad2reflProduct, Product targetProduct, String bandNameSubstring) {
        for (String bandname : rad2reflProduct.getBandNames()) {
            if (bandname.contains(bandNameSubstring) && !targetProduct.containsBand(bandname)) {
                System.out.println("copy band: " + bandname);
                ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
            }
        }
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
