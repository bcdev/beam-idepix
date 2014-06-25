package org.esa.beam.idepix.operators;


import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.util.io.FileUtils;
import org.junit.*;

import java.io.File;
import java.text.ParseException;
import java.util.HashMap;

import static org.junit.Assert.fail;

public class MerisBrrCorrectionOpIntegrationTest {

    private File testOutDirectory;

    @BeforeClass
    public static void beforeClass() throws ParseException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new MerisBrrCorrectionOp.Spi());
    }

    @AfterClass
    public static void afterClass() throws ParseException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(new MerisBrrCorrectionOp.Spi());
    }

    @Before
    public void setUp() {
        testOutDirectory = new File("output");
        if (!testOutDirectory.mkdirs()) {
            fail("unable to create test directory: " + testOutDirectory);
        }
    }

    @After
    public void tearDown() {
        if (testOutDirectory != null) {
            if (!FileUtils.deleteTree(testOutDirectory)) {
                fail("Unable to delete test directory: " + testOutDirectory);
            }
        }
    }

    @Test
    public void testProcessMerisL1B(){
        final Product merisL1BProduct = MerisL1BProduct.create();

        final HashMap<String, Object> parameterMap = createParameterMap(merisL1BProduct);
//        Product savedProduct = null;
//          final Product target = GPF.createProduct("idepix.operators.MerisBrrCorrection", parameterMap, merisL1BProduct);
    }

    private HashMap<String, Object> createParameterMap(Product product) {
        final HashMap<String, Object> parameterMap = new HashMap<String, Object>();

        parameterMap.put("l1b", product);
        return parameterMap;
    }
}
