package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.util.math.MathUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Operator for Idepix pixel classification of AVHRR land products from NOAA NCEI.
 * https://www.ncei.noaa.gov/data/avhrr-land-surface-reflectance/
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.ncei.classification",
        version = "2.2",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Operator for Idepix pixel classification of AVHRR land products from NOAA NCEI.")
public class AvhrrAcNCEIClassificationOp extends AbstractAvhrrAcClassificationOp {

    @SourceProduct(alias = "aacl1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    // AvhrrAc parameters
//    @Parameter(defaultValue = "false", label = " Copy input radiance bands (with albedo1/2 converted)")
    boolean aacCopyRadiances = true;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    int aacCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    boolean aacUseWaterMaskFraction = true;

    @Parameter(defaultValue = "false", label = " Flip source images (check before if needed!)")
    private boolean flipSourceImages;

    @Parameter(defaultValue = "2.15",
            label = " NN cloud ambiguous lower boundary ",
            description = " NN cloud ambiguous lower boundary ")
    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " NN cloud ambiguous/sure separation value ",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
            label = " NN cloud sure/snow separation value ",
            description = " NN cloud ambiguous cloud sure/snow separation value ")
    double avhrracSchillerNNCloudSureSnowSeparationValue;

    ElevationModel getasseElevationModel;

    // todo: set as constants
    double[] lPath = {0.001, 2.7E-4};
    double[] eg0 = {0.153, 0.099};
    double[] alb = {0.0445, 0.0213};
    double[] trans = {0.8836, 0.895};
    double szaForAc = 60.0;
    double vzaForAc = 40.0;
    double relaziForAc = 20.0;

    @Override
    public void prepareInputs() throws OperatorException {
        if (flipSourceImages) {
            flipSourceImages();
        }
        setNoaaId();
        readSchillerNets();
        createTargetProduct();
        computeSunPosition();

        if (sourceProduct.getGeoCoding() == null) {
            sourceProduct.setGeoCoding(
                    new TiePointGeoCoding(sourceProduct.getTiePointGrid("latitude"),
                            sourceProduct.getTiePointGrid("longitude"))
            );
        }

        try {
            vzaTable = AvhrrAcAuxdata.getInstance().createLine2ViewZenithTable();
            rad2BTTable = AvhrrAcAuxdata.getInstance().createRad2BTTable(noaaId);
        } catch (IOException e) {
            throw new OperatorException("Failed to get VZA from auxdata - cannot proceed: ", e);
        }

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(
                demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);
    }

    void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(SCHILLER_AVHRRAC_NET_NAME)) {
            avhrracNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    GeoPos computeSatPosition(int y) {
        return getGeoPos(sourceProduct.getSceneRasterWidth() / 2, y);    // LAC_NADIR = 1024.5
    }

    @Override
    void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAcAlgorithm aacAlgorithm = new AvhrrAcAlgorithm();
        aacAlgorithm.setNoaaId(noaaId);
        aacAlgorithm.setDistanceCorr(getDistanceCorr());

        if (x == 155 && y == 210) {
            System.out.println("x = " + x);  // cloud
        }
//        if (x == 335 && y == 280) {
        if (x == 172 && y == 90) {
            System.out.println("x = " + x);  // snow
        }

//        final double sza = sourceSamples[0].getDouble();
//        final double vza = sourceSamples[1].getDouble();
//        final double relAzi = sourceSamples[2].getDouble();
        double albedo1 = sourceSamples[3].getDouble();
        final double lToa1 = 10000.0*AvhrrAcUtils.getLToaFromRhoToc(lPath[0], eg0[0], alb[0], trans[0], albedo1);
        final double rhoToa1 = convertBetweenAlbedoAndRadiance(lToa1, szaForAc, RADIANCE_TO_ALBEDO, 0);
        albedo1 *= 100.0;             // %
        double albedo2 = sourceSamples[4].getDouble();
        final double lToa2 = 10000.0*AvhrrAcUtils.getLToaFromRhoToc(lPath[1], eg0[1], alb[1], trans[1], albedo2);
        final double rhoToa2 = convertBetweenAlbedoAndRadiance(lToa2, szaForAc, RADIANCE_TO_ALBEDO, 1);
        albedo2 *= 100.0;             // %
        final double albedo3 = sourceSamples[5].getDouble();             // on [0, 1]
        double bt3;
        double bt4;
        double bt5;

        final GeoPos pointPosition = getGeoPos(x, y);
        aacAlgorithm.setLatitude(pointPosition.getLat());
        aacAlgorithm.setLongitude(pointPosition.getLon());
        aacAlgorithm.setSza(szaForAc);

        final double altitude = computeGetasseAltitude(x, y);

        double[] avhrrRadiance = new double[AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length];

        // GK, 20150325: convert albedo1, 2 to 'normalized' albedo:
        // norm_albedo_i = albedo_i / (d^2_sun * cos(theta_sun))    , effect is a few % only
//        final double d = getDistanceCorr() * Math.cos(sza * MathUtils.DTOR);
//        final double d = 1.0; // neglect
//        final double albedo1Norm = albedo1 / d;
//        final double albedo2Norm = albedo2 / d;

        if (albedo1 >= 0.0 && albedo2 >= 0.0 && !AvhrrAcUtils.anglesInvalid(szaForAc, vzaForAc, 0, 0)) {
//            avhrrRadiance[0] = convertBetweenAlbedoAndRadiance(albedo1, sza, ALBEDO_TO_RADIANCE, 0);
            avhrrRadiance[0] = lToa1;
//            avhrrRadiance[1] = convertBetweenAlbedoAndRadiance(albedo2, sza, ALBEDO_TO_RADIANCE, 1);
            avhrrRadiance[1] = lToa2;
            bt3 = sourceSamples[6].getDouble();
            avhrrRadiance[2] = AvhrrAcUtils.convertBtToRadiance(bt3, 3);  // in NCEI we have BT as input!
            bt4 = sourceSamples[7].getDouble();
            avhrrRadiance[3] = AvhrrAcUtils.convertBtToRadiance(bt4, 4);  // in NCEI we have BT as input!
            bt5 = sourceSamples[8].getDouble();
            avhrrRadiance[4] = AvhrrAcUtils.convertBtToRadiance(bt5, 5);  // in NCEI we have BT as input!
            aacAlgorithm.setRadiance(avhrrRadiance);

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[AvhrrAcConstants.SRC_USGS_WATERFRACTION].getFloat();
            }

            SchillerNeuralNetWrapper nnWrapper = avhrracNeuralNet.get();
//            input  1 is PIXEL_SUN_ZENITH in [10.337752,89.987251]
//            input  2 is PIXEL_VIEW_ZENITH in [0.020000,65.509499]
//            input  3 is PIXEL_DELTA_AZIMUTH in [-179.963486,179.908524]
//            input  4 is PIXEL_sqrt_RADIANCE_1 in [1.576690,22.638479]
//            input  5 is PIXEL_sqrt_RADIANCE_2 in [1.116786,17.314881]
//            input  6 is PIXEL_sqrt_RADIANCE_4 in [0.000000,13.153937]
//            input  7 is PIXEL_sqrt_RADIANCE_5 in [0.000000,13.649639]
            double[] inputVector = nnWrapper.getInputVector();
            inputVector[0] = szaForAc;
            inputVector[1] = vzaForAc;
            inputVector[2] = relaziForAc;
            inputVector[3] = Math.sqrt(avhrrRadiance[0]);
            inputVector[4] = Math.sqrt(avhrrRadiance[1]);
            inputVector[5] = Math.sqrt(avhrrRadiance[3]);
            inputVector[6] = Math.sqrt(avhrrRadiance[4]);
            aacAlgorithm.setRadiance(avhrrRadiance);
            aacAlgorithm.setWaterFraction(waterFraction);

            double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

            aacAlgorithm.setNnOutput(nnOutput);
            aacAlgorithm.setAmbiguousLowerBoundaryValue(avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
            aacAlgorithm.setAmbiguousSureSeparationValue(avhrracSchillerNNCloudAmbiguousSureSeparationValue);
            aacAlgorithm.setSureSnowSeparationValue(avhrracSchillerNNCloudSureSnowSeparationValue);

            aacAlgorithm.setReflCh1(rhoToa1 / 100.0); // on [0,1]        --> put here albedo_norm now!!
            aacAlgorithm.setReflCh2(rhoToa2 / 100.0); // on [0,1]
            aacAlgorithm.setReflCh3(albedo3); // on [0,1]

            aacAlgorithm.setBtCh3(bt3);   // in NCEI we have BT as input!
            aacAlgorithm.setBtCh4(bt4);
            aacAlgorithm.setBtCh5(bt5);
            aacAlgorithm.setElevation(altitude);

            setClassifFlag(targetSamples, aacAlgorithm);
//            targetSamples[1].set(albedo2);
            targetSamples[1].set(rhoToa2);
            targetSamples[2].set(nnOutput[0]);

        } else {
            targetSamples[0].set(AvhrrAcConstants.F_INVALID, true);
            targetSamples[1].set(Float.NaN);
            targetSamples[2].set(Float.NaN);
        }
    }

    private double computeGetasseAltitude(float x, float y) {
        final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
        GeoPos geoPos = sourceProduct.getGeoCoding().getGeoPos(pixelPos, null);
        double altitude;
        try {
            altitude = getasseElevationModel.getElevation(geoPos);
        } catch (Exception e) {
            // todo
            e.printStackTrace();
            altitude = 0.0;
        }
        return altitude;
    }

    @Override
    void setClassifFlag(WritableSample[] targetSamples, AvhrrAcAlgorithm algorithm) {
        targetSamples[0].set(AvhrrAcConstants.F_INVALID, algorithm.isInvalid());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD, algorithm.isCloud());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(AvhrrAcConstants.F_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(AvhrrAcConstants.F_GLINT_RISK, algorithm.isGlintRisk());
        targetSamples[0].set(AvhrrAcConstants.F_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(AvhrrAcConstants.F_LAND, algorithm.isLand());
    }

    @Override
    String getProductDatestring() {
        // provides datestring as DDMMYY !!!
        // allow names such as subset_0_of_AVHRR-Land_v004_AVH09C1_NOAA-14_19971025_c20131001102729
        final int productNameStartIndex = sourceProduct.getName().indexOf("AVHRR-Land_v004_");
        return sourceProduct.getName().substring(productNameStartIndex + 34, productNameStartIndex + 40);
    }

    @Override
    void setNoaaId() {
        // allow names such as subset_0_of_AVHRR-Land_v004_AVH09C1_NOAA-14_19971025_c20131001102729
        final int productNameStartIndex = sourceProduct.getName().indexOf("AVHRR-Land_v004_");
        noaaId = sourceProduct.getName().substring(productNameStartIndex + 29, productNameStartIndex + 31);
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, "SZEN");
        sampleConfigurer.defineSample(index++, "VZEN");
        sampleConfigurer.defineSample(index++, "RELAZ");
        sampleConfigurer.defineSample(index++, "SREFL_CH1");
        sampleConfigurer.defineSample(index++, "SREFL_CH2");
        sampleConfigurer.defineSample(index++, "SREFL_CH3");
        sampleConfigurer.defineSample(index++, "BT_CH3");
        sampleConfigurer.defineSample(index++, "BT_CH4");
        sampleConfigurer.defineSample(index++, "BT_CH5");
        sampleConfigurer.defineSample(index, AvhrrAcConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        // todo: tbd
        sampleConfigurer.defineSample(0, AvhrrAcConstants.CLASSIF_BAND_NAME);
        sampleConfigurer.defineSample(1, "SREFL_CH2");
        sampleConfigurer.defineSample(2, "nn_value");
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(AvhrrAcConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = AvhrrAcUtils.createAvhrrAcFlagCoding(AvhrrAcConstants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        Band refl2Band = productConfigurer.addBand("SREFL_CH2", ProductData.TYPE_FLOAT32);
        refl2Band.setDescription("Channel 2 surface reflectance");
        refl2Band.setUnit("dl");
        refl2Band.setNoDataValue(Float.NaN);
        refl2Band.setNoDataValueUsed(true);

        Band nnValueBand = productConfigurer.addBand("nn_value", ProductData.TYPE_FLOAT32);
        nnValueBand.setDescription("NN output value");
        nnValueBand.setUnit("dl");
        nnValueBand.setNoDataValue(Float.NaN);
        nnValueBand.setNoDataValueUsed(true);

        productConfigurer.copyGeoCoding();
        AvhrrAcUtils.setupAvhrrAcClassifBitmask(getTargetProduct());
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcNCEIClassificationOp.class);
        }
    }
}
