package org.esa.beam.classif.algorithm;

import org.esa.beam.classif.TestProductConfigurer;
import org.esa.beam.classif.TestSample;
import org.esa.beam.classif.TestSampleConfigurer;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CC_2013_03_01Test extends AlgorithmTest {

    private CC_2013_03_01 algorithm;

    @Before
    public void setUp() {
        algorithm = new CC_2013_03_01();
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        algorithm.configureSourceSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(18, sampleMap.size());
        for (int i = 0; i < 15; i++) {
            assertEquals("radiance_" + (i + 1), sampleMap.get(i));
        }

        assertEquals("latitude", sampleMap.get(15));
        assertEquals("longitude", sampleMap.get(16));
        assertEquals("sun_zenith", sampleMap.get(17));
    }

    @Test
    public void testConfigureTargetSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        algorithm.configureTargetSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(31, sampleMap.size());
        assertEquals("cl_all_1", sampleMap.get(0));
        assertEquals("cl_all_2", sampleMap.get(1));
        assertEquals("cl_ter_1", sampleMap.get(2));
        assertEquals("cl_ter_2", sampleMap.get(3));
        assertEquals("cl_wat_1", sampleMap.get(4));
        assertEquals("cl_wat_2", sampleMap.get(5));
        assertEquals("cl_simple_wat_1", sampleMap.get(6));
        assertEquals("cl_simple_wat_2", sampleMap.get(7));

        assertEquals("cl_all_1_val", sampleMap.get(8));
        assertEquals("cl_all_2_val", sampleMap.get(9));
        assertEquals("cl_ter_1_val", sampleMap.get(10));
        assertEquals("cl_ter_2_val", sampleMap.get(11));
        assertEquals("cl_wat_1_val", sampleMap.get(12));
        assertEquals("cl_wat_2_val", sampleMap.get(13));
        assertEquals("cl_simple_wat_1_val", sampleMap.get(14));
        assertEquals("cl_simple_wat_2_val", sampleMap.get(15));

        for (int i = 0; i < 15; i++) {
            assertEquals("reflec_" + (i + 1), sampleMap.get(16 + i));
        }
    }

    @Test
    public void testConfigureTargetProduct() {
        final Product sourceProduct = new Product("overwrites the old", "don'tcare", 2, 2);
        final TestProductConfigurer configurer = new TestProductConfigurer();

        algorithm.configureTargetProduct(sourceProduct, configurer);

        final Product targetProduct = configurer.getTargetProduct();
        assertEquals("overwrites the old", targetProduct.getName());

        final ProductNodeFilter<Band> copyBandsFilter = configurer.getCopyBandsFilter();
        assertNotNull(copyBandsFilter);

        assertIntBandPresent(targetProduct, "cl_all_1");
        assertIntBandPresent(targetProduct, "cl_all_2");
        assertIntBandPresent(targetProduct, "cl_ter_1");
        assertIntBandPresent(targetProduct, "cl_ter_2");
        assertIntBandPresent(targetProduct, "cl_wat_1");
        assertIntBandPresent(targetProduct, "cl_wat_2");
        assertIntBandPresent(targetProduct, "cl_simple_wat_1");
        assertIntBandPresent(targetProduct, "cl_simple_wat_2");

        assertFloatBandPresent(targetProduct, "cl_all_1_val");
        assertFloatBandPresent(targetProduct, "cl_all_2_val");
        assertFloatBandPresent(targetProduct, "cl_ter_1_val");
        assertFloatBandPresent(targetProduct, "cl_ter_2_val");
        assertFloatBandPresent(targetProduct, "cl_wat_1_val");
        assertFloatBandPresent(targetProduct, "cl_wat_2_val");
        assertFloatBandPresent(targetProduct, "cl_simple_wat_1_val");
        assertFloatBandPresent(targetProduct, "cl_simple_wat_2_val");

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
        final double[] inverseSolarFluxes = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        double[] inputVector = new double[20];
        final WritableSample[] inputSamples = new WritableSample[18];
        for (int i = 0; i < inputSamples.length; i++) {
            inputSamples[i] = new TestSample();
            inputSamples[i].set((double) i + 1);
        }

        final double sinTime = 0.5;
        final double cosTime = 0.6;
        algorithm.injectTimeSines(sinTime, cosTime);
        algorithm.injectInverseSolarFluxes(inverseSolarFluxes);

        inputVector = algorithm.assembleInput(inputSamples, inputVector);

        // check radiances
        for (int i = 0; i < 15; i++) {
            assertEquals(Math.sqrt((i + 1) * Math.PI * inverseSolarFluxes[i] / Math.cos(Math.PI / 180.0 * 18)), inputVector[i], 1e-8);
        }
        assertEquals(sinTime, inputVector[15], 1e-8);
        assertEquals(cosTime, inputVector[16], 1e-8);

        assertEquals(Math.sin(16 * Math.PI / 180.0), inputVector[17], 1e-8);
        assertEquals(Math.sin(17 * Math.PI / 180.0), inputVector[18], 1e-8);
        assertEquals(Math.cos(17 * Math.PI / 180.0), inputVector[19], 1e-8);
    }

    @Test
    public void testSetToUnprocessed() {
        final TestSample testSamples[] = new TestSample[31];
        for (int i = 0; i < testSamples.length; i++) {
            testSamples[i] = new TestSample();
            testSamples[i].set(7878);
        }

        algorithm.setToUnprocessed(testSamples);

        for (int i = 0; i < 8; i++) {
            assertEquals(Constants.UNPROCESSD_MASK, testSamples[i].getInt());
        }
        for (int i = 8; i < 31; i++) {
            assertEquals(Float.NaN, testSamples[i].getFloat());
        }
    }
}
