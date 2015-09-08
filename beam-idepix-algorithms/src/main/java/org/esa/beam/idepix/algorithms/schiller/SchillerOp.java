package org.esa.beam.idepix.algorithms.schiller;

import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.meris.radiometry.MerisRadiometryCorrectionOp;
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.schiller",
                  internal = true,  // currently hidden
                  version = "2.2",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with IPF (former MEPIX) algorithm.")
public class SchillerOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(defaultValue = "true", label = " Copy input radiance/reflectance bands")
    private boolean copyRadiances = true;

    @Parameter(label = " CTP value to use in MERIS cloud shadow algorithm", defaultValue = "Derive from Neural Net",
               valueSet = {
                       IdepixConstants.CTP_MODE_DEFAULT,
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
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobCover);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }
        processSchiller();
        renameL1bMaskNames(targetProduct);
    }

    private void processSchiller() {
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);

        // convert radiance bands to reflectance
        Map<String, Object> relfParam = new HashMap<>(3);
        relfParam.put("doRadToRefl", true);
        Product reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisRadiometryCorrectionOp.class),
                                                relfParam, sourceProduct);

        Operator operator = new SchillerClassificationOp();
        operator.setSourceProduct(reflProduct);
        Product cloudProduct = operator.getTargetProduct();

        Map<String, Product> shadowInput = new HashMap<>(4);
        shadowInput.put("l1b", sourceProduct);
        shadowInput.put("cloud", cloudProduct);
        shadowInput.put("ctp", ctpProduct);   // may be null
        Map<String, Object> params = new HashMap<>(1);
        params.put("ctpMode", ctpMode);
        targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudShadowOp.class), params, shadowInput);
        if (copyRadiances) {
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct, targetProduct,
                                      true);
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(SchillerOp.class);
        }
    }
}
