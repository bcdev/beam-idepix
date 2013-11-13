package org.esa.beam.idepix.operators;

import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.util.ProductUtils;

@OperatorMetadata(alias = "idepix.operators.Basis",
                  version = "2.0.1",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Idepix operator for basic product generation.")
public abstract class BasisOp extends Operator {

    /**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     *
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

    public void renameL1bMaskNames(Product product) {
        prefixMask("coastline", product);
        prefixMask("land", product);
        prefixMask("water", product);
        prefixMask("cosmetic", product);
        prefixMask("duplicated", product);
        prefixMask("glint_risk", product);
        prefixMask("suspect", product);
        prefixMask("bright", product);
        prefixMask("invalid", product);
    }

    public void prefixMask(String maskName, Product product) {
        Mask mask = product.getMaskGroup().get(maskName);
        if (mask != null) {
            mask.setName("l1b_" + mask.getName());
        }
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

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(BasisOp.class, "idepix.operators.Basis");
        }
    }
}
