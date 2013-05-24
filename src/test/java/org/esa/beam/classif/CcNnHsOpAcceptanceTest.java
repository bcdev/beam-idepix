package org.esa.beam.classif;


import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.util.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import static org.junit.Assert.*;

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
    public void testProcessTestProduct() throws IOException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(ccNnHsSpi);
        Product product = null;
        try {
            final String testProductPath = getTestProductPath();
            product = ProductIO.readProduct(testProductPath);
            assertNotNull(product);
            final String targetFilePath = targetDirectory.getPath() + File.separator + "cloud_classified.dim";

            final Product ccProduct = GPF.createProduct("Meris.CCNNHS",
                    createDefaultParameterMap(),
                    new Product[]{product});

            ProductIO.writeProduct(ccProduct, targetFilePath, "BEAM-DIMAP");

            assertCorrectProduct(targetFilePath);
        } finally {
            if (product != null) {
                product.dispose();
            }
            GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(ccNnHsSpi);
        }
    }

    private void assertCorrectProduct(String targetFilePath) throws IOException {
        final Product product = ProductIO.readProduct(targetFilePath);
        assertNotNull(product);

        try {
            assertBandValue("cl_all_1", 0, 1, CloudClassifier.UNPROCESSD_MASK, product);
            assertBandValue("cl_all_2", 9, 28, CloudClassifier.UNPROCESSD_MASK, product);
            assertBandValue("cl_ter_1", 288, 252, CloudClassifier.CLEAR_MASK, product);
            assertBandValue("cl_ter_2", 472, 176, CloudClassifier.CLEAR_MASK, product);
            assertBandValue("cl_wat_1", 144, 295, CloudClassifier.NONCL_MASK, product);
        } finally {
            product.dispose();
        }
    }

    private void assertBandValue(String bandname, int x, int y, int expected, Product product) throws IOException {
        final Band band = product.getBand(bandname);
        assertNotNull(band);

        band.loadRasterData();
        final int pixelInt = band.getPixelInt(x, y);
        assertEquals(expected, pixelInt);
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
