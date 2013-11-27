package org.esa.beam.idepix.algorithms.scapem;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * Idepix operator for pixel identification and classification with Scape-M cloud mask from L. Guanter, FUB.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.scapem",
                  version = "2.0.2-SNAPSHOT",
                  authors = "Olaf Danne, Tonio Fincke",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Pixel identification and classification with Scape-M cloud mask from L. Guanter, FUB.")
public class FubScapeMOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(description = "Reflectance Threshold for reflectance 12")
    private float reflectance_water_threshold;

    @Parameter(description = "The thickness of the coastline in kilometers.")
    private float thicknessOfCoast;

    @Parameter(description = "The minimal size for a water region to be acknowledged as an ocean in km².")
    private float minimumOceanSize;

    @Parameter(description = "Whether or not to calculate a lake mask", notEmpty = false)
    private boolean calculateLakes;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.FubScapeM);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processFubScapeM();
        renameL1bMaskNames(targetProduct);
    }

    private void processFubScapeM() {
        Operator operator = new FubScapeMClassificationOp();
        operator.setSourceProduct(sourceProduct);
        if(reflectance_water_threshold > 0) {
            operator.setParameter("reflectance_water_threshold", reflectance_water_threshold);
        }
        if(thicknessOfCoast > 0) {
            operator.setParameter("thicknessOfCoast", thicknessOfCoast);
        }
        if(minimumOceanSize > 0) {
            operator.setParameter("minimumOceanSize", minimumOceanSize);
        }
        if(!calculateLakes) {
            operator.setParameter("calculatelakes", calculateLakes);
        }
        targetProduct = operator.getTargetProduct();
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FubScapeMOp.class, "idepix.scapem");
        }
    }
}
