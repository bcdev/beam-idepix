package org.esa.beam.classif.algorithm;


import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeFilter;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;

class CC_2013_05_09 implements CCAlgorithm {

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(i, "radiance_" + (i + 1));
        }
        sampleConfigurer.defineSample(15, "sun_zenith");
    }

    @Override
    public void computePixel(Sample[] sourceSamples, WritableSample[] targetSamples) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setToUnprocessed(WritableSample[] samples) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "cl_all_3");
        sampleConfigurer.defineSample(1, "cl_ter_3");
        sampleConfigurer.defineSample(2, "cl_wat_3");
        sampleConfigurer.defineSample(3, "cl_simple_wat_3");

        sampleConfigurer.defineSample(4, "cl_all_3_val");
        sampleConfigurer.defineSample(5, "cl_ter_3_val");
        sampleConfigurer.defineSample(6, "cl_wat_3_val");
        sampleConfigurer.defineSample(7, "cl_simple_wat_3_val");

        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(8 + i, "reflec_" + (i + 1));
        }
    }

    @Override
    public void configureTargetProduct(Product sourceProduct, ProductConfigurer productConfigurer) {
        final Product targetProduct = productConfigurer.getTargetProduct();
        targetProduct.setName(sourceProduct.getName());

        for (int i = 0; i < 15; i++) {
            Utils.addFloatBand(productConfigurer, "reflec_" + (i + 1));
        }

        productConfigurer.copyBands(new ProductNodeFilter<Band>() {
            @Override
            public boolean accept(Band productNode) {
                final String name = productNode.getName();
                return !(targetProduct.containsBand(name) || name.contains("radiance"));
            }
        });

        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_all_3");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_ter_3");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_wat_3");

        Utils.addBandWithSimpleFlagCoding(productConfigurer, targetProduct, "cl_simple_wat_3");

        Utils.addFloatBand(productConfigurer, "cl_all_3_val");
        Utils.addFloatBand(productConfigurer, "cl_ter_3_val");
        Utils.addFloatBand(productConfigurer, "cl_wat_3_val");
        Utils.addFloatBand(productConfigurer, "cl_simple_wat_3_val");
    }

    @Override
    public void prepareInputs(Product sourceProduct) throws OperatorException {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
