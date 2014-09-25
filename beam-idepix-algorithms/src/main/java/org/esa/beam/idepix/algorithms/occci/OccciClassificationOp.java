package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Operator for OC-CCI MODIS cloud screening
 * todo: implement (see GlobAlbedoMerisClassificationOp)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "OC-CCI pixel classification operator.",
                  internal = true)
public class OccciClassificationOp extends PixelOperator {

    @SourceProduct(alias = "refl", description = "MODIS/SeaWiFS L1b reflectance product")
    private Product reflProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @Parameter(description = "Defines the sensor type to use. If the parameter is not set, the product type defined by the input file is used.")
    String productTypeString;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;

    @Parameter(defaultValue = "false",
               label = " Debug bands",
               description = "Write further useful bands to target product.")
    private boolean ocOutputDebug = false;

    @Parameter(defaultValue = "true",
               label = " Reflectance bands",
               description = "Write TOA reflectance to target product (SeaWiFS).")
    private boolean ocOutputSeawifsRefl;


    private SensorContext sensorContext;

    public static final String SCHILLER_MODIS_WATER_NET_NAME = "9x7x5x3_130.3_water.net";
    public static final String SCHILLER_MODIS_LAND_NET_NAME = "8x6x4x2_290.4_land.net";
    public static final String SCHILLER_MODIS_ALL_NET_NAME = "9x7x5x3_319.7_all.net";
    public static final String SCHILLER_SEAWIFS_NET_NAME = "6x3_166.0.net";

    String modisWaterNeuralNetString;
    String modisLandNeuralNetString;
    String modisAllNeuralNetString;
    String seawifsNeuralNetString;
    NNffbpAlphaTabFast modisWaterNeuralNet;
    NNffbpAlphaTabFast modisLandNeuralNet;
    NNffbpAlphaTabFast modisAllNeuralNet;
    NNffbpAlphaTabFast seawifsNeuralNet;


    @Override
    protected void prepareInputs() throws OperatorException {
        readSchillerNets();
        sensorContext = SensorContextFactory.fromTypeString(getProductTypeString());
        sensorContext.init(reflProduct);
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        OccciAlgorithm algorithm;

        switch (sensorContext.getSensor()) {
            case MODIS:
                algorithm = createOccciAlgorithm(x, y, sourceSamples, targetSamples);
                break;
            case SEAWIFS:
                algorithm = createOccciAlgorithm(x, y, sourceSamples, targetSamples);
                break;
            default:
                throw new IllegalArgumentException("Invalid sensor: " + sensorContext.getSensor());
        }
        setClassifFlag(targetSamples, algorithm);
    }

    private void readSchillerNets() {
        final InputStream modisWaterNeuralNetStream = getClass().getResourceAsStream(SCHILLER_MODIS_WATER_NET_NAME);
        modisWaterNeuralNetString = readNeuralNetFromStream(modisWaterNeuralNetStream);
        final InputStream modisLandNeuralNetStream = getClass().getResourceAsStream(SCHILLER_MODIS_LAND_NET_NAME);
        modisLandNeuralNetString = readNeuralNetFromStream(modisLandNeuralNetStream);
        final InputStream modisAllNeuralNetStream = getClass().getResourceAsStream(SCHILLER_MODIS_ALL_NET_NAME);
        modisAllNeuralNetString = readNeuralNetFromStream(modisAllNeuralNetStream);
        final InputStream seawifsNeuralNetStream = getClass().getResourceAsStream(SCHILLER_SEAWIFS_NET_NAME);
        seawifsNeuralNetString = readNeuralNetFromStream(seawifsNeuralNetStream);

        try {
            modisWaterNeuralNet = new NNffbpAlphaTabFast(modisWaterNeuralNetString);
            modisLandNeuralNet = new NNffbpAlphaTabFast(modisLandNeuralNetString);
            modisAllNeuralNet = new NNffbpAlphaTabFast(modisAllNeuralNetString);
            seawifsNeuralNet = new NNffbpAlphaTabFast(seawifsNeuralNetString);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller seaice neural nets: " + e.getMessage());
        }
    }

    private String readNeuralNetFromStream(InputStream neuralNetStream) {
        // todo: method occurs multiple times --> move to core
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

    private void setClassifFlag(WritableSample[] targetSamples, OccciAlgorithm algorithm) {
        targetSamples[0].set(Constants.F_INVALID, algorithm.isInvalid());
        targetSamples[0].set(Constants.F_CLOUD, algorithm.isCloud());
        targetSamples[0].set(Constants.F_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(Constants.F_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(Constants.F_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(Constants.F_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(Constants.F_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(Constants.F_MIXED_PIXEL, algorithm.isMixedPixel());
        targetSamples[0].set(Constants.F_GLINT_RISK, algorithm.isGlintRisk());
        targetSamples[0].set(Constants.F_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(Constants.F_LAND, algorithm.isLand());

        if (ocOutputDebug) {
            targetSamples[1].set(algorithm.brightValue());
            targetSamples[2].set(algorithm.ndsiValue());
        }
    }

    private OccciAlgorithm createOccciAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        OccciAlgorithm occciAlgorithm;
        final double[] reflectance = new double[sensorContext.getNumSpectralInputBands()];
        double[] neuralNetOutput;

        float waterFraction = Float.NaN;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            waterFraction =
                    sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + sensorContext.getNumSpectralInputBands() + 1].getFloat();
        }

        if (sensorContext.getSensor() == Sensor.MODIS) {
            occciAlgorithm = new OccciModisAlgorithm();
            for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
                reflectance[i] = sourceSamples[i].getFloat();
            }
            occciAlgorithm.setRefl(reflectance);
            occciAlgorithm.setWaterFraction(waterFraction);

            double[] modisNeuralNetInput = new double[Constants.MODIS_NN_INPUT_LENGTH];
            modisNeuralNetInput[0] = Math.sqrt(sourceSamples[0].getFloat());    // EV_250_Aggr1km_RefSB.1
            modisNeuralNetInput[1] = Math.sqrt(sourceSamples[2].getFloat());    // EV_250_Aggr1km_RefSB.3
            modisNeuralNetInput[2] = Math.sqrt(sourceSamples[3].getFloat());    // EV_250_Aggr1km_RefSB.4
            modisNeuralNetInput[3] = Math.sqrt(sourceSamples[4].getFloat());    // EV_250_Aggr1km_RefSB.5
            modisNeuralNetInput[4] = Math.sqrt(sourceSamples[6].getFloat());    // EV_250_Aggr1km_RefSB.7
            final float emissive23Rad = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + 3].getFloat();
            modisNeuralNetInput[5] = Math.sqrt(emissive23Rad);                  // EV_1KM_Emissive.23
            final float emissive25Rad = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + 5].getFloat();
            modisNeuralNetInput[6] = Math.sqrt(emissive25Rad);                  // EV_1KM_Emissive.25
            modisNeuralNetInput[7] = Math.sqrt(sourceSamples[21].getFloat());
            final float emissive31Rad = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + 10].getFloat();
            modisNeuralNetInput[8] = Math.sqrt(emissive31Rad);                  // EV_1KM_Emissive.31
            final float emissive32Rad = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + 11].getFloat();
            modisNeuralNetInput[9] = Math.sqrt(emissive32Rad);                  // EV_1KM_Emissive.32

            synchronized (this) {
//                if (occciAlgorithm.isLand()) {
//                    neuralNetOutput = modisLandNeuralNet.calc(modisNeuralNetInput);
//                } else {
//                    neuralNetOutput = modisWaterNeuralNet.calc(modisNeuralNetInput);
//                }
                neuralNetOutput = modisAllNeuralNet.calc(modisNeuralNetInput);
            }
        } else if (sensorContext.getSensor() == Sensor.SEAWIFS) {
            occciAlgorithm = new OccciSeawifsAlgorithm();
            double[] seawifsNeuralNetInput = new double[sensorContext.getNumSpectralInputBands()];
            for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
                reflectance[i] = sourceSamples[Constants.SEAWIFS_SRC_RAD_OFFSET+ i].getFloat();
                sensorContext.scaleInputSpectralDataToReflectance(reflectance, 0);
                seawifsNeuralNetInput[i] = Math.sqrt(reflectance[i]);
            }
            occciAlgorithm.setRefl(reflectance);
            occciAlgorithm.setWaterFraction(waterFraction);

            synchronized (this) {
                neuralNetOutput = seawifsNeuralNet.calc(seawifsNeuralNetInput);
            }
        } else {
            throw new OperatorException("Sensor " + sensorContext.getSensor().name() + " not supported.");
        }

        occciAlgorithm.setNnOutput(neuralNetOutput);

        if (ocOutputDebug) {
            targetSamples[3].set(neuralNetOutput[0]);
        }

        // SeaWiFS reflectances output:
        if (ocOutputSeawifsRefl && sensorContext.getSensor() == Sensor.SEAWIFS) {
            for (int i=0; i<sensorContext.getNumSpectralInputBands(); i++) {
                targetSamples[4+i].set(reflectance[i]);
            }
        }

        return occciAlgorithm;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = reflProduct.getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }


    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sensorContext.configureSourceSamples(sampleConfigurer, reflProduct);

        int index = sensorContext.getSrcRadOffset() + sensorContext.getNumSpectralInputBands() + 1;
        sampleConfigurer.defineSample(index, Constants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        // the only standard band:
        sampleConfigurer.defineSample(0, Constants.CLASSIF_BAND_NAME);

        // debug bands:
        if (ocOutputDebug) {
            sampleConfigurer.defineSample(1, Constants.BRIGHTNESS_BAND_NAME);
            sampleConfigurer.defineSample(2, Constants.NDSI_BAND_NAME);
            sampleConfigurer.defineSample(3, Constants.SCHILLER_NN_OUTPUT_BAND_NAME);
        }

        // SeaWiFS reflectances:
        if (ocOutputSeawifsRefl && sensorContext.getSensor() == Sensor.SEAWIFS) {
            for (int i=0; i<sensorContext.getNumSpectralInputBands(); i++) {
                sampleConfigurer.defineSample(4 + i,
                                              SeaWiFSSensorContext.SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i] + "_refl");
            }
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(Constants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = OccciUtils.createOccciFlagCoding(Constants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        productConfigurer.copyGeoCoding();
//        getTargetProduct().setGeoCoding(reflProduct.getGeoCoding());
        OccciUtils.setupOccciClassifBitmask(getTargetProduct());

        // debug bands:
        if (ocOutputDebug) {
            Band brightnessValueBand = productConfigurer.addBand(Constants.BRIGHTNESS_BAND_NAME, ProductData.TYPE_FLOAT32);
            brightnessValueBand.setDescription("Brightness value (uses EV_250_Aggr1km_RefSB_1) ");
            brightnessValueBand.setUnit("dl");

            Band ndsiValueBand = productConfigurer.addBand(Constants.NDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
            ndsiValueBand.setDescription("NDSI value (uses EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7)");
            ndsiValueBand.setUnit("dl");

            Band nnValueBand = productConfigurer.addBand(Constants.SCHILLER_NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
            nnValueBand.setDescription("Schiller NN output value");
            nnValueBand.setUnit("dl");
        }

        // SeaWiFS reflectances:
        if (ocOutputSeawifsRefl && sensorContext.getSensor() == Sensor.SEAWIFS) {
            for (int i=0; i<sensorContext.getNumSpectralInputBands(); i++) {
                Band reflBand = productConfigurer.addBand(SeaWiFSSensorContext.SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i] + "_refl", ProductData.TYPE_FLOAT32);
                reflBand.setDescription(SeaWiFSSensorContext.SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i] + " TOA reflectance");
                reflBand.setUnit("dl");
            }
        }
    }

    String getProductTypeString() {
        if (StringUtils.isNotNullAndNotEmpty(productTypeString)) {
            return productTypeString;
        } else {
            return reflProduct.getProductType();
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OccciClassificationOp.class);
        }
    }

}
