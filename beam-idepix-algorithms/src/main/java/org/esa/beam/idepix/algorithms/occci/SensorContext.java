package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

/**
 * OC-CCI sensor context interface
 * todo: duplicated from beam-waterradiance - put in a more general place?
 *
 * @author olafd
 */
public interface SensorContext {
    Sensor getSensor();

    int getNumSpectralInputBands();

    void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct, String spectralBandPrefix);

    /**
     * Scales the input spectral data to be consistent with MERIS TOA reflectances  (dimensionless)
     * Scaling is performed "in place", if necessary
     *
     * @param inputs input data vector
     */
    void scaleInputSpectralDataToReflectance(double[] inputs, int offset);

    void init(Product sourceProduct);

    int getSrcRadOffset();
}
