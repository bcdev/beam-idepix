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

    protected void assertFloatBandPresent(Product targetProduct, String bandName, float wavelength, float bandwidth) {
        final Band band = targetProduct.getBand(bandName);
        assertNotNull(band);
        assertEquals(ProductData.TYPE_FLOAT32, band.getDataType());
        assertEquals(wavelength, band.getSpectralWavelength(), 1e-8);
        assertEquals(bandwidth, band.getSpectralBandwidth(), 1e-8);
    }

    protected Product createProduct() {
        final Product product = new Product("overwrites the old", "don'tcare", 2, 2);
        for (int i = 0; i < Constants.NUM_RADIANCE_BANDS; i++) {
            final Band band = new Band("radiance_" + (i + 1), ProductData.TYPE_FLOAT32, 2, 2);
            band.setSpectralWavelength(2 * i);
            band.setSpectralBandwidth(3 * i);
            product.addBand(band);
        }
        return product;
    }
}
