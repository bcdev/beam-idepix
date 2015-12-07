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
import org.esa.beam.idepix.util.SunPosition;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.util.math.RsMathUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Basic operator for GlobAlbedo pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.usgs.classification",
        version = "2.2",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Basic operator for pixel classification from AVHRR L1b data.")
public class AvhrrAcUSGSClassificationOp extends AbstractAvhrrAcClassificationOp {

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

    ElevationModel getasseElevationModel;

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

    static double computeRelativeAzimuth(double vaaRad, double saaRad) {
        return correctRelAzimuthRange(vaaRad, saaRad);
    }

    static double[] computeAzimuthAngles(double sza, double vza,
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

        double vaaRad;
        if (Math.abs(vza) >= 0.09 && greatCirclePointToSatRad > 0.0) {
            vaaRad = computeVaa(latPointRad, lonPointRad, latSatRad, lonSatRad, greatCirclePointToSatRad);
        } else {
            vaaRad = 0.0;
        }

        final double saaRad = computeSaa(sza, latPointRad, lonPointRad, latSunRad, lonSunRad);

        return new double[]{saaRad, vaaRad, greatCirclePointToSatRad};
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

        final double sza = sourceSamples[AvhrrAcConstants.SRC_USGS_SZA].getDouble();
        final double latitude = sourceSamples[AvhrrAcConstants.SRC_USGS_LAT].getDouble();
        final double longitude = sourceSamples[AvhrrAcConstants.SRC_USGS_LON].getDouble();
        aacAlgorithm.setLatitude(latitude);
        aacAlgorithm.setLongitude(longitude);
        aacAlgorithm.setSza(sza);
        double vza = Math.abs(vzaTable.getVza(x));  // !!!

        final GeoPos satPosition = computeSatPosition(y);
        final GeoPos pointPosition = getGeoPos(x, y);

        final double[] azimuthAngles = computeAzimuthAngles(sza, vza, satPosition, pointPosition, sunPosition);
        final double saaRad = azimuthAngles[0];
        final double vaaRad = azimuthAngles[1];
        final double relAzi = computeRelativeAzimuth(saaRad, vaaRad) * MathUtils.RTOD;
        final double altitude = computeGetasseAltitude(x, y);

        double[] avhrrRadiance = new double[AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length];
        final double albedo1 = sourceSamples[AvhrrAcConstants.SRC_USGS_ALBEDO_1].getDouble();             // %
        final double albedo2 = sourceSamples[AvhrrAcConstants.SRC_USGS_ALBEDO_2].getDouble();             // %

        // GK, 20150325: convert albedo1, 2 to 'normalized' albedo:
        // norm_albedo_i = albedo_i / (d^2_sun * cos(theta_sun))    , effect is a few % only
        final double d = getDistanceCorr() * Math.cos(sza * MathUtils.DTOR);
        final double albedo1Norm = albedo1 / d;
        final double albedo2Norm = albedo2 / d;

        int targetSamplesIndex;
        if (albedo1 >= 0.0 && albedo2 >= 0.0 && !AvhrrAcUtils.anglesInvalid(sza, vza, azimuthAngles[0], azimuthAngles[1])) {

            avhrrRadiance[0] = convertBetweenAlbedoAndRadiance(albedo1, sza, ALBEDO_TO_RADIANCE, 0);
            avhrrRadiance[1] = convertBetweenAlbedoAndRadiance(albedo2, sza, ALBEDO_TO_RADIANCE, 1);
            avhrrRadiance[2] = sourceSamples[AvhrrAcConstants.SRC_USGS_RADIANCE_3].getDouble();           // mW*cm/(m^2*sr)
            final double albedo3 = convertBetweenAlbedoAndRadiance(avhrrRadiance[2], sza, RADIANCE_TO_ALBEDO, 2);
            avhrrRadiance[3] = sourceSamples[AvhrrAcConstants.SRC_USGS_RADIANCE_4].getDouble();           // mW*cm/(m^2*sr)
            avhrrRadiance[4] = sourceSamples[AvhrrAcConstants.SRC_USGS_RADIANCE_5].getDouble();           // mW*cm/(m^2*sr)
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
            aacAlgorithm.setRadiance(avhrrRadiance);
            aacAlgorithm.setWaterFraction(waterFraction);

            double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

            aacAlgorithm.setNnOutput(nnOutput);
            aacAlgorithm.setAmbiguousLowerBoundaryValue(avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
            aacAlgorithm.setAmbiguousSureSeparationValue(avhrracSchillerNNCloudAmbiguousSureSeparationValue);
            aacAlgorithm.setSureSnowSeparationValue(avhrracSchillerNNCloudSureSnowSeparationValue);

            aacAlgorithm.setReflCh1(albedo1Norm / 100.0); // on [0,1]        --> put here albedo_norm now!!
            aacAlgorithm.setReflCh2(albedo2Norm / 100.0); // on [0,1]
            aacAlgorithm.setReflCh3(albedo3); // on [0,1]
//            final double btCh3 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[2], 3) - 273.15;     // !! todo: K or C, make uniform!
            final double btCh3 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[2], 3);     // GK,MB 20151102: use K everywhere!!
//            aacAlgorithm.setBtCh3(btCh3 + 273.15);
            aacAlgorithm.setBtCh3(btCh3);
            final double btCh4 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[3], 4);
            aacAlgorithm.setBtCh4(btCh4);
            final double btCh5 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[4], 5);
            aacAlgorithm.setBtCh5(btCh5);
            aacAlgorithm.setElevation(altitude);

            setClassifFlag(targetSamples, aacAlgorithm);
            targetSamplesIndex = 1;
            targetSamples[targetSamplesIndex++].set(vza);
            targetSamples[targetSamplesIndex++].set(sza);
            targetSamples[targetSamplesIndex++].set(vaaRad * MathUtils.RTOD);
            targetSamples[targetSamplesIndex++].set(saaRad * MathUtils.RTOD);
            targetSamples[targetSamplesIndex++].set(relAzi);
            targetSamples[targetSamplesIndex++].set(altitude);
            targetSamples[targetSamplesIndex++].set(btCh3);
            targetSamples[targetSamplesIndex++].set(btCh4);
            targetSamples[targetSamplesIndex++].set(btCh5);
            targetSamples[targetSamplesIndex++].set(albedo1Norm/100.);     // GK, 20150326
            targetSamples[targetSamplesIndex++].set(albedo2Norm/100.);
            targetSamples[targetSamplesIndex++].set(albedo3); // todo: normalize somehow?

        } else {
            targetSamplesIndex = 0;
            targetSamples[targetSamplesIndex++].set(AvhrrAcConstants.F_INVALID, true);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            targetSamples[targetSamplesIndex++].set(Float.NaN);
            avhrrRadiance[0] = Float.NaN;
            avhrrRadiance[1] = Float.NaN;
        }

        if (aacCopyRadiances) {
//            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
            for (int i = 2; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
                // do just radiances 3-5
                targetSamples[targetSamplesIndex + (i-2)].set(avhrrRadiance[i]);
            }
        }
    }

    private double computeGetasseAltitude(float x, float y)  {
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
        final int productNameStartIndex = sourceProduct.getName().indexOf("ao");
        // allow names such as subset_of_ao11060992103109_120417.dim
        return sourceProduct.getName().substring(productNameStartIndex + 4, productNameStartIndex + 10);
    }

    @Override
    void setNoaaId() {
        // ao11060992103109_120417.l1b
        final int productNameStartIndex = sourceProduct.getName().indexOf("ao");
        noaaId =  sourceProduct.getName().substring(productNameStartIndex + 2, productNameStartIndex + 4);
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, "sun_zenith");
        sampleConfigurer.defineSample(index++, "latitude");
        sampleConfigurer.defineSample(index++, "longitude");
        for (int i = 0; i < 2; i++) {
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_ALBEDO_BAND_NAMES[i]);
        }
        for (int i = 0; i < 3; i++) {
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES[i + 2]);
        }
        sampleConfigurer.defineSample(index, AvhrrAcConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        // the only standard band:
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.CLASSIF_BAND_NAME);
        sampleConfigurer.defineSample(index++, "vza");
        sampleConfigurer.defineSample(index++, "sza");
        sampleConfigurer.defineSample(index++, "vaa");
        sampleConfigurer.defineSample(index++, "saa");
        sampleConfigurer.defineSample(index++, "rel_azimuth");
        sampleConfigurer.defineSample(index++, "altitude");
        sampleConfigurer.defineSample(index++, "bt_3");
        sampleConfigurer.defineSample(index++, "bt_4");
        sampleConfigurer.defineSample(index++, "bt_5");
        sampleConfigurer.defineSample(index++, "refl_1");
        sampleConfigurer.defineSample(index++, "refl_2");
        sampleConfigurer.defineSample(index++, "rt_3");

        // radiances:
        if (aacCopyRadiances) {
            for (int i = 2; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
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

        Band vzaBand = productConfigurer.addBand("vza", ProductData.TYPE_FLOAT32);
        vzaBand.setDescription("view zenith angle");
        vzaBand.setUnit("dl");
        vzaBand.setNoDataValue(Float.NaN);
        vzaBand.setNoDataValueUsed(true);

        Band szaBand = productConfigurer.addBand("sza", ProductData.TYPE_FLOAT32);
        szaBand.setDescription("sun zenith angle");
        szaBand.setUnit("dl");
        szaBand.setNoDataValue(Float.NaN);
        szaBand.setNoDataValueUsed(true);

        Band vaaBand = productConfigurer.addBand("vaa", ProductData.TYPE_FLOAT32);
        vaaBand.setDescription("view azimuth angle");
        vaaBand.setUnit("dl");
        vaaBand.setNoDataValue(Float.NaN);
        vaaBand.setNoDataValueUsed(true);

        Band saaBand = productConfigurer.addBand("saa", ProductData.TYPE_FLOAT32);
        saaBand.setDescription("sun azimuth angle");
        saaBand.setUnit("dl");
        saaBand.setNoDataValue(Float.NaN);
        saaBand.setNoDataValueUsed(true);

        Band relaziBand = productConfigurer.addBand("rel_azimuth", ProductData.TYPE_FLOAT32);
        relaziBand.setDescription("relative azimuth");
        relaziBand.setUnit("deg");
        relaziBand.setNoDataValue(Float.NaN);
        relaziBand.setNoDataValueUsed(true);

        Band altitudeBand = productConfigurer.addBand("altitude", ProductData.TYPE_FLOAT32);
        altitudeBand.setDescription("Altitude from GETASSE");
        altitudeBand.setUnit("m");
        altitudeBand.setNoDataValue(Float.NaN);
        altitudeBand.setNoDataValueUsed(true);

        Band bt3Band = productConfigurer.addBand("bt_3", ProductData.TYPE_FLOAT32);
        bt3Band.setDescription("Channel 3 brightness temperature");
        bt3Band.setUnit("K");
        bt3Band.setNoDataValue(Float.NaN);
        bt3Band.setNoDataValueUsed(true);

        Band bt4Band = productConfigurer.addBand("bt_4", ProductData.TYPE_FLOAT32);
        bt4Band.setDescription("Channel 4 brightness temperature");
        bt4Band.setUnit("K");
        bt4Band.setNoDataValue(Float.NaN);
        bt4Band.setNoDataValueUsed(true);

        Band bt5Band = productConfigurer.addBand("bt_5", ProductData.TYPE_FLOAT32);
        bt5Band.setDescription("Channel 5 brightness temperature");
        bt5Band.setUnit("K");
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

        Band refl3Band = productConfigurer.addBand("rt_3", ProductData.TYPE_FLOAT32);
        refl3Band.setDescription("Channel 3 reflective part (rt3)");
        refl3Band.setUnit("dl");
        refl3Band.setNoDataValue(Float.NaN);
        refl3Band.setNoDataValueUsed(true);

        // radiances:
        if (aacCopyRadiances) {
            for (int i = 2; i < AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length; i++) {
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
            super(AvhrrAcUSGSClassificationOp.class);
        }
    }
}
