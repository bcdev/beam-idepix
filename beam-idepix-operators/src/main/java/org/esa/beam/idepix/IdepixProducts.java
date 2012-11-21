package org.esa.beam.idepix;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.*;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.BrrOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.cloud.BlueBandOp;
import org.esa.beam.meris.cloud.CloudProbabilityOp;
import org.esa.beam.meris.cloud.CloudTopPressureOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.unmixing.Endmember;
import org.esa.beam.unmixing.SpectralUnmixingOp;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides intermediate products required in Idepix processing chains.
 *
 * @author olafd
 */
public class IdepixProducts {

    public static Product computeRadiance2ReflectanceProduct(Product sourceProduct) {
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS, sourceProduct);
    }

    public static Product computeCloudTopPressureProduct(Product sourceProduct) {
        return GPF.createProduct("Meris.CloudTopPressureOp", GPF.NO_PARAMS, sourceProduct);
    }

    // Cloud Top Pressure with FUB Straylight Correction
    public static  Product computeCloudTopPressureStraylightProduct(Product sourceProduct, boolean straylightCorr) {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("straylightCorr", straylightCorr);
        return GPF.createProduct("Meris.CloudTopPressureOp", params, sourceProduct);
    }


    public static Product computeBarometricPressureProduct(Product sourceProduct, boolean useGetasseDem) {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("useGetasseDem", useGetasseDem);
        return GPF.createProduct("Meris.BarometricPressure", params, sourceProduct);
    }

    public static Product computePressureLiseProduct(Product sourceProduct, Product rad2reflProduct,
                                                     boolean ipfOutputL2CloudDetection,
                                                     boolean straylightCorr,
                                                     boolean outputP1,
                                                     boolean outputPressureSurface,
                                                     boolean outputP2,
                                                     boolean outputPScatt) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        Map<String, Object> params = new HashMap<String, Object>(6);
        params.put("straylightCorr", straylightCorr);
        params.put("outputP1", outputP1);
        params.put("outputPressureSurface", outputPressureSurface);
        params.put("outputP2", outputP2);
        params.put("outputPScatt", outputPScatt);
        params.put("l2CloudDetection", ipfOutputL2CloudDetection);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LisePressureOp.class), params, input);
    }

    public static Product computeGaseousCorrectionProduct(Product sourceProduct, Product rad2reflProduct, Product merisCloudProduct,
                                                          boolean correctWater) {
        Map<String, Product> input = new HashMap<String, Product>(3);
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("correctWater", correctWater);
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

    public static Product computeSpectralUnmixingProduct(Product rayleighProduct, boolean computeErrorBands) {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("sourceProduct", rayleighProduct);
        Map<String, Object> params = new HashMap<String, Object>(3);
        params.put("sourceBandNames", IdepixConstants.SMA_SOURCE_BAND_NAMES);
        final Endmember[] endmembers = IdepixUtils.setupCCSpectralUnmixingEndmembers();
        params.put("endmembers", endmembers);
        params.put("computeErrorBands", computeErrorBands);
        params.put("minBandwidth", 5.0);
        params.put("unmixingModelName", "Fully Constrained LSU");
        return GPF.createProduct(OperatorSpi.getOperatorAlias(SpectralUnmixingOp.class), params, input);
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

    public static Product computeBlueBandProduct(Product sourceProduct, Product brrProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("toar", brrProduct);
        return GPF.createProduct("Meris.BlueBand", GPF.NO_PARAMS, input);
    }

    public static Product computeBrrProduct(Product sourceProduct, boolean outputToar, boolean correctWater) {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("input", sourceProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("outputToar", outputToar);
        params.put("correctWater", correctWater);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(BrrOp.class), params, input);
    }

    public static Product computeCombinedCloudProduct(Product blueBandProduct, Product cloudProbabilityProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("cloudProb", cloudProbabilityProduct);
        input.put("blueBand", blueBandProduct);
        return GPF.createProduct("Meris.CombinedCloud", GPF.NO_PARAMS, input);
    }

    public static Product computePsurfNNProduct(Product sourceProduct, Product merisCloudProduct,
                                                boolean pressureFubTropicalAtmosphere,
                                                boolean straylightCorr) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("cloud", merisCloudProduct);
        Map<String, Object> params = new HashMap<String, Object>(2);
        params.put("tropicalAtmosphere", pressureFubTropicalAtmosphere);
        // mail from RL, 2009/03/19: always apply correction on FUB pressure
        // currently only for RR (FR coefficients still missing)
        params.put("straylightCorr", straylightCorr);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(SurfacePressureFubOp.class), params, input);
    }

    public static Product computeCloudProbabilityProduct(Product sourceProduct) {
        Map<String, Product> input = new HashMap<String, Product>(1);
        input.put("input", sourceProduct);
        Map<String, Object> params = new HashMap<String, Object>(3);
        params.put("configFile", "cloud_config.txt");
        params.put("validLandExpression", "not l1_flags.INVALID and dem_alt > -50");
        params.put("validOceanExpression", "not l1_flags.INVALID and dem_alt <= -50");
        return GPF.createProduct("Meris.CloudProbability", params, input);
    }

}
