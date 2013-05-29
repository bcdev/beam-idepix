package org.esa.beam.classif;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeFilter;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;

public class TestProductConfigurer implements ProductConfigurer {

    private Product targetProduct;
    private ProductNodeFilter<Band> copyBandsFilter;

    public TestProductConfigurer() {
        targetProduct = new Product("ZAPP", "schnuffi", 2, 2);
    }

    @Override
    public Product getSourceProduct() {
        return null;
    }

    @Override
    public void setSourceProduct(Product sourceProduct) {
    }

    @Override
    public Product getTargetProduct() {
        return targetProduct;
    }

    @Override
    public void copyMetadata() {
    }

    @Override
    public void copyTimeCoding() {
    }

    @Override
    public void copyGeoCoding() {
    }

    @Override
    public void copyMasks() {
    }

    @Override
    public void copyTiePointGrids(String... gridName) {
    }

    @Override
    public void copyBands(String... bandName) {
    }

    @Override
    public void copyBands(ProductNodeFilter<Band> filter) {
        copyBandsFilter = filter;
    }

    public ProductNodeFilter<Band> getCopyBandsFilter() {
        return copyBandsFilter;
    }

    @Override
    public void copyVectorData() {
    }

    @Override
    public Band addBand(String name, int dataType) {
        return targetProduct.addBand(name, dataType);
    }

    @Override
    public Band addBand(String name, int dataType, double noDataValue) {
        return null;
    }

    @Override
    public Band addBand(String name, String expression) {
        return null;
    }

    @Override
    public Band addBand(String name, String expression, double noDataValue) {
        return null;
    }
}