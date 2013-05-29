package org.esa.beam.classif.algorithm;


import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;

public interface CCAlgorithm {

    void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException;

    void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException;

    void configureTargetProduct(Product sourceProduct, ProductConfigurer productConfigurer);

    void prepareInputs(Product sourceProduct) throws OperatorException;

    void computePixel(Sample[] sourceSamples, WritableSample[] targetSamples);

    void setToUnprocessed(WritableSample[] samples);
}
