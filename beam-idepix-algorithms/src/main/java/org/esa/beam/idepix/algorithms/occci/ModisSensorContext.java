package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

/**
 * MODIS sensor context implementation
 *
 * @author olafd
 */
class ModisSensorContext implements SensorContext {
    private static final int[] SPECTRAL_OUTPUT_INDEXES = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final float[] SPECTRAL_OUTPUT_WAVELENGTHS = new float[]{
            413.f, 443.f, 488.f, 531.f, 551.f, 667.f, 678.f, 748.f, 870.f,
            645.f, 859.f,
            469.f, 555.f, 1240.f, 1640.f, 2130.f
    };
    private static final int[] NN_OUTPUT_INDICES = new int[]{1, 2, 4, 8, 9, 15, 18, 21, 26};

    private static final String[] EARTH_SUN_DISTANCE_NAMES = {"Earth-Sun_Distance", "Earth-Sun Distance"};
    private static final int[] START_POSITION_IN_PRODUCT_DATA = new int[]{180, 190, 200, 210, 220, 230, 250, 270, 280};

    private final static String MODIS_L1B_REFLECTANCE_1_BAND_NAME = "EV_1KM_RefSB_8";                    // 412
    private final static String MODIS_L1B_REFLECTANCE_2_BAND_NAME = "EV_1KM_RefSB_9";                    // 443
    private final static String MODIS_L1B_REFLECTANCE_3_BAND_NAME = "EV_1KM_RefSB_10";                   // 488
    private final static String MODIS_L1B_REFLECTANCE_4_BAND_NAME = "EV_1KM_RefSB_11";                   // 531
    private final static String MODIS_L1B_REFLECTANCE_5_BAND_NAME = "EV_1KM_RefSB_12";                   // 547
    private final static String MODIS_L1B_REFLECTANCE_6_BAND_NAME = "EV_1KM_RefSB_13lo";                 // 667
    private final static String MODIS_L1B_REFLECTANCE_7_BAND_NAME = "EV_1KM_RefSB_13hi";                 // 667
    private final static String MODIS_L1B_REFLECTANCE_8_BAND_NAME = "EV_1KM_RefSB_14lo";                 // 678
    private final static String MODIS_L1B_REFLECTANCE_9_BAND_NAME = "EV_1KM_RefSB_14hi";                 // 678
    private final static String MODIS_L1B_REFLECTANCE_10_BAND_NAME = "EV_1KM_RefSB_15";                   // 748
    private final static String MODIS_L1B_REFLECTANCE_11_BAND_NAME = "EV_1KM_RefSB_16";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_12_BAND_NAME = "EV_1KM_RefSB_17";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_13_BAND_NAME = "EV_1KM_RefSB_18";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_14_BAND_NAME = "EV_1KM_RefSB_19";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_15_BAND_NAME = "EV_1KM_RefSB_26";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_16_BAND_NAME = "EV_250_Aggr1km_RefSB_1";           // 645
    private final static String MODIS_L1B_REFLECTANCE_17_BAND_NAME = "EV_250_Aggr1km_RefSB_2";           // 859
    private final static String MODIS_L1B_REFLECTANCE_18_BAND_NAME = "EV_500_Aggr1km_RefSB_3";           // 469
    private final static String MODIS_L1B_REFLECTANCE_19_BAND_NAME = "EV_500_Aggr1km_RefSB_4";           // 555
    private final static String MODIS_L1B_REFLECTANCE_20_BAND_NAME = "EV_500_Aggr1km_RefSB_5";           // 1240
    private final static String MODIS_L1B_REFLECTANCE_21_BAND_NAME = "EV_500_Aggr1km_RefSB_6";           // 1640
    private final static String MODIS_L1B_REFLECTANCE_22_BAND_NAME = "EV_500_Aggr1km_RefSB_7";           // 2130

    private final static String[] MODIS_L1B_SPECTRAL_BAND_NAMES = {
            MODIS_L1B_REFLECTANCE_1_BAND_NAME,
            MODIS_L1B_REFLECTANCE_2_BAND_NAME,
            MODIS_L1B_REFLECTANCE_3_BAND_NAME,
            MODIS_L1B_REFLECTANCE_4_BAND_NAME,
            MODIS_L1B_REFLECTANCE_5_BAND_NAME,
            MODIS_L1B_REFLECTANCE_6_BAND_NAME,
            MODIS_L1B_REFLECTANCE_7_BAND_NAME,
            MODIS_L1B_REFLECTANCE_8_BAND_NAME,
            MODIS_L1B_REFLECTANCE_9_BAND_NAME,
            MODIS_L1B_REFLECTANCE_10_BAND_NAME,
            MODIS_L1B_REFLECTANCE_11_BAND_NAME,
            MODIS_L1B_REFLECTANCE_12_BAND_NAME,
            MODIS_L1B_REFLECTANCE_13_BAND_NAME,
            MODIS_L1B_REFLECTANCE_14_BAND_NAME,
            MODIS_L1B_REFLECTANCE_15_BAND_NAME,
            MODIS_L1B_REFLECTANCE_16_BAND_NAME,
            MODIS_L1B_REFLECTANCE_17_BAND_NAME,
            MODIS_L1B_REFLECTANCE_18_BAND_NAME,
            MODIS_L1B_REFLECTANCE_19_BAND_NAME,
            MODIS_L1B_REFLECTANCE_20_BAND_NAME,
            MODIS_L1B_REFLECTANCE_21_BAND_NAME,
            MODIS_L1B_REFLECTANCE_22_BAND_NAME,
    };
    private final static int MODIS_L1B_NUM_SPECTRAL_BANDS = MODIS_L1B_SPECTRAL_BAND_NAMES.length;

    private final static String MODIS_L1B_EMISSIVITY_1_BAND_NAME = "EV_1KM_Emissive_20";                    // 3750
    private final static String MODIS_L1B_EMISSIVITY_2_BAND_NAME = "EV_1KM_Emissive_21";                    // 3959
    private final static String MODIS_L1B_EMISSIVITY_3_BAND_NAME = "EV_1KM_Emissive_22";                    // 3959
    private final static String MODIS_L1B_EMISSIVITY_4_BAND_NAME = "EV_1KM_Emissive_23";                    // 4050
    private final static String MODIS_L1B_EMISSIVITY_5_BAND_NAME = "EV_1KM_Emissive_24";                    // 4465
    private final static String MODIS_L1B_EMISSIVITY_6_BAND_NAME = "EV_1KM_Emissive_25";                    // 4515
    private final static String MODIS_L1B_EMISSIVITY_7_BAND_NAME = "EV_1KM_Emissive_27";                    // 6715
    private final static String MODIS_L1B_EMISSIVITY_8_BAND_NAME = "EV_1KM_Emissive_28";                    // 7325
    private final static String MODIS_L1B_EMISSIVITY_9_BAND_NAME = "EV_1KM_Emissive_29";                    // 8550
    private final static String MODIS_L1B_EMISSIVITY_10_BAND_NAME = "EV_1KM_Emissive_30";                    // 9730
    private final static String MODIS_L1B_EMISSIVITY_11_BAND_NAME = "EV_1KM_Emissive_31";                    // 11030
    private final static String MODIS_L1B_EMISSIVITY_12_BAND_NAME = "EV_1KM_Emissive_32";                    // 12020
    private final static String MODIS_L1B_EMISSIVITY_13_BAND_NAME = "EV_1KM_Emissive_33";                    // 13335
    private final static String MODIS_L1B_EMISSIVITY_14_BAND_NAME = "EV_1KM_Emissive_34";                    // 13635
    private final static String MODIS_L1B_EMISSIVITY_15_BAND_NAME = "EV_1KM_Emissive_35";                    // 13935
    private final static String MODIS_L1B_EMISSIVITY_16_BAND_NAME = "EV_1KM_Emissive_36";                    // 14235

    private final static String[] MODIS_L1B_EMISSIVE_BAND_NAMES = {
            MODIS_L1B_EMISSIVITY_1_BAND_NAME,
            MODIS_L1B_EMISSIVITY_2_BAND_NAME,
            MODIS_L1B_EMISSIVITY_3_BAND_NAME,
            MODIS_L1B_EMISSIVITY_4_BAND_NAME,
            MODIS_L1B_EMISSIVITY_5_BAND_NAME,
            MODIS_L1B_EMISSIVITY_6_BAND_NAME,
            MODIS_L1B_EMISSIVITY_7_BAND_NAME,
            MODIS_L1B_EMISSIVITY_8_BAND_NAME,
            MODIS_L1B_EMISSIVITY_9_BAND_NAME,
            MODIS_L1B_EMISSIVITY_10_BAND_NAME,
            MODIS_L1B_EMISSIVITY_11_BAND_NAME,
            MODIS_L1B_EMISSIVITY_12_BAND_NAME,
            MODIS_L1B_EMISSIVITY_13_BAND_NAME,
            MODIS_L1B_EMISSIVITY_14_BAND_NAME,
            MODIS_L1B_EMISSIVITY_15_BAND_NAME,
            MODIS_L1B_EMISSIVITY_16_BAND_NAME,
    };

    private final static int MODIS_L1B_NUM_EMISSIVE_BANDS = MODIS_L1B_EMISSIVE_BAND_NAMES.length;

    private final static double surfacePressureDefaultValue = 1019.0;
    private final static double ozoneDefaultValue = 330.0;

    // derived from cahalan table from Kerstin tb 2013-11-15
    private final static double[] defaultSolarFluxes = new double[]{
            1740.458085,          // 412
            1844.698571,          // 443
            1949.723913,          // 488
            1875.394737,          // 531
            1882.428333,          // 547
            1545.183846,          // 667
            1507.529167,          // 678
            1277.037,             // 748
            945.3382727,          // 869
            1601.482295,          // 645
            967.137667,           // 859
            2072.03625,           // 469
            1874.005,             // 555
            456.1987143,          // 1240
            229.882,              // 1640
            92.5171833            // 2130
    };
    private static final String globalMetadataName = "GLOBAL_METADATA";

    private double[] solarFluxes;
    private double earthSunDistance;


    @Override
    public int getNumSpectralInputBands() {
        return MODIS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public String[] getSpectralInputBandNames() {
        return MODIS_L1B_SPECTRAL_BAND_NAMES;
    }

    @Override
    public int getNumSpectralOutputBands() {
        return MODIS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public int[] getSpectralOutputBandIndices() {
        return SPECTRAL_OUTPUT_INDEXES;
    }

    /**
     * Retrieves the center wavelengths for the output spectral bands in [nm]
     *
     * @return the array of wavelengths
     */
    @Override
    public float[] getSpectralOutputWavelengths() {
        return SPECTRAL_OUTPUT_WAVELENGTHS;
    }

    @Override
    public int[] getNnOutputIndices() {
        return NN_OUTPUT_INDICES;
    }

    @Override
    public Sensor getSensor() {
        return Sensor.MODIS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct) {
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(i, MODIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct);
        }
        for (int i = 0; i < MODIS_L1B_NUM_EMISSIVE_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.MODIS_SRC_RAD_OFFSET + i, MODIS_L1B_EMISSIVE_BAND_NAMES[i], sourceProduct);
        }
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer) {
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(i, MODIS_L1B_SPECTRAL_BAND_NAMES[i]);
        }
        for (int i = 0; i < MODIS_L1B_NUM_EMISSIVE_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.MODIS_SRC_RAD_OFFSET + i, MODIS_L1B_EMISSIVE_BAND_NAMES[i]);
        }
    }

    /**
     * Scales the input spectral data to be consistent with the MERIS case. Resulting data should be TOA radiance in
     *      [mW/(m^2 * sr * nm)] or [LU], i.e. Luminance Unit. So this is the inversion of 'Rad2Refl'.
     * Scaling is performed "in place", if necessary
     *
     * @param inputs input data vector
     */
    @Override
    public void scaleInputSpectralDataToRadiance(double[] inputs, int offset) {
        final double oneDivEarthSunDistanceSquare = 1.0 / (earthSunDistance * earthSunDistance);
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final int index = offset + i;
            inputs[index] = inputs[index] * solarFluxes[i] * oneDivEarthSunDistanceSquare / Math.PI;
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs, int offset) {
        // nothing to do - MODIS comes as TOA reflectance
    }

    @Override
    public void copyTiePointData(double[] inputs, Sample[] sourceSamples) {
        // todo: check if needed for MODIS
//        inputs[0] = sourceSamples[Constants.SRC_SZA].getDouble();
//        inputs[1] = sourceSamples[Constants.SRC_SAA].getDouble();
//        inputs[2] = sourceSamples[Constants.SRC_VZA].getDouble();
//        inputs[3] = sourceSamples[Constants.SRC_VAA].getDouble();
//        inputs[4] = surfacePressureDefaultValue;
//        inputs[5] = ozoneDefaultValue;
    }

    @Override
    public double[] getSolarFluxes(Product sourceProduct) {
        return solarFluxes;
    }

    @Override
    public double getSurfacePressure() {
        return surfacePressureDefaultValue;
    }

    @Override
    public double getOzone() {
        return ozoneDefaultValue;
    }

    @Override
    public double getEarthSunDistanceInAU() {
        return earthSunDistance;
    }

    @Override
    public void init(Product sourceProduct) {
        earthSunDistance = 1;
        solarFluxes = defaultSolarFluxes;
        MetadataElement globalMetadataElement = sourceProduct.getMetadataRoot().getElement(globalMetadataName);
        if (globalMetadataElement != null) {
            final MetadataAttribute solarFluxesAttribute = globalMetadataElement.getAttribute("Solar_Irradiance_on_RSB_Detectors_over_pi");
            if (solarFluxesAttribute != null) {
                final ProductData productData = solarFluxesAttribute.getData();
                solarFluxes = new double[MODIS_L1B_NUM_SPECTRAL_BANDS];
                for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                    for (int j = 0; j < 10; j++) {
                        solarFluxes[i] += productData.getElemDoubleAt(START_POSITION_IN_PRODUCT_DATA[i] + j);
                    }
                    solarFluxes[i] /= 10;
                    solarFluxes[i] *= Math.PI;
                }
            }
        } else {
            globalMetadataElement = sourceProduct.getMetadataRoot().getElement("Global_Attributes");
        }
        if (globalMetadataElement != null) {
            for (String EARTH_SUN_DISTANCE_NAME : EARTH_SUN_DISTANCE_NAMES) {
                final MetadataAttribute earthSunDistanceAttribute = globalMetadataElement.getAttribute(EARTH_SUN_DISTANCE_NAME);
                if (earthSunDistanceAttribute != null) {
                    earthSunDistance = earthSunDistanceAttribute.getData().getElemDouble();
                    break;
                }
            }
        }
    }

    @Override
    public int getDetectorIndex(Sample[] samples) {
        return -1;
    }

    @Override
    public int getSrcRadOffset() {
        return Constants.MODIS_SRC_RAD_OFFSET;
    }

    @Override
    public int getTargetSampleOffset() {
        return 0;
    }

    @Override
    public double correctSunAzimuth(double sunAzimuth) {
        return correctAzimuthAngle(sunAzimuth);
    }

    @Override
    public double correctViewAzimuth(double viewAzimuth) {
        return correctAzimuthAngle(viewAzimuth);
    }

    private double correctAzimuthAngle(double azimuth) {
        if (azimuth < 0.0) {
            return azimuth + 360.0;
        }
        return azimuth;
    }

    public static double convertModisEmissiveRadianceToTemperature(double radiance, int emissiveBandIndex) {

        final double c1 = 2.0 * Constants.PLANCK_CONSTANT *
                Math.pow(Constants.VACUUM_LIGHT_SPEED, 2.0);

        final double c2 = Constants.PLANCK_CONSTANT * Constants.VACUUM_LIGHT_SPEED /
                Constants.BOLTZMANN_CONSTANT;

        // use metres in units:
        final double wvlMetres = Constants.MODIS_EMISSIVE_WAVELENGTHS[emissiveBandIndex] / 1.E9;  // input is in microns!
        final double radMetres = radiance * 1.E6;

        double temperature = c2 / (wvlMetres * Math.log(c1 / (radMetres * Math.pow(wvlMetres, 5.0)) + 1.0));
        temperature = (temperature - Constants.TCI[emissiveBandIndex]) / Constants.TCS[emissiveBandIndex];

        return temperature;
    }

}
