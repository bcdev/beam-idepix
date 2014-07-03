package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.util.StringUtils;

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
    String sensorTypeString;

    @Parameter(label = "Schiller cloud Threshold ambiguous clouds", defaultValue = "1.4")
    private double schillerAmbiguous;

    @Parameter(label = "Schiller cloud Threshold sure clouds", defaultValue = "1.8")
    private double schillerSure;

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

    private SchillerAlgorithm waterNN;


    @Override
    protected void prepareInputs() throws OperatorException {
        sensorContext = SensorContextFactory.fromTypeString(getSensorTypeString());
        sensorContext.init(reflProduct);

        waterNN = new SchillerAlgorithm(SchillerAlgorithm.Net.WATER);
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        OccciAlgorithm algorithm;

        if (x == 1090 && y == 690) {
            System.out.println("x = " + x);
        }

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

    private OccciSeawifsAlgorithm createOccciSeaWifsAlgorithm(int x, int y, Sample[] sourceSamples) {
        OccciSeawifsAlgorithm occciAlgorithm = new OccciSeawifsAlgorithm();

        double[] seawifsReflectance = new double[sensorContext.getNumSpectralInputBands()];
        for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
            seawifsReflectance[i] = sourceSamples[Constants.SEAWIFS_SRC_RAD_OFFSET + i].getFloat();
        }

        occciAlgorithm.setRefl(seawifsReflectance);

        return occciAlgorithm;
    }

    private OccciAlgorithm createOccciAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        OccciAlgorithm occciAlgorithm;
        final double[] reflectance = new double[sensorContext.getNumSpectralInputBands()];
        if (sensorContext.getSensor() == Sensor.MODIS) {
            occciAlgorithm = new OccciModisAlgorithm();
            for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
                reflectance[i] = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + i].getFloat();
            }

        } else if (sensorContext.getSensor() == Sensor.SEAWIFS) {
            occciAlgorithm = new OccciSeawifsAlgorithm();
            for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
                reflectance[i] = sourceSamples[Constants.SEAWIFS_SRC_RAD_OFFSET+ i].getFloat();
                sensorContext.scaleInputSpectralDataToReflectance(reflectance, 0);
            }
        } else {
            throw new OperatorException("Sensor " + sensorContext.getSensor().name() + " not supported.");
        }


        float waterFraction = Float.NaN;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            waterFraction =
                    sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + sensorContext.getNumSpectralInputBands() + 1].getFloat();
        }

        occciAlgorithm.setWaterNN(null);
        occciAlgorithm.setAccessor(null);
        // activate this once we got a NN for MODIS from Schiller...
//        SchillerAlgorithm.Accessor accessor = new SchillerAlgorithm.Accessor() {
//            @Override
//            public double get(int index) {
//                return modisReflectance[index];
//            }
//        };
//        occciAlgorithm.setWaterNN(waterNN);
//        occciAlgorithm.setAccessor(accessor);

        occciAlgorithm.setAmbiguousThresh(schillerAmbiguous);
        occciAlgorithm.setSureThresh(schillerSure);
        occciAlgorithm.setRefl(reflectance);
        occciAlgorithm.setWaterFraction(waterFraction);

        // SeaWiFS reflectances output:
        if (ocOutputSeawifsRefl && sensorContext.getSensor() == Sensor.SEAWIFS) {
            for (int i=0; i<sensorContext.getNumSpectralInputBands(); i++) {
                targetSamples[3+i].set(reflectance[i]);
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
        }

        // SeaWiFS reflectances:
        if (ocOutputSeawifsRefl && sensorContext.getSensor() == Sensor.SEAWIFS) {
            for (int i=0; i<sensorContext.getNumSpectralInputBands(); i++) {
                sampleConfigurer.defineSample(3 + i,
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

        OccciUtils.setupOccciClassifBitmask(getTargetProduct());

        // debug bands:
        if (ocOutputDebug) {
            Band brightnessValueBand = productConfigurer.addBand(Constants.BRIGHTNESS_BAND_NAME, ProductData.TYPE_FLOAT32);
            brightnessValueBand.setDescription("Brightness value (uses EV_250_Aggr1km_RefSB_1) ");
            brightnessValueBand.setUnit("dl");

            Band ndsiValueBand = productConfigurer.addBand(Constants.NDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
            ndsiValueBand.setDescription("NDSI value (uses EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7)");
            ndsiValueBand.setUnit("dl");
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

    String getSensorTypeString() {
        if (StringUtils.isNotNullAndNotEmpty(sensorTypeString)) {
            return sensorTypeString;
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
