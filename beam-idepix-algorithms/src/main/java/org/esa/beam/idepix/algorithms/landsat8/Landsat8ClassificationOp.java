package org.esa.beam.idepix.algorithms.landsat8;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Landsat 8 pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.landsat8.classification",
        version = "2.3",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "Landsat 8 water pixel classification operator.")
public class Landsat8ClassificationOp extends Operator {

    // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
    private static final float WATER_MASK_SOUTH_BOUND = -58.0f;
    // todo - temporary bands; should be removed when algorithm is stable (mp/10.09.2015)
    private static final String NN_RESULT_BAND_NAME = "nnResult";
    private static final String DARK_GLINT_TEST_ONE_BAND_NAME = "darkGlintTest1";
    private static final String DARK_Glint_TEST_TWO_BAND_NAME = "darkGlintTest2";


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

    @Parameter(defaultValue = "0.15",
            label = "Dark Glint Test 1",
            description = "'Dark glint' threshold: Cloud possible only if refl > THRESH.")
    private double darkGlintThreshTest1;

    @Parameter(defaultValue = "865",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 2 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over water.",
            label = "Wavelength used for Dark Glint Test 1")
    private int darkGlintThreshTest1Wavelength;

    @Parameter(defaultValue = "0.15",
            label = "Dark Glint Test 2",
            description = "'Dark glint' threshold: Cloud possible only if refl > THRESH.")
    private double darkGlintThreshTest2;

    @Parameter(defaultValue = "1610",
            valueSet = {"440", "480", "560", "655", "865", "1610", "2200", "590", "1370", "10895", "12005"},
            description = "Wavelength 2 for whiteness computation (wh = R(wvl_1) / R(wvl_2)) over water.",
            label = "Wavelength used for Dark Glint Test 2")
    private int darkGlintThreshTest2Wavelength;

    // SHIMEZ parameters
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
    @Parameter(defaultValue = "true",
            label = " Apply CLOST cloud test")
    private boolean applyClostCloudTest;

    @Parameter(defaultValue = "0.00001",
            description = "Threshold A for CLOST cloud test: cloud if coastal_aerosol*blue*panchromatic*cirrus > A.",
            label = "Threshold A for CLOST cloud test")
    private double clostThresh;

    // OTSU parameters:
    @Parameter(defaultValue = "true",
            label = " Apply OTSU cloud test")
    private boolean applyOtsuCloudTest;

    // currently not used todo: clarify if needed
//    @Parameter(defaultValue = "2.0",
//            label = "Alternative for first classification boundary ")
//    private double alternativeFirstClassBoundary;
//    @Parameter(defaultValue = "3.6",
//            label = "Alternative for second classification boundary ")
//    private double alternativeSecondClassBoundary;
//    @Parameter(defaultValue = "4.2",
//            label = "Alternative for third classification boundary ")
//    private double alternativeThirdClassBoundary;

    @Parameter(defaultValue = "ALL", valueSet = {"ALL", "LAND",
            "WATER", "WATER_NOTIDAL", "WATER_USE_THERMAL", "WATER_NOTIDAL_USE_THERMAL"},
            label = "Neural Net to be applied",
            description = "The Neural Net which will be applied.")
    private NNSelector nnSelector;

    @Parameter(defaultValue = "1.95",
            label = "NN cloud ambiguous lower boundary ")
    private double nnCloudAmbiguousLowerBoundaryValue;
    @Parameter(defaultValue = "3.45",
            label = "NN cloud ambiguous/sure separation value ")
    private double nnCloudAmbiguousSureSeparationValue;
    @Parameter(defaultValue = "4.3",
            label = "NN cloud sure / snow separation value ")
    private double nnCloudSureSnowSeparationValue;


    @SourceProduct(alias = "l8source", description = "The source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "otsu", optional = true, description = "The OTSU product.")
    private Product otsuProduct;

    @SourceProduct(alias = "waterMask", optional = true)
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Band[] l8ReflectanceBands;
    private Band landWaterBand;
    private Band clostBand;
    private Band otsuBand;

    static final int L8_F_DESIGNATED_FILL = 0;
    static final int L8_F_WATER_CONFIDENCE_HIGH = 5;  // todo: do we need this?
    private String cloudFlagBandName;


//    private static final String LANDSAT8_CLOUD_NET_NAME = "8x5x3_342.3.net";
    private ThreadLocal<SchillerNeuralNetWrapper> landsat8CloudNet;

    @Override
    public void initialize() throws OperatorException {
        initCloudNet();
        setBands();
        createTargetProduct();

        if (waterMaskProduct != null) {
            landWaterBand = waterMaskProduct.getBand("land_water_fraction");
        }
        if (otsuProduct != null) {
            clostBand = otsuProduct.getBand(ClostOp.CLOST_BAND_NAME);
            otsuBand = otsuProduct.getBand(OtsuBinarizeOp.OTSU_BINARY_BAND_NAME);
        }
    }

    public void setBands() {
        l8ReflectanceBands = new Band[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8ReflectanceBands[i] = sourceProduct.getBand(Landsat8Constants.LANDSAT8_SPECTRAL_BAND_NAMES[i]);
        }
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        // shall be the only target band!!
        cloudFlagBandName = IdepixUtils.IDEPIX_CLOUD_FLAGS;
        Band cloudFlagBand = targetProduct.addBand(cloudFlagBandName, ProductData.TYPE_INT32);
        FlagCoding flagCoding = Landsat8Utils.createLandsat8FlagCoding(cloudFlagBandName);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        // todo - temporarily added the bands for testing. Shall be removed later. (mp/08.09.2015)
        targetProduct.addBand(NN_RESULT_BAND_NAME, ProductData.TYPE_FLOAT32);
        final Band dgt1 = targetProduct.addBand(DARK_GLINT_TEST_ONE_BAND_NAME, ProductData.TYPE_INT8);
        dgt1.setDescription(String.format("Dark Glint Test 1 @%d", darkGlintThreshTest1Wavelength));
        final Band dgt2 = targetProduct.addBand(DARK_Glint_TEST_TWO_BAND_NAME, ProductData.TYPE_INT8);
        dgt2.setDescription(String.format("Dark Glint Test 2 @%d", darkGlintThreshTest2Wavelength));

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // MERIS variables
        Tile waterFractionTile = null;
        if (waterMaskProduct != null) {
            waterFractionTile = getSourceTile(landWaterBand, rectangle);
        }

        Tile clostTile = null;
        Tile otsuTile = null;
        if (otsuProduct != null) {
            clostTile = getSourceTile(clostBand, rectangle);
            otsuTile = getSourceTile(otsuBand, rectangle);
        }

        final Band l8FlagBand = sourceProduct.getBand(Landsat8Constants.Landsat8_FLAGS_NAME);
        final Tile l8FlagTile = getSourceTile(l8FlagBand, rectangle);

        Tile[] l8ReflectanceTiles = new Tile[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8ReflectanceTiles[i] = getSourceTile(l8ReflectanceBands[i], rectangle);
        }

        final Tile cloudFlagTargetTile = targetTiles.get(targetProduct.getBand(cloudFlagBandName));
        final Tile nnResultTargetTile = targetTiles.get(targetProduct.getBand(NN_RESULT_BAND_NAME));
        final Tile darkGlintTest1TargetTile = targetTiles.get(targetProduct.getBand(DARK_GLINT_TEST_ONE_BAND_NAME));
        final Tile darkGlintTest2TargetTile = targetTiles.get(targetProduct.getBand(DARK_Glint_TEST_TWO_BAND_NAME));

        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    // set up pixel properties for given instruments...
                    Landsat8Algorithm landsat8Algorithm = createLandsat8Algorithm(
                            l8ReflectanceTiles,
                            l8FlagTile,
                            waterFractionTile,
                            clostTile,
                            otsuTile,
                            x, y
                    );

                    setCloudFlag(cloudFlagTargetTile, x, y, landsat8Algorithm);
                    nnResultTargetTile.setSample(x, y, landsat8Algorithm.getNnResult()[0]);
                    darkGlintTest1TargetTile.setSample(x, y, landsat8Algorithm.isDarkGlintTest1());
                    darkGlintTest2TargetTile.setSample(x, y, landsat8Algorithm.isDarkGlintTest2());
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide Landsat8 cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean isLandPixel(int x, int y, Tile l8FlagTile, int waterFraction) {
        if (getGeoPos(x, y).lat > WATER_MASK_SOUTH_BOUND) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return !l8FlagTile.getSampleBit(x, y, L8_F_WATER_CONFIDENCE_HIGH); // todo: check!
            }
        } else {
            return !l8FlagTile.getSampleBit(x, y, L8_F_WATER_CONFIDENCE_HIGH);  // todo
        }
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private void setCloudFlag(Tile targetTile, int x, int y, Landsat8Algorithm l8Algorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, Landsat8Constants.F_INVALID, l8Algorithm.isInvalid());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SHIMEZ, applyShimezCloudTest && l8Algorithm.isCloudShimez());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SHIMEZ_BUFFER, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_HOT, applyHotCloudTest && l8Algorithm.isCloudHot());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_HOT_BUFFER, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_OTSU, applyOtsuCloudTest && l8Algorithm.isCloudOtsu());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_OTSU_BUFFER, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_CLOST, applyClostCloudTest && l8Algorithm.isCloudClost());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_CLOST_BUFFER, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SURE, l8Algorithm.isCloud());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_AMBIGUOUS, l8Algorithm.isCloudAmbiguous());
        targetTile.setSample(x, y, Landsat8Constants.F_SNOW_ICE, l8Algorithm.isSnowIce());
        targetTile.setSample(x, y, Landsat8Constants.F_BRIGHT, l8Algorithm.isBright());
        targetTile.setSample(x, y, Landsat8Constants.F_WHITE, l8Algorithm.isWhite());
        targetTile.setSample(x, y, Landsat8Constants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, Landsat8Constants.F_GLINTRISK, false);   // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_COASTLINE, false);   // TODO
        targetTile.setSample(x, y, Landsat8Constants.F_LAND, l8Algorithm.isLand());         // TODO
    }


    private Landsat8Algorithm createLandsat8Algorithm(Tile[] l8ReflectanceTiles,
                                                      Tile l8FlagTile,
                                                      Tile waterFractionTile,
                                                      Tile clostTile,
                                                      Tile otsuTile,
                                                      int x, int y) {
        Landsat8Algorithm l8Algorithm = new Landsat8Algorithm();

        boolean isLand = false;
        if (waterMaskProduct != null) {
            final int waterFraction = waterFractionTile.getSampleInt(x, y);
            isLand = isLandPixel(x, y, l8FlagTile, waterFraction);
        }

        float[] l8Reflectance = new float[Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            l8Reflectance[i] = l8ReflectanceTiles[i].getSampleFloat(x, y);
        }

        l8Algorithm.setInvalid(l8FlagTile.getSampleBit(x, y, L8_F_DESIGNATED_FILL));
        l8Algorithm.setL8SpectralBandData(l8Reflectance);
        l8Algorithm.setIsLand(isLand);

        l8Algorithm.setNnCloudAmbiguousLowerBoundaryValue(nnCloudAmbiguousLowerBoundaryValue);
        l8Algorithm.setNnCloudAmbiguousSureSeparationValue(nnCloudAmbiguousSureSeparationValue);
        l8Algorithm.setNnCloudSureSnowSeparationValue(nnCloudSureSnowSeparationValue);

        l8Algorithm.setApplyShimezCloudTest(applyShimezCloudTest);
        l8Algorithm.setShimezDiffThresh(shimezDiffThresh);
        l8Algorithm.setShimezMeanThresh(shimezMeanThresh);
        l8Algorithm.setHotThresh(hotThresh);
        l8Algorithm.setApplyHotCloudTest(applyHotCloudTest);
        l8Algorithm.setClostThresh(clostThresh);
        l8Algorithm.setApplyClostCloudTest(applyClostCloudTest);
        l8Algorithm.setApplyOtsuCloudTest(applyOtsuCloudTest);
        if (otsuProduct != null) {
            l8Algorithm.setClostValue(clostTile.getSampleFloat(x, y));
            l8Algorithm.setOtsuValue(otsuTile.getSampleFloat(x, y));
        }

        l8Algorithm.setBrightnessBandLand(brightnessBandLand);
        l8Algorithm.setBrightnessThreshLand(brightnessThreshLand);
        l8Algorithm.setBrightnessBand1Water(brightnessBand1Water);
        l8Algorithm.setBrightnessWeightBand1Water(brightnessWeightBand1Water);
        l8Algorithm.setBrightnessBand2Water(brightnessBand2Water);
        l8Algorithm.setBrightnessWeightBand2Water(brightnessWeightBand2Water);
        l8Algorithm.setBrightnessThreshWater(brightnessThreshWater);
        l8Algorithm.setWhitenessBand1Land(whitenessBand1Land);
        l8Algorithm.setWhitenessBand2Land(whitenessBand2Land);
        l8Algorithm.setWhitenessThreshLand(whitenessThreshLand);
        l8Algorithm.setWhitenessBand1Water(whitenessBand1Water);
        l8Algorithm.setWhitenessBand2Water(whitenessBand2Water);
        l8Algorithm.setWhitenessThreshWater(whitenessThreshWater);

        l8Algorithm.setDarkGlintThresholdTest1(darkGlintThreshTest1);
        l8Algorithm.setDarkGlintThresholdTest1Wvl(darkGlintThreshTest1Wavelength);
        l8Algorithm.setDarkGlintThresholdTest2(darkGlintThreshTest2);
        l8Algorithm.setDarkGlintThresholdTest2Wvl(darkGlintThreshTest2Wavelength);

        double[] netResult = calcNeuralNetResult(l8Reflectance);
        l8Algorithm.setNnResult(netResult);

        return l8Algorithm;
    }

    private double[] calcNeuralNetResultOld(float[] l8Reflectance) {
        SchillerNeuralNetWrapper neuralNetWrapper = landsat8CloudNet.get();
        double[] cloudNetInput = neuralNetWrapper.getInputVector();
        for (int i = 0; i < Landsat8Constants.LANDSAT8_NUM_SPECTRAL_BANDS; i++) {
            double sqrtRefl = Math.sqrt(l8Reflectance[i]);
            if (i == 8) {
                // cirrus band can have negative values
                // --> not allowing values lower as the net minimum
                cloudNetInput[8] = Math.max(sqrtRefl, neuralNetWrapper.getNeuralNet().getInmin()[8]);
            } else {
                cloudNetInput[i] = sqrtRefl;
            }
        }
        return neuralNetWrapper.getNeuralNet().calc(cloudNetInput);
    }

    private double[] calcNeuralNetResult(float[] l8Reflectance) {
        SchillerNeuralNetWrapper neuralNetWrapper = landsat8CloudNet.get();
        double[] cloudNetInput = neuralNetWrapper.getInputVector();
        for (int i = 0; i < 7; i++) {
            cloudNetInput[i] = Math.sqrt(l8Reflectance[i]);
        }
        cloudNetInput[7] = Math.max(Math.sqrt((double) l8Reflectance[7]), neuralNetWrapper.getNeuralNet().getInmin()[7]);
        if (nnSelector.getLabel().endsWith("_USE_THERMAL")) {
            cloudNetInput[8] = Math.sqrt(l8Reflectance[9]);
            cloudNetInput[9] = Math.sqrt(l8Reflectance[10]);
        }
        return neuralNetWrapper.getNeuralNet().calc(cloudNetInput);
    }


    private void initCloudNet() {
//        try (InputStream cloudNet = getClass().getResourceAsStream(LANDSAT8_CLOUD_NET_NAME)) {
        // use selected new NN (20151119), chosen from 6 different nets:
        try (InputStream cloudNet = getClass().getResourceAsStream(nnSelector.getNnFileName())) {
            landsat8CloudNet = SchillerNeuralNetWrapper.create(cloudNet);
        } catch (IOException e) {
            throw new OperatorException("Cannot read cloud neural net: " + e.getMessage());
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(Landsat8ClassificationOp.class);
        }
    }

}
