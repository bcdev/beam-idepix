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
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Rectangle;
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
                  version = "2.0.3-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2008, 2012 by Brockmann Consult",
                  description = "Basic operator for pixel classification from MERIS, AATSR or VGT data.")
public abstract class GlobAlbedoClassificationOp extends Operator {

    @SourceProduct(alias = "gal1b", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "false", label = "Compute only the flag band")
    boolean gaComputeFlagsOnly;
    @Parameter(defaultValue = "true", label = "Copy input radiance bands")
    boolean gaCopyRadiances;
    @Parameter(defaultValue = "false", label = " Copy Rayleigh Corrected Reflectances (MERIS)")
    boolean gaCopyRayleigh = false;
    @Parameter(defaultValue = "false", label = "Copy subset of input radiance bands (MERIS/AATSR synergy)")
    boolean gaCopySubsetOfRadiances;
    @Parameter(defaultValue = "false", label = "Copy MERIS TOA reflectance bands (MERIS/AATSR synergy)")
    boolean gaCopyMerisToaReflectances;
    @Parameter(defaultValue = "false", label = "Copy pressure bands (MERIS)")
    boolean gaCopyPressure;
    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    int gaCloudBufferWidth;
    @Parameter(defaultValue = "true", label = "Use land-water flag from L1b product instead (faster)")
    boolean gaUseL1bLandWaterFlag;
    @Parameter(defaultValue = "false", label = " Use the LC cloud buffer algorithm")
    boolean gaLcCloudBuffer = false;
    @Parameter(defaultValue = "false", label = " Apply 'Blue dense' cloud algorithm  (MERIS)")
    boolean gaApplyBlueDenseCloudAlgorithm;
    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    boolean gaUseWaterMaskFraction = true;
    @Parameter(defaultValue = "false", label = "Copy input annotation bands (VGT)")
    boolean gaCopyAnnotations;
    @Parameter(defaultValue = "false", label = " Use the NN based Schiller cloud algorithm")
    boolean gaComputeSchillerClouds = false;
    @Parameter(defaultValue = "false", label = " Use forward view for cloud flag determination (AATSR)")
    boolean gaUseAatsrFwardForClouds;
    @Parameter(defaultValue = "false", label = " Use Istomena et al. algorithm for sea ice determination (AATSR)")
    boolean gaUseIstomenaSeaIceAlgorithm;
    @Parameter(defaultValue = "true", label = " Use Schiller algorithm for sea ice determination outside AATSR")
    boolean gaUseSchillerSeaIceAlgorithm;
    @Parameter(defaultValue = "2.0", label = " AATSR refl[1600] threshold for sea ice determination (MERIS/AATSR)")
    float gaRefl1600SeaIceThresh;
    @Parameter(defaultValue = "false", label = "Write Schiller Seaice Output bands (MERIS 1600 and Cloud/Seaice prob)")
    boolean gaWriteSchillerSeaiceNetBands;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = "Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    int wmResolution;

    public static final String SCHILLER_SEAICE_INNER_NET_NAME = "6_1271.6.net";
    public static final String SCHILLER_SEAICE_OUTER_NET_NAME = "6_912.1.net";

    String seaiceInnerNeuralNetString;
    String seaiceOuterNeuralNetString;

    NNffbpAlphaTabFast seaiceInnerNeuralNet;
    NNffbpAlphaTabFast seaiceOuterNeuralNet;

    WatermaskClassifier classifier;

    WatermaskStrategy strategy = null;

    static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water
    Band temperatureBand;
    Band cloudFlagBand;
    Band brightBand;
    Band whiteBand;
    Band brightWhiteBand;
    Band spectralFlatnessBand;
    Band ndviBand;
    Band ndsiBand;
    Band glintRiskBand;
    Band radioLandBand;
    Band radioWaterBand;
    Band pressureBand;
    Band pbaroOutputBand;
    Band schillerSeaiceMeris1600Band;
    Band schillerSeaiceCloudProbBand;

    Band p1OutputBand;

    Band pscattOutputBand;
    static final int MERIS_L1B_F_LAND = 4;
    static final int AATSR_L1B_F_LAND = 0;

    @Override
    public void initialize() throws OperatorException {
        setBands();

        readSchillerSeaiceNets();
        setWatermaskStrategy();
        createTargetProduct();
        extendTargetProduct();
    }

    abstract void setBands();

    abstract void extendTargetProduct();

    private void readSchillerSeaiceNets() {
        final InputStream innerNeuralNetStream = getClass().getResourceAsStream(SCHILLER_SEAICE_INNER_NET_NAME);
        seaiceInnerNeuralNetString = readNeuralNetFromStream(innerNeuralNetStream);
        final InputStream outerNeuralNetStream = getClass().getResourceAsStream(SCHILLER_SEAICE_OUTER_NET_NAME);
        seaiceOuterNeuralNetString = readNeuralNetFromStream(outerNeuralNetStream);

        try {
            seaiceInnerNeuralNet = new NNffbpAlphaTabFast(seaiceInnerNeuralNetString);
            seaiceOuterNeuralNet = new NNffbpAlphaTabFast(seaiceOuterNeuralNetString);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller seaice neural nets: " + e.getMessage());
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

    private String readNeuralNetFromStream(InputStream neuralNetStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(neuralNetStream));
        try {
            String line = reader.readLine();
            final StringBuilder sb = new StringBuilder();
            while (line != null) {
                // have to append line terminator, cause it's not included in line
                sb.append(line).append('\n');
                line = reader.readLine();
            }
            return sb.toString();
        } catch (IOException ioe) {
            throw new OperatorException("Could not initialize neural net", ioe);
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createIdepixFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (!gaComputeFlagsOnly) {
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

        if (gaWriteSchillerSeaiceNetBands) {
            schillerSeaiceCloudProbBand = targetProduct.addBand("schiller_seaice_cloud_prob", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(schillerSeaiceCloudProbBand, "Schiller Seaice Cloud Prob Value", "", IdepixConstants.NO_DATA_VALUE,
                                             true);
            schillerSeaiceMeris1600Band = targetProduct.addBand("schiller_seaice_meris1600", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(schillerSeaiceMeris1600Band, "Schiller Seaice MERIS1600 Value", "", IdepixConstants.NO_DATA_VALUE,
                                             true);
        }

        // new bit masks:
        IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

    }

    void setPixelSamples(Band band, Tile targetTile, Tile p1Tile, Tile pbaroTile, Tile pscattTile, int y, int x,
                         GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            if (x == 500 && y == 1200) {
                System.out.println("x = " + x);
            }
            if (x == 560 && y == 1200) {
                System.out.println("x = " + x);
            }
            targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue());
//            targetTile.setSample(x, y, ((GlobAlbedoMerisAatsrSynergyAlgorithm) globAlbedoAlgorithm).getBrr442ThreshMeris()); // test!
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
        } else if (band == pressureBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.pressureValue());
        } else if (band == pbaroOutputBand) {
            targetTile.setSample(x, y, pbaroTile.getSampleFloat(x, y));
        } else if (band == p1OutputBand) {
            targetTile.setSample(x, y, p1Tile.getSampleFloat(x, y));
        } else if (band == pscattOutputBand) {
            targetTile.setSample(x, y, pscattTile.getSampleFloat(x, y));
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricWaterValue());
        } else if (band == schillerSeaiceMeris1600Band) {
            targetTile.setSample(x, y, ((GlobAlbedoMerisAatsrSynergyAlgorithm) globAlbedoAlgorithm).getSchillerRefl1600Meris());
        } else if (band == schillerSeaiceCloudProbBand) {
            targetTile.setSample(x, y, ((GlobAlbedoMerisAatsrSynergyAlgorithm) globAlbedoAlgorithm).getSchillerSeaiceCloudProb());
        }
    }

    void setCloudFlag(Tile targetTile, int y, int x, GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, IdepixConstants.F_INVALID, globAlbedoAlgorithm.isInvalid());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, globAlbedoAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, globAlbedoAlgorithm.isClearLand());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, globAlbedoAlgorithm.isClearWater());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, globAlbedoAlgorithm.isClearSnow());
        targetTile.setSample(x, y, IdepixConstants.F_LAND, globAlbedoAlgorithm.isLand());
        targetTile.setSample(x, y, IdepixConstants.F_WATER, globAlbedoAlgorithm.isWater());
        targetTile.setSample(x, y, IdepixConstants.F_SEAICE, globAlbedoAlgorithm.isSeaIce());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, globAlbedoAlgorithm.isBright());
        targetTile.setSample(x, y, IdepixConstants.F_WHITE, globAlbedoAlgorithm.isWhite());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, globAlbedoAlgorithm.isBrightWhite());
        targetTile.setSample(x, y, IdepixConstants.F_HIGH, globAlbedoAlgorithm.isHigh());
        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, globAlbedoAlgorithm.isVegRisk());
        targetTile.setSample(x, y, IdepixConstants.F_GLINT_RISK, globAlbedoAlgorithm.isGlintRisk());
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
            super(GlobAlbedoClassificationOp.class, "idepix.globalbedo.classification");
        }
    }
}
