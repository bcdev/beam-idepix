package org.esa.beam.classif;


import org.esa.beam.classif.algorithm.AlgorithmFactory;
import org.esa.beam.classif.algorithm.Constants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
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

    private Band getBand(String bandname, Product product) throws IOException {
        final Band band = product.getBand(bandname);
        assertNotNull(band);

        band.loadRasterData();
        return band;
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