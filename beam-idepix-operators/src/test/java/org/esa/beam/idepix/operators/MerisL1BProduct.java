package org.esa.beam.idepix.operators;


import org.esa.beam.framework.datamodel.Product;

class MerisL1BProduct {

    static Product create() {
        final Product merisL1BProduct = new Product("Meris L1B", "MER_RR__1P", 2, 1);

        return merisL1BProduct;
    }
}
