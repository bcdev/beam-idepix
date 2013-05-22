package org.esa.beam.classif;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.SampleOperator;
import org.esa.beam.framework.gpf.pointop.WritableSample;

import java.util.Calendar;
import java.util.GregorianCalendar;

@OperatorMetadata(alias = "Meris.CCNNHS",
        version = "1.0",
        authors = "Tom Block",
        copyright = "(c) 2013 by Brockmann Consult",
        description = "Computing cloud masks using neural networks by H.Schiller")
public class CcNnHsOp extends SampleOperator {

    private static final int NUM_RADIANCE_BANDS = 15;

    @SourceProduct
    private Product sourceProduct;

    private double sinTime;
    private double cosTime;

    @Override
    protected void computeSample(int x, int y, Sample[] sourceSamples, WritableSample targetSample) {
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();

        final double dayOfYearFraction = getDayOfYearFraction(sourceProduct);
        final double daysFractionArgument = 2.0 * Math.PI * dayOfYearFraction;
        sinTime = Math.sin(daysFractionArgument);
        cosTime = Math.cos(daysFractionArgument);
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

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CcNnHsOp.class);
        }
    }
}
