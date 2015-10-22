package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

/**
 * MERIS sensor context implementation
 * todo: implement if needed
 *
 * @author olafd
 */
class MerisSensorContext implements SensorContext {
    @Override
    public Sensor getSensor() {
        return Sensor.MERIS;
    }

    @Override
    public int getNumSpectralInputBands() {
        return EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct, String spectralBandPrefix) {
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (sourceProduct.containsBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i])) {
                sampleConfigurer.defineSample(i, EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct);
            }
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs, int offset) {

    }

    @Override
    public void init(Product sourceProduct) {

    }

    @Override
    public int getSrcRadOffset() {
        return 0;
    }
}
