package org.esa.beam.classif;


import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.GregorianCalendar;
import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CcNnHsOpTest {

    private CcNnHsOp ccNnHsOp;

    @Before
    public void setUp() {
        ccNnHsOp = new CcNnHsOp();
    }

    @Test
    public void testOperatorMetadata() {
        final OperatorMetadata operatorMetadata = CcNnHsOp.class.getAnnotation(OperatorMetadata.class);
        assertNotNull(operatorMetadata);
        assertEquals("Meris.CCNNHS", operatorMetadata.alias());
        assertEquals("1.0", operatorMetadata.version());
        assertEquals("Tom Block", operatorMetadata.authors());
        assertEquals("(c) 2013 by Brockmann Consult", operatorMetadata.copyright());
        assertEquals("Computing cloud masks using neural networks by H.Schiller", operatorMetadata.description());
    }

    @Test
    public void testSourceProductAnnotation() throws NoSuchFieldException {
        final Field productField = CcNnHsOp.class.getDeclaredField("sourceProduct");
        assertNotNull(productField);

        final SourceProduct productFieldAnnotation = productField.getAnnotation(SourceProduct.class);
        assertNotNull(productFieldAnnotation);
    }

    @Test
    public void testValidPixelExpressionAnnotation() throws NoSuchFieldException {
        final Field validPixelField = CcNnHsOp.class.getDeclaredField("validPixelExpression");

        final Parameter annotation = validPixelField.getAnnotation(Parameter.class);
        assertNotNull(annotation);
        assertEquals("NOT (l1_flags.INVALID OR l1_flags.COSMETIC)", annotation.defaultValue());
        assertEquals("A flag expression that defines pixels to be processed.", annotation.description());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testInheritance() {
        assertTrue(ccNnHsOp instanceof PixelOperator);
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        ccNnHsOp.configureSourceSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(17, sampleMap.size());
        for (int i = 0; i < 15; i++) {
            assertEquals("radiance_" + (i + 1), sampleMap.get(i));
        }
    }

    @Test
    public void testConfigureTargetSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        ccNnHsOp.configureTargetSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(8, sampleMap.size());
        assertEquals("cl_all_1", sampleMap.get(0));
        assertEquals("cl_all_2", sampleMap.get(1));
        assertEquals("cl_ter_1", sampleMap.get(2));
        assertEquals("cl_ter_2", sampleMap.get(3));
        assertEquals("cl_wat_1", sampleMap.get(4));
        assertEquals("cl_wat_2", sampleMap.get(5));
        assertEquals("cl_simple_wat_1", sampleMap.get(6));
        assertEquals("cl_simple_wat_2", sampleMap.get(7));
    }

    @Test
    public void testGetDayOfYearFraction() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2007 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));

        double fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(0.038356164383561646, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("18-MAR-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("18-MAR-2009 11:23:11"));

        fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(0.21095890410958903, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("01-JAN-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("01-JAN-2009 11:23:11"));

        fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(0.0027397260273972603, fraction, 1e-8);

        product.setStartTime(ProductData.UTC.parse("31-DEC-2009 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("31-DEC-2009 11:23:11"));

        fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(1.0, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_leapYear() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2004 09:23:11"));
        product.setEndTime(ProductData.UTC.parse("14-JAN-2004 11:23:11"));

        final GregorianCalendar cal = (GregorianCalendar) GregorianCalendar.getInstance();
        assertTrue(cal.isLeapYear(2004));

        double fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(0.03825136612021858, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_coveringTwoDays() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(ProductData.UTC.parse("14-JAN-2011 23:56:14"));
        product.setEndTime(ProductData.UTC.parse("15-JAN-2011 00:15:11"));

        double fraction = CcNnHsOp.getDayOfYearFraction(product);
        assertEquals(0.03972602739726028, fraction, 1e-8);
    }

    @Test
    public void testGetDayOfYearFraction_missingTimes() throws ParseException {
        final Product product = new Product("bla", "test", 3, 3);
        product.setStartTime(null);
        product.setEndTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));

        try {
            CcNnHsOp.getDayOfYearFraction(product);
            fail("OperatorException expected");
        } catch (OperatorException expected) {
        }

        product.setStartTime(ProductData.UTC.parse("14-JAN-2007 11:23:11"));
        product.setEndTime(null);

        try {
            CcNnHsOp.getDayOfYearFraction(product);
            fail("OperatorException expected");
        } catch (OperatorException expected) {
        }
    }

    @Test
    public void testConfigureTargetProduct() {
        final Product sourceProduct = new Product("overwrites the old", "don'tcare", 2, 2);
        final TestProductConfigurer configurer = new TestProductConfigurer();
        ccNnHsOp.injectProduct(sourceProduct);

        ccNnHsOp.configureTargetProduct(configurer);

        final Product targetProduct = configurer.getTargetProduct();
        assertEquals("overwrites the old", targetProduct.getName());

        final ProductNodeFilter<Band> copyBandsFilter = configurer.getCopyBandsFilter();
        assertNotNull(copyBandsFilter);

        assertBandPresent(targetProduct, "cl_all_1");
        assertBandPresent(targetProduct, "cl_all_2");
        assertBandPresent(targetProduct, "cl_ter_1");
        assertBandPresent(targetProduct, "cl_ter_2");
        assertBandPresent(targetProduct, "cl_wat_1");
        assertBandPresent(targetProduct, "cl_wat_2");
        assertBandPresent(targetProduct, "cl_simple_wat_1");
        assertBandPresent(targetProduct, "cl_simple_wat_2");

        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        assertNotNull(flagCodingGroup.get("cl_all_1"));
        assertNotNull(flagCodingGroup.get("cl_all_2"));
        assertNotNull(flagCodingGroup.get("cl_ter_1"));
        assertNotNull(flagCodingGroup.get("cl_ter_2"));
        assertNotNull(flagCodingGroup.get("cl_wat_1"));
        assertNotNull(flagCodingGroup.get("cl_wat_2"));
        assertNotNull(flagCodingGroup.get("cl_simple_wat_1"));
        assertNotNull(flagCodingGroup.get("cl_simple_wat_2"));
    }

    @Test
    public void testAssembleInput() {
        final double[] inverseSolarFluxes = new double[] {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        double[] inputVector = new double[20];
        final WritableSample[] inputSamples = new WritableSample[17];
        for (int i = 0; i < inputSamples.length; i++) {
            inputSamples[i] = new TestSample();
            inputSamples[i].set((double) i + 1);
        }

        final double sinTime = 0.5;
        final double cosTime = 0.6;
        ccNnHsOp.injectTimeSines(sinTime, cosTime);
        ccNnHsOp.injectInverseSolarFluxes(inverseSolarFluxes);

        inputVector = ccNnHsOp.assembleInput(inputSamples, inputVector);

        // check radiances
        for (int i = 0; i < 15; i++) {
            assertEquals(Math.sqrt((i + 1) * Math.PI * inverseSolarFluxes[i]), inputVector[i], 1e-8);
        }
        assertEquals(sinTime, inputVector[15], 1e-8);
        assertEquals(cosTime, inputVector[16], 1e-8);

        assertEquals(Math.cos(16), inputVector[17], 1e-8);
        assertEquals(Math.sin(17), inputVector[18], 1e-8);
        assertEquals(Math.cos(17), inputVector[19], 1e-8);
    }

    @Test
    public void testCreateFullFlagCoding() {
        final FlagCoding flagCoding = CcNnHsOp.createFullFlagCoding("band_name");
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

    @Test
    public void testCreateSimpleFlagCoding() {
        final FlagCoding flagCoding = CcNnHsOp.createSimpleFlagCoding("band_name");
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
    public void testSetToUnprocessed() {
        final TestSample testSamples[] = new TestSample[8];
        for (int i = 0; i < testSamples.length; i++) {
            testSamples[i] = new TestSample();
            testSamples[i].set(7878);
        }

        CcNnHsOp.setToUnprocessed(testSamples);

        for (TestSample testSample : testSamples) {
            assertEquals(0x10, testSample.getInt());
        }
    }

    @Test
    public void testSpi() {
        final CcNnHsOp.Spi spi = new CcNnHsOp.Spi();
        final Class<? extends Operator> operatorClass = spi.getOperatorClass();
        assertTrue(operatorClass.isAssignableFrom(CcNnHsOp.class));
    }

    private void assertBandPresent(Product targetProduct, String bandName) {
        final Band band = targetProduct.getBand(bandName);
        assertNotNull(band);
        assertEquals(ProductData.TYPE_INT8, band.getDataType());
        assertNotNull(band.getFlagCoding());
    }

    private class TestSampleConfigurer implements SampleConfigurer {
        private HashMap<Integer, String> sampleMap = new HashMap<Integer, String>();

        @Override
        public void defineSample(int index, String name) {
            sampleMap.put(index, name);
        }

        private HashMap<Integer, String> getSampleMap() {
            return sampleMap;
        }

        @Override
        public void defineSample(int index, String name, Product product) {
        }
    }

    private class TestProductConfigurer implements ProductConfigurer {

        private Product targetProduct;
        private ProductNodeFilter<Band> copyBandsFilter;

        private TestProductConfigurer() {
            targetProduct = new Product("ZAPP", "schnuffi", 2, 2);
        }

        @Override
        public Product getSourceProduct() {
            return null;
        }

        @Override
        public void setSourceProduct(Product sourceProduct) {
        }

        @Override
        public Product getTargetProduct() {
            return targetProduct;
        }

        @Override
        public void copyMetadata() {
        }

        @Override
        public void copyTimeCoding() {
        }

        @Override
        public void copyGeoCoding() {
        }

        @Override
        public void copyMasks() {
        }

        @Override
        public void copyTiePointGrids(String... gridName) {
        }

        @Override
        public void copyBands(String... bandName) {
        }

        @Override
        public void copyBands(ProductNodeFilter<Band> filter) {
            copyBandsFilter = filter;
        }

        private ProductNodeFilter<Band> getCopyBandsFilter() {
            return copyBandsFilter;
        }

        @Override
        public void copyVectorData() {
        }

        @Override
        public Band addBand(String name, int dataType) {
            return targetProduct.addBand(name, dataType);
        }

        @Override
        public Band addBand(String name, int dataType, double noDataValue) {
            return null;
        }

        @Override
        public Band addBand(String name, String expression) {
            return null;
        }

        @Override
        public Band addBand(String name, String expression, double noDataValue) {
            return null;
        }
    }
}
