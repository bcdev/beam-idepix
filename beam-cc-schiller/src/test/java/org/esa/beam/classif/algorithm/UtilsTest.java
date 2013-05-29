package org.esa.beam.classif.algorithm;

import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.Test;

import java.text.ParseException;
import java.util.GregorianCalendar;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UtilsTest {

    @Test
    public void testGetDayOfYearFraction() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2007 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));

        double fraction = Utils.getDayOfYearFraction(product);
        assertEquals(0.038356164383561646, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("18-MAR-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("18-MAR-2009 11:23:11"));

        fraction = Utils.getDayOfYearFraction(product);
        assertEquals(0.21095890410958903, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("01-JAN-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("01-JAN-2009 11:23:11"));

        fraction = Utils.getDayOfYearFraction(product);
        assertEquals(0.0027397260273972603, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("31-DEC-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("31-DEC-2009 11:23:11"));

        fraction = Utils.getDayOfYearFraction(product);
        assertEquals(1.0, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_leapYear() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2004 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("14-JAN-2004 11:23:11"));

        final GregorianCalendar cal = (GregorianCalendar) GregorianCalendar.getInstance();
        assertTrue(cal.isLeapYear(2004));

        double fraction = Utils.getDayOfYearFraction(product);
        assertEquals(0.03825136612021858, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_coveringTwoDays() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2011 23:56:14"));
        product.setEndTime(ProductData.UTC.parse("15-JAN-2011 00:15:11"));

        double fraction = Utils.getDayOfYearFraction(product);
        assertEquals(0.03972602739726028, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_missingTimes() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(null);
        product.setEndTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));

        try {
            Utils.getDayOfYearFraction(product);
            fail("OperatorException expected");
        } catch (OperatorException expected) {
        }

        product.setStartTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));
        product.setEndTime(null);

        try {
            Utils.getDayOfYearFraction(product);
            fail("OperatorException expected");
        } catch (OperatorException expected) {
        }
    }

    @Test
    public void testCreateSimpleFlagCoding() {
        final FlagCoding flagCoding = Utils.createSimpleFlagCoding("band_name");
        assertNotNull(flagCoding);
        assertEquals("band_name", flagCoding.getName());

        final MetadataAttribute clear = flagCoding.getFlag("clear");
        assertNotNull(clear);
        assertEquals(1, clear.getData().getElemInt());

        final MetadataAttribute spamx = flagCoding.getFlag("spamx_or_noncl");
        assertNotNull(spamx);
        assertEquals(2, spamx.getData().getElemInt());

        final MetadataAttribute cloud = flagCoding.getFlag("cloud");
        assertNotNull(cloud);
        assertEquals(8, cloud.getData().getElemInt());

        final MetadataAttribute unproc = flagCoding.getFlag("unproc");
        assertNotNull(unproc);
        assertEquals(16, unproc.getData().getElemInt());
    }

    @Test
    public void testCreateFullFlagCoding() {
        final FlagCoding flagCoding = Utils.createFullFlagCoding("band_name");
        assertNotNull(flagCoding);
        assertEquals("band_name", flagCoding.getName());

        final MetadataAttribute clear = flagCoding.getFlag("clear");
        assertNotNull(clear);
        assertEquals(1, clear.getData().getElemInt());

        final MetadataAttribute spamx = flagCoding.getFlag("spamx");
        assertNotNull(spamx);
        assertEquals(2, spamx.getData().getElemInt());

        final MetadataAttribute noncl = flagCoding.getFlag("noncl");
        assertNotNull(noncl);
        assertEquals(4, noncl.getData().getElemInt());

        final MetadataAttribute cloud = flagCoding.getFlag("cloud");
        assertNotNull(cloud);
        assertEquals(8, cloud.getData().getElemInt());

        final MetadataAttribute unproc = flagCoding.getFlag("unproc");
        assertNotNull(unproc);
        assertEquals(16, unproc.getData().getElemInt());
    }
}
