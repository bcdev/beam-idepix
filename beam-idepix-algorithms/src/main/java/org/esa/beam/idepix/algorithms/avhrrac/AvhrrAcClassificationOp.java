package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.idepix.algorithms.occci.OccciUtils;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.esa.beam.util.math.MathUtils;

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
@OperatorMetadata(alias = "idepix.avhrrac.classification",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Basic operator for pixel classification from AVHRR L1b data.")
public class AvhrrAcClassificationOp extends PixelOperator {

    @SourceProduct(alias = "aacl1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    // AvhrrAc parameters
    @Parameter(defaultValue = "true", label = " Copy input radiance bands (with albedo1/2 converted)")
    boolean aacCopyRadiances = true;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    int aacCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    boolean aacUseWaterMaskFraction = true;

    @Parameter(defaultValue = "false",
               label = " Debug bands",
               description = "Write further useful bands to target product.")
    private boolean avhrracOutputDebug = false;

    @Parameter(defaultValue = "2.15",
               label = " Schiller NN cloud ambiguous lower boundary ",
               description = " Schiller NN cloud ambiguous lower boundary ")
    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
               label = " Schiller NN cloud ambiguous/sure separation value ",
               description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
               label = " Schiller NN cloud sure/snow separation value ",
               description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double avhrracSchillerNNCloudSureSnowSeparationValue;


    private static final String SCHILLER_AVHRRAC_NET_NAME = "6x3_114.1.net";

    String avhrracNeuralNetString;
    NNffbpAlphaTabFast avhrracNeuralNet;

    AvhrrAcAuxdata.Line2ViewZenithTable vzaTable;

    public Product getSourceProduct() {
        // this is the source product for the ProductConfigurer
        return sourceProduct;
    }

    @Override
    public void prepareInputs() throws OperatorException {
        readSchillerNets();
        createTargetProduct();

        try {
            vzaTable = AvhrrAcAuxdata.getInstance().createLine2ViewZenithTable();
        } catch (IOException e) {
            // todo
            e.printStackTrace();
        }
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        runAvhrrAcAlgorithm(x, y, sourceSamples, targetSamples);
    }

    private void readSchillerNets() {
        final InputStream seawifsNeuralNetStream = getClass().getResourceAsStream(SCHILLER_AVHRRAC_NET_NAME);
        avhrracNeuralNetString = readNeuralNetFromStream(seawifsNeuralNetStream);

        try {
            avhrracNeuralNet = new NNffbpAlphaTabFast(avhrracNeuralNetString);
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

    private void setClassifFlag(WritableSample[] targetSamples, AvhrrAcAlgorithm algorithm) {
        targetSamples[0].set(Constants.F_INVALID, algorithm.isInvalid());
        targetSamples[0].set(Constants.F_CLOUD, algorithm.isCloud());
        targetSamples[0].set(Constants.F_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(Constants.F_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(Constants.F_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(Constants.F_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(Constants.F_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(Constants.F_GLINT_RISK, algorithm.isGlintRisk());
        targetSamples[0].set(Constants.F_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(Constants.F_LAND, algorithm.isLand());

//        if (ocOutputDebug) {
//            targetSamples[1].set(algorithm.brightValue());
//            targetSamples[2].set(algorithm.ndsiValue());
//        }
    }

    private void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAcAlgorithm aacAlgorithm = new AvhrrAcAlgorithm();

        final double sza = sourceSamples[Constants.SRC_SZA].getDouble();
        //double vza = 45.0f; // todo: vza is not in product, clarify how to determine
        double vza = vzaTable.getVza(x);

        final double aziDiff = 45.0f; // todo: aziDiff is not in product, clarify how to determine
        double[] avhrrRadiance = new double[Constants.AVHRR_AC_RADIANCE_BAND_NAMES.length];
        final double albedo1 = sourceSamples[Constants.SRC_ALBEDO_1].getDouble();
        final double albedo2 = sourceSamples[Constants.SRC_ALBEDO_2].getDouble();

        if (albedo1 >= 0.0 && albedo2 >= 0.0) {

            avhrrRadiance[0] = convertAlbedoToRadiance(albedo1, sza);
            avhrrRadiance[1] = convertAlbedoToRadiance(albedo2, sza);
            avhrrRadiance[2] = sourceSamples[Constants.SRC_RADIANCE_3].getDouble();
            avhrrRadiance[3] = sourceSamples[Constants.SRC_RADIANCE_4].getDouble();
            avhrrRadiance[4] = sourceSamples[Constants.SRC_RADIANCE_5].getDouble();
            aacAlgorithm.setRadiance(avhrrRadiance);

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[Constants.SRC_WATERFRACTION].getFloat();
            }

            double[] avhrracNeuralNetInput = new double[7];
            avhrracNeuralNetInput[0] = sza;
            avhrracNeuralNetInput[1] = vza;
            avhrracNeuralNetInput[2] = aziDiff;
            avhrracNeuralNetInput[3] = Math.sqrt(avhrrRadiance[0]);
            avhrracNeuralNetInput[4] = Math.sqrt(avhrrRadiance[1]);
            avhrracNeuralNetInput[5] = Math.sqrt(avhrrRadiance[3]);
            avhrracNeuralNetInput[6] = Math.sqrt(avhrrRadiance[4]);
            aacAlgorithm.setRadiance(avhrrRadiance);
            aacAlgorithm.setWaterFraction(waterFraction);

            double[] neuralNetOutput;
            synchronized (this) {
                neuralNetOutput = avhrracNeuralNet.calc(avhrracNeuralNetInput);
            }

            aacAlgorithm.setNnOutput(neuralNetOutput);
            aacAlgorithm.setAmbiguousLowerBoundaryValue(avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
            aacAlgorithm.setAmbiguousSureSeparationValue(avhrracSchillerNNCloudAmbiguousSureSeparationValue);
            aacAlgorithm.setSureSnowSeparationValue(avhrracSchillerNNCloudSureSnowSeparationValue);

            setClassifFlag(targetSamples, aacAlgorithm);
            targetSamples[1].set(neuralNetOutput[0]);
        } else {
            targetSamples[0].set(Constants.F_INVALID, true);
            targetSamples[1].set(Float.NaN);
            avhrrRadiance[0] = Float.NaN;
            avhrrRadiance[1] = Float.NaN;
        }

        if (aacCopyRadiances) {
            for (int i = 0; i < Constants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                targetSamples[2 + i].set(avhrrRadiance[i]);
            }
        }
    }

    private double convertAlbedoToRadiance(double albedo, double sza) {
        // follows GK formula
//        ao11060992103109_120417.l1b
        final int productNameStartIndex = sourceProduct.getName().indexOf("ao");
        // allow names such as subset_of_ao11060992103109_120417.dim
        final String noaaId = sourceProduct.getName().substring(productNameStartIndex + 2, productNameStartIndex + 4);
        final String datestring = sourceProduct.getName().substring(productNameStartIndex + 4, productNameStartIndex + 10);
        final int doy = IdepixUtils.getDoyFromYYMMDD(datestring);
        final double distanceCorr = 1.0 + 0.033 * Math.cos(2.0 * Math.PI * doy / 365.0);
        float integrSolarSpectralIrrad; // F
        float spectralResponseWidth; // W
        switch (noaaId) {
            case "11":
                // NOAA 11
                integrSolarSpectralIrrad = 189.02f;
                spectralResponseWidth = 0.1130f;
                break;
            case "14":
                // NOAA 14
                integrSolarSpectralIrrad = 221.42f;
                spectralResponseWidth = 0.1360f;
                break;
            default:
                throw new OperatorException("Cannot parse source product name " + sourceProduct.getName() + " properly.");
        }
        // GK: R=A (F/100 PI W  cos(sun_zenith)  abstandkorrektur)

        return albedo * 0.01 * integrSolarSpectralIrrad * Math.PI * spectralResponseWidth *
                Math.cos(sza * MathUtils.DTOR) * distanceCorr;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, "sun_zenith");
        sampleConfigurer.defineSample(index++, "latitude");
        sampleConfigurer.defineSample(index++, "longitude");
        for (int i = 0; i < 2; i++) {
            sampleConfigurer.defineSample(index++, Constants.AVHRR_AC_ALBEDO_BAND_NAMES[i]);
        }
        for (int i = 0; i < 3; i++) {
            sampleConfigurer.defineSample(index++, Constants.AVHRR_AC_RADIANCE_BAND_NAMES[i + 2]);
        }
        sampleConfigurer.defineSample(index, Constants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        // the only standard band:
        sampleConfigurer.defineSample(index++, Constants.CLASSIF_BAND_NAME);

        // debug bands:
//        if (ocOutputDebug) {
//            sampleConfigurer.defineSample(index++, Constants.BRIGHTNESS_BAND_NAME);
//            sampleConfigurer.defineSample(index++, occci.Constants.NDSI_BAND_NAME);
//        }
        sampleConfigurer.defineSample(index++, Constants.SCHILLER_NN_OUTPUT_BAND_NAME);

        // radiances:
        if (aacCopyRadiances) {
            for (int i = 0; i < Constants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                sampleConfigurer.defineSample(index++, Constants.AVHRR_AC_RADIANCE_BAND_NAMES[i]);
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
        AvhrrAcUtils.setupAvhrrAcClassifBitmask(getTargetProduct());

        // debug bands:
//        if (avhrracOutputDebug) {
//            Band brightnessValueBand = productConfigurer.addBand(Constants.BRIGHTNESS_BAND_NAME, ProductData.TYPE_FLOAT32);
//            brightnessValueBand.setDescription("Brightness value (uses EV_250_Aggr1km_RefSB_1) ");
//            brightnessValueBand.setUnit("dl");
//
//            Band ndsiValueBand = productConfigurer.addBand(Constants.NDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
//            ndsiValueBand.setDescription("NDSI value (uses EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7)");
//            ndsiValueBand.setUnit("dl");
//
//        }
        Band nnValueBand = productConfigurer.addBand(Constants.SCHILLER_NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        nnValueBand.setDescription("Schiller NN output value");
        nnValueBand.setUnit("dl");
        nnValueBand.setNoDataValue(Float.NaN);
        nnValueBand.setNoDataValueUsed(true);

        // radiances:
        if (aacCopyRadiances) {
            for (int i = 0; i < Constants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                Band radianceBand = productConfigurer.addBand("radiance_" + (i + 1), ProductData.TYPE_FLOAT32);
                radianceBand.setDescription("TOA radiance band " + (i + 1));
                radianceBand.setUnit("mW/(m^2 sr cm^-1)");
                radianceBand.setNoDataValue(Float.NaN);
                radianceBand.setNoDataValueUsed(true);
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcClassificationOp.class);
        }
    }
}
