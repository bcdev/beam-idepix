package org.esa.beam.classif.algorithm;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class Utils {


    // @todo 3 tb/tb add test tb 2013-05-29
    static void addFloatBand(ProductConfigurer productConfigurer, String bandName) {
        productConfigurer.addBand(bandName, ProductData.TYPE_FLOAT32);
    }

    // package access for testing only tb 2013-05-22
    static FlagCoding createSimpleFlagCoding(String bandName) {
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.addFlag("clear", Constants.CLEAR_MASK, "clear");
        flagCoding.addFlag("spamx_or_noncl", Constants.SPAMX_OR_NONCL_MASK, "spamx or noncl");
        flagCoding.addFlag("cloud", Constants.CLOUD_MASK, "cloud");
        flagCoding.addFlag("unproc", Constants.UNPROCESSD_MASK, "unprocessed");
        return flagCoding;
    }

    // package access for testing only tb 2013-05-22
     static FlagCoding createFullFlagCoding(String bandName) {
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.addFlag("clear", Constants.CLEAR_MASK, "clear");
        flagCoding.addFlag("spamx", Constants.SPAMX_MASK, "spamx");
        flagCoding.addFlag("noncl", Constants.NONCL_MASK, "noncl");
        flagCoding.addFlag("cloud", Constants.CLOUD_MASK, "cloud");
        flagCoding.addFlag("unproc", Constants.UNPROCESSD_MASK, "unprocessed");
        return flagCoding;
    }

    // @todo 3 tb/tb add test tb 2013-05-29
    static void addBandWithFullFlagCoding(ProductConfigurer productConfigurer, Product targetProduct, String bandname) {
        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        final Band band = productConfigurer.addBand(bandname, ProductData.TYPE_INT8);
        final FlagCoding flagCoding = createFullFlagCoding(bandname);
        flagCodingGroup.add(flagCoding);
        band.setSampleCoding(flagCoding);
    }

    // @todo 3 tb/tb add test tb 2013-05-29
    static void addBandWithSimpleFlagCoding(ProductConfigurer productConfigurer, Product targetProduct, String bandname) {
        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        final Band band = productConfigurer.addBand(bandname, ProductData.TYPE_INT8);
        final FlagCoding flagCoding = createSimpleFlagCoding(bandname);
        flagCodingGroup.add(flagCoding);
        band.setSampleCoding(flagCoding);
    }

    // package access for testing only tb 2013-05-22
    public static double getDayOfYearFraction(Product product) {
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
}
