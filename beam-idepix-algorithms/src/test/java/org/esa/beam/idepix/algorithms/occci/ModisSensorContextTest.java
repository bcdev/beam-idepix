package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author olafd
 */
public class ModisSensorContextTest {
    private ModisSensorContext modisSensorContext;

    @Before
    public void setUp() throws Exception {
        modisSensorContext = new ModisSensorContext();
        modisSensorContext.init(new Product("dont", "care", 2, 2));
    }

    @Test
    public void testGetNumSpectralInputBands() {
        assertEquals(22, modisSensorContext.getNumSpectralInputBands());
    }

    @Test
    public void testGetNumSpectralOutputBands() {
        assertEquals(22, modisSensorContext.getNumSpectralOutputBands());
    }

    @Test
    public void testGetSpectralOutputBandIndices() {
        final int[] expectedIndices = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        final int[] indices = modisSensorContext.getSpectralOutputBandIndices();

        assertArrayEquals(expectedIndices, indices);
    }

    @Test
    public void testGetSpectralOutputWavelengths() {
        final float[] expectedWavelengths = new float[]{413.f, 443.f, 488.f, 531.f, 551.f, 667.f, 678.f, 748.f, 870.f,
        645.0f, 859.0f, 469.0f, 555.0f, 1240.0f, 1640.0f, 2130.0f};

        final float[] wavelengths = modisSensorContext.getSpectralOutputWavelengths();
        assertArrayEquals(expectedWavelengths, wavelengths, 1e-8f);
    }

    @Test
    public void testGetNnOutputIndices() {
        final int[] expectedIndices = new int[]{1, 2, 4, 8, 9, 15, 18, 21, 26};

        assertArrayEquals(expectedIndices, modisSensorContext.getNnOutputIndices());
    }

    @Test
    public void testGetSpectralInputBandNames() {
        final String[] spectralBandNames = modisSensorContext.getSpectralInputBandNames();
        assertNotNull(spectralBandNames);
        assertEquals(22, spectralBandNames.length);

        assertEquals("EV_250_Aggr1km_RefSB.1", spectralBandNames[0]);
        assertEquals("EV_250_Aggr1km_RefSB.2", spectralBandNames[1]);
        assertEquals("EV_500_Aggr1km_RefSB.3", spectralBandNames[2]);
        assertEquals("EV_500_Aggr1km_RefSB.4", spectralBandNames[3]);
        assertEquals("EV_500_Aggr1km_RefSB.5", spectralBandNames[4]);
        assertEquals("EV_500_Aggr1km_RefSB.6", spectralBandNames[5]);
        assertEquals("EV_500_Aggr1km_RefSB.7", spectralBandNames[6]);
        assertEquals("EV_1KM_RefSB.8", spectralBandNames[7]);
        assertEquals("EV_1KM_RefSB.9", spectralBandNames[8]);
        assertEquals("EV_1KM_RefSB.10", spectralBandNames[9]);
        assertEquals("EV_1KM_RefSB.11", spectralBandNames[10]);
        assertEquals("EV_1KM_RefSB.12", spectralBandNames[11]);
        assertEquals("EV_1KM_RefSB.13lo", spectralBandNames[12]);
        assertEquals("EV_1KM_RefSB.13hi", spectralBandNames[13]);
        assertEquals("EV_1KM_RefSB.14lo", spectralBandNames[14]);
        assertEquals("EV_1KM_RefSB.14hi", spectralBandNames[15]);
        assertEquals("EV_1KM_RefSB.15", spectralBandNames[16]);
        assertEquals("EV_1KM_RefSB.16", spectralBandNames[17]);
        assertEquals("EV_1KM_RefSB.17", spectralBandNames[18]);
        assertEquals("EV_1KM_RefSB.18", spectralBandNames[19]);
        assertEquals("EV_1KM_RefSB.19", spectralBandNames[20]);
        assertEquals("EV_1KM_RefSB.26", spectralBandNames[21]);
    }

    @Test
    public void testGetEmissiveInputBandNames() {
        final String[] emissiveBandNames = modisSensorContext.getEmissiveInputBandNames();
        assertNotNull(emissiveBandNames);
        assertEquals(16, emissiveBandNames.length);

        for (int i=0; i<6; i++) {
            assertEquals("EV_1KM_Emissive." + Integer.toString(20+i), emissiveBandNames[i]);
        }
        for (int i=6; i<emissiveBandNames.length; i++) {
            assertEquals("EV_1KM_Emissive." + Integer.toString(20+i+1), emissiveBandNames[i]);
        }
    }


    @Test
    public void testGetSensorType() {
        assertEquals(Sensor.MODIS, modisSensorContext.getSensor());
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer testSampleConfigurer = new TestSampleConfigurer();

        modisSensorContext.configureSourceSamples(testSampleConfigurer);

        assertEquals("EV_250_Aggr1km_RefSB.1", testSampleConfigurer.get(0));
        assertEquals("EV_250_Aggr1km_RefSB.2", testSampleConfigurer.get(1));
        assertEquals("EV_500_Aggr1km_RefSB.3", testSampleConfigurer.get(2));
        assertEquals("EV_500_Aggr1km_RefSB.4", testSampleConfigurer.get(3));
        assertEquals("EV_500_Aggr1km_RefSB.5", testSampleConfigurer.get(4));
        assertEquals("EV_500_Aggr1km_RefSB.6", testSampleConfigurer.get(5));
        assertEquals("EV_500_Aggr1km_RefSB.7", testSampleConfigurer.get(6));
        assertEquals("EV_1KM_RefSB.8", testSampleConfigurer.get(7));
        assertEquals("EV_1KM_RefSB.9", testSampleConfigurer.get(8));
        assertEquals("EV_1KM_RefSB.10", testSampleConfigurer.get(9));
        assertEquals("EV_1KM_RefSB.11", testSampleConfigurer.get(10));
        assertEquals("EV_1KM_RefSB.12", testSampleConfigurer.get(11));
        assertEquals("EV_1KM_RefSB.13lo", testSampleConfigurer.get(12));
        assertEquals("EV_1KM_RefSB.13hi", testSampleConfigurer.get(13));
        assertEquals("EV_1KM_RefSB.14lo", testSampleConfigurer.get(14));
        assertEquals("EV_1KM_RefSB.14hi", testSampleConfigurer.get(15));
        assertEquals("EV_1KM_RefSB.15", testSampleConfigurer.get(16));
        assertEquals("EV_1KM_RefSB.16", testSampleConfigurer.get(17));
        assertEquals("EV_1KM_RefSB.17", testSampleConfigurer.get(18));
        assertEquals("EV_1KM_RefSB.18", testSampleConfigurer.get(19));
        assertEquals("EV_1KM_RefSB.19", testSampleConfigurer.get(20));
        assertEquals("EV_1KM_RefSB.26", testSampleConfigurer.get(21));

        for (int i=0; i<6; i++) {
            assertEquals("EV_1KM_Emissive." + Integer.toString(20+i), testSampleConfigurer.get(22+i));
        }
        for (int i=6; i<modisSensorContext.getNumEmissiveInputBands(); i++) {
            assertEquals("EV_1KM_Emissive." + Integer.toString(20+i+1), testSampleConfigurer.get(22+i));
        }
    }

    @Test
    public void testGetSolarFluxes() {
        final double[] expectedResults = new double[]{1740.458085, 1844.698571, 1949.723913, 1875.394737,
                1882.428333, 1545.183846, 1507.529167, 1277.037,
                945.3382727, 1601.482295, 967.137667, 2072.03625,
                1874.005, 456.1987143, 229.882, 92.5171833
        };
        final Product product = new Product("No", "flux", 2, 2);

        modisSensorContext.init(product);

        final double[] solarFluxes = modisSensorContext.getSolarFluxes(product);
        assertArrayEquals(expectedResults, solarFluxes, 1e-8);
    }

    @Test
    public void testGetSurfacePressure() {
        assertEquals(1019.0, modisSensorContext.getSurfacePressure(), 1e-8);
    }

    @Test
    public void testGetOzone() {
        assertEquals(330.0, modisSensorContext.getOzone(), 1e-8);
    }

    @Test
    public void testGetDetectorIndex() {
        assertEquals(-1, modisSensorContext.getDetectorIndex(new Sample[0]));
    }

    @Test
    public void testGetTargetSamplesOffset() {
        assertEquals(0, modisSensorContext.getTargetSampleOffset());
    }

    @Test
    public void testCorrectSunAzimuth() {
        assertEquals(33.8, modisSensorContext.correctSunAzimuth(33.8), 1e-8);
        assertEquals(-25.88 + 360.0, modisSensorContext.correctSunAzimuth(-25.88), 1e-8);
    }

    @Test
    public void testCorrectViewAzimuth() {
        assertEquals(43.9, modisSensorContext.correctViewAzimuth(43.9), 1e-8);
        assertEquals(-55.13 + 360.0, modisSensorContext.correctViewAzimuth(-55.13), 1e-8);
    }

}
