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
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.occci",
                  version = "2.2",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Pixel identification and classification with OC-CCI algorithm.")
public class OccciOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MODIS/SeaWiFS L1b product)", description = "The source product.")
    private Product sourceProduct;

    private Product rad2reflProduct;

    @Parameter(defaultValue = "true",
               label = " Reflective solar bands (MODIS)",
               description = "Write TOA reflective solar bands (RefSB) to target product (MODIS).")
    private boolean ocOutputRad2Refl = true;

    @Parameter(defaultValue = "false",
               label = " Emissive bands (MODIS)",
               description = "Write 'Emissive' bands to target product (MODIS).")
    private boolean ocOutputEmissive = false;

    //    @Parameter(defaultValue = "0.15",
//               label = " Brightness test threshold (MODIS)",
//               description = "Brightness test threshold: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private double ocModisBrightnessThreshCloudSure = 0.15;

    @Parameter(defaultValue = "0.15",
               label = " 'Dark glint' threshold at 859nm (MODIS)",
               description = "'Dark glint' threshold: Cloud possible only if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double ocModisGlintThresh859 = 0.15;

    @Parameter(defaultValue = "true",
               label = " Apply brightness test (MODIS)",
               description = "Apply brightness test: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private boolean ocModisApplyBrightnessTest = true;

    @Parameter(defaultValue = "true",
               label = " Apply 'OR' logic in cloud test (MODIS)",
               description = "Apply 'OR' logic instead of 'AND' logic in cloud test (MODIS).")
    private boolean ocModisApplyOrLogicInCloudTest = true;

    //    @Parameter(defaultValue = "0.07",
//               label = " Brightness test 'cloud ambiguous' threshold (MODIS)",
//               description = "Brightness test 'cloud ambiguous' threshold: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private double ocModisBrightnessThreshCloudAmbiguous = 0.125;

    @Parameter(defaultValue = "false",
               label = " Radiance bands (SeaWiFS)",
               description = "Write TOA radiance bands to target product (SeaWiFS).")
    private boolean ocOutputSeawifsRadiance = false;

    @Parameter(defaultValue = "true",
               label = " Reflectance bands (SeaWiFS)",
               description = "Write TOA reflectance bands to target product (SeaWiFS).")
    private boolean ocOutputSeawifsRefl = true;

    @Parameter(defaultValue = "true",
               label = " Geometry bands (SeaWiFS)",
               description = "Write geometry bands to target product (SeaWiFS).")
    private boolean ocOutputGeometry = true;

    @Parameter(defaultValue = "L_", valueSet = {"L_", "Lt_"}, label = " Prefix of input radiance bands (SeaWiFS).",
               description = "Prefix of input radiance bands (SeaWiFS)")
    private String ocSeawifsRadianceBandPrefix;

    @Parameter(defaultValue = "false",
               label = " Debug bands",
               description = "Write further useful bands to target product.")
    private boolean ocOutputDebug = false;

    @Parameter(label = " Product type",
               description = "Defines the product type to use. If the parameter is not set, the product type defined by the input file is used.")
    String productTypeString;

    @Parameter(defaultValue = "1", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int ocWaterMaskResolution;

    private Product classifProduct;
    private Product waterMaskProduct;


    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Occci);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }

        processOccci(createOccciCloudClassificationParameters());
    }

    private void processOccci(Map<String, Object> occciCloudClassificationParameters) {
        Map<String, Product> modisClassifInput = new HashMap<>(4);
        computeAlgorithmInputProducts(modisClassifInput);

        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciClassificationOp.class),
                                           occciCloudClassificationParameters, modisClassifInput);

        // post processing:
        // - cloud buffer
        // - cloud shadow todo
        Map<String, Object> postProcessParameters = new HashMap<>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        Map<String, Product> postProcessInput = new HashMap<>();
        postProcessInput.put("refl", rad2reflProduct);
        postProcessInput.put("classif", classifProduct);
        postProcessInput.put("waterMask", waterMaskProduct);
        Product postProcessProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciPostProcessingOp.class),
                                                       postProcessParameters, postProcessInput);

        setTargetProduct(postProcessProduct);
        addBandsToTargetProduct(postProcessProduct);
    }

    private void computeAlgorithmInputProducts(Map<String, Product> modisClassifInput) {
        createWaterMaskProduct();
        modisClassifInput.put("waterMask", waterMaskProduct);

        rad2reflProduct = sourceProduct; // we will convert pixelwise later, for MODIS inputs are TOA reflectances anyway
        modisClassifInput.put("refl", rad2reflProduct);
    }

    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<>();
        waterParameters.put("resolution", ocWaterMaskResolution);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }

    private Map<String, Object> createOccciCloudClassificationParameters() {
        Map<String, Object> occciCloudClassificationParameters = new HashMap<>(1);
        occciCloudClassificationParameters.put("productTypeString", productTypeString);
        occciCloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        occciCloudClassificationParameters.put("wmResolution", ocWaterMaskResolution);
        occciCloudClassificationParameters.put("ocOutputDebug", ocOutputDebug);
        occciCloudClassificationParameters.put("ocOutputSeawifsRadiance", ocOutputSeawifsRadiance);
        occciCloudClassificationParameters.put("ocSeawifsRadianceBandPrefix", ocSeawifsRadianceBandPrefix);
        occciCloudClassificationParameters.put("ocModisApplyBrightnessTest", ocModisApplyBrightnessTest);
        occciCloudClassificationParameters.put("ocModisBrightnessThreshCloudSure", ocModisBrightnessThreshCloudSure);
        occciCloudClassificationParameters.put("ocModisBrightnessThreshCloudAmbiguous", ocModisBrightnessThreshCloudAmbiguous);
        occciCloudClassificationParameters.put("ocModisGlintThresh859", ocModisGlintThresh859);
        occciCloudClassificationParameters.put("ocModisApplyOrLogicInCloudTest", ocModisApplyOrLogicInCloudTest);

        return occciCloudClassificationParameters;
    }

    private void addBandsToTargetProduct(Product targetProduct) {
//        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        if (ocOutputRad2Refl) {
            copySourceBands(rad2reflProduct, targetProduct, "RefSB");
        }
        if (ocOutputEmissive) {
            copySourceBands(rad2reflProduct, targetProduct, "Emissive");
        }
        if (ocOutputSeawifsRadiance) {
//            copySourceBands(rad2reflProduct, targetProduct, "L_");
            copySourceBands(rad2reflProduct, targetProduct, "Lt_");
        }

        if (ocOutputDebug) {
            copySourceBands(classifProduct, targetProduct, "_value");
        }
        copySourceBands(classifProduct, targetProduct, Constants.SCHILLER_NN_OUTPUT_BAND_NAME);

        if (ocOutputSeawifsRefl) {
            copySourceBands(classifProduct, targetProduct, "_refl");
        }

        if (ocOutputGeometry) {
            copySourceBands(rad2reflProduct, targetProduct, "sol");       // SeaWiFS
            copySourceBands(rad2reflProduct, targetProduct, "sen");       // SeaWiFS
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
