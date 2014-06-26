package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

/**
 * Basic operator for OC-CCI pixel classification
 * todo: implement (see GlobAlbedoClassificationOp)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "Basic operator for pixel classification from MODIS/SeaWiFS data.")
public class OccciClassificationOp extends Operator {

    @SourceProduct(alias = "ocL1b", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Override
    public void initialize() throws OperatorException {

    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OccciClassificationOp.class);
        }
    }
}
