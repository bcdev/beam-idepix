package org.esa.beam.idepix.algorithms.magicstick;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.AlgorithmSelector;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.operators.IdepixCloudShadowOp;
import org.esa.beam.idepix.util.IdepixUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.magicstick",
                  version = "2.2-EVOLUTION-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with IPF (former MEPIX) algorithm.")
public class MagicStickOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(label = " CTP value to use in MERIS cloud shadow algorithm", defaultValue = "Derive from Neural Net",
               valueSet = {
                       IdepixConstants.ctpModeDefault,
                       "850 hPa",
                       "700 hPa",
                       "500 hPa",
                       "400 hPa",
                       "300 hPa"
               })
    private String ctpMode;

    private Product ctpProduct;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.MagicStick);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processMagicStick();
        renameL1bMaskNames(targetProduct);
    }

    private void processMagicStick() {
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);

        Operator operator = new MagicStickClassificationOp();
        operator.setSourceProduct(sourceProduct);
        Product cloudProduct = operator.getTargetProduct();

        Map<String, Product> shadowInput = new HashMap<String, Product>(4);
        shadowInput.put("l1b", sourceProduct);
        shadowInput.put("cloud", cloudProduct);
        shadowInput.put("ctp", ctpProduct);   // may be null
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("ctpMode", ctpMode);
        targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class), params, shadowInput);
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MagicStickOp.class, "idepix.magicstick");
        }
    }
}
