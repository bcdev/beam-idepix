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

public class CC_2013_05_09Test extends AlgorithmTest {

    private CC_2013_05_09 algorithm;

    @Before
    public void setUp() {
        algorithm = new CC_2013_05_09();
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        algorithm.configureSourceSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(16, sampleMap.size());
        for (int i = 0; i < 15; i++) {
            assertEquals("radiance_" + (i + 1), sampleMap.get(i));
        }

        assertEquals("sun_zenith", sampleMap.get(15));
    }

    @Test
    public void testConfigureTargetSamples() {
        final TestSampleConfigurer sampleConfigurer = new TestSampleConfigurer();

        algorithm.configureTargetSamples(sampleConfigurer);

        final HashMap<Integer, String> sampleMap = sampleConfigurer.getSampleMap();
        assertEquals(23, sampleMap.size());
        assertEquals("cl_all_3", sampleMap.get(0));
        assertEquals("cl_ter_3", sampleMap.get(1));
        assertEquals("cl_wat_3", sampleMap.get(2));
        assertEquals("cl_simple_wat_3", sampleMap.get(3));

        assertEquals("cl_all_3_val", sampleMap.get(4));
        assertEquals("cl_ter_3_val", sampleMap.get(5));
        assertEquals("cl_wat_3_val", sampleMap.get(6));
        assertEquals("cl_simple_wat_3_val", sampleMap.get(7));

        for (int i = 0; i < 15; i++) {
            assertEquals("reflec_" + (i + 1), sampleMap.get(8 + i));
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

        assertIntBandPresent(targetProduct, "cl_all_3");
        assertIntBandPresent(targetProduct, "cl_ter_3");
        assertIntBandPresent(targetProduct, "cl_wat_3");
        assertIntBandPresent(targetProduct, "cl_simple_wat_3");

        assertFloatBandPresent(targetProduct, "cl_all_3_val");
        assertFloatBandPresent(targetProduct, "cl_ter_3_val");
        assertFloatBandPresent(targetProduct, "cl_wat_3_val");
        assertFloatBandPresent(targetProduct, "cl_simple_wat_3_val");

        final ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        assertNotNull(flagCodingGroup.get("cl_all_3"));
        assertNotNull(flagCodingGroup.get("cl_ter_3"));
        assertNotNull(flagCodingGroup.get("cl_wat_3"));
        assertNotNull(flagCodingGroup.get("cl_simple_wat_3"));
    }

    @Test
    public void testAssembleInput() {
        final double[] inverseSolarFluxes = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

        double[] inputVector = new double[16];
        final WritableSample[] inputSamples = new WritableSample[16];
        for (int i = 0; i < inputSamples.length; i++) {
            inputSamples[i] = new TestSample();
            inputSamples[i].set((double) i + 1);
        }

        algorithm.injectInverseSolarFluxes(inverseSolarFluxes);

        inputVector = algorithm.assembleInput(inputSamples, inputVector);

        // check radiances
        for (int i = 0; i < 10; i++) {
            assertEquals("" + i, Math.sqrt((i + 1) * Math.PI * inverseSolarFluxes[i] / Math.cos(Math.PI / 180.0 * 16)), inputVector[i], 1e-8);
        }
        for (int i = 11; i < 15; i++) {
            assertEquals("" + i, Math.sqrt((i + 1) * Math.PI * inverseSolarFluxes[i] / Math.cos(Math.PI / 180.0 * 16)), inputVector[i - 1], 1e-8);
        }
    }

    @Test
    public void testSetToUnprocessed() {
        final TestSample testSamples[] = new TestSample[23];
        for (int i = 0; i < testSamples.length; i++) {
            testSamples[i] = new TestSample();
            testSamples[i].set(7878);
        }

        algorithm.setToUnprocessed(testSamples);

        for (int i = 0; i < 4; i++) {
            assertEquals(Constants.UNPROCESSD_MASK, testSamples[i].getInt());
        }
        for (int i = 4; i < 23; i++) {
            assertEquals(Float.NaN, testSamples[i].getFloat());
        }
    }
}
