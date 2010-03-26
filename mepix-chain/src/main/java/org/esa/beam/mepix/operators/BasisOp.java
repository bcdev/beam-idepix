package org.esa.beam.mepix.operators;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.util.ProductUtils;

public abstract class BasisOp extends Operator {
	/**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     * @return targetProduct
     */
    public Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductTrunk(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies basic information for a MERIS product to the target product
     *
     * @param sourceProduct
     * @param targetProduct
     */
    public void copyProductTrunk(Product sourceProduct,
                                 Product targetProduct) {
        copyTiePoints(sourceProduct, targetProduct);
        copyBaseGeoInfo(sourceProduct, targetProduct);
    }

    /**
     * Copies the tie point data.
     *
     * @param sourceProduct
     * @param targetProduct
     */
    private void copyTiePoints(Product sourceProduct,
                               Product targetProduct) {
        // copy all tie point grids to output product
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
    }

    /**
     * Copies geocoding and the start and stop time.
     *
     * @param sourceProduct
     * @param targetProduct
     */
    private void copyBaseGeoInfo(Product sourceProduct,
                                 Product targetProduct) {
        // copy geo-coding to the output product
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

}
