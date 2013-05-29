package org.esa.beam.classif.algorithm;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class AlgorithmTest {

    protected void assertIntBandPresent(Product targetProduct, String bandName) {
        final Band band = targetProduct.getBand(bandName);
        assertNotNull(band);
        assertEquals(ProductData.TYPE_INT8, band.getDataType());
        assertNotNull(band.getFlagCoding());
    }

    protected void assertFloatBandPresent(Product targetProduct, String bandName) {
        final Band band = targetProduct.getBand(bandName);
        assertNotNull(band);
        assertEquals(ProductData.TYPE_FLOAT32, band.getDataType());
    }
}
