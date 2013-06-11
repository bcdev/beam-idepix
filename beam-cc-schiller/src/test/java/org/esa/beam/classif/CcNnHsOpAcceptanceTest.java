package org.esa.beam.classif;


import org.esa.beam.classif.algorithm.AlgorithmFactory;
import org.esa.beam.classif.algorithm.Constants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.util.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import static org.junit.Assert.*;

@RunWith(AcceptanceTestRunner.class)
public class CcNnHsOpAcceptanceTest {

    private static final float[] MERIS_WAVELENGTHS = {412.691f, 442.55902f, 489.88202f, 509.81903f, 559.69403f, 619.601f, 664.57306f, 680.82104f, 708.32904f, 753.37103f, 761.50806f, 778.40906f, 864.87604f, 884.94403f, 900.00006f};
    private static final float[] MERIS_BANDWIDTHS = {9.937f, 9.946f, 9.957001f, 9.961f, 9.97f, 9.979f, 9.985001f, 7.4880004f, 9.992001f, 7.4950004f, 3.7440002f, 15.010001f, 20.047f, 10.018001f, 10.02f};
    private File targetDirectory;
    private OperatorSpi ccNnHsSpi;

    @Before
    public void setUp() {
        ccNnHsSpi = new CcNnHsOp.Spi();

        targetDirectory = new File("test_out");
        if (!targetDirectory.mkdirs()) {
            fail("Unable to create test target directory");
        }
    }

    @After
    public void tearDown() {
        if (targetDirectory.isDirectory()) {
            if (!FileUtils.deleteTree(targetDirectory)) {
                fail("Unable to delete test directory");
            }
        }
    }

    @Test
    public void testProcessTestProduct_CC_2013_03_01() throws IOException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(ccNnHsSpi);
        Product product = null;
        try {
            final String testProductPath = getTestProductPath();
            product = ProductIO.readProduct(testProductPath);
            assertNotNull(product);
            final String targetFilePath = targetDirectory.getPath() + File.separator + "cloud_classified.dim";

            final HashMap<String, Object> parameterMap = createDefaultParameterMap();
            parameterMap.put("algorithmName", AlgorithmFactory.ALGORITHM_2013_03_01);
            final Product ccProduct = GPF.createProduct("Meris.CCNNHS",
                    parameterMap,
                    new Product[]{product});

            ProductIO.writeProduct(ccProduct, targetFilePath, "BEAM-DIMAP");

            assertCorrectProduct_CC_2013_03_01(targetFilePath);
        } finally {
            if (product != null) {
                product.dispose();
            }
            GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(ccNnHsSpi);
        }
    }

    @Test
    public void testProcessTestProduct_CC_2013_05_09() throws IOException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(ccNnHsSpi);
        Product product = null;
        try {
            final String testProductPath = getTestProductPath();
            product = ProductIO.readProduct(testProductPath);
            assertNotNull(product);
            final String targetFilePath = targetDirectory.getPath() + File.separator + "cloud_classified.dim";

            final HashMap<String, Object> parameterMap = createDefaultParameterMap();
            parameterMap.put("algorithmName", AlgorithmFactory.ALGORITHM_2013_05_09);
            final Product ccProduct = GPF.createProduct("Meris.CCNNHS",
                    parameterMap,
                    new Product[]{product});

            ProductIO.writeProduct(ccProduct, targetFilePath, "BEAM-DIMAP");

            assertCorrectProduct_CC_2013_05_09(targetFilePath);
        } finally {
            if (product != null) {
                product.dispose();
            }
            GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(ccNnHsSpi);
        }
    }

    private void assertCorrectProduct_CC_2013_03_01(String targetFilePath) throws IOException {
        final Product product = ProductIO.readProduct(targetFilePath);
        assertNotNull(product);

        try {
            assertBandValue("reflec_1", 445, 471, 0.15434381, product);
            assertBandValue("reflec_2", 252, 453, 0.16002783, product);
            assertBandValue("reflec_3", 105, 462, 0.11247465, product);
            assertBandValue("reflec_4", 58, 340, 0.10575512, product);
            assertBandValue("reflec_5", 293, 268, 0.111449786, product);
            assertBandValue("reflec_6", 469, 245, 0.059470117, product);
            assertBandValue("reflec_7", 413, 190, 0.5032309, product);
            assertBandValue("reflec_8", 180, 197, 0.253931, product);
            assertBandValue("reflec_9", 35, 216, 0.2979992, product);
            assertBandValue("reflec_10", 128, 107, 0.3431538, product);
            assertBandValue("reflec_11", 292, 100, 0.13401371, product);
            assertBandValue("reflec_12", 479, 91, 0.21171556, product);
            assertBandValue("reflec_13", 461, 30, 0.30378696, product);
            assertBandValue("reflec_14", 218, 58, 0.40996724, product);
            assertBandValue("reflec_15", 10, 18, Float.NaN, product);

            assertBandValue("L1_flags", 461, 412, 0, product);
            assertBandValue("detector_index", 114, 342, 866, product);

            assertBandValue("cl_all_1", 0, 1, Constants.UNPROCESSD_MASK, product);
            assertBandValue("cl_all_2", 9, 28, Constants.UNPROCESSD_MASK, product);
            assertBandValue("cl_ter_1", 288, 252, Constants.CLEAR_MASK, product);
            assertBandValue("cl_ter_2", 472, 176, Constants.CLEAR_MASK, product);
            assertBandValue("cl_wat_1", 144, 295, Constants.CLOUD_MASK, product);
            assertBandValue("cl_wat_2", 409, 37, Constants.NONCL_MASK, product);
            assertBandValue("cl_simple_wat_1", 106, 445, Constants.SPAMX_MASK, product);
            assertBandValue("cl_simple_wat_2", 256, 17, Constants.CLEAR_MASK, product);

            assertBandValue("cl_all_1_val", 113, 417, 1.0484172, product);
            assertBandValue("cl_all_2_val", 445, 192, 2.1028845, product);
            assertBandValue("cl_ter_1_val", 55, 24, 1.0523382, product);
            assertBandValue("cl_ter_2_val", 45, 438, 1.0852429, product);
            assertBandValue("cl_wat_1_val", 394, 242, 2.9738734, product);
            assertBandValue("cl_wat_2_val", 352, 446, 2.1792252, product);
            assertBandValue("cl_simple_wat_1_val", 257, 213, 2.9915278, product);
            assertBandValue("cl_simple_wat_2_val", 261, 102, 1.6574280, product);

            assertTiePointValue("latitude", 78, 451, 43.80181121826172, product);
            assertTiePointValue("longitude", 88, 11, 3.566375732421875, product);
            assertTiePointValue("dem_alt", 389, 168, 1634.1015625, product);
            assertTiePointValue("dem_rough", 108, 200, 18.125, product);
            assertTiePointValue("lat_corr", 41, 417, -3.474804398138076E-4, product);
            assertTiePointValue("lon_corr", 356, 39, -0.0018366562435403466, product);
            assertTiePointValue("sun_zenith", 276, 256, 34.10325241088867, product);
            assertTiePointValue("sun_azimuth", 74, 392, 130.77969360351562, product);
            assertTiePointValue("view_zenith", 119, 22, 33.68973922729492, product);
            assertTiePointValue("view_azimuth", 186, 163, 101.44153594970703, product);
            assertTiePointValue("zonal_wind", 358, 72, -2.1015625, product);
            assertTiePointValue("merid_wind", 475, 405, 0.3558593690395355, product);
            assertTiePointValue("atm_press", 88, 346, 1014.9000244140625, product);
            assertTiePointValue("ozone", 66, 378, 354.1706237792969, product);
            assertTiePointValue("rel_hum", 343, 16, 46.464847564697266, product);

            assertReflectanceBandWavelengths(product);
        } finally {
            product.dispose();
        }
    }

    private void assertCorrectProduct_CC_2013_05_09(String targetFilePath) throws IOException {
        final Product product = ProductIO.readProduct(targetFilePath);
        assertNotNull(product);

        try {
            assertBandValue("reflec_1", 42, 472, 0.19949509, product);
            assertBandValue("reflec_2", 277, 336, 0.1427908, product);
            assertBandValue("reflec_3", 333, 428, 0.09807474, product);
            assertBandValue("reflec_4", 450, 381, 0.103234366, product);
            assertBandValue("reflec_5", 448, 288, 0.11879827, product);
            assertBandValue("reflec_6", 383, 249, 1.0290083, product);
            assertBandValue("reflec_7", 288, 213, 0.12584665, product);
            assertBandValue("reflec_8", 205, 228, 0.19809099, product);
            assertBandValue("reflec_9", 139, 217, 0.28512987, product);
            assertBandValue("reflec_10", 66, 205, 0.6176892, product);
            assertBandValue("reflec_11", 53, 111, 0.1076371, product);
            assertBandValue("reflec_12", 187, 115, 0.4913238, product);
            assertBandValue("reflec_13", 312, 83, 0.4109033, product);
            assertBandValue("reflec_14", 398, 120, 0.2914316, product);
            assertBandValue("reflec_15", 11, 33, Float.NaN, product);

            assertBandValue("L1_flags", 317, 135, 80, product);
            assertBandValue("detector_index", 368, 126, 653, product);

            assertBandValue("cl_all_3", 156, 305, Constants.NONCL_MASK, product);
            assertBandValue("cl_ter_3", 11, 431, Constants.UNPROCESSD_MASK, product);
            assertBandValue("cl_wat_3", 441, 169, Constants.SPAMX_MASK, product);
            assertBandValue("cl_simple_wat_3", 263, 25, Constants.SPAMX_MASK, product);

            assertBandValue("cl_all_3_val", 58, 28, 1.9669095, product);
            assertBandValue("cl_ter_3_val", 390, 113, 2.8934863, product);
            assertBandValue("cl_wat_3_val", 128, 302, 2.8276384, product);
            assertBandValue("cl_simple_wat_3_val", 167, 468, 1.6830046, product);

            assertTiePointValue("latitude", 358, 343, 44.39118576049805, product);
            assertTiePointValue("longitude", 86, 19, 3.515789747238159, product);
            assertTiePointValue("dem_alt", 157, 295, 938.74609375, product);
            assertTiePointValue("dem_rough", 362, 394, 63.625, product);
            assertTiePointValue("lat_corr", 336, 19, -4.3556251330301166E-4, product);
            assertTiePointValue("lon_corr", 346, 29, -0.0016613437328487635, product);
            assertTiePointValue("sun_zenith", 286, 266, 33.96331787109375, product);
            assertTiePointValue("sun_azimuth", 84, 382, 131.07623291015625, product);
            assertTiePointValue("view_zenith", 109, 52, 34.316463470458984, product);
            assertTiePointValue("view_azimuth", 176, 173, 101.34014129638672, product);
            assertTiePointValue("zonal_wind", 103, 188, -2.891406297683716, product);
            assertTiePointValue("merid_wind", 284, 335, 0.10781249403953552, product);
            assertTiePointValue("atm_press", 371, 169, 1016.34375, product);
            assertTiePointValue("ozone", 76, 478, 346.4862365722656, product);
            assertTiePointValue("rel_hum", 313, 102, 47.131248474121094, product);

            assertReflectanceBandWavelengths(product);
        } finally {
            product.dispose();
        }
    }

    private void assertBandValue(String bandname, int x, int y, int expected, Product product) throws IOException {
        final Band band = getBand(bandname, product);
        final int pixelInt = band.getPixelInt(x, y);
        assertEquals(expected, pixelInt);
    }

    private void assertBandValue(String bandname, int x, int y, double expected, Product product) throws IOException {
        final Band band = getBand(bandname, product);
        final double pixelDouble = band.getPixelDouble(x, y);
        assertEquals(expected, pixelDouble, 1e-7);
    }

    private void assertTiePointValue(String tpGridname, int x, int y, double expected, Product product) throws IOException {
        final TiePointGrid tiePointGrid = getTiePointGrid(tpGridname, product);
        final double pixelDouble = tiePointGrid.getPixelDouble(x, y);
        assertEquals(expected, pixelDouble, 1e-7);
    }

    private void assertReflectanceBandWavelengths(Product product) {
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final Band band = product.getBand("reflec_" + (i + 1));
            assertNotNull(band);
            assertEquals(MERIS_WAVELENGTHS[i], band.getSpectralWavelength(), 1e-8);
            assertEquals(MERIS_BANDWIDTHS[i], band.getSpectralBandwidth(), 1e-8);
        }
    }

    private Band getBand(String bandName, Product product) throws IOException {
        final Band band = product.getBand(bandName);
        assertNotNull(band);

        band.loadRasterData();
        return band;
    }

    private TiePointGrid getTiePointGrid(String tpGridName, Product product) throws IOException {
        final TiePointGrid tiePointGrid = product.getTiePointGrid(tpGridName);
        assertNotNull(tiePointGrid);

        tiePointGrid.loadRasterData();
        return tiePointGrid;
    }

    private String getTestProductPath() {
        final URL resource = CcNnHsOpAcceptanceTest.class.getResource("../../../../subset_0_of_MER_RR__1PRACR20060511_094214_000026402047_00337_21934_0000.dim");
        final String resourcePath = resource.getPath();
        assertTrue(new File(resourcePath).isFile());
        return resourcePath;
    }

    private HashMap<String, Object> createDefaultParameterMap() {
        return new HashMap<String, Object>();
    }
}
