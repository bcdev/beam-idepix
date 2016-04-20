package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.idepix.IdepixConstants;

/**
 * VIIRS sensor context implementation
 *
 * @author olafd
 */
class ViirsSensorContext implements SensorContext {
    private static final int[] SPECTRAL_OUTPUT_INDEXES = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final float[] SPECTRAL_OUTPUT_WAVELENGTHS = new float[]{
            410.f, 443.f, 486.f, 551.f, 671.f, 745.f, 862.f, 1238.f, 1601.f, 2257.f
    };
    private static final int[] NN_OUTPUT_INDICES = new int[]{0};

    private static String[] VIIRS_SPECTRAL_BAND_NAMES = IdepixConstants.VIIRS_SPECTRAL_BAND_NAMES;
    private final static int VIIRS_L1B_NUM_SPECTRAL_BANDS = VIIRS_SPECTRAL_BAND_NAMES.length;

    @Override
    public int getNumSpectralInputBands() {
        return VIIRS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public Sensor getSensor() {
        return Sensor.VIIRS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct, String spectralBandPrefix) {
        for (int i = 0; i < VIIRS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (sourceProduct.containsBand(VIIRS_SPECTRAL_BAND_NAMES[i])) {
                sampleConfigurer.defineSample(i, VIIRS_SPECTRAL_BAND_NAMES[i], sourceProduct);
            } else {
                sampleConfigurer.defineSample(i, VIIRS_SPECTRAL_BAND_NAMES[i].replace(".", "_"), sourceProduct);
            }
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs, int offset) {
        // nothing to do - VIIRS comes as TOA reflectance
    }

    @Override
    public void init(Product sourceProduct) {
        // nothing to do
    }

    @Override
    public int getSrcRadOffset() {
        return 0;
    }
}
