package org.esa.beam.idepix;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.idepix.operators.BarometricPressureOp;
import org.esa.beam.idepix.operators.IdepixRayleighCorrectionOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.operators.MerisClassificationOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
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

    public static Product computeBarometricPressureProduct(Product sourceProduct, boolean useGetasseDem) {
        Map<String, Object> params = new HashMap<String, Object>(1);
        params.put("useGetasseDem", useGetasseDem);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(BarometricPressureOp.class), params, sourceProduct);
    }

    public static Product computePressureLiseProduct(Product sourceProduct, Product rad2reflProduct,
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
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisClassificationOp.class),
                params, input);
    }

    public static Product computeLandClassificationProduct(Product sourceProduct, Product gasProduct) {
        Map<String, Product> input = new HashMap<String, Product>(2);
        input.put("l1b", sourceProduct);
        input.put("gascor", gasProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), GPF.NO_PARAMS, input);
    }

    public static void addRadiance2ReflectanceBands(Product rad2reflProduct, Product targetProduct) {
        for (String bandname : rad2reflProduct.getBandNames()) {
            if (!targetProduct.containsBand(bandname)) {
                System.out.println("adding band: " + bandname);
                targetProduct.addBand(rad2reflProduct.getBand(bandname));
            }
        }
    }

    public static void addCCSeaiceClimatologyValueBand(Product merisCloudProduct, Product targetProduct) {
        for (String bandname : merisCloudProduct.getBandNames()) {
            if (bandname.equalsIgnoreCase("sea_ice_climatology_value")) {
                moveBand(targetProduct, merisCloudProduct, bandname);
            }
        }
    }

    public static void addCCCloudProbabilityValueBand(Product merisCloudProduct, Product targetProduct) {
        for (String bandname : merisCloudProduct.getBandNames()) {
            if (bandname.equalsIgnoreCase(MerisClassificationOp.CLOUD_PROBABILITY_VALUE)) {
                moveBand(targetProduct, merisCloudProduct, bandname);
            }
        }
    }

    public static void addRayleighCorrectionBands(Product rayleighProduct, Product targetProduct) {
        int l1_band_num = RayleighCorrectionOp.L1_BAND_NUM;
        FlagCoding flagCoding = RayleighCorrectionOp.createFlagCoding(l1_band_num);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        for (Band band : rayleighProduct.getBands()) {
            // do not add the normalized bands
            if (!targetProduct.containsBand(band.getName()) && !band.getName().endsWith("_n")) {
                if (band.getName().equals(RayleighCorrectionOp.RAY_CORR_FLAGS)) {
                    band.setSampleCoding(flagCoding);
                }
                System.out.println("adding band: " + band.getName());
                targetProduct.addBand(band);
                targetProduct.getBand(band.getName()).setSourceImage(band.getSourceImage());
            }
        }
    }

    public static void addSpectralUnmixingBands(Product smaProduct, Product targetProduct) {
        for (Band band : smaProduct.getBands()) {
            // do not add the normalized bands
            if (!targetProduct.containsBand(band.getName()) &&
                    (band.getName().startsWith("summary") || band.getName().endsWith("_abundance"))) {
                System.out.println("adding band: " + band.getName());
                targetProduct.addBand(band);
                targetProduct.getBand(band.getName()).setSourceImage(band.getSourceImage());
                final String origBandName = band.getName();
                targetProduct.getBand(origBandName).setName("spec_unmix_" + origBandName);
            }
        }
    }

    public static void addGaseousCorrectionBands(Product gasProduct, Product targetProduct) {
        FlagCoding flagCoding = GaseousCorrectionOp.createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        Band band = gasProduct.getBand(GaseousCorrectionOp.GAS_FLAGS);
        band.setSampleCoding(flagCoding);
        System.out.println("adding band: " + band.getName());
        targetProduct.addBand(band);
    }

    private static void moveBand(Product targetProduct, Product product, String bandname) {
        if (!targetProduct.containsBand(bandname)) {
            System.out.println("adding band: " + bandname);
            targetProduct.addBand(product.getBand(bandname));
        }
    }

}
