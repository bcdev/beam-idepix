package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.idepix.util.SunAngles;
import org.esa.beam.idepix.util.SunAnglesCalculator;
import org.esa.beam.idepix.util.SunPosition;
import org.esa.beam.idepix.util.SunPositionCalculator;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.util.math.RsMathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Basic operator for AVHRR pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.classification2",
                  version = "2.1.5",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Basic operator for pixel classification from AVHRR L1b data " +
                          "(uses old AVHRR AC test data (like '95070912_pr') read by avhrr-ac-directory-reader).")
public class AvhrrAcClassification2Op extends PixelOperator {

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


    private static final String SCHILLER_AVHRRAC_NET_NAME = "6x3_114.1.net";

    private static final int ALBEDO_TO_RADIANCE = 0;
    private static final int RADIANCE_TO_ALBEDO = 1;

    private static final double NU_CH3 = 2694.0;
    private static final double NU_CH4 = 925.0;
    private static final double NU_CH5 = 839.0;

    ThreadLocal<SchillerNeuralNetWrapper> avhrracNeuralNet;

    AvhrrAcAuxdata.Line2ViewZenithTable vzaTable;
    private SunAngles sunAngles;
    private SunPosition sunPosition;

    private String dateString;


    public Product getSourceProduct() {
        // this is the source product for the ProductConfigurer
        return sourceProduct;
    }

    @Override
    public void prepareInputs() throws OperatorException {
        readSchillerNets();
        createTargetProduct();
        dateString = getProductDatestring();
        sunPosition = computeSunPosition(dateString);

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

    // package local for testing
    static double computeRelativeAzimuth(double sza,
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

        final double latSun = sunPosition.getLat();
        final double lonSun = sunPosition.getLon();
        final double latSunRad = sunPosition.getLat() * MathUtils.DTOR;
        final double lonSunRad = sunPosition.getLon() * MathUtils.DTOR;
        final double greatCirclePointToSatRad = computeGreatCircleFromPointToSat(latPointRad, lonPointRad, latSatRad, lonSatRad);


        final double vaa = computeVaa(latPointRad, lonPointRad, latSatRad, lonSatRad, greatCirclePointToSatRad);
        final double saa = computeSaa(sza, latPointRad, lonPointRad, latSunRad, lonSunRad);

//        double relAzimuth = saa * MathUtils.RTOD - vaa * MathUtils.RTOD;
        double relAzimuth = correctRelAzimuthRange(vaa, saa);
        return relAzimuth * MathUtils.RTOD;
    }

    // package local for testing
    static double correctRelAzimuthRange(double vaa, double saa) {
        double relAzimuth = saa - vaa;
        if (relAzimuth < -Math.PI) {
            relAzimuth += 2.0*Math.PI;
        } else if (relAzimuth > Math.PI) {
            relAzimuth -= 2.0*Math.PI;
        }
        return relAzimuth;
    }

    // package local for testing
    static double computeGreatCircleFromPointToSat(double latPointRad, double lonPointRad, double latSatRad, double lonSatRad) {
        // http://mathworld.wolfram.com/GreatCircle.html, eq. (5):
        final double greatCirclePointToSat = 0.001 * RsMathUtils.MEAN_EARTH_RADIUS *
                Math.acos(Math.cos(latPointRad) * Math.cos(latSatRad) * Math.cos(lonPointRad - lonSatRad) +
                                  Math.sin(latPointRad) * Math.sin(latSatRad));

        return 2.0 * Math.PI * greatCirclePointToSat / (0.001 * RsMathUtils.MEAN_EARTH_RADIUS);
    }

    // package local for testing
    static SunPosition computeSunPosition(String ddmmyy) {
        final Calendar calendar = getDateAsCalendar(ddmmyy);
        return SunPositionCalculator.calculate(calendar);
    }

    // package local for testing
    static Calendar getDateAsCalendar(String ddmmyy) {
        final Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        int year = Integer.parseInt(ddmmyy.substring(4, 6));
        if (year < 50) {
            year = 2000 + year;
        } else {
            year = 1900 + year;
        }
        final int month = Integer.parseInt(ddmmyy.substring(2, 4)) - 1;
        final int day = Integer.parseInt(ddmmyy.substring(0, 2));
        calendar.set(year, month, day, 12, 0, 0);
        return calendar;
    }

    // package local for testing
    static double computeSaa(double sza, double latPointRad, double lonPointRad, double latSunRad, double lonSunRad) {
        double saaRad = Math.acos((Math.sin(latSunRad) - Math.sin(latPointRad) * Math.cos(sza * MathUtils.DTOR)) /
                                              (Math.cos(latPointRad) * Math.sin(sza * MathUtils.DTOR)));
        if (Math.sin(lonSunRad - lonPointRad) < 0.0) {
            saaRad = 2.0*Math.PI - saaRad;
        }
        return saaRad * MathUtils.RTOD;
    }

    // package local for testing
    static double computeVaa(double latPointRad, double lonPointRad, double latSatRad, double lonSatRad,
                             double greatCirclePointToSatRad) {
        double vaaRad = Math.acos((Math.sin(latSatRad) - Math.sin(latPointRad) * Math.cos(greatCirclePointToSatRad)) /
                                              (Math.cos(latPointRad) * Math.sin(greatCirclePointToSatRad)));
        if (Math.sin(lonSatRad - lonPointRad) < 0.0) {
            vaaRad = 2.0*Math.PI - vaaRad;
        }

        return vaaRad * MathUtils.RTOD;
    }

    private void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(SCHILLER_AVHRRAC_NET_NAME)) {
            avhrracNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
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
        // test:
        targetSamples[0].set(Constants.F_LAND + 1, algorithm.isReflCh1Bright());
        targetSamples[0].set(Constants.F_LAND + 2, algorithm.isReflCh2Bright());
        targetSamples[0].set(Constants.F_LAND + 3, algorithm.isR2R1RatioAboveThresh());
        targetSamples[0].set(Constants.F_LAND + 4, algorithm.isR3R1RatioAboveThresh());
        targetSamples[0].set(Constants.F_LAND + 5, algorithm.isCh4BtAboveThresh());
        targetSamples[0].set(Constants.F_LAND + 6, algorithm.isCh5BtAboveThresh());
    }

    private void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAcAlgorithm aacAlgorithm = new AvhrrAcAlgorithm();

        final double sza = sourceSamples[0].getDouble();
        final double vza = sourceSamples[1].getDouble();
        final double relAzi = sourceSamples[2].getDouble();
        //double vza = 45.0f;
//        double vza = vzaTable.getVza(x);
//        final double relAzi = computeRelativeAzimuth(x, y, sza);
        final GeoPos satPosition = computeSatPosition(y);
        final GeoPos pointPosition = getGeoPos(x, y);
        final double myRelAzi = computeRelativeAzimuth(sza, satPosition, pointPosition, sunPosition);
        double[] avhrrRadiance = new double[Constants.AVHRR_AC_RADIANCE_OLD_BAND_NAMES.length];

        boolean compute = true;
        for (int i = 0; i < 5; i++) {
            avhrrRadiance[i] = sourceSamples[i + 3].getDouble();
            if (avhrrRadiance[i] < 0.0) {
                compute = false;
                break;
            }
        }

        if (compute) {

            aacAlgorithm.setRadiance(avhrrRadiance);

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[Constants.SRC_WATERFRACTION].getFloat();
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

            final double reflCh1 = convertBetweenAlbedoAndRadiance(avhrrRadiance[0], sza, RADIANCE_TO_ALBEDO);
            final double reflCh2 = convertBetweenAlbedoAndRadiance(avhrrRadiance[1], sza, RADIANCE_TO_ALBEDO);
            final double reflCh3 = convertBetweenAlbedoAndRadiance(avhrrRadiance[2], sza, RADIANCE_TO_ALBEDO);
            aacAlgorithm.setReflCh1(reflCh1);
            aacAlgorithm.setReflCh2(reflCh2);
            aacAlgorithm.setReflCh3(reflCh3);
            final double btCh4 = convertRadianceToBt(avhrrRadiance[3], 4) - 273.15;
            aacAlgorithm.setBtCh4(btCh4);
            final double btCh5 = convertRadianceToBt(avhrrRadiance[4], 5) - 273.15;
            aacAlgorithm.setBtCh5(btCh5);

            aacAlgorithm.setReflCh1Thresh(reflCh1Thresh);
            aacAlgorithm.setReflCh2Thresh(reflCh2Thresh);
            aacAlgorithm.setR2r1RatioThresh(r2r1RatioThresh);
            aacAlgorithm.setR3r1RatioThresh(r3r1RatioThresh);
            aacAlgorithm.setBtCh4Thresh(btCh4Thresh);
            aacAlgorithm.setBtCh5Thresh(btCh5Thresh);

            setClassifFlag(targetSamples, aacAlgorithm);
            targetSamples[1].set(nnOutput[0]);
            targetSamples[2].set(vza);
            targetSamples[3].set(relAzi);
            targetSamples[4].set(btCh4);
            targetSamples[5].set(btCh5);
            targetSamples[6].set(reflCh1);
            targetSamples[7].set(reflCh2);
            targetSamples[8].set(reflCh3);

        } else {
            targetSamples[0].set(Constants.F_INVALID, true);
            targetSamples[1].set(Float.NaN);
            targetSamples[2].set(Float.NaN);
            targetSamples[3].set(Float.NaN);
            targetSamples[4].set(Float.NaN);
            targetSamples[5].set(Float.NaN);
            targetSamples[6].set(Float.NaN);
            targetSamples[7].set(Float.NaN);
            targetSamples[8].set(Float.NaN);
            avhrrRadiance[0] = Float.NaN;
            avhrrRadiance[1] = Float.NaN;
        }

        if (aacCopyRadiances) {
            for (int i = 0; i < Constants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                targetSamples[9 + i].set(avhrrRadiance[i]);
            }
        }
    }

    private double convertRadianceToBt(double radiance, int channel) {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;

        switch (channel) {
            case 3:
                return c2 * NU_CH3 / Math.log(1.0 + c1 * Math.pow(NU_CH3, 3.0) / radiance);
            case 4:
                return c2 * NU_CH4 / Math.log(1.0 + c1 * Math.pow(NU_CH4, 3.0) / radiance);
            case 5:
                return c2 * NU_CH5 / Math.log(1.0 + c1 * Math.pow(NU_CH5, 3.0) / radiance);
            default:
                throw new IllegalArgumentException("wrong channel " + channel + " for radiance to BT conversion");
        }
    }

    private SunAngles computeSunAngles(String ddmmyy, double lat, double lon) {
        final Calendar calendar = getDateAsCalendar(ddmmyy);
        return SunAnglesCalculator.calculate(calendar, lat, lon);
    }

    private GeoPos computeSatPosition(int y) {
        return getGeoPos(sourceProduct.getSceneRasterWidth() / 2, y);    // LAC_NADIR = 1024.5
    }

    private double convertBetweenAlbedoAndRadiance(double input, double sza, int mode) {
        // follows GK formula
//        95070912_pr
        final String year = sourceProduct.getName().substring(0, 2);
        final String month = sourceProduct.getName().substring(2, 4);
        final String day = sourceProduct.getName().substring(4, 6);

        final int doy = IdepixUtils.getDoyFromYYMMDD(year + month + day);
        final double distanceCorr = 1.0 + 0.033 * Math.cos(2.0 * Math.PI * doy / 365.0);
        float integrSolarSpectralIrrad; // F
        float spectralResponseWidth; // W
//        switch (noaaId) {
//            case "11":
        // NOAA 11
        integrSolarSpectralIrrad = 189.02f;
        spectralResponseWidth = 0.1130f;
//                break;
//            case "14":
//                NOAA 14
//                integrSolarSpectralIrrad = 221.42f;
//                spectralResponseWidth = 0.1360f;
//                break;
//            default:
//                throw new OperatorException("Cannot parse source product name " + sourceProduct.getName() + " properly.");
//        }
        // GK: R=A (F/100 PI W  cos(sun_zenith)  abstandkorrektur)

        final double conversionFactor = 0.01 * integrSolarSpectralIrrad * Math.PI * spectralResponseWidth *
                Math.cos(sza * MathUtils.DTOR) * distanceCorr;
        double result;
        if (mode == ALBEDO_TO_RADIANCE) {
            result = input * conversionFactor;
        } else if (mode == RADIANCE_TO_ALBEDO) {
            result = input / conversionFactor;
        } else {
            throw new IllegalArgumentException("wrong mode " + mode + " for albedo/radance converison");
        }
        return result;
    }

    private int getDoy(String yymmdd) {
        return IdepixUtils.getDoyFromYYMMDD(yymmdd);
    }

    private String getProductDatestring() {
        // provides datestring as DDMMYY !!!
        final int productNameStartIndex = sourceProduct.getName().indexOf("ao");
        // allow names such as subset_of_ao11060992103109_120417.dim
        return sourceProduct.getName().substring(productNameStartIndex + 4, productNameStartIndex + 10);
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
        sampleConfigurer.defineSample(index++, "sat_zenith");
        sampleConfigurer.defineSample(index++, "rel_azimuth");
        for (int i = 0; i < 5; i++) {
            sampleConfigurer.defineSample(index++, Constants.AVHRR_AC_RADIANCE_OLD_BAND_NAMES[i]);
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
        sampleConfigurer.defineSample(index++, "vza");
        sampleConfigurer.defineSample(index++, "rel_azimuth");
        sampleConfigurer.defineSample(index++, "bt_4");
        sampleConfigurer.defineSample(index++, "bt_5");
        sampleConfigurer.defineSample(index++, "refl_1");
        sampleConfigurer.defineSample(index++, "refl_2");
        sampleConfigurer.defineSample(index++, "refl_3");

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
        FlagCoding flagCoding = AvhrrAcUtils.createAvhrrAcFlagCoding(Constants.CLASSIF_BAND_NAME);
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
            super(AvhrrAcClassification2Op.class);
        }
    }
}
