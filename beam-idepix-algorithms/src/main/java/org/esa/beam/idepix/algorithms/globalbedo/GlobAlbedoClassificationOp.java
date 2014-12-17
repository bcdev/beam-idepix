package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
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
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.pixel.AbstractPixelProperties;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Basic operator for GlobAlbedo pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification",
        version = "2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2008, 2012 by Brockmann Consult",
        description = "Basic operator for pixel classification from MERIS, AATSR or VGT data.")
public abstract class GlobAlbedoClassificationOp extends Operator {

    @SourceProduct(alias = "gal1b", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;


    // Globalbedo user options
    @Parameter(defaultValue = "true",
            label = " Write TOA Radiances to the target product",
            description = " Write TOA Radiances to the target product (has only effect for MERIS L1b products)")
    boolean gaCopyRadiances = true;

    @Parameter(defaultValue = "true",
            label = " Write TOA Reflectances to the target product",
            description = " Write TOA Reflectances to the target product")
    boolean gaCopyToaReflectances = true;


    @Parameter(defaultValue = "false",
            label = " Write Feature Values to the target product",
            description = " Write all Feature Values to the target product")
    boolean gaCopyFeatureValues = false;

    @Parameter(defaultValue = "false",
            label = " Write input annotation bands to the target product (VGT only)",
            description = " Write input annotation bands to the target product (has only effect for VGT L1b products)")
    boolean gaCopyAnnotations;

    @Parameter(defaultValue = "true",
            label = " Apply alternative Schiller NN for MERIS cloud classification",
            description = " Apply Schiller NN for MERIS cloud classification (has only effect for MERIS L1b products)")
    boolean gaApplyMERISAlternativeSchillerNN;

    @Parameter(defaultValue = "false",
               label = " Apply alternative Schiller NN for MERIS cloud classification purely (not combined with previous approach)",
               description = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean gaApplyMERISAlternativeSchillerNNPure;

    @Parameter(defaultValue = "1.1",
               label = " Alternative Schiller NN cloud ambiguous lower boundary (MERIS only)",
               description = " Alternative Schiller NN cloud ambiguous lower boundary (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
               label = " Alternative Schiller NN cloud ambiguous/sure separation value (MERIS only)",
               description = " Alternative Schiller NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
               label = " Alternative Schiller NN cloud sure/snow separation value (MERIS only)",
               description = " Alternative Schiller NN cloud ambiguous cloud sure/snow separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Apply Schiller NN for VGT cloud classification",
            description = " Apply Schiller NN for VGT cloud classification (has only effect for VGT L1b products)")
    boolean gaApplyVGTSchillerNN;

    @Parameter(defaultValue = "1.1",
            label = " Schiller NN cloud ambiguous lower boundary (VGT only)",
            description = " Schiller NN cloud ambiguous lower boundary (has only effect for VGT L1b products)")
    double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " Schiller NN cloud ambiguous/sure separation value (VGT only)",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for VGT L1b products)")
    double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " Schiller NN cloud sure/snow separation value (VGT only)",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value (has only effect for VGT L1b products)")
    double gaSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "2",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    int gaCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"},
            label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution of the used SRTM land-water mask in m/pixel")
    int wmResolution;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead",
            description = "Use land-water flag from L1b product instead")
    boolean gaUseL1bLandWaterFlag;

    public static final String SCHILLER_VGT_NET_NAME = "3x2x2_341.8.net";
    ThreadLocal<SchillerNeuralNetWrapper> vgtNeuralNet;

    public static final String SCHILLER_MERIS_LAND_NET_NAME = "11x8x5x3_1062.5_land.net";
    ThreadLocal<SchillerNeuralNetWrapper> merisLandNeuralNet;

    WatermaskClassifier classifier;

    WatermaskStrategy strategy = null;

    static final int MERIS_L1B_F_LAND = 4;
    static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water

    Band cloudFlagBand;
    Band temperatureBand;
    Band brightBand;
    Band whiteBand;
    Band brightWhiteBand;
    Band spectralFlatnessBand;
    Band ndviBand;
    Band ndsiBand;
    Band glintRiskBand;
    Band radioLandBand;

    Band radioWaterBand;

    @Override
    public void initialize() throws OperatorException {
        setBands();

        readSchillerNeuralNets();
        setWatermaskStrategy();
        createTargetProduct();
        extendTargetProduct();
    }

    abstract void setBands();

    abstract void extendTargetProduct();

    private void readSchillerNeuralNets() {
        try (InputStream merisLandIS = getClass().getResourceAsStream(SCHILLER_MERIS_LAND_NET_NAME);
             InputStream vgtLandIS = getClass().getResourceAsStream(SCHILLER_VGT_NET_NAME)) {
            merisLandNeuralNet = SchillerNeuralNetWrapper.create(merisLandIS);
            vgtNeuralNet = SchillerNeuralNetWrapper.create(vgtLandIS);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    void setWatermaskStrategy() {
        try {
            classifier = new WatermaskClassifier(wmResolution, 3, 3);
        } catch (IOException e) {
            getLogger().warning("Watermask classifier could not be initialized - fallback mode is used.");
        }
        strategy = new DefaultWatermaskStrategy(classifier);
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT32);
        FlagCoding flagCoding = IdepixUtils.createIdepixFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (gaCopyFeatureValues) {
            brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", IdepixConstants.NO_DATA_VALUE,
                    true);
            temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(temperatureBand, "Temperature", "K", IdepixConstants.NO_DATA_VALUE, true);
            spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl",
                    IdepixConstants.NO_DATA_VALUE, true);
            ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            glintRiskBand = targetProduct.addBand("glint_risk_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(glintRiskBand, "GLINT_RISK", "dl", IdepixConstants.NO_DATA_VALUE, true);
            radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", IdepixConstants.NO_DATA_VALUE,
                    true);
            radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "",
                    IdepixConstants.NO_DATA_VALUE, true);
        }

        // new bit masks:
        IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

    }

    void setPixelSamples(Band band, Tile targetTile, int y, int x,
                         GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue());
        } else if (band == whiteBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.whiteValue());
        } else if (band == brightWhiteBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue() + globAlbedoAlgorithm.whiteValue());
        } else if (band == temperatureBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.temperatureValue());
        } else if (band == spectralFlatnessBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.spectralFlatnessValue());
        } else if (band == ndviBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.ndviValue());
        } else if (band == ndsiBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.ndsiValue());
        } else if (band == glintRiskBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.glintRiskValue());
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricWaterValue());
        }
    }

    void setCloudFlag(Tile targetTile, int y, int x, GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, IdepixConstants.F_INVALID, globAlbedoAlgorithm.isInvalid());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, globAlbedoAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, globAlbedoAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, globAlbedoAlgorithm.isClearLand());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, globAlbedoAlgorithm.isClearWater());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, globAlbedoAlgorithm.isClearSnow());
        targetTile.setSample(x, y, IdepixConstants.F_LAND, globAlbedoAlgorithm.isLand());
        targetTile.setSample(x, y, IdepixConstants.F_WATER, globAlbedoAlgorithm.isWater());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, globAlbedoAlgorithm.isBright());
        targetTile.setSample(x, y, IdepixConstants.F_WHITE, globAlbedoAlgorithm.isWhite());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, globAlbedoAlgorithm.isBrightWhite());
        targetTile.setSample(x, y, IdepixConstants.F_HIGH, globAlbedoAlgorithm.isHigh());
        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, globAlbedoAlgorithm.isVegRisk());
        targetTile.setSample(x, y, IdepixConstants.F_SEAICE, globAlbedoAlgorithm.isSeaIce());
    }

    void setCloudBuffer(String bandName, Tile targetTile, Rectangle rectangle) {
        if (bandName.equals(IdepixUtils.IDEPIX_CLOUD_FLAGS)) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    if (targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD)) {
                        int LEFT_BORDER = Math.max(x - gaCloudBufferWidth, rectangle.x);
                        int RIGHT_BORDER = Math.min(x + gaCloudBufferWidth, rectangle.x + rectangle.width - 1);
                        int TOP_BORDER = Math.max(y - gaCloudBufferWidth, rectangle.y);
                        int BOTTOM_BORDER = Math.min(y + gaCloudBufferWidth, rectangle.y + rectangle.height - 1);
                        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                                if (!targetTile.getSampleBit(i, j, IdepixConstants.F_INVALID)) {
                                    targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void setIsWater(byte watermask, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermask == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermask == WatermaskClassifier.WATER_VALUE;
        }
        pixelProperties.setIsWater(isWater);
    }

    void setIsWaterByFraction(byte watermaskFraction, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermaskFraction == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermaskFraction >= WATERMASK_FRACTION_THRESH;
        }
        pixelProperties.setIsWater(isWater);
    }

    // currently not used
//    private void printPixelFeatures(GlobAlbedoAlgorithm algorithm) {
//        System.out.println("bright            = " + algorithm.brightValue());
//        System.out.println("white             = " + algorithm.whiteValue());
//        System.out.println("temperature       = " + algorithm.temperatureValue());
//        System.out.println("spec_flat         = " + algorithm.spectralFlatnessValue());
//        System.out.println("ndvi              = " + algorithm.ndviValue());
//        System.out.println("ndsi              = " + algorithm.ndsiValue());
//        System.out.println("pressure          = " + algorithm.pressureValue());
//        System.out.println("cloudy            = " + algorithm.isCloud());
//        System.out.println("clear snow        = " + algorithm.isClearSnow());
//        System.out.println("radiometric_land  = " + algorithm.radiometricLandValue());
//        System.out.println("radiometric_water = " + algorithm.radiometricWaterValue());
//    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoClassificationOp.class);
        }
    }
}
