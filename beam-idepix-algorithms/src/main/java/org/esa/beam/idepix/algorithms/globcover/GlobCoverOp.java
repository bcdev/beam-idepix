package org.esa.beam.idepix.algorithms.globcover;

import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.operators.BasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.cloud.CloudEdgeOp;
import org.esa.beam.meris.cloud.CloudShadowOp;
import org.esa.beam.util.ProductUtils;

/**
 * Idepix operator for pixel identification and classification with CoastColour algorithm.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.globcover",
                  internal = true,  // currently hidden
                  version = "2.2-EVOLUTION-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Pixel identification and classification with IPF (former MEPIX) algorithm.")
public class GlobCoverOp extends BasisOp {

    @SourceProduct(alias = "source", label = "Name (MERIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(defaultValue = "true", label = " Copy input radiance/reflectance bands")
    private boolean copyRadiances = true;

    private Product ctpProduct;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.GlobCover);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.inputconsistencyErrorMessage);
        }
        processGlobCover();
        renameL1bMaskNames(targetProduct);
    }

    private void processGlobCover() {
        Product brrProduct = IdepixProducts.computeBrrProduct(sourceProduct, true, true);
        Product blueBandProduct = IdepixProducts.computeBlueBandProduct(sourceProduct, brrProduct);
        Product cloudProbabilityProduct = IdepixProducts.computeCloudProbabilityProduct(sourceProduct);
        Product combinedCloudProduct = IdepixProducts.computeCombinedCloudProduct(blueBandProduct, cloudProbabilityProduct);
        ctpProduct = IdepixProducts.computeCloudTopPressureProduct(sourceProduct);

        Operator cloudEdgeOp = new CloudEdgeOp();
        cloudEdgeOp.setSourceProduct(combinedCloudProduct);
        Product cloudEdgeProduct = cloudEdgeOp.getTargetProduct();

        Operator cloudShadowOp = new CloudShadowOp();
        cloudShadowOp.setSourceProduct("l1b", sourceProduct);
        cloudShadowOp.setSourceProduct("cloud", cloudEdgeProduct);
        cloudShadowOp.setSourceProduct("ctp", ctpProduct);
        Product cloudShadowProduct = cloudShadowOp.getTargetProduct();

        Operator idepixGlobCoverOp = new GlobCoverClassificationOp();
        idepixGlobCoverOp.setSourceProduct("cloudProduct", cloudShadowProduct);
        idepixGlobCoverOp.setSourceProduct("brrProduct", brrProduct);
        targetProduct = idepixGlobCoverOp.getTargetProduct();
        if (copyRadiances) {
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct, targetProduct,
                                      true);
            }
            ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME, sourceProduct, targetProduct, true);
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobCoverOp.class);
        }
    }
}
