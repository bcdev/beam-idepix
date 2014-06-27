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
    private static final int[] SPECTRAL_OUTPUT_INDEXES = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final float[] SPECTRAL_OUTPUT_WAVELENGTHS = new float[]{413.f, 443.f, 488.f, 531.f, 551.f, 667.f, 678.f, 748.f, 870.f};
    private static final int[] NN_OUTPUT_INDICES = new int[]{1, 2, 4, 8, 9, 15, 18, 21, 26};

    private static final String[] EARTH_SUN_DISTANCE_NAMES = {"Earth-Sun_Distance", "Earth-Sun Distance"};
    private static final int[] START_POSITION_IN_PRODUCT_DATA = new int[]{180, 190, 200, 210, 220, 230, 250, 270, 280};

    private final static String MODIS_L1B_RADIANCE_1_BAND_NAME = "EV_1KM_RefSB_8";
    private final static String MODIS_L1B_RADIANCE_2_BAND_NAME = "EV_1KM_RefSB_9";
    private final static String MODIS_L1B_RADIANCE_3_BAND_NAME = "EV_1KM_RefSB_10";
    private final static String MODIS_L1B_RADIANCE_4_BAND_NAME = "EV_1KM_RefSB_11";
    private final static String MODIS_L1B_RADIANCE_5_BAND_NAME = "EV_1KM_RefSB_12";
    private final static String MODIS_L1B_RADIANCE_6_BAND_NAME = "EV_1KM_RefSB_13lo";
    private final static String MODIS_L1B_RADIANCE_7_BAND_NAME = "EV_1KM_RefSB_14lo";
    private final static String MODIS_L1B_RADIANCE_8_BAND_NAME = "EV_1KM_RefSB_15";
    private final static String MODIS_L1B_RADIANCE_9_BAND_NAME = "EV_1KM_RefSB_16";

    private final static String[] MODIS_L1B_SPECTRAL_BAND_NAMES = {
            MODIS_L1B_RADIANCE_1_BAND_NAME,
            MODIS_L1B_RADIANCE_2_BAND_NAME,
            MODIS_L1B_RADIANCE_3_BAND_NAME,
            MODIS_L1B_RADIANCE_4_BAND_NAME,
            MODIS_L1B_RADIANCE_5_BAND_NAME,
            MODIS_L1B_RADIANCE_6_BAND_NAME,
            MODIS_L1B_RADIANCE_7_BAND_NAME,
            MODIS_L1B_RADIANCE_8_BAND_NAME,
            MODIS_L1B_RADIANCE_9_BAND_NAME,
    };
    private final static int MODIS_L1B_NUM_SPECTRAL_BANDS = MODIS_L1B_SPECTRAL_BAND_NAMES.length;
    private final static double surfacePressureDefaultValue = 1019.0;
    private final static double ozoneDefaultValue = 330.0;

    // derived from cahalan table from Kerstin tb 2013-11-15
    private final static double[] defaultSolarFluxes = new double[]{1740.458085, 1844.698571, 1949.723913, 1875.394737, 1882.428333, 1545.183846, 1507.529167, 1277.037, 945.3382727};
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
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, boolean csvMode) {
        sampleConfigurer.defineSample(Constants.SRC_SZA, "SolarZenith");
        sampleConfigurer.defineSample(Constants.SRC_SAA, "SolarAzimuth");
        sampleConfigurer.defineSample(Constants.SRC_VZA, "SensorZenith");
        sampleConfigurer.defineSample(Constants.SRC_VAA, "SensorAzimuth");
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.MODIS_SRC_RAD_OFFSET + i, MODIS_L1B_SPECTRAL_BAND_NAMES[i]);
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
    public void scaleInputSpectralDataToRadiance(double[] inputs) {
        final double oneDivEarthSunDistanceSquare = 1.0 / (earthSunDistance * earthSunDistance);
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final int index = Constants.SEAWIFS_SRC_RAD_OFFSET + i;
            inputs[index] = inputs[index] * solarFluxes[i] * oneDivEarthSunDistanceSquare / Math.PI;
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs) {
        // nothing to do - MODIS comes as TOA reflectance
    }

    @Override
    public void copyTiePointData(double[] inputs, Sample[] sourceSamples) {
        inputs[0] = sourceSamples[Constants.SRC_SZA].getDouble();
        inputs[1] = sourceSamples[Constants.SRC_SAA].getDouble();
        inputs[2] = sourceSamples[Constants.SRC_VZA].getDouble();
        inputs[3] = sourceSamples[Constants.SRC_VAA].getDouble();
        inputs[4] = surfacePressureDefaultValue;
        inputs[5] = ozoneDefaultValue;
    }

    @Override
    public double[] getSolarFluxes(Product sourceProduct) {
        return solarFluxes;
    }

    @Override
    public double[] copySolarFluxes(double[] input, double[] solarFluxes) {
        System.arraycopy(solarFluxes, 0, input, Constants.SRC_SOL_FLUX_OFFSET, MODIS_L1B_NUM_SPECTRAL_BANDS);
        return input;
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
}
