package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

/**
 * SeaWiFS sensor context implementation
 *
 * @author olafd
 */
class SeaWiFSSensorContext implements SensorContext {
    private static final int[] SPECTRAL_OUTPUT_INDEXES = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
    private static final float[] SPECTRAL_OUTPUT_WAVELENGTHS = new float[]{412.f, 443.f, 490.f, 510.f, 555.f, 670.f, 765.f, 865.f};
    private static final int[] NN_OUTPUT_INDICES = new int[]{1, 2, 4, 6, 10, 16, 23, 25};

    // those are definitely provided in the worn physical unit
    // private static final double[] defaultNasaSolarFluxes = {171.18, 188.76, 193.38, 192.56, 183.76, 151.22, 123.91, 95.965};

    // derived from cahalan table from Kerstin tb 2013-11-22
    private static final double[] defaultNasaSolarFluxes = {1735.518167, 1858.404314, 1981.076667, 1881.566829, 1874.005, 1537.254783, 1230.04, 957.6122143};

    private static final String SEAWIFS_L1B_RADIANCE_1_BAND_NAME = "L_412";
    private static final String SEAWIFS_L1B_RADIANCE_2_BAND_NAME = "L_443";
    private static final String SEAWIFS_L1B_RADIANCE_3_BAND_NAME = "L_490";
    private static final String SEAWIFS_L1B_RADIANCE_4_BAND_NAME = "L_510";
    private static final String SEAWIFS_L1B_RADIANCE_5_BAND_NAME = "L_555";
    private static final String SEAWIFS_L1B_RADIANCE_6_BAND_NAME = "L_670";
    private static final String SEAWIFS_L1B_RADIANCE_7_BAND_NAME = "L_765";
    private static final String SEAWIFS_L1B_RADIANCE_8_BAND_NAME = "L_865";

    static final String[] SEAWIFS_L1B_SPECTRAL_BAND_NAMES = {
            SEAWIFS_L1B_RADIANCE_1_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_2_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_3_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_4_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_5_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_6_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_7_BAND_NAME,
            SEAWIFS_L1B_RADIANCE_8_BAND_NAME,
    };

    static final int SEAWIFS_L1B_NUM_SPECTRAL_BANDS = SEAWIFS_L1B_SPECTRAL_BAND_NAMES.length;

    private static final double surfacePressureDefaultValue = 1019.0;
    private static final double ozoneDefaultValue = 330.0;

    private double[] solarFluxes;
    private double earthSunDistance;

    @Override
    public int getNumSpectralInputBands() {
        return SEAWIFS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public String[] getSpectralInputBandNames() {
        return SEAWIFS_L1B_SPECTRAL_BAND_NAMES;
    }

    @Override
    public int getNumSpectralOutputBands() {
        return SEAWIFS_L1B_NUM_SPECTRAL_BANDS;
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
        return Sensor.SEAWIFS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct) {
        sampleConfigurer.defineSample(Constants.SRC_SZA, "solz", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_SAA, "sola", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_VZA, "senz", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_VAA, "sena", sourceProduct);
        for (int i = 0; i < SEAWIFS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.SEAWIFS_SRC_RAD_OFFSET+ i, SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct);
        }
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer) {
        sampleConfigurer.defineSample(Constants.SRC_SZA, "solz");
        sampleConfigurer.defineSample(Constants.SRC_SAA, "sola");
        sampleConfigurer.defineSample(Constants.SRC_VZA, "senz");
        sampleConfigurer.defineSample(Constants.SRC_VAA, "sena");
        for (int i = 0; i < SEAWIFS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.SEAWIFS_SRC_RAD_OFFSET + i, SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i]);
        }
    }


    /**
     * Scales the input spectral data to be consistent with the MERIS case. Resulting data should be TOA radiance in
     * [mW/(m^2 * sr * nm)] or [LU], i.e. Luminance Unit
     * Scaling is performed "in place", if necessary
     *
     * @param inputs input data vector
     */
    @Override
    public void scaleInputSpectralDataToRadiance(double[] inputs, int offset) {
        for (int i = 0; i < SEAWIFS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final int index = offset + i;
            inputs[index] *= 10.0;
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs, int offset) {
        // first scale to consistent radiances:
        scaleInputSpectralDataToRadiance(inputs, offset);
        final double oneDivEarthSunDistanceSquare = 1.0 / (earthSunDistance * earthSunDistance);
        for (int i = 0; i < SEAWIFS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final int index = offset + i;
            // this is rad2refl:
            inputs[index] = inputs[index] * Math.PI  / (solarFluxes[i] * oneDivEarthSunDistanceSquare);
        }
    }

    @Override
    public void copyTiePointData(double[] inputs, Sample[] sourceSamples) {
        inputs[0] = sourceSamples[Constants.SRC_SZA].getDouble();
        inputs[1] = sourceSamples[Constants.SRC_SAA].getDouble();
        inputs[2] = sourceSamples[Constants.SRC_VZA].getDouble();
        inputs[3] = sourceSamples[Constants.SRC_VAA].getDouble();
        inputs[4] = 1019.0;
        inputs[5] = 330.0;
    }

    @Override
    public double[] getSolarFluxes(Product sourceProduct) {
        return defaultNasaSolarFluxes;
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
        solarFluxes = defaultNasaSolarFluxes;
    }

    @Override
    public int getDetectorIndex(Sample[] samples) {
        return -1;
    }

    @Override
    public int getSrcRadOffset() {
        return Constants.SEAWIFS_SRC_RAD_OFFSET;
    }

    @Override
    public int getTargetSampleOffset() {
        // SeaWiFS uses a pixel geo-coding. The framework automatically copies the longitude and latitude bands to
        // the target product. We need to skip overwriting these bands, therefore add an offset of 2. tb 2013-11-27
        return 2;
    }

    @Override
    public double correctSunAzimuth(double sunAzimuth) {
        // after lengthy discussion with Tonio, we came to the conclusion that the solar azimuth for SeaWiFS is defined
        // the same way as for MERIS, i.e. 0 is north, 90 east, 180 south, 270 west. So, nothing to correct. tb 2013-11-28
        return sunAzimuth;
    }

    @Override
    public double correctViewAzimuth(double viewAzimuth) {
        // after lengthy discussion with Tonio, we came to the conclusion that the viewing azimuth for SeaWiFS is defined
        // the same way as for MERIS, i.e. 0 is north, 90 east, 180 south, 270 west. So, nothing to correct. tb 2013-11-28
        return viewAzimuth;
    }
}
