package org.esa.beam.classif.algorithm;


import org.esa.beam.classif.CloudClassifier;
import org.esa.beam.classif.NnThreadLocal;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeFilter;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;

class CC_2013_05_09 implements CCAlgorithm {

    private static final int SUN_ZENITH_INDEX = 15;
    private static final int NUM_NN_INPUTS = 14;
    private static final double DEG_TO_RAD = Math.PI / 180.0;

    private double[] inverse_solar_fluxes;

    private NnThreadLocal nn_all_3;
    private NnThreadLocal nn_ter_3;
    private NnThreadLocal nn_wat_3;
    private NnThreadLocal nn_simple_wat_3;

    private ThreadLocal<double[]> inputVector = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[NUM_NN_INPUTS];
        }
    };

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(i, "radiance_" + (i + 1));
        }
        sampleConfigurer.defineSample(15, "sun_zenith");
    }

    @Override
    public void computePixel(Sample[] sourceSamples, WritableSample[] targetSamples) {
        final double[] inputLocal = inputVector.get();
        assembleInput(sourceSamples, inputLocal);

        final double[] nn_all_out = nn_all_3.get().calc(inputLocal);
        final double[] nn_ter_out = nn_ter_3.get().calc(inputLocal);
        final double[] nn_wat_out = nn_wat_3.get().calc(inputLocal);
        final double[] nn_simple_wat_out = nn_simple_wat_3.get().calc(inputLocal);

        targetSamples[0].set(CloudClassifier.toFlag_all_var3(nn_all_out[0]));
        targetSamples[1].set(CloudClassifier.toFlag_ter_var3(nn_ter_out[0]));
        targetSamples[2].set(CloudClassifier.toFlag_wat_var3(nn_wat_out[0]));
        targetSamples[3].set(CloudClassifier.toFlag_wat_simple_var3(nn_simple_wat_out[0]));

        targetSamples[4].set(nn_all_out[0]);
        targetSamples[5].set(nn_ter_out[0]);
        targetSamples[6].set(nn_wat_out[0]);
        targetSamples[7].set(nn_simple_wat_out[0]);

        final double sza = sourceSamples[SUN_ZENITH_INDEX].getDouble();
        final double inverse_cos_sza = 1.0 / Math.cos(sza * DEG_TO_RAD);
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            targetSamples[8 + i].set(getToaRef(inverse_cos_sza, i, sourceSamples[i].getDouble()));
        }
    }

    @Override
    public void setToUnprocessed(WritableSample[] samples) {
        for (int i = 0; i < 4; i++) {
            samples[i].set(Constants.UNPROCESSD_MASK);
        }
        for (int i = 4; i < samples.length; i++) {
            samples[i].set(Float.NaN);
        }
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
        // @todo 2 tb/tb move to base class tb 2013-05-29
        inverse_solar_fluxes = new double[Constants.NUM_RADIANCE_BANDS];
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final float solarFlux = sourceProduct.getBandAt(i).getSolarFlux();
            inverse_solar_fluxes[i] = 1.0 / solarFlux;
        }

        nn_all_3 = new NnThreadLocal("ver2013_05_09/NN4all/clind/varin3/11x8x5x3_2628.7.net");
        nn_ter_3 = new NnThreadLocal("ver2013_05_09/NN4ter/clind/varin3/11x8x5x3_1258.8.net");
        nn_wat_3 = new NnThreadLocal("ver2013_05_09/NN4wat/clind/varin3/11x8x5x3_1057.8.net");
        nn_simple_wat_3 = new NnThreadLocal("ver2013_05_09/NN4wat/simpclind/varin3/11x8x5x3_737.7.net");
    }

    // package access for testing only tb 2013-05-23
    double[] assembleInput(Sample[] inputSamples, double[] inputVector) {
        final double sza = inputSamples[SUN_ZENITH_INDEX].getDouble();
        final double inverse_cos_sza = 1.0 / Math.cos(sza * DEG_TO_RAD);
        for (int i = 0; i < 10; i++) {
            final double toa_rad = inputSamples[i].getDouble();
            inputVector[i] = Math.sqrt(getToaRef(inverse_cos_sza, i, toa_rad));
        }
        for (int i = 11; i < 15; i++) {
            final double toa_rad = inputSamples[i].getDouble();
            inputVector[i - 1] = Math.sqrt(getToaRef(inverse_cos_sza, i, toa_rad));
        }

        return inputVector;
    }

    private double getToaRef(double inverse_cos_sza, int i, double toa_rad) {
        return toa_rad * Math.PI * inverse_solar_fluxes[i] * inverse_cos_sza;
    }

    // for testing only tb 2013-05-29
    void injectInverseSolarFluxes(double[] inverse_solar_fluxes) {
        this.inverse_solar_fluxes = inverse_solar_fluxes;
    }
}
