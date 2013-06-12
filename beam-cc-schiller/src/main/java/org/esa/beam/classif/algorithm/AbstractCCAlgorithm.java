package org.esa.beam.classif.algorithm;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;

abstract class AbstractCCAlgorithm implements CCAlgorithm {

    protected double[] inverse_solar_fluxes;

    abstract public void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException;

    abstract public void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException;

    abstract public void configureTargetProduct(Product sourceProduct, ProductConfigurer productConfigurer);

    abstract public void prepareInputs(Product sourceProduct) throws OperatorException;

    abstract public void computePixel(Sample[] sourceSamples, WritableSample[] targetSamples);

    abstract public void setToUnprocessed(WritableSample[] samples);

    protected void calculateInverseSolarFluxes(Product sourceProduct) {
        inverse_solar_fluxes = new double[Constants.NUM_RADIANCE_BANDS];
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final float solarFlux = sourceProduct.getBandAt(i).getSolarFlux();
            inverse_solar_fluxes[i] = 1.0 / solarFlux;
        }
    }

    protected double getToaRef(double inverse_cos_sza, int i, double toa_rad) {
        return toa_rad * Math.PI * inverse_solar_fluxes[i] * inverse_cos_sza;
    }
}
