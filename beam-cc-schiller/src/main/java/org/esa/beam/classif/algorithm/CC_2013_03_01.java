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

class CC_2013_03_01 implements CCAlgorithm {

    private static final int NUM_NN_INPUTS = 20;

    private static final int LAT_INDEX = 15;
    private static final int LON_INDEX = 16;
    private static final int SUN_ZENITH_INDEX = 17;

    private static final double DEG_TO_RAD = Math.PI / 180.0;

    private double sinTime;
    private double cosTime;
    private double[] inverse_solar_fluxes;

    private NnThreadLocal nn_all_1;
    private NnThreadLocal nn_all_2;
    private NnThreadLocal nn_ter_1;
    private NnThreadLocal nn_ter_2;
    private NnThreadLocal nn_wat_1;
    private NnThreadLocal nn_wat_2;
    private NnThreadLocal nn_simple_wat_1;
    private NnThreadLocal nn_simple_wat_2;

    private ThreadLocal<double[]> inputVector = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[NUM_NN_INPUTS];
        }
    };

    @Override
    public void computePixel(Sample[] sourceSamples, WritableSample[] targetSamples) {
        final double[] inputLocal = inputVector.get();
        assembleInput(sourceSamples, inputLocal);

        final double[] all_1_out = nn_all_1.get().calc(inputLocal);
        final double[] all_2_out = nn_all_2.get().calc(inputLocal);

        final double[] ter_1_out = nn_ter_1.get().calc(inputLocal);
        final double[] ter_2_out = nn_ter_2.get().calc(inputLocal);
        final double[] wat_1_out = nn_wat_1.get().calc(inputLocal);
        final double[] wat_2_out = nn_wat_2.get().calc(inputLocal);
        final double[] simple_wat_1_out = nn_simple_wat_1.get().calc(inputLocal);
        final double[] simple_wat_2_out = nn_simple_wat_2.get().calc(inputLocal);

        targetSamples[0].set(CloudClassifier.toFlag_all_var1(all_1_out[0]));
        targetSamples[1].set(CloudClassifier.toFlag_all_var2(all_2_out[0]));
        targetSamples[2].set(CloudClassifier.toFlag_ter_var1(ter_1_out[0]));
        targetSamples[3].set(CloudClassifier.toFlag_ter_var2(ter_2_out[0]));
        targetSamples[4].set(CloudClassifier.toFlag_wat_var1(wat_1_out[0]));
        targetSamples[5].set(CloudClassifier.toFlag_wat_var2(wat_2_out[0]));
        targetSamples[6].set(CloudClassifier.toFlag_wat_simple_var1(simple_wat_1_out[0]));
        targetSamples[7].set(CloudClassifier.toFlag_wat_simple_var2(simple_wat_2_out[0]));

        targetSamples[8].set(all_1_out[0]);
        targetSamples[9].set(all_2_out[0]);
        targetSamples[10].set(ter_1_out[0]);
        targetSamples[11].set(ter_2_out[0]);
        targetSamples[12].set(wat_1_out[0]);
        targetSamples[13].set(wat_2_out[0]);
        targetSamples[14].set(simple_wat_1_out[0]);
        targetSamples[15].set(simple_wat_2_out[0]);

        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            targetSamples[16 + i].set(inputLocal[i] * inputLocal[i]);
        }
    }

    @Override
    public void setToUnprocessed(WritableSample[] samples) {
        for (int i = 0; i < 16; i++) {
            samples[i].set(Constants.UNPROCESSD_MASK);
        }
        for (int i = 16; i < 31; i++) {
            samples[i].set(Float.NaN);
        }
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(i, "radiance_" + (i + 1));
        }
        sampleConfigurer.defineSample(15, "latitude");
        sampleConfigurer.defineSample(16, "longitude");
        sampleConfigurer.defineSample(17, "sun_zenith");
    }

    @Override
    public void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "cl_all_1");
        sampleConfigurer.defineSample(1, "cl_all_2");
        sampleConfigurer.defineSample(2, "cl_ter_1");
        sampleConfigurer.defineSample(3, "cl_ter_2");
        sampleConfigurer.defineSample(4, "cl_wat_1");
        sampleConfigurer.defineSample(5, "cl_wat_2");
        sampleConfigurer.defineSample(6, "cl_simple_wat_1");
        sampleConfigurer.defineSample(7, "cl_simple_wat_2");

        sampleConfigurer.defineSample(8, "cl_all_1_val");
        sampleConfigurer.defineSample(9, "cl_all_2_val");
        sampleConfigurer.defineSample(10, "cl_ter_1_val");
        sampleConfigurer.defineSample(11, "cl_ter_2_val");
        sampleConfigurer.defineSample(12, "cl_wat_1_val");
        sampleConfigurer.defineSample(13, "cl_wat_2_val");
        sampleConfigurer.defineSample(14, "cl_simple_wat_1_val");
        sampleConfigurer.defineSample(15, "cl_simple_wat_2_val");

        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(16 + i, "reflec_" + (i + 1));
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

        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_all_1");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_all_2");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_ter_1");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_ter_2");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_wat_1");
        Utils.addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_wat_2");

        Utils.addBandWithSimpleFlagCoding(productConfigurer, targetProduct, "cl_simple_wat_1");
        Utils.addBandWithSimpleFlagCoding(productConfigurer, targetProduct, "cl_simple_wat_2");

        Utils.addFloatBand(productConfigurer, "cl_all_1_val");
        Utils.addFloatBand(productConfigurer, "cl_all_2_val");
        Utils.addFloatBand(productConfigurer, "cl_ter_1_val");
        Utils.addFloatBand(productConfigurer, "cl_ter_2_val");
        Utils.addFloatBand(productConfigurer, "cl_wat_1_val");
        Utils.addFloatBand(productConfigurer, "cl_wat_2_val");
        Utils.addFloatBand(productConfigurer, "cl_simple_wat_1_val");
        Utils.addFloatBand(productConfigurer, "cl_simple_wat_2_val");
    }

    @Override
    public void prepareInputs(Product sourceProduct) throws OperatorException {
        final double dayOfYearFraction = Utils.getDayOfYearFraction(sourceProduct);
        final double daysFractionArgument = 2.0 * Math.PI * dayOfYearFraction;
        sinTime = Math.sin(daysFractionArgument);
        cosTime = Math.cos(daysFractionArgument);

        inverse_solar_fluxes = new double[Constants.NUM_RADIANCE_BANDS];
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final float solarFlux = sourceProduct.getBandAt(i).getSolarFlux();
            inverse_solar_fluxes[i] = 1.0 / solarFlux;
        }

        nn_all_1 = new NnThreadLocal("ver2013_03_01/NN4all/clind/varin1/11x8x5x3_2440.4.net");
        nn_all_2 = new NnThreadLocal("ver2013_03_01/NN4all/clind/varin2/11x8x5x3_2247.9.net");
        nn_ter_1 = new NnThreadLocal("ver2013_03_01/NN4ter/clind/varin1/11x8x5x3_1114.6.net");
        nn_ter_2 = new NnThreadLocal("ver2013_03_01/NN4ter/clind/varin2/11x8x5x3_1015.2.net");
        nn_wat_1 = new NnThreadLocal("ver2013_03_01/NN4wat/clind/varin1/11x8x5x3_956.1.net");
        nn_wat_2 = new NnThreadLocal("ver2013_03_01/NN4wat/clind/varin2/11x8x5x3_890.6.net");
        nn_simple_wat_1 = new NnThreadLocal("ver2013_03_01/NN4wat/simpclind/varin1/11x8x5x3_728.2.net");
        nn_simple_wat_2 = new NnThreadLocal("ver2013_03_01/NN4wat/simpclind/varin2/11x8x5x3_639.5.net");
    }

    // package access for testing only tb 2013-05-23
    double[] assembleInput(Sample[] inputSamples, double[] inputVector) {
        final double sza = inputSamples[SUN_ZENITH_INDEX].getDouble();
        final double inverse_cos_sza = 1.0 / Math.cos(sza * DEG_TO_RAD);
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final double toa_rad = inputSamples[i].getDouble();
            inputVector[i] = Math.sqrt(toa_rad * Math.PI * inverse_solar_fluxes[i] * inverse_cos_sza);
        }
        inputVector[15] = sinTime;
        inputVector[16] = cosTime;
        inputVector[17] = Math.cos(inputSamples[LAT_INDEX].getDouble() * DEG_TO_RAD);
        inputVector[18] = Math.sin(inputSamples[LON_INDEX].getDouble() * DEG_TO_RAD);
        inputVector[19] = Math.cos(inputSamples[LON_INDEX].getDouble() * DEG_TO_RAD);
        return inputVector;
    }

    // for testing only tb 2013-05-23
    void injectTimeSines(double sinTime, double cosTime) {
        this.sinTime = sinTime;
        this.cosTime = cosTime;
    }

    // for testing only tb 2013-05-23
    void injectInverseSolarFluxes(double[] inverse_solar_fluxes) {
        this.inverse_solar_fluxes = inverse_solar_fluxes;
    }

}
