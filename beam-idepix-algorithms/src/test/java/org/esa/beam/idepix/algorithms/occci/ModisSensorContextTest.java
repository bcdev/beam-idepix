package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 27.06.2014
 * Time: 14:35
 *
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
        assertEquals(16, modisSensorContext.getNumSpectralInputBands());
    }

    @Test
    public void testGetNumSpectralOutputBands() {
        assertEquals(16, modisSensorContext.getNumSpectralOutputBands());
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
        assertEquals(16, spectralBandNames.length);

        assertEquals("EV_1KM_RefSB_8", spectralBandNames[0]);
        assertEquals("EV_1KM_RefSB_9", spectralBandNames[1]);
        assertEquals("EV_1KM_RefSB_10", spectralBandNames[2]);
        assertEquals("EV_1KM_RefSB_11", spectralBandNames[3]);
        assertEquals("EV_1KM_RefSB_12", spectralBandNames[4]);
        assertEquals("EV_1KM_RefSB_13lo", spectralBandNames[5]);
        assertEquals("EV_1KM_RefSB_14lo", spectralBandNames[6]);
        assertEquals("EV_1KM_RefSB_15", spectralBandNames[7]);
        assertEquals("EV_1KM_RefSB_16", spectralBandNames[8]);
        assertEquals("EV_250_Aggr1km_RefSB_1", spectralBandNames[9]);
        assertEquals("EV_250_Aggr1km_RefSB_2", spectralBandNames[10]);
        assertEquals("EV_500_Aggr1km_RefSB_3", spectralBandNames[11]);
        assertEquals("EV_500_Aggr1km_RefSB_4", spectralBandNames[12]);
        assertEquals("EV_500_Aggr1km_RefSB_5", spectralBandNames[13]);
        assertEquals("EV_500_Aggr1km_RefSB_6", spectralBandNames[14]);
        assertEquals("EV_500_Aggr1km_RefSB_7", spectralBandNames[15]);
    }

    @Test
    public void testGetSensorType() {
        assertEquals(Sensor.MODIS, modisSensorContext.getSensor());
    }

    @Test
    public void testConfigureSourceSamples() {
        final TestSampleConfigurer testSampleConfigurer = new TestSampleConfigurer();

        modisSensorContext.configureSourceSamples(testSampleConfigurer);

        assertEquals("SolarZenith", testSampleConfigurer.get(0));
        assertEquals("SolarAzimuth", testSampleConfigurer.get(1));
        assertEquals("SensorZenith", testSampleConfigurer.get(2));
        assertEquals("SensorAzimuth", testSampleConfigurer.get(3));

        for (int i = 4; i < 8; i++) {
            assertNull(testSampleConfigurer.get(i));
        }

        assertEquals("EV_1KM_RefSB_8", testSampleConfigurer.get(8));
        assertEquals("EV_1KM_RefSB_9", testSampleConfigurer.get(9));
        assertEquals("EV_1KM_RefSB_10", testSampleConfigurer.get(10));
        assertEquals("EV_1KM_RefSB_11", testSampleConfigurer.get(11));
        assertEquals("EV_1KM_RefSB_12", testSampleConfigurer.get(12));
        assertEquals("EV_1KM_RefSB_13lo", testSampleConfigurer.get(13));
        assertEquals("EV_1KM_RefSB_14lo", testSampleConfigurer.get(14));
        assertEquals("EV_1KM_RefSB_15", testSampleConfigurer.get(15));
        assertEquals("EV_1KM_RefSB_16", testSampleConfigurer.get(16));
    }

    @Test
    public void testCopyTiePointData() {
        final double[] inputs = new double[6];
        final TestSample[] sourceSamples = new TestSample[4];
        for (int i = 0; i < sourceSamples.length; i++) {
            sourceSamples[i] = new TestSample();
            sourceSamples[i].set((double) i);
        }

        modisSensorContext.copyTiePointData(inputs, sourceSamples);

        for (int i = 0; i < sourceSamples.length; i++) {
            assertEquals(i, inputs[i], 1e-8);
        }
        assertEquals(1019.0, inputs[4], 1e-8);
        assertEquals(330.0, inputs[5], 1e-8);
    }

    @Test
    public void testGetSolarFluxes_noFluxesInMetadata() {
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

    private Product createProductWithSolarFluxMetadata() {
        final Product product = new Product("test", "type", 5, 5);
        double[] solarFluxes = new double[330];
        int[] startPositionInProductData = new int[]{180, 190, 200, 210, 220, 230, 250, 270, 280};
        for (int aStartPositionInProductData : startPositionInProductData) {
            for (int j = 0; j < 10; j++) {
                solarFluxes[aStartPositionInProductData + j] = aStartPositionInProductData + j;
            }
        }
        final ProductData productData = ProductData.createInstance(solarFluxes);
        final MetadataAttribute attribute = new MetadataAttribute("Solar_Irradiance_on_RSB_Detectors_over_pi", productData, true);
        final MetadataElement globalMetadataElement = new MetadataElement("GLOBAL_METADATA");
        globalMetadataElement.addAttribute(attribute);

        product.getMetadataRoot().addElement(globalMetadataElement);
        return product;
    }
}
