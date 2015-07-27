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

    // 'pixbox' band names?
//    private static final String SEAWIFS_L1B_RADIANCE_1_BAND_NAME = "L_412";
//    private static final String SEAWIFS_L1B_RADIANCE_2_BAND_NAME = "L_443";
//    private static final String SEAWIFS_L1B_RADIANCE_3_BAND_NAME = "L_490";
//    private static final String SEAWIFS_L1B_RADIANCE_4_BAND_NAME = "L_510";
//    private static final String SEAWIFS_L1B_RADIANCE_5_BAND_NAME = "L_555";
//    private static final String SEAWIFS_L1B_RADIANCE_6_BAND_NAME = "L_670";
//    private static final String SEAWIFS_L1B_RADIANCE_7_BAND_NAME = "L_765";
//    private static final String SEAWIFS_L1B_RADIANCE_8_BAND_NAME = "L_865";

    // in GAC/HRPT we have these names
    private static final String SEAWIFS_L1B_RADIANCE_1_BAND_NAME = "412";
    private static final String SEAWIFS_L1B_RADIANCE_2_BAND_NAME = "443";
    private static final String SEAWIFS_L1B_RADIANCE_3_BAND_NAME = "490";
    private static final String SEAWIFS_L1B_RADIANCE_4_BAND_NAME = "510";
    private static final String SEAWIFS_L1B_RADIANCE_5_BAND_NAME = "555";
    private static final String SEAWIFS_L1B_RADIANCE_6_BAND_NAME = "670";
    private static final String SEAWIFS_L1B_RADIANCE_7_BAND_NAME = "765";
    private static final String SEAWIFS_L1B_RADIANCE_8_BAND_NAME = "865";

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

    private double[] solarFluxes;
    private double earthSunDistance;

    @Override
    public int getNumSpectralInputBands() {
        return SEAWIFS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public Sensor getSensor() {
        return Sensor.SEAWIFS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct, String spectralBandPrefix) {
        sampleConfigurer.defineSample(Constants.SRC_SZA, "solz", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_SAA, "sola", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_VZA, "senz", sourceProduct);
        sampleConfigurer.defineSample(Constants.SRC_VAA, "sena", sourceProduct);
        for (int i = 0; i < SEAWIFS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sampleConfigurer.defineSample(Constants.SEAWIFS_SRC_RAD_OFFSET+ i,
                                          spectralBandPrefix + SEAWIFS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct);
        }
    }

    /**
     * Scales the input spectral data to be consistent with the MERIS case. Resulting data should be TOA radiance in
     * [mW/(m^2 * sr * nm)] or [LU], i.e. Luminance Unit
     * Scaling is performed "in place", if necessary
     *
     * @param inputs input data vector
     */
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
    public void init(Product sourceProduct) {
        earthSunDistance = 1;
        solarFluxes = defaultNasaSolarFluxes;
    }

    @Override
    public int getSrcRadOffset() {
        return Constants.SEAWIFS_SRC_RAD_OFFSET;
    }

}
