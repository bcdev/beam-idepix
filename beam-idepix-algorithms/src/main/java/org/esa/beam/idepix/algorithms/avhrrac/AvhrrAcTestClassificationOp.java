package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.idepix.util.*;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.util.math.RsMathUtils;

import java.io.IOException;
import java.util.Calendar;

/**
 * Basic operator for AVHRR pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.test.classification",
        version = "2.2",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Basic operator for pixel classification from AVHRR L1b data " +
                "(uses old AVHRR AC test data (like '95070912_pr') read by avhrr-ac-directory-reader).")
public class AvhrrAcTestClassificationOp extends AbstractAvhrrAcClassificationOp {

    // TODO: cloud mask does currently not work, radiance units are weird if compared with USGS data. Check!! - OD, 20150220

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

    @Parameter(defaultValue = "false", label = " Flip source images (check before if needed!)")
    private boolean flipSourceImages;

//    @Parameter(defaultValue = "false",
//               label = " Debug bands",
//               description = "Write further useful bands to target product.")
//    private boolean avhrracOutputDebug = false;

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


    @Parameter(defaultValue = "20.0",
            label = " Reflectance 1 'brightness' threshold ",
            description = " Reflectance 1 'brightness' threshold ")
    double reflCh1Thresh;

    @Parameter(defaultValue = "20.0",
            label = " Reflectance 2 'brightness' threshold ",
            description = " Reflectance 2 'brightness' threshold ")
    double reflCh2Thresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 2/1 ratio threshold ",
            description = " Reflectance 2/1 ratio threshold ")
    double r2r1RatioThresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 3/1 ratio threshold ",
            description = " Reflectance 3/1 ratio threshold ")
    double r3r1RatioThresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 4 brightness temperature threshold (C)",
            description = " Channel 4 brightness temperature threshold (C)")
    double btCh4Thresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 5 brightness temperature threshold (C)",
            description = " Channel 5 brightness temperature threshold (C)")
    double btCh5Thresh;

    private String dateString;


    public Product getSourceProduct() {
        // this is the source product for the ProductConfigurer
        return sourceProduct;
    }

    @Override
    public void prepareInputs() throws OperatorException {
        setNoaaId();
        readSchillerNets();
        createTargetProduct();
        computeSunPosition();

        try {
            vzaTable = AvhrrAcAuxdata.getInstance().createLine2ViewZenithTable();
        } catch (IOException e) {
            throw new OperatorException("Failed to get VZA from auxdata - cannot proceed: ", e);
        }
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        runAvhrrAcAlgorithm(x, y, sourceSamples, targetSamples);
    }

    private static double computeRelativeAzimuth(double vaaRad, double saaRad) {
        return correctRelAzimuthRange(vaaRad, saaRad);
    }

    private static double[] computeAzimuthAngles(double sza,
                                         GeoPos satPosition,
                                         GeoPos pointPosition,
                                         SunPosition sunPosition) {

        final double latPoint = pointPosition.getLat();
        final double lonPoint = pointPosition.getLon();

        final double latSat = satPosition.getLat();
        final double lonSat = satPosition.getLon();

        final double latPointRad = latPoint * MathUtils.DTOR;
        final double lonPointRad = lonPoint * MathUtils.DTOR;
        final double latSatRad = latSat * MathUtils.DTOR;
        final double lonSatRad = lonSat * MathUtils.DTOR;

        final double latSunRad = sunPosition.getLat() * MathUtils.DTOR;
        final double lonSunRad = sunPosition.getLon() * MathUtils.DTOR;
        final double greatCirclePointToSatRad = computeGreatCircleFromPointToSat(latPointRad, lonPointRad, latSatRad, lonSatRad);

        final double vaaRad = computeVaa(latPointRad, lonPointRad, latSatRad, lonSatRad, greatCirclePointToSatRad);
        final double saaRad = computeSaa(sza, latPointRad, lonPointRad, latSunRad, lonSunRad);

        return new double[]{saaRad, vaaRad};
    }

    static double correctRelAzimuthRange(double vaaRad, double saaRad) {
        double relAzimuth = saaRad - vaaRad;
        if (relAzimuth < -Math.PI) {
            relAzimuth += 2.0 * Math.PI;
        } else if (relAzimuth > Math.PI) {
            relAzimuth -= 2.0 * Math.PI;
        }
        return Math.abs(relAzimuth);
    }

    static double computeGreatCircleFromPointToSat(double latPointRad, double lonPointRad, double latSatRad, double lonSatRad) {
        // http://mathworld.wolfram.com/GreatCircle.html, eq. (5):
        final double greatCirclePointToSat = 0.001 * RsMathUtils.MEAN_EARTH_RADIUS *
                Math.acos(Math.cos(latPointRad) * Math.cos(latSatRad) * Math.cos(lonPointRad - lonSatRad) +
                        Math.sin(latPointRad) * Math.sin(latSatRad));

//        return 2.0 * Math.PI * greatCirclePointToSat / (0.001 * RsMathUtils.MEAN_EARTH_RADIUS);
        return greatCirclePointToSat / (0.001 * RsMathUtils.MEAN_EARTH_RADIUS);
    }

    static SunPosition computeSunPosition(String ddmmyy) {
        final Calendar calendar = AvhrrAcUtils.getProductDateAsCalendar(ddmmyy);
        return SunPositionCalculator.calculate(calendar);
    }

    static double computeSaa(double sza, double latPointRad, double lonPointRad, double latSunRad, double lonSunRad) {
        double arg = (Math.sin(latSunRad) - Math.sin(latPointRad) * Math.cos(sza * MathUtils.DTOR)) /
                (Math.cos(latPointRad) * Math.sin(sza * MathUtils.DTOR));
        arg = Math.min(Math.max(arg, -1.0), 1.0);    // keep in range [-1.0, 1.0]
        double saaRad = Math.acos(arg);
        if (Math.sin(lonSunRad - lonPointRad) < 0.0) {
            saaRad = 2.0 * Math.PI - saaRad;
        }
        return saaRad;
    }

    static double computeVaa(double latPointRad, double lonPointRad, double latSatRad, double lonSatRad,
                             double greatCirclePointToSatRad) {
        double arg = (Math.sin(latSatRad) - Math.sin(latPointRad) * Math.cos(greatCirclePointToSatRad)) /
                (Math.cos(latPointRad) * Math.sin(greatCirclePointToSatRad));
        arg = Math.min(Math.max(arg, -1.0), 1.0);    // keep in range [-1.0, 1.0]
        double vaaRad = Math.acos(arg);
        if (Math.sin(lonSatRad - lonPointRad) < 0.0) {
            vaaRad = 2.0 * Math.PI - vaaRad;
        }

        return vaaRad;
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
        // test:
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 1, algorithm.isReflCh1Bright());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 2, algorithm.isReflCh2Bright());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 3, algorithm.isR2R1RatioAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 4, algorithm.isR3R1RatioAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 5, algorithm.isCh4BtAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 6, algorithm.isCh5BtAboveThresh());
    }

    @Override
    void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAcAlgorithm aacAlgorithm = new AvhrrAcAlgorithm();

        final double sza = sourceSamples[0].getDouble();
        final double vza = sourceSamples[1].getDouble();
        final double relAzi = sourceSamples[2].getDouble();
        final GeoPos satPosition = computeSatPosition(y);
        final GeoPos pointPosition = getGeoPos(x, y);

        final double[] azimuthAngles = computeAzimuthAngles(sza, satPosition, pointPosition, sunPosition);
        final double saaRad = azimuthAngles[0];
        final double vaaRad = azimuthAngles[1];
        final double myRelAzi = computeRelativeAzimuth(saaRad, vaaRad) * MathUtils.RTOD;

        double[] avhrrRadiance = new double[AvhrrAcConstants.AVHRR_AC_RADIANCE_OLD_BAND_NAMES.length];

        boolean compute = true;
        for (int i = 0; i < 5; i++) {
            avhrrRadiance[i] = sourceSamples[i + 3].getDouble();
            if (avhrrRadiance[i] < 0.0) {
                compute = false;
                break;
            }
        }

        if (x == 400 && y == 700) {
            System.out.println("x, y = " + x + "," + y);
        }

        if (compute) {

            aacAlgorithm.setRadiance(avhrrRadiance);

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[AvhrrAcConstants.SRC_USGS_WATERFRACTION].getFloat();
            }

            SchillerNeuralNetWrapper nnWrapper = avhrracNeuralNet.get();
            double[] inputVector = nnWrapper.getInputVector();
            inputVector[0] = sza;
            inputVector[1] = vza;
            inputVector[2] = relAzi;
            inputVector[3] = Math.sqrt(avhrrRadiance[0]);
            inputVector[4] = Math.sqrt(avhrrRadiance[1]);
            inputVector[5] = Math.sqrt(avhrrRadiance[3]);
            inputVector[6] = Math.sqrt(avhrrRadiance[4]);

            double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);
            aacAlgorithm.setNnOutput(nnOutput);
            aacAlgorithm.setRadiance(avhrrRadiance);
            aacAlgorithm.setWaterFraction(waterFraction);
            aacAlgorithm.setAmbiguousLowerBoundaryValue(avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
            aacAlgorithm.setAmbiguousSureSeparationValue(avhrracSchillerNNCloudAmbiguousSureSeparationValue);
            aacAlgorithm.setSureSnowSeparationValue(avhrracSchillerNNCloudSureSnowSeparationValue);

            final double reflCh1 = convertBetweenAlbedoAndRadiance(avhrrRadiance[0], sza, RADIANCE_TO_ALBEDO, 0);
            final double reflCh2 = convertBetweenAlbedoAndRadiance(avhrrRadiance[1], sza, RADIANCE_TO_ALBEDO, 1);
            final double reflCh3 = convertBetweenAlbedoAndRadiance(avhrrRadiance[2], sza, RADIANCE_TO_ALBEDO, 2);
            aacAlgorithm.setReflCh1(reflCh1);
            aacAlgorithm.setReflCh2(reflCh2);
            aacAlgorithm.setReflCh3(reflCh3);
            final double btCh4 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[3], 4) - 273.15;
            aacAlgorithm.setBtCh4(btCh4);
            final double btCh5 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[4], 5) - 273.15;
            aacAlgorithm.setBtCh5(btCh5);

//            aacAlgorithm.setReflCh1Thresh(reflCh1Thresh);
//            aacAlgorithm.setReflCh2Thresh(reflCh2Thresh);
//            aacAlgorithm.setR2r1RatioThresh(r2r1RatioThresh);
//            aacAlgorithm.setR3r1RatioThresh(r3r1RatioThresh);
//            aacAlgorithm.setBtCh4Thresh(btCh4Thresh);
//            aacAlgorithm.setBtCh5Thresh(btCh5Thresh);

            setClassifFlag(targetSamples, aacAlgorithm);
            targetSamples[1].set(nnOutput[0]);
            targetSamples[2].set(vza);
            targetSamples[3].set(relAzi);
            targetSamples[4].set(myRelAzi);
            targetSamples[5].set(saaRad * MathUtils.RTOD);
            targetSamples[6].set(vaaRad * MathUtils.RTOD);
            targetSamples[7].set(btCh4);
            targetSamples[8].set(btCh5);
            targetSamples[9].set(reflCh1);
            targetSamples[10].set(reflCh2);
            targetSamples[11].set(reflCh3);

        } else {
            targetSamples[0].set(AvhrrAcConstants.F_INVALID, true);
            targetSamples[1].set(Float.NaN);
            targetSamples[2].set(Float.NaN);
            targetSamples[3].set(Float.NaN);
            targetSamples[4].set(Float.NaN);
            targetSamples[5].set(Float.NaN);
            targetSamples[6].set(Float.NaN);
            targetSamples[7].set(Float.NaN);
            targetSamples[8].set(Float.NaN);
            targetSamples[9].set(Float.NaN);
            targetSamples[10].set(Float.NaN);
            targetSamples[11].set(Float.NaN);
            avhrrRadiance[0] = Float.NaN;
            avhrrRadiance[1] = Float.NaN;
        }

        if (aacCopyRadiances) {
            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                targetSamples[12 + i].set(avhrrRadiance[i]);
            }
        }
    }

    @Override
    String getProductDatestring() {
        // 95070912_pr.zip
        // provides datestring as DDMMYY !!!
        final int startIndex = sourceProduct.getName().indexOf("_pr");
        // allow names such as subset_of_ao11060992103109_120417.dim
        final String yy = sourceProduct.getName().substring(startIndex - 8, startIndex - 6);
        final String mm = sourceProduct.getName().substring(startIndex - 6, startIndex - 4);
        final String dd = sourceProduct.getName().substring(startIndex - 4, startIndex - 2);
        return dd + mm + yy;
    }

    @Override
    void setNoaaId() {
        noaaId = "14";
        // todo: identify from product metadata
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, "sun_zenith");
        sampleConfigurer.defineSample(index++, "sat_zenith");
        sampleConfigurer.defineSample(index++, "rel_azimuth");
        for (int i = 0; i < 5; i++) {
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_RADIANCE_OLD_BAND_NAMES[i]);
        }
        sampleConfigurer.defineSample(index, AvhrrAcConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        // the only standard band:
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.CLASSIF_BAND_NAME);

        sampleConfigurer.defineSample(index++, AvhrrAcConstants.SCHILLER_NN_OUTPUT_BAND_NAME);
        sampleConfigurer.defineSample(index++, "vza");
        sampleConfigurer.defineSample(index++, "rel_azimuth");
        sampleConfigurer.defineSample(index++, "rel_azimuth_computed");
        sampleConfigurer.defineSample(index++, "saa_computed");
        sampleConfigurer.defineSample(index++, "vaa_computed");
        sampleConfigurer.defineSample(index++, "bt_4");
        sampleConfigurer.defineSample(index++, "bt_5");
        sampleConfigurer.defineSample(index++, "refl_1");
        sampleConfigurer.defineSample(index++, "refl_2");
        sampleConfigurer.defineSample(index++, "refl_3");

        // radiances:
        if (aacCopyRadiances) {
            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES[i]);
            }
        }
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

        productConfigurer.copyGeoCoding();
        AvhrrAcUtils.setupAvhrrAcClassifBitmask(getTargetProduct());

        Band nnValueBand = productConfigurer.addBand(AvhrrAcConstants.SCHILLER_NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        nnValueBand.setDescription("Schiller NN output value");
        nnValueBand.setUnit("dl");
        nnValueBand.setNoDataValue(Float.NaN);
        nnValueBand.setNoDataValueUsed(true);

        Band vzaBand = productConfigurer.addBand("vza", ProductData.TYPE_FLOAT32);
        vzaBand.setDescription("view zenith angle");
        vzaBand.setUnit("dl");
        vzaBand.setNoDataValue(Float.NaN);
        vzaBand.setNoDataValueUsed(true);

        Band relaziBand = productConfigurer.addBand("rel_azimuth", ProductData.TYPE_FLOAT32);
        relaziBand.setDescription("relative azimuth");
        relaziBand.setUnit("deg");
        relaziBand.setNoDataValue(Float.NaN);
        relaziBand.setNoDataValueUsed(true);

        Band relaziComputedBand = productConfigurer.addBand("rel_azimuth_computed", ProductData.TYPE_FLOAT32);
        relaziComputedBand.setDescription("relative azimuth computed");
        relaziComputedBand.setUnit("deg");
        relaziComputedBand.setNoDataValue(Float.NaN);
        relaziComputedBand.setNoDataValueUsed(true);

        Band saaComputedBand = productConfigurer.addBand("saa_computed", ProductData.TYPE_FLOAT32);
        saaComputedBand.setDescription("saa computed");
        saaComputedBand.setUnit("deg");
        saaComputedBand.setNoDataValue(Float.NaN);
        saaComputedBand.setNoDataValueUsed(true);

        Band vaaComputedBand = productConfigurer.addBand("vaa_computed", ProductData.TYPE_FLOAT32);
        vaaComputedBand.setDescription("vaa computed");
        vaaComputedBand.setUnit("deg");
        vaaComputedBand.setNoDataValue(Float.NaN);
        vaaComputedBand.setNoDataValueUsed(true);

        Band bt4Band = productConfigurer.addBand("bt_4", ProductData.TYPE_FLOAT32);
        bt4Band.setDescription("Channel 4 brightness temperature");
        bt4Band.setUnit("C");
        bt4Band.setNoDataValue(Float.NaN);
        bt4Band.setNoDataValueUsed(true);

        Band bt5Band = productConfigurer.addBand("bt_5", ProductData.TYPE_FLOAT32);
        bt5Band.setDescription("Channel 5 brightness temperature");
        bt5Band.setUnit("C");
        bt5Band.setNoDataValue(Float.NaN);
        bt5Band.setNoDataValueUsed(true);

        Band refl1Band = productConfigurer.addBand("refl_1", ProductData.TYPE_FLOAT32);
        refl1Band.setDescription("Channel 1 TOA reflectance");
        refl1Band.setUnit("dl");
        refl1Band.setNoDataValue(Float.NaN);
        refl1Band.setNoDataValueUsed(true);

        Band refl2Band = productConfigurer.addBand("refl_2", ProductData.TYPE_FLOAT32);
        refl2Band.setDescription("Channel 2 TOA reflectance");
        refl2Band.setUnit("dl");
        refl2Band.setNoDataValue(Float.NaN);
        refl2Band.setNoDataValueUsed(true);

        Band refl3Band = productConfigurer.addBand("refl_3", ProductData.TYPE_FLOAT32);
        refl3Band.setDescription("Channel 3 TOA reflectance");
        refl3Band.setUnit("dl");
        refl3Band.setNoDataValue(Float.NaN);
        refl3Band.setNoDataValueUsed(true);


        // radiances:
        if (aacCopyRadiances) {
            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
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
            super(AvhrrAcTestClassificationOp.class);
        }
    }
}
