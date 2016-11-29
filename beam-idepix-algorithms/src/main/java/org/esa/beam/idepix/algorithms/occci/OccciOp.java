package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.operators.CloudBufferOp;
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
    private Product pressureLiseProduct;
    private Product ctpProduct;
    private Product pbaroProduct;

    @Parameter(defaultValue = "false",
            label = " Process MERIS for sea sce ",
            description = " Use experimental 'sea ice' mode for MERIS (instead of standard CC 'WATER' approach) ")
    private boolean processMerisSeaIce = false;

    @Parameter(defaultValue = "false",
            label = " Process MERIS for sea ice in Antarctic regions ",
            description = " Use experimental 'sea ice' mode for MERIS in Antarctic rather than Arctic regions ")
    private boolean processMerisSeaIceAntarctic = false;

    @Parameter(defaultValue = "false",
            label = " Retrieve MERIS sea ice edge from L3 ",
            description = "Retrieve MERIS sea ice edge from a specific L3 product containing sea ice extent ")
    private boolean retrieveMerisSeaIceEdge = false;

    @Parameter(label = " Sea ice buffer width for ice edge determination ",
            defaultValue = "1")
    private int seaiceBufferWidth;

    @Parameter(label = " Minimum number of sea ice neighbours to classify water pixel as sea ice edge ('MIZ') ",
            defaultValue = "2")
    private int numSeaIceNeighboursThresh;

    @Parameter(defaultValue = "false",
            description = "Apply 'Blue Filter' for wet ice in case of processing MERIS for sea ice.",
            label = "Apply 'Blue Filter' for wet ice in case of processing MERIS for sea ice."
    )
    private boolean applyBlueFilter;

    // disable for the moment (20160801)
//    @Parameter(defaultValue = "SIX_CLASSES",
//            valueSet = {"SIX_CLASSES", "FOUR_CLASSES", "SIX_CLASSES_NORTH", "FOUR_CLASSES_NORTH"},
//            label = "Neural Net for MERIS in case of sea ice classification",
//            description = "The Neural Net which will be applied.")
//    private MerisSeaiceNNSelector nnSelector;
    private MerisSeaiceNNSelector nnSelector = MerisSeaiceNNSelector.SIX_CLASSES;

    //    @Parameter(defaultValue = "5.0",
//            label = " 'MERIS1600' threshold (MERIS Sea Ice) ",
//            description = " 'MERIS1600' threshold value ")
//    double schillerMeris1600Threshold;
    double schillerMeris1600Threshold = 5.0;

    //    @Parameter(defaultValue = "0.5",
//            label = " 'MERIS/AATSR' cloud/ice separation value (MERIS Sea Ice) ",
//            description = " 'MERIS/AATSR' cloud/ice separation value ")
//    double schillerMerisAatsrCloudIceSeparationValue;
    double schillerMerisAatsrCloudIceSeparationValue = 0.5;

    @Parameter(defaultValue = "false",
            label = " Radiance bands (MERIS)",
            description = "Write TOA radiance bands to target product (MERIS).")
    private boolean ocOutputMerisRadiance = false;

    @Parameter(defaultValue = "true",
            label = " Reflectance bands (MERIS)",
            description = "Write TOA reflectance bands to target product (MERIS).")
    private boolean ocOutputMerisRefl = true;

    @Parameter(defaultValue = "true",
            label = " Cloud Top Pressure (MERIS)",
            description = "Write CTP band to target product (MERIS).")
    private boolean ocOutputCtp = true;

    //    @Parameter(defaultValue = "false",
//            label = " Write NN value to the target product (MERIS).",
//            description = " If applied, write NN value to the target product (MERIS)")
//    private boolean outputSchillerMerisNNValue;
    private boolean outputSchillerMerisNNValue = true;

    //    @Parameter(defaultValue = "2.0",
//            label = " Schiller NN cloud ambiguous lower boundary (MERIS)",
//            description = " Schiller NN cloud ambiguous lower boundary (MERIS)")
//    double schillerMerisNNCloudAmbiguousLowerBoundaryValue;
    double schillerMerisNNCloudAmbiguousLowerBoundaryValue = 2.0;

    //    @Parameter(defaultValue = "3.7",
//            label = " Schiller NN cloud ambiguous/sure separation value (MERIS)",
//            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value (MERIS)")
//    double schillerMerisNNCloudAmbiguousSureSeparationValue;
    double schillerMerisNNCloudAmbiguousSureSeparationValue = 3.7;

    //    @Parameter(defaultValue = "4.05",
//            label = " Schiller NN cloud sure/snow separation value (MERIS)",
//            description = " Schiller NN cloud ambiguous cloud sure/snow separation value (MERIS)")
//    double schillerMerisNNCloudSureSnowSeparationValue;
    double schillerMerisNNCloudSureSnowSeparationValue = 4.05;


    @Parameter(defaultValue = "true",
            label = " Reflective solar bands (MODIS)",
            description = "Write TOA reflective solar bands (RefSB) to target product (MODIS).")
    private boolean ocOutputRad2Refl = true;

    // DO NOT add this parameter to bundle descriptor!
    // todo: if still needed, copy all OCCCI stuff needed for MODIS also into CAWA part
    @Parameter(defaultValue = "true",
            description = "Write CAWA RefSB (bands 2, 5, 17-19) to the target product.",
            label = " Write CAWA RefSB (bands 2, 5, 17-19) to the target product")
    private boolean ocOutputCawaRefSB = true;

    @Parameter(defaultValue = "false",
            label = " Emissive bands (MODIS)",
            description = "Write 'Emissive' bands to target product (MODIS).")
    private boolean ocOutputEmissive = false;

    //    @Parameter(defaultValue = "0.15",
//               label = " Brightness test threshold (MODIS)",
//               description = "Brightness test threshold: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private double ocModisBrightnessThreshCloudSure = 0.15;

    @Parameter(defaultValue = "0.027",
            label = " 'B_NIR' threshold at 859nm (MODIS)",
            description = "'B_NIR' threshold: 'Cloud B_NIR' set if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double ocModisBNirThresh859;

    @Parameter(defaultValue = "0.15",
            label = " 'Dark glint' threshold at 859nm for 'cloud sure' (MODIS)",
            description = "'Dark glint' threshold: 'Cloud sure' possible only if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double ocModisGlintThresh859forCloudSure;

    @Parameter(defaultValue = "0.15",
            label = " 'Dark glint' threshold at 859nm for 'cloud ambiguous' (MODIS)",
            description = "'Dark glint' threshold: 'Cloud ambiguous' possible only if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double ocModisGlintThresh859forCloudAmbiguous;

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

    @Parameter(defaultValue = "2.0",
            label = " NN cloud ambiguous lower boundary (MODIS)",
            description = " NN cloud ambiguous lower boundary (MODIS)")
    double ocModisNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.35",
            label = " NN cloud ambiguous/sure separation value (MODIS)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (MODIS)")
    double ocModisNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.2",
            label = " NN cloud sure/snow separation value (MODIS)",
            description = " NN cloud ambiguous cloud sure/snow separation value (MODIS)")
    double ocModisNNCloudSureSnowSeparationValue;


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

    @Parameter(defaultValue = "L_", valueSet = {"L_", "Lt_", "rhot_"}, label = " Prefix of input spectral bands (SeaWiFS).",
            description = "Prefix of input radiance or reflectance bands (SeaWiFS)")
    private String ocSeawifsRadianceBandPrefix;

    @Parameter(defaultValue = "true",
            label = " RhoTOA bands (VIIRS)",
            description = "Write RhoTOA bands to target product (VIIRS).")
    private boolean ocOutputViirsRhoToa = true;


    @Parameter(defaultValue = "true",
            label = " Debug bands",
            description = "Write further useful bands to target product.")
    private boolean ocOutputDebug = true;

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
    private Map<String, Object> waterClassificationParameters;


    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.Occci);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        processOccci(createOccciCloudClassificationParameters());
    }

    private void processOccci(Map<String, Object> occciCloudClassificationParameters) {
        if (retrieveMerisSeaIceEdge) {
            processOccciMerisSeaIceEdge();
            return;
        }

        Map<String, Product> occciClassifInput = new HashMap<>(4);
        computeAlgorithmInputProducts(occciClassifInput);

        // post processing input:
        // - cloud buffer
        // - cloud shadow todo (currently only for Meris)
        Map<String, Object> postProcessParameters = new HashMap<>();
        Map<String, Product> postProcessInput = new HashMap<>();
        postProcessInput.put("waterMask", waterMaskProduct);

        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            classifProduct = computeMerisClassificationProduct();
            postProcessInput.put("refl", sourceProduct);
            postProcessInput.put("ctp", ctpProduct);
            postProcessParameters.put("computeCloudShadow", true);
        } else {
            postProcessInput.put("refl", rad2reflProduct);
            if (IdepixUtils.isValidViirsProduct(sourceProduct, IdepixConstants.VIIRS_SPECTRAL_BAND_NAMES)) {
                // our VIIRS L1C products have the type 'Level 2'...
                occciCloudClassificationParameters.put("productTypeString", "VIIRS Level 1C");
            }
            classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciClassificationOp.class),
                                               occciCloudClassificationParameters, occciClassifInput);
        }

        postProcessInput.put("classif", classifProduct);

        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OccciPostProcessingOp.class),
                                                            postProcessParameters, postProcessInput);

        postProcessInput = new HashMap<>();
        postProcessInput.put("classifiedProduct", classifiedProduct);
        postProcessParameters = new HashMap<>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        final Product postProcessingProductWithCloudBuffer =
                GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                                  postProcessParameters, postProcessInput);

        setTargetProduct(postProcessingProductWithCloudBuffer);
        addBandsToTargetProduct(postProcessingProductWithCloudBuffer);
    }

    private void processOccciMerisSeaIceEdge() {
        OccciMerisSeaiceEdgeOp seaiceEdgeOp = new OccciMerisSeaiceEdgeOp();
        seaiceEdgeOp.setSourceProduct("l3", sourceProduct);
        seaiceEdgeOp.setParameterDefaultValues();
        seaiceEdgeOp.setParameter("seaiceBufferWidth", seaiceBufferWidth);
        seaiceEdgeOp.setParameter("numSeaIceNeighboursThresh", numSeaIceNeighboursThresh);

        setTargetProduct(seaiceEdgeOp.getTargetProduct());
    }

    private void computeAlgorithmInputProducts(Map<String, Product> occciClassifInput) {
        createWaterMaskProduct();
        occciClassifInput.put("waterMask", waterMaskProduct);

        if (IdepixUtils.isValidMerisProduct(sourceProduct)) {
            // MERIS:
            rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
            ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);
            pressureLiseProduct = IdepixProducts.computePressureLiseProduct(sourceProduct, rad2reflProduct,
                                                                            false, false, true, false, false, true);
            pbaroProduct = IdepixProducts.computeBarometricPressureProduct(sourceProduct, false);
        } else {
            // MODIS, SeaWIFS, VIIRS: we will convert pixelwise later, for MODIS/VIIRS inputs are TOA reflectances anyway
            rad2reflProduct = sourceProduct;
        }
        occciClassifInput.put("refl", rad2reflProduct);
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
        occciCloudClassificationParameters.put("ocModisGlintThresh859forCloudSure", ocModisGlintThresh859forCloudSure);
        occciCloudClassificationParameters.put("ocModisBNirThresh859", ocModisBNirThresh859);
        occciCloudClassificationParameters.put("ocModisGlintThresh859forCloudAmbiguous", ocModisGlintThresh859forCloudAmbiguous);
        occciCloudClassificationParameters.put("ocModisApplyOrLogicInCloudTest", ocModisApplyOrLogicInCloudTest);
        occciCloudClassificationParameters.put("ocModisNNCloudAmbiguousLowerBoundaryValue", ocModisNNCloudAmbiguousLowerBoundaryValue);
        occciCloudClassificationParameters.put("ocModisNNCloudAmbiguousSureSeparationValue", ocModisNNCloudAmbiguousSureSeparationValue);
        occciCloudClassificationParameters.put("ocModisNNCloudSureSnowSeparationValue", ocModisNNCloudSureSnowSeparationValue);

        return occciCloudClassificationParameters;
    }

    private Product computeMerisClassificationProduct() {
        Map<String, Product> classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        classificationInputProducts.put("pressure", ctpProduct);
        classificationInputProducts.put("pressureLise", pressureLiseProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
        if (processMerisSeaIce) {
            setMerisSeaIceClassificationParameters();
            return GPF.createProduct(OperatorSpi.getOperatorAlias(OccciMerisSeaiceClassificationOp.class),
                                     waterClassificationParameters, classificationInputProducts);
        } else {
            setMerisStandardClassificationParameters();
            return GPF.createProduct(OperatorSpi.getOperatorAlias(OccciMerisClassificationOp.class),
                                     waterClassificationParameters, classificationInputProducts);
        }
    }

    private void setMerisStandardClassificationParameters() {
        waterClassificationParameters = new HashMap<>();
        waterClassificationParameters.put("copyAllTiePoints", true);
        waterClassificationParameters.put("outputSchillerNNValue", outputSchillerMerisNNValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                                          schillerMerisNNCloudAmbiguousLowerBoundaryValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                                          schillerMerisNNCloudAmbiguousSureSeparationValue);
        waterClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                                          schillerMerisNNCloudSureSnowSeparationValue);
    }

    private void setMerisSeaIceClassificationParameters() {
        waterClassificationParameters = new HashMap<>();
        waterClassificationParameters.put("nnSelector", nnSelector);
        waterClassificationParameters.put("copyAllTiePoints", true);
        waterClassificationParameters.put("schillerMeris1600Threshold", schillerMeris1600Threshold);
        waterClassificationParameters.put("schillerMerisAatsrCloudIceSeparationValue", schillerMerisAatsrCloudIceSeparationValue);

        if (!productContainsPolarRegions()) {
            throw new OperatorException("Max latitude in product is < 50N and > 50S - not suitable for sea ice classification");
        }

        // provide histograms for wet ice (described in 'nn5.pdf', MPa 21.7.2016)
        final Band refl3 = rad2reflProduct.getBand("reflec_3");
        final Band refl14 = rad2reflProduct.getBand("reflec_14");
        final Band refl15 = rad2reflProduct.getBand("reflec_15");

        final String roiExpr = processMerisSeaIceAntarctic ? "latitude < -50.0" : "latitude > 50.0";
        final double[] refl3AB =
                OccciMerisSeaiceAlgorithm.computeHistogram95PercentInterval(refl3, roiExpr);
        final double[] refll4AB =
                OccciMerisSeaiceAlgorithm.computeHistogram95PercentInterval(refl14, roiExpr);
        final double[] refll5AB =
                OccciMerisSeaiceAlgorithm.computeHistogram95PercentInterval(refl15, roiExpr);

        waterClassificationParameters.put("refl3AB", refl3AB);
        waterClassificationParameters.put("refll4AB", refll4AB);
        waterClassificationParameters.put("refll5AB", refll5AB);

        waterClassificationParameters.put("applyBlueFilter", applyBlueFilter);
    }

    private boolean productContainsPolarRegions() {
        final TiePointGrid latTpg = sourceProduct.getTiePointGrid("latitude");
        final Stx stxLat = new StxFactory().create(latTpg, ProgressMonitor.NULL);
        return (processMerisSeaIceAntarctic && stxLat.getMinimum() < -50.0) ||
                (!processMerisSeaIceAntarctic && stxLat.getMaximum() > 50.0);
    }


    private void addBandsToTargetProduct(Product targetProduct) {
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyFlagBands(classifProduct, targetProduct, true);

        if (IdepixUtils.isValidMerisProduct(sourceProduct) && ocOutputMerisRadiance) {
            copySourceBands(sourceProduct, targetProduct, "radiance_");
        }
        if (IdepixUtils.isValidMerisProduct(sourceProduct) && ocOutputMerisRefl) {
            copySourceBands(rad2reflProduct, targetProduct, "reflec");
            targetProduct.setAutoGrouping("radiance:rho_toa");
        }
        if (IdepixUtils.isValidModisProduct(sourceProduct) && ocOutputCawaRefSB) {
            ocOutputRad2Refl = false;
            ocOutputEmissive = false;
            copySourceBands(sourceProduct, targetProduct, "EV_250_Aggr1km_RefSB_2");
            copySourceBands(sourceProduct, targetProduct, "EV_500_Aggr1km_RefSB_5");
            copySourceBands(sourceProduct, targetProduct, "EV_1KM_RefSB_17");
            copySourceBands(sourceProduct, targetProduct, "EV_1KM_RefSB_18");
            copySourceBands(sourceProduct, targetProduct, "EV_1KM_RefSB_19");
//            for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
//                TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
//                if ((srcTPG.getName().contains("Zenith") || srcTPG.getName().contains("Azimuth")) &&
//                        !targetProduct.containsTiePointGrid(srcTPG.getName())) {
//                    targetProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());
//                }
//            }
        }

        if (IdepixUtils.isValidModisProduct(sourceProduct) && ocOutputRad2Refl) {
            copySourceBands(rad2reflProduct, targetProduct, "RefSB");
        }

        if (ocOutputCtp && ctpProduct != null) {
            copySourceBands(ctpProduct, targetProduct, "cloud_top_press");
        }

        if (IdepixUtils.isValidModisProduct(sourceProduct) && ocOutputEmissive) {
            copySourceBands(rad2reflProduct, targetProduct, "Emissive");
        }

        if (ocOutputCtp && ctpProduct != null) {
            copySourceBands(ctpProduct, targetProduct, "cloud_top_press");
        }

        if (IdepixUtils.isValidSeawifsProduct(sourceProduct) && ocOutputSeawifsRadiance) {
//            copySourceBands(rad2reflProduct, targetProduct, "L_");
            copySourceBands(rad2reflProduct, targetProduct, "Lt_");
        }

        if (ocOutputDebug) {
            copySourceBands(classifProduct, targetProduct, "_value");
        }
        if (outputSchillerMerisNNValue) {
            copySourceBands(classifProduct, targetProduct, OccciConstants.SCHILLER_NN_OUTPUT_BAND_NAME);
        }

        if (IdepixUtils.isValidSeawifsProduct(sourceProduct) && ocOutputSeawifsRefl) {
            copySourceBands(classifProduct, targetProduct, "_refl");
        }

        if (ocOutputViirsRhoToa) {
            copySourceBands(sourceProduct, targetProduct, "rhot");
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
