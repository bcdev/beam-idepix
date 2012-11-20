package org.esa.beam.idepix;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.*;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.unmixing.Endmember;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides intermediate products required in Idepix processing chains.
 *
 * @author olafd
 */
public class IdepixProducts {

    public static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS,
                                 sourceProduct);
    }

    public static Product computeCloudTopPressureProduct(Product sourceProduct) {
        return GPF.createProduct("Meris.CloudTopPressureOp", GPF.NO_PARAMS, sourceProduct);
    }

    public static Product computeBarometricPressureProduct(Product sourceProduct) {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("useGetasseDem", false);
        return GPF.createProduct("Meris.BarometricPressure", params, sourceProduct);
    }

    public static Product computePressureLiseProduct(Product sourceProduct, Product rad2reflProduct,
                                                     boolean ipfOutputL2CloudDetection) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        Map<String, Object> params = new HashMap<String, Object>(6);
        params.put("straylightCorr", false);
        params.put("outputP1", true);
        params.put("outputPressureSurface", false);
        params.put("outputP2", false);
        params.put("outputPScatt", true);
        params.put("l2CloudDetection", ipfOutputL2CloudDetection);
        return GPF.createProduct("Meris.LisePressure", params, input);
    }

    public static Product computeGaseousCorrectionProduct(Product sourceProduct, Product rad2reflProduct, Product merisCloudProduct) {
        Map<String, Product> input = new HashMap<String, Product>(3);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("correctWater", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), params, input);
    }

    public static Product computeRayleighCorrectionProduct(Product sourceProduct,
                                                           Product gasProduct,
                                                           Product rad2reflProduct,
                                                           Product landProduct,
                                                           Product merisCloudProduct,
                                                           boolean ccOutputRayleigh,
                                                           String landExpression) {
        Map<String, Product> input = new HashMap<String, Product>(3);
        input.put("l1b", sourceProduct);
        input.put("input", gasProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("land", landProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("correctWater", true);
        params.put("landExpression", landExpression);
        params.put("exportBrrNormalized", ccOutputRayleigh);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixRayleighCorrectionOp.class), params,
                                 input);
    }

    public static Product computeSpectralUnmixingProduct(Product rayleighProduct) {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("sourceProduct", rayleighProduct);
        Map<String, Object> params = new HashMap<String, Object>(3);
        // todo: do we need more than one endmember file? do more parameters need to be flexible?
        params.put("sourceBandNames", IdepixConstants.SMA_SOURCE_BAND_NAMES);
        final Endmember[] endmembers = IdepixUtils.setupCCSpectralUnmixingEndmembers();
        params.put("endmembers", endmembers);
        params.put("computeErrorBands", true);
        params.put("minBandwidth", 5.0);
        params.put("unmixingModelName", "Fully Constrained LSU");
        return GPF.createProduct("Unmix", params, input);
    }

    public static Product computeMerisCloudProduct(Product sourceProduct,
                                                   Product rad2reflProduct,
                                                   Product ctpProduct,
                                                   Product pressureLiseProduct,
                                                   Product pbaroProduct,
                                                   boolean computeL2Pressure) {
        Map<String, Product> input = new HashMap<String, Product>(4);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("ctp", ctpProduct);
        input.put("pressureOutputLise", pressureLiseProduct);
        input.put("pressureBaro", pbaroProduct);

        Map<String, Object> params = new HashMap<String, Object>(11);
        params.put("l2Pressures", computeL2Pressure);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixCloudClassificationOp.class),
                                              params, input);
    }

    public static Product computeLandClassificationProduct(Product sourceProduct, Product gasProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("gascor", gasProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), GPF.NO_PARAMS, input);
    }

}
