package org.esa.beam.classif;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

@OperatorMetadata(alias = "Meris.CCNNHS",
        version = "1.0",
        authors = "Tom Block",
        copyright = "(c) 2013 by Brockmann Consult",
        description = "Computing cloud masks using neural networks by H.Schiller")
public class CcNnHsOp extends PixelOperator {

    private static final int NUM_RADIANCE_BANDS = 15;
    private static final int NUM_NN_INPUTS = 20;
    public static final int LAT_INDEX = 15;
    public static final int LON_INDEX = 16;

    //private static final double[] unprocessed = new double[]{-1.0};

    @SourceProduct
    private Product sourceProduct;

    @Parameter(defaultValue = "NOT (l1_flags.INVALID OR l1_flags.COSMETIC)",
            description = "A flag expression that defines pixels to be processed.")
    private String validPixelExpression;

    private double sinTime;
    private double cosTime;

    private VirtualBandOpImage validOpImage;
    private WatermaskClassifier watermaskClassifier;

    private ThreadLocal<double[]> inputVector = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[NUM_NN_INPUTS];
        }
    };

    private NnThreadLocal nn_all_1;
    private NnThreadLocal nn_all_2;
    private NnThreadLocal nn_ter_1;
    private NnThreadLocal nn_ter_2;
    private NnThreadLocal nn_wat_1;
    private NnThreadLocal nn_wat_2;
    private NnThreadLocal nn_simple_wat_1;
    private NnThreadLocal nn_simple_wat_2;

    private double[] inverse_solar_fluxes;

    private ThreadLocal<Rectangle> sampleRegion = new ThreadLocal<Rectangle>() {
        @Override
        protected Rectangle initialValue() {
            return new Rectangle(0, 0, 1, 1);
        }
    };

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        if (isSampleValid(x, y)) {
            // try {
            final double[] inputLocal = inputVector.get();
            assembleInput(sourceSamples, inputLocal);

            //final boolean sampleOverLand = isSampleOverLand(sourceSamples[LAT_INDEX].getFloat(), sourceSamples[LON_INDEX].getFloat());

            final double[] all_1_out = nn_all_1.get().calc(inputLocal);
            final double[] all_2_out = nn_all_2.get().calc(inputLocal);

            double[] ter_1_out;
            double[] ter_2_out;
            double[] wat_1_out;
            double[] wat_2_out;
            double[] simple_wat_1_out;
            double[] simple_wat_2_out;
            //if (sampleOverLand) {
//                    wat_1_out = unprocessed;
//                    wat_2_out = unprocessed;
//                    simple_wat_1_out = unprocessed;
//                    simple_wat_2_out = unprocessed;
            ter_1_out = nn_ter_1.get().calc(inputLocal);
            ter_2_out = nn_ter_2.get().calc(inputLocal);
            // } else {
            wat_1_out = nn_wat_1.get().calc(inputLocal);
            wat_2_out = nn_wat_2.get().calc(inputLocal);
            simple_wat_1_out = nn_simple_wat_1.get().calc(inputLocal);
            simple_wat_2_out = nn_simple_wat_2.get().calc(inputLocal);
//                    ter_1_out = unprocessed;
//                    ter_2_out = unprocessed;
            //}

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

//            } catch (IOException e) {
//                throw new OperatorException(e.getMessage());
//            }
        } else {
            setToUnprocessed(targetSamples);
        }
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();

        validOpImage = VirtualBandOpImage.createMask(validPixelExpression,
                sourceProduct,
                ResolutionLevel.MAXRES);

        final double dayOfYearFraction = getDayOfYearFraction(sourceProduct);
        final double daysFractionArgument = 2.0 * Math.PI * dayOfYearFraction;
        sinTime = Math.sin(daysFractionArgument);
        cosTime = Math.cos(daysFractionArgument);

        inverse_solar_fluxes = new double[NUM_RADIANCE_BANDS];
        for (int i = 0; i < NUM_RADIANCE_BANDS; i++) {
            final float solarFlux = sourceProduct.getBandAt(i).getSolarFlux();
            inverse_solar_fluxes[i] = 1.0 / solarFlux;
        }

        try {
            // @todo 3 tb/tb find out what these parameters should be set to --- optimally tb 2013-05-23
            watermaskClassifier = new WatermaskClassifier(50, 3, 3);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }

        nn_all_1 = new NnThreadLocal("NN4all/clind/varin1/11x8x5x3_2440.4.net");
        nn_all_2 = new NnThreadLocal("NN4all/clind/varin2/11x8x5x3_2247.9.net");
        nn_ter_1 = new NnThreadLocal("NN4ter/clind/varin1/11x8x5x3_1114.6.net");
        nn_ter_2 = new NnThreadLocal("NN4ter/clind/varin2/11x8x5x3_1015.2.net");
        nn_wat_1 = new NnThreadLocal("NN4wat/clind/varin1/11x8x5x3_956.1.net");
        nn_wat_2 = new NnThreadLocal("NN4wat/clind/varin2/11x8x5x3_890.6.net");
        nn_simple_wat_1 = new NnThreadLocal("NN4wat/simpclind/varin1/11x8x5x3_728.2.net");
        nn_simple_wat_2 = new NnThreadLocal("NN4wat/simpclind/varin2/11x8x5x3_639.5.net");
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int i = 0; i < NUM_RADIANCE_BANDS; i++) {
            sampleConfigurer.defineSample(i, "radiance_" + (i + 1));
        }
        sampleConfigurer.defineSample(15, "latitude");
        sampleConfigurer.defineSample(16, "longitude");
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
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
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);

        final Product targetProduct = productConfigurer.getTargetProduct();
        targetProduct.setName(sourceProduct.getName());

        productConfigurer.copyBands(new ProductNodeFilter<Band>() {
            @Override
            public boolean accept(Band productNode) {
                return !targetProduct.containsBand(productNode.getName());
            }
        });

        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_all_1");
        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_all_2");
        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_ter_1");
        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_ter_2");
        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_wat_1");
        addBandWithFullFlagCoding(productConfigurer, targetProduct, "cl_wat_2");

        addBandWithSimpleFlagCoding(productConfigurer, targetProduct, "cl_simple_wat_1");
        addBandWithSimpleFlagCoding(productConfigurer, targetProduct, "cl_simple_wat_2");

        addFloatBand(productConfigurer, "cl_all_1_val");
        addFloatBand(productConfigurer, "cl_all_2_val");
        addFloatBand(productConfigurer, "cl_ter_1_val");
        addFloatBand(productConfigurer, "cl_ter_2_val");
        addFloatBand(productConfigurer, "cl_wat_1_val");
        addFloatBand(productConfigurer, "cl_wat_2_val");
        addFloatBand(productConfigurer, "cl_simple_wat_1_val");
        addFloatBand(productConfigurer, "cl_simple_wat_2_val");
    }

    private void addFloatBand(ProductConfigurer productConfigurer, String bandName) {
        productConfigurer.addBand(bandName, ProductData.TYPE_FLOAT32);
    }

    private void addBandWithFullFlagCoding(ProductConfigurer productConfigurer, Product targetProduct, String bandname) {
        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        final Band band = productConfigurer.addBand(bandname, ProductData.TYPE_INT8);
        final FlagCoding flagCoding = createFullFlagCoding(bandname);
        flagCodingGroup.add(flagCoding);
        band.setSampleCoding(flagCoding);
    }

    private void addBandWithSimpleFlagCoding(ProductConfigurer productConfigurer, Product targetProduct, String bandname) {
        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        final Band band = productConfigurer.addBand(bandname, ProductData.TYPE_INT8);
        final FlagCoding flagCoding = createSimpleFlagCoding(bandname);
        flagCodingGroup.add(flagCoding);
        band.setSampleCoding(flagCoding);
    }


    // package access for testing only tb 2013-05-22
    static double getDayOfYearFraction(Product product) {
        final ProductData.UTC startTime = product.getStartTime();
        final ProductData.UTC endTime = product.getEndTime();
        if (startTime == null || endTime == null) {
            throw new OperatorException("Unable to read start or stop time from product.");
        }

        final Calendar startTimeAsCalendar = startTime.getAsCalendar();
        final int day_Start = startTimeAsCalendar.get(Calendar.DAY_OF_YEAR);
        final int day_End = endTime.getAsCalendar().get(Calendar.DAY_OF_YEAR);

        final int year = startTimeAsCalendar.get(Calendar.YEAR);
        final GregorianCalendar calendar = (GregorianCalendar) GregorianCalendar.getInstance();
        final double daysInYear;
        if (calendar.isLeapYear(year)) {
            daysInYear = 366.0;
        } else {
            daysInYear = 365.0;
        }

        final double midDay = (day_End + day_Start) * 0.5;
        return midDay / daysInYear;
    }

    // for testing only tb 2013-05-22
    void injectProduct(Product sourceProduct) {
        this.sourceProduct = sourceProduct;
    }

    // for testing only tb 2013-05-23
    void injectTimeSines(double sinTime, double cosTime) {
        this.sinTime = sinTime;
        this.cosTime = cosTime;
    }

    void injectInverseSolarFluxes(double[] inverse_solar_fluxes) {
        this.inverse_solar_fluxes = inverse_solar_fluxes;
    }

    // package access for testing only tb 2013-05-22
    static FlagCoding createFullFlagCoding(String bandName) {
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.addFlag("clear", CloudClassifier.CLEAR_MASK, "clear");
        flagCoding.addFlag("spamx", CloudClassifier.SPAMX_MASK, "spamx");
        flagCoding.addFlag("noncl", CloudClassifier.NONCL_MASK, "noncl");
        flagCoding.addFlag("cloud", CloudClassifier.CLOUD_MASK, "cloud");
        flagCoding.addFlag("unproc", 0x10, "unprocessed");
        return flagCoding;
    }

    // package access for testing only tb 2013-05-22
    static FlagCoding createSimpleFlagCoding(String bandName) {
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.addFlag("clear", CloudClassifier.CLEAR_MASK, "clear");
        flagCoding.addFlag("spamx_or_noncl", CloudClassifier.SPAMX_OR_NONCL_MASK, "spamx or noncl");
        flagCoding.addFlag("cloud", CloudClassifier.CLOUD_MASK, "cloud");
        flagCoding.addFlag("unproc", 0x10, "unprocessed");
        return flagCoding;
    }

    // package access for testing only tb 2013-05-23
    double[] assembleInput(Sample[] inputSamples, double[] inputVector) {
        for (int i = 0; i < NUM_RADIANCE_BANDS; i++) {
            final double toa_rad = inputSamples[i].getDouble();
            inputVector[i] = Math.sqrt(toa_rad * Math.PI * inverse_solar_fluxes[i]);
        }
        inputVector[15] = sinTime;
        inputVector[16] = cosTime;
        inputVector[17] = Math.cos(inputSamples[LAT_INDEX].getDouble());
        inputVector[18] = Math.sin(inputSamples[LON_INDEX].getDouble());
        inputVector[19] = Math.cos(inputSamples[LON_INDEX].getDouble());
        return inputVector;
    }

    // package access for testing only tb 2013-05-23
    static void setToUnprocessed(WritableSample[] samples) {
        for (WritableSample sample : samples) {
            sample.set(0x10);
        }
    }

    private boolean isSampleValid(int x, int y) {
        final Rectangle localRect = sampleRegion.get();
        localRect.setLocation(x, y);
        return validOpImage.getData(localRect).getSample(x, y, 0) != 0;
    }

    private boolean isSampleOverLand(float lat, float lon) throws IOException {
        return !watermaskClassifier.isWater(lat, lon);
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CcNnHsOp.class);
        }
    }
}
