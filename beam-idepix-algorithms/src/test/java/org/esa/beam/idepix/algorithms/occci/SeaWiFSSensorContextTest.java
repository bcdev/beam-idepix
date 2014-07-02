package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class SeaWiFSSensorContextTest {
    private SeaWiFSSensorContext seaWiFSSensorContext;

    @Before
    public void setUp() throws Exception {
        seaWiFSSensorContext = new SeaWiFSSensorContext();
    }

    @Test
    public void testGetNumSpectralInputBands() {
        assertEquals(8, seaWiFSSensorContext.getNumSpectralInputBands());
    }

    @Test
    public void testGetNumSpectralOutputBands() {
        assertEquals(8, seaWiFSSensorContext.getNumSpectralOutputBands());
    }

    @Test
    public void testGetSpectralOutputBandIndices() {
        final int[] expectedIndices = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        final int[] indices = seaWiFSSensorContext.getSpectralOutputBandIndices();

        assertArrayEquals(expectedIndices, indices);
    }

    @Test
    public void testGetSpectralOutputWavelengths() {
        final float[] expectedWavelengths = new float[]{412.f, 443.f, 490.f, 510.f, 555.f, 670.f, 765.f, 865.f};

        final float[] wavelengths = seaWiFSSensorContext.getSpectralOutputWavelengths();
        assertArrayEquals(expectedWavelengths, wavelengths, 1e-8f);
    }

    @Test
    public void testGetSpectralInputBandNames() {
        final String[] spectralBandNames = seaWiFSSensorContext.getSpectralInputBandNames();
        assertNotNull(spectralBandNames);
        assertEquals(8, spectralBandNames.length);

        assertEquals("L_412", spectralBandNames[0]);
        assertEquals("L_443", spectralBandNames[1]);
        assertEquals("L_490", spectralBandNames[2]);
        assertEquals("L_510", spectralBandNames[3]);
        assertEquals("L_555", spectralBandNames[4]);
        assertEquals("L_670", spectralBandNames[5]);
        assertEquals("L_765", spectralBandNames[6]);
        assertEquals("L_865", spectralBandNames[7]);
    }

    @Test
    public void testGetNnOutputIndices() {
        final int[] expectedIndices = new int[]{1, 2, 4, 6, 10, 16, 23, 25};

        assertArrayEquals(expectedIndices, seaWiFSSensorContext.getNnOutputIndices());
    }

    @Test
    public void testGetSensorType() {
        assertEquals(Sensor.SEAWIFS, seaWiFSSensorContext.getSensor());
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer testSampleConfigurer = new TestSampleConfigurer();

        seaWiFSSensorContext.configureSourceSamples(testSampleConfigurer);

        assertEquals("solz", testSampleConfigurer.get(0));
        assertEquals("sola", testSampleConfigurer.get(1));
        assertEquals("senz", testSampleConfigurer.get(2));
        assertEquals("sena", testSampleConfigurer.get(3));

        for (int i = 4; i < 8; i++) {
            assertNull(testSampleConfigurer.get(i));
        }

        assertEquals("L_412", testSampleConfigurer.get(8));
        assertEquals("L_443", testSampleConfigurer.get(9));
        assertEquals("L_490", testSampleConfigurer.get(10));
        assertEquals("L_510", testSampleConfigurer.get(11));
        assertEquals("L_555", testSampleConfigurer.get(12));
        assertEquals("L_670", testSampleConfigurer.get(13));
        assertEquals("L_765", testSampleConfigurer.get(14));
        assertEquals("L_865", testSampleConfigurer.get(15));
    }

    @Test
    public void testCopyTiePointData() {
        final double[] inputs = new double[6];
        final TestSample[] sourceSamples = new TestSample[4];
        for (int i = 0; i < sourceSamples.length; i++) {
            sourceSamples[i] = new TestSample();
            sourceSamples[i].set((double) i);
        }

        seaWiFSSensorContext.copyTiePointData(inputs, sourceSamples);

        for (int i = 0; i < sourceSamples.length; i++) {
            assertEquals(i, inputs[i], 1e-8);
        }
        assertEquals(1019.0, inputs[4], 1e-8);
        assertEquals(330.0, inputs[5], 1e-8);
    }

    @Test
    public void testGetSolarFluxes() {
        final Product product = new Product("test", "type", 5, 5);
        final double[] solarFluxes = seaWiFSSensorContext.getSolarFluxes(product);
        assertEquals(8, solarFluxes.length);
        final double[] expectedSolarFluxes = {1735.518167, 1858.404314, 1981.076667, 1881.566829, 1874.005, 1537.254783, 1230.04, 957.6122143};
        for (int i = 0; i < expectedSolarFluxes.length; i++) {
            assertEquals(expectedSolarFluxes[i], solarFluxes[i], 1e-8f);
        }
    }

    @Test
    public void testGetSurfacePressure() {
        Assert.assertEquals(1019.0, seaWiFSSensorContext.getSurfacePressure(), 1e-8);
    }

    @Test
    public void testGetOzone() {
        Assert.assertEquals(330.0, seaWiFSSensorContext.getOzone(), 1e-8);
    }

    @Test
    public void testGetDetectorIndex() {
        assertEquals(-1, seaWiFSSensorContext.getDetectorIndex(new Sample[0]));
    }

    @Test
    public void testGetTargetSampleOffset() {
        assertEquals(2, seaWiFSSensorContext.getTargetSampleOffset());
    }

    @Test
    public void testCorrectSunAzimuth() {
        assertEquals(13.8, seaWiFSSensorContext.correctSunAzimuth(13.8), 1e-8);
        assertEquals(-35.88, seaWiFSSensorContext.correctSunAzimuth(-35.88), 1e-8);
    }

    @Test
    public void testCorrectViewAzimuth() {
        assertEquals(3.7, seaWiFSSensorContext.correctViewAzimuth(3.7), 1e-8);
        assertEquals(-45.43, seaWiFSSensorContext.correctViewAzimuth(-45.43), 1e-8);
    }

    @Test
    @Ignore
    public void testScaleInputSpectralData() {
        final double[] input = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};

        seaWiFSSensorContext.scaleInputSpectralDataToRadiance(input, Constants.SEAWIFS_SRC_RAD_OFFSET);

        final double[] expected = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0};
        assertArrayEquals(expected, input, 1e-8);
    }
}
