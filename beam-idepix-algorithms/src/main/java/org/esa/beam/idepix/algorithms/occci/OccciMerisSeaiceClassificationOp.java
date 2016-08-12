package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.MerisClassificationOp;
import org.esa.beam.idepix.seaice.SeaIceClassification;
import org.esa.beam.idepix.seaice.SeaIceClassifier;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataException;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

//import org.esa.beam.meris.l2auxdata.Constants;

/**
 * MERIS pixel classification operator for OCCCI.
 * Only water pixels are classified, following CC algorithm there, and same as current CAWA water processor.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification.meris.seaice",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "MERIS water pixel classification operator for OCCCI. SeaIce CCN mode.")
public class OccciMerisSeaiceClassificationOp extends MerisBasisOp {

    public static final double CC_RHO_TOA_442_THRESHOLD = 0.03;
    public static final double CC_DELTA_RHO_TOA_442_THRESHOLD = 0.03;

    private static final int BAND_BRIGHT_N = MerisClassificationOp.BAND_BRIGHT_N;
    private static final int BAND_SLOPE_N_1 = MerisClassificationOp.BAND_SLOPE_N_1;

    private static final int BAND_SLOPE_N_2 = MerisClassificationOp.BAND_SLOPE_N_2;
    private static final int BAND_FLH_7 = MerisClassificationOp.BAND_FLH_7;
    private static final int BAND_FLH_8 = MerisClassificationOp.BAND_FLH_8;

    private static final int BAND_FLH_9 = MerisClassificationOp.BAND_FLH_9;
    private static final double CC_GLINT_THRESHOLD = 0.2;
    private static final int CC_RHO_AG_REFERENCE_WAVELENGTH_INDEX = 12; // 865nm;

    private static final double CC_P1_SCALED_THRESHOLD = 1000.0;
    private static final double CC_NDVI_THRESHOLD = 0.1;
    private static final double CC_SEA_ICE_THRESHOLD = 10.0;

    private L2AuxData auxData;

    private Band cloudFlagBand;
    private SeaIceClassifier seaIceClassifier;
    private Band landWaterBand;
    private Band nnOutputBand;
    private Band wetIceOutputBand;


    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias = "pressure")
    private Product ctpProduct;
    @SourceProduct(alias = "pressureLise")
    private Product lisePressureProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @SuppressWarnings({"FieldCanBeLocal"})
    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "SIX_CLASSES",
            valueSet = {"SIX_CLASSES", "FOUR_CLASSES", "SIX_CLASSES_NORTH", "FOUR_CLASSES_NORTH"},
            label = "Neural Net for MERIS in case of sea ice classification",
            description = "The Neural Net which will be applied.")
    private MerisSeaiceNNSelector nnSelector;

    @Parameter(label = " Sea Ice Climatology Value", defaultValue = "false")
    private boolean ccOutputSeaIceClimatologyValue;

    @Parameter(defaultValue = "false",
            description = "Check for sea/lake ice also outside Sea Ice Climatology area.",
            label = "Check for sea/lake ice also outside Sea Ice Climatology area"
    )
    private boolean ignoreSeaIceClimatology;

    @Parameter(label = "Cloud screening 'ambiguous' threshold", defaultValue = "1.4")
    private double cloudScreeningAmbiguous;     // Schiller, used in previous approach only

    @Parameter(label = "Cloud screening 'sure' threshold", defaultValue = "1.8")
    private double cloudScreeningSure;         // Schiller, used in previous approach only

    @Parameter(defaultValue = "0.1",
            label = "Cloud screening threshold addition in case of glint")
    private double glintCloudThresholdAddition;

    @Parameter(defaultValue = "5.0",
            label = " 'MERIS1600' threshold (MERIS Sea Ice) ",
            description = " 'MERIS1600' threshold value ")
    double schillerMeris1600Threshold;

    @Parameter(defaultValue = "0.5",
            label = " 'MERIS/AATSR' cloud/ice separation value (MERIS Sea Ice) ",
            description = " 'MERIS/AATSR' cloud/ice separation value ")
    double schillerMerisAatsrCloudIceSeparationValue;

    @Parameter(description = " 95 percent histogram interval for reflectance band 3 ")
    double[] refl3AB;

    @Parameter(description = " 95 percent histogram interval for reflectance band 14 ")
    double[] refll4AB;

    @Parameter(description = " 95 percent histogram interval for reflectance band 15 ")
    double[] refll5AB;

    @Parameter(defaultValue = "true",
            description = "Apply 'Blue Filter' for wet ice in case of processing MERIS for sea ice.",
            label = "Apply 'Blue Filter' for wet ice in case of processing MERIS for sea ice."
    )
    private boolean applyBlueFilter;

    public static final String SCHILLER_MERIS_WATER_NET_NAME = "11x8x5x3_876.8_water.net";
    public static final String SCHILLER_MERIS_ALL_NET_NAME = "11x8x5x3_1409.7_all.net";
    public static final String SCHILLER_MERIS_AATSR_OUTER_NET_NAME = "9_3282.3.net";  // latest 'outer' net, HS 20151206
//    public static final String SCHILLER_MERIS_ALL_NET_4CLASS_NAME = "8_671.3.net";  // latest net, HS 20160303
    //    public static final String SCHILLER_MERIS_AATSR_OUTER_NET_NAME = "6_912.1.net";  // VZA > 14deg
    public static final String SCHILLER_MERIS_AATSR_INNER_NET_NAME = "6_1271.6.net"; // VZA < 7deg

    ThreadLocal<SchillerNeuralNetWrapper> merisWaterNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAllNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAatsrOuterNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisSeaIceNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAatsrInnerNeuralNet;

    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }

        readSchillerNets();

        createTargetProduct();

        initSeaIceClassifier();

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    private void readSchillerNets() {
        try (InputStream isWater = getClass().getResourceAsStream(SCHILLER_MERIS_WATER_NET_NAME);
             InputStream isAll = getClass().getResourceAsStream(SCHILLER_MERIS_ALL_NET_NAME);
             InputStream isMerisAatsrOuter = getClass().getResourceAsStream(SCHILLER_MERIS_AATSR_OUTER_NET_NAME);
             InputStream isMerisSeaIceNet = getClass().getResourceAsStream(nnSelector.getNnFileName());
             InputStream isMerisAatsrInner = getClass().getResourceAsStream(SCHILLER_MERIS_AATSR_INNER_NET_NAME)) {
            merisWaterNeuralNet = SchillerNeuralNetWrapper.create(isWater);
            merisAllNeuralNet = SchillerNeuralNetWrapper.create(isAll);
            merisAatsrOuterNeuralNet = SchillerNeuralNetWrapper.create(isMerisAatsrOuter);
            merisSeaIceNeuralNet = SchillerNeuralNetWrapper.create(isMerisSeaIceNet);
            merisAatsrInnerNeuralNet = SchillerNeuralNetWrapper.create(isMerisAatsrInner);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    private void initSeaIceClassifier() {
        final ProductData.UTC startTime = getSourceProduct().getStartTime();
        final int monthIndex = startTime.getAsCalendar().get(Calendar.MONTH);
        try {
            seaIceClassifier = new SeaIceClassifier(monthIndex + 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createTargetProduct() {
        targetProduct = createCompatibleProduct(l1bProduct, "MER", "MER_L2");

        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = OccciUtils.createOccciFlagCoding(IdepixUtils.IDEPIX_PIXEL_CLASSIF_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        OccciUtils.setupOccciClassifBitmask(targetProduct);

        ProductUtils.copyFlagBands(l1bProduct, targetProduct, true);
        nnOutputBand = targetProduct.addBand("nn_value", ProductData.TYPE_FLOAT32);
        wetIceOutputBand = targetProduct.addBand("wet_ice_value", ProductData.TYPE_FLOAT32);
        wetIceOutputBand.setNoDataValue(Float.NaN);
        wetIceOutputBand.setNoDataValueUsed(true);
    }

    private SourceData loadSourceTiles(Rectangle rectangle) throws OperatorException {

        SourceData sd = new SourceData();
        sd.rhoToa = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS][0];
        sd.radiance = new Tile[6];

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sd.rhoToa[i] = (float[]) getSourceTile(
                    rhoToaProduct.getBand("reflec_" + (i + 1)),
                    rectangle).getRawSamples().getElems();
        }
        sd.radiance[BAND_BRIGHT_N] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[auxData.band_bright_n]),
                rectangle);
        sd.radiance[BAND_SLOPE_N_1] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[auxData.band_slope_n_1]),
                rectangle);
        sd.radiance[BAND_SLOPE_N_2] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[auxData.band_slope_n_2]),
                rectangle);

        sd.radiance[BAND_FLH_7] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_7_BAND_NAME), rectangle);
        sd.radiance[BAND_FLH_8] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_8_BAND_NAME), rectangle);
        sd.radiance[BAND_FLH_9] = getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_9_BAND_NAME), rectangle);

        sd.sza = (float[]) getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                         rectangle).getRawSamples().getElems();
        sd.vza = (float[]) getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                         rectangle).getRawSamples().getElems();
        sd.saa = (float[]) getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                                         rectangle).getRawSamples().getElems();
        sd.vaa = (float[]) getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                                         rectangle).getRawSamples().getElems();

        sd.sins = new float[sd.sza.length];
        sd.sinv = new float[sd.vza.length];
        sd.coss = new float[sd.sza.length];
        sd.cosv = new float[sd.vza.length];
        sd.deltaAzimuth = new float[sd.vza.length];
        for (int i = 0; i < sd.sza.length; i++) {
            sd.sins[i] = (float) Math.sin(sd.sza[i] * MathUtils.DTOR);
            sd.sinv[i] = (float) Math.sin(sd.vza[i] * MathUtils.DTOR);
            sd.coss[i] = (float) Math.cos(sd.sza[i] * MathUtils.DTOR);
            sd.cosv[i] = (float) Math.cos(sd.vza[i] * MathUtils.DTOR);
            sd.deltaAzimuth[i] = (float) HelperFunctions.computeAzimuthDifference(sd.vaa[i], sd.saa[i]);
        }

        sd.windu = (float[]) getSourceTile(l1bProduct.getTiePointGrid("zonal_wind"),
                                           rectangle).getRawSamples().getElems();

        sd.windv = (float[]) getSourceTile(l1bProduct.getTiePointGrid("merid_wind"),
                                           rectangle).getRawSamples().getElems();

        sd.l1Flags = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME), rectangle);
        return sd;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        try {
            final Rectangle sourceRectangle = createSourceRectangle(band, targetRectangle);
            final SourceData sd = loadSourceTiles(sourceRectangle);

            final Tile waterFractionTile = getSourceTile(landWaterBand, sourceRectangle);

            final PixelInfo pixelInfo = new PixelInfo();

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                pixelInfo.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    final int i = (y - sourceRectangle.y) * sourceRectangle.width + (x - sourceRectangle.x);
                    pixelInfo.x = x;
                    pixelInfo.index = i;
                    if (!sd.l1Flags.getSampleBit(x, y, Constants.L1_F_INVALID)) {
                        final int waterFraction = waterFractionTile.getSampleInt(pixelInfo.x, pixelInfo.y);

                        if (isLandPixel(pixelInfo, sd, waterFraction)) {
                            if (band == cloudFlagBand) {
                                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_LAND, true);
                            } else {
                                targetTile.setSample(pixelInfo.x, pixelInfo.y, Float.NaN);
                            }
                        } else {
                            if (band == cloudFlagBand) {
                                classifyCloud(sd, pixelInfo, targetTile, waterFraction);
                            }
                            if (band == nnOutputBand) {
                                final double[] nnOutput = getMerisNNOutput(sd, pixelInfo);
                                targetTile.setSample(pixelInfo.x, pixelInfo.y, nnOutput[0]);
                            }
                            if (band == wetIceOutputBand) {
                                final float wetIceValue = getWetIceValue(sd, pixelInfo);
                                targetTile.setSample(pixelInfo.x, pixelInfo.y, wetIceValue);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private boolean isLandPixel(PixelInfo pixelInfo, SourceData sd, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(pixelInfo).lat > -58f) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return sd.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, Constants.L1_F_LAND);
            }
        } else {
            return sd.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, Constants.L1_F_LAND);
        }
    }

    private boolean isCoastlinePixel(PixelInfo pixelInfo, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        // values bigger than 100 indicate no data
        // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return getGeoPos(pixelInfo).lat > -58f && waterFraction <= 100 && waterFraction < 100 && waterFraction > 0;
    }

    private Rectangle createSourceRectangle(Band band, Rectangle rectangle) {
        int x = rectangle.x;
        int y = rectangle.y;
        int w = rectangle.width;
        int h = rectangle.height;
        if (x > 0) {
            x -= 1;
            w += 2;
        } else {
            w += 1;
        }
        if (x + w > band.getRasterWidth()) {
            w = band.getRasterWidth() - x;
        }
        if (y > 0) {
            y -= 1;
            h += 2;
        } else {
            h += 1;
        }
        if (y + h > band.getRasterHeight()) {
            h = band.getRasterHeight() - y;
        }
        return new Rectangle(x, y, w, h);
    }

    public void classifyCloud(SourceData sd, PixelInfo pixelInfo, Tile targetTile, int waterFraction) {
        final boolean isCoastline = isCoastlinePixel(pixelInfo, waterFraction);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_COASTLINE, isCoastline);

        boolean is_glint_risk = !isCoastline && isGlintRisk(sd, pixelInfo);
        boolean checkForSeaIce = false;
        if (!isCoastline) {
            // over water
            final GeoPos geoPos = getGeoPos(pixelInfo);
            checkForSeaIce = ignoreSeaIceClimatology || isPixelClassifiedAsSeaice(geoPos);

            // glint makes sense only if we have no sea ice
            is_glint_risk = is_glint_risk && !isPixelClassifiedAsSeaice(geoPos);
        }

        boolean isCloudSure = false;
        boolean isCloudAmbiguous;

        double nnOutput = getMerisNNOutput(sd, pixelInfo)[0];
        // latest net 8_671.3.net (4 CLASSES), 20160303:
        //  nnOutput <  0.55       : totally cloudy                      --> F_CLOUD_SURE
        //  0.55 < nnOutput <  1.5 : ice (clear + semi-transparent)      --> F_SNOW_ICE
        //  1.5 < nnOutput < 2.45  : spatially mixed ice/water           --> F_SNOW_ICE or ignore // todo
        //  2.45 < nnOutput        : water (clear + semi-transparent)    ignore

        // latest net 9x6_935.4.net (6 CLASSES), 20160531:
        //  nnOutput <  0.7       : totally cloudy                       --> F_CLOUD_SURE
        //  0.45 < nnOutput <  1.65 : clear ice                           --> F_SNOW_ICE
        //  1.65 < nnOutput <  2.5 : semi-transparent clouds over ice    --> F_CLOUD_AMBIGUOUS
        //  2.45 < nnOutput <  3.5 : spatially mixed ice/water            --> F_SNOW_ICE or ignore // todo
        //  3.35 < nnOutput < 4.6  : semi-transparent clouds over water   --> F_CLOUD_AMBIGUOUS
        //  4.5 < nnOutput        : clear water                           ignore

        if (!targetTile.getSampleBit(pixelInfo.x, pixelInfo.y, IdepixConstants.F_INVALID)) {
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, false);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, false);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_WHITE_ICE, false);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_WET_ICE, false);

            // deactivate for the moment, apply MPa wetIce, dryIce instead...
//            isSnowIce = isSnowIceFromNN(nnOutput);
//            if (isSnowIce) {
//                // this would be as 'SNOW/ICE'...
//                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, true);
//                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, false);
//                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, false);
//            }

            isCloudSure = isCloudSureFromNN(nnOutput);
            if (isCloudSure) {
                // this would be 'CLOUD_SURE'...
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, true);
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, false);
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, true);
            }

            isCloudAmbiguous = isCloudAmbiguousFromNN(nnOutput);
            if (isCloudAmbiguous) {
                // this would be 'CLOUD_SURE'...
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, false);
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, true);
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, true);
            }

            // white/wet ice classification (MPa, 21.07.2016):
            if (checkForSeaIce) {  // only within sea ice climatology
                final boolean isWhiteIce = nnOutput > 0.0 && nnOutput < 1.45;
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_WHITE_ICE, isWhiteIce);
                final boolean isBlueIce = applyBlueFilter &&
                        OccciMerisSeaiceAlgorithm.isBlueIce(sd.getRhoToa(), pixelInfo.index);
                final boolean isWetIce = isBlueIce ||
                        OccciMerisSeaiceAlgorithm.isWetIce(sd.getRhoToa(), pixelInfo.index, refl3AB, refll4AB, refll5AB);
                if (!isWhiteIce && isWetIce) {
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_WET_ICE, true);
                }
                final boolean isWhiteOrWetIce = isWetIce || isWhiteIce;
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, isWhiteOrWetIce);
                // set clouds to false is there is white or wet ice...
                if (isWhiteOrWetIce) {
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, false);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, false);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, false);
                }
            }
            // end white/wet ice classification
        }
        targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_GLINT_RISK, is_glint_risk && !isCloudSure);
    }

    private float getWetIceValue(SourceData sd, PixelInfo pixelInfo) {
        final float rhoToa3 = sd.rhoToa[2][pixelInfo.index];
        final float rhoToa14 = sd.rhoToa[13][pixelInfo.index];
        final float rhoToa15 = sd.rhoToa[14][pixelInfo.index];
        return OccciMerisSeaiceAlgorithm.getWetIceValue(rhoToa3, rhoToa14, rhoToa15, refl3AB, refll4AB, refll5AB);
    }

    private boolean isCloudSureFromNN(double nnOutput) {
        final double sep1 = nnSelector.getSeparationValues()[1];
        if (nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES ||
                nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES_NORTH) {
            // FOUR_CLASSES("FOUR_CLASSES", "8_671.3.net", new double[]{0.55, 1.5, 2.45}),
            return nnOutput < sep1;  // this is not Schillers best value!
        } else {
            // SIX_CLASSES("SIX_CLASSES", "9x6_935.4.net", new double[]{0.45, 1.65, 2.45, 3.35, 4.5});
            final double sep2 = nnSelector.getSeparationValues()[2];
            final double sep3 = nnSelector.getSeparationValues()[3];
            return nnOutput >= sep2 && nnOutput < sep3;
        }
    }

    private boolean isCloudAmbiguousFromNN(double nnOutput) {
//        final double sep0 = nnSelector.getSeparationValues()[0];
        final double sep1 = nnSelector.getSeparationValues()[1];
//        final double sep2 = nnSelector.getSeparationValues()[2];
        if (nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES ||
                nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES_NORTH) {
            // FOUR_CLASSES("FOUR_CLASSES", "8_671.3.net", new double[]{0.55, 1.5, 2.45}),
            return nnOutput < sep1;  // this is not Schillers best value!
        } else {
            // SIX_CLASSES("SIX_CLASSES", "9x6_935.4.net", new double[]{0.45, 1.65, 2.45, 3.35, 4.5});
            final double sep3 = nnSelector.getSeparationValues()[3];
            final double sep4 = nnSelector.getSeparationValues()[4];
            return (nnOutput >= sep3 && nnOutput < sep4);
        }
    }

//    private boolean isSnowIceFromNN(double nnOutput) {
//        final double sep0 = nnSelector.getSeparationValues()[0];
//        final double sep1 = nnSelector.getSeparationValues()[1];
//        final double sep2 = nnSelector.getSeparationValues()[2];
//        if (nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES ||
//                nnSelector == MerisSeaiceNNSelector.FOUR_CLASSES_NORTH) {
////            return nnOutput >= sep0 && nnOutput < sep1;
//            return nnOutput >= sep0 && nnOutput < sep2;
//        } else {
//            final double sep3 = nnSelector.getSeparationValues()[3];
//            return (nnOutput >= sep0 && nnOutput < sep1) || (nnOutput >= sep2 && nnOutput < sep3);
//        }
//    }

    private double[] getMerisNNOutput(SourceData sd, PixelInfo pixelInfo) {
//        return getMerisNNOutputImpl(sd, pixelInfo, merisAatsrOuterNeuralNet.get());
        return getMerisNNOutputImpl(sd, pixelInfo, merisSeaIceNeuralNet.get()); // latest net, 20160531
    }

    private double[] getMerisNNOutputImpl(SourceData sd, PixelInfo pixelInfo, SchillerNeuralNetWrapper nnWrapper) {
        double[] nnInput = nnWrapper.getInputVector();
        for (int i = 0; i < nnInput.length; i++) {
//            nnInput[i] = sd.rhoToa[i][pixelInfo.index];   //  latest 'outer' NN 9_3282.3.net";
            nnInput[i] = Math.sqrt(sd.rhoToa[i][pixelInfo.index]);   //  latest NN 9x6_935.4.net";
        }
        return nnWrapper.getNeuralNet().calc(nnInput);
    }

    private boolean isPixelClassifiedAsSeaice(GeoPos geoPos) {
        // check given pixel, but also neighbour cell from 1x1 deg sea ice climatology...
        final double maxLon = 360.0;
        final double minLon = 0.0;
        final double maxLat = 180.0;
        final double minLat = 0.0;

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                // for sea ice climatology indices, we need to shift lat/lon onto [0,180]/[0,360]...
                double lon = geoPos.lon + 180.0 + x * 1.0;
                double lat = 90.0 - geoPos.lat + y * 1.0;
                lon = Math.max(lon, minLon);
                lon = Math.min(lon, maxLon);
                lat = Math.max(lat, minLat);
                lat = Math.min(lat, maxLat);
                final SeaIceClassification classification = seaIceClassifier.getClassification(lat, lon);
                if (classification.max >= CC_SEA_ICE_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGlintRisk(SourceData sd, PixelInfo pixelInfo) {
        final float rhoGlint = (float) computeRhoGlint(sd, pixelInfo);
        return (rhoGlint >= CC_GLINT_THRESHOLD * sd.rhoToa[Constants.bb865][pixelInfo.index]);
    }

    private GeoPos getGeoPos(PixelInfo pixelInfo) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getGeoCoding();
        final PixelPos pixelPos = new PixelPos(pixelInfo.x, pixelInfo.y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private double computeChiW(SourceData sd, PixelInfo pixelInfo) {
        final double phiw = azimuth(sd.windu[pixelInfo.index], sd.windv[pixelInfo.index]);
        /* and "scattering" angle */
        final double arg = MathUtils.DTOR * (sd.saa[pixelInfo.index] - phiw);
        return MathUtils.RTOD * (Math.acos(Math.cos(arg)));
    }

    private double computeRhoGlint(SourceData sd, PixelInfo pixelInfo) {
        final double chiw = computeChiW(sd, pixelInfo);
        final double deltaAzimuth = sd.deltaAzimuth[pixelInfo.index];
        final double windm = Math.sqrt(sd.windu[pixelInfo.index] * sd.windu[pixelInfo.index] +
                                               sd.windv[pixelInfo.index] * sd.windv[pixelInfo.index]);
            /* allows to retrieve Glint reflectance for wurrent geometry and wind */
        return glintRef(sd.sza[pixelInfo.index], sd.vza[pixelInfo.index], deltaAzimuth, windm, chiw);
    }

    private double azimuth(double x, double y) {
        if (y > 0.0) {
            // DPM #2.6.5.1.1-1
            return (MathUtils.RTOD * Math.atan(x / y));
        } else if (y < 0.0) {
            // DPM #2.6.5.1.1-5
            return (180.0 + MathUtils.RTOD * Math.atan(x / y));
        } else {
            // DPM #2.6.5.1.1-6
            return (x >= 0.0 ? 90.0 : 270.0);
        }
    }

    private double glintRef(double thetas, double thetav, double delta, double windm, double chiw) {
        FractIndex[] rogIndex = FractIndex.createArray(5);

        Interp.interpCoord(chiw, auxData.rog.getTab(0), rogIndex[0]);
        Interp.interpCoord(thetav, auxData.rog.getTab(1), rogIndex[1]);
        Interp.interpCoord(delta, auxData.rog.getTab(2), rogIndex[2]);
        Interp.interpCoord(windm, auxData.rog.getTab(3), rogIndex[3]);
        Interp.interpCoord(thetas, auxData.rog.getTab(4), rogIndex[4]);
        return Interp.interpolate(auxData.rog.getJavaArray(), rogIndex);
    }

    private class SourceData {

        private float[][] rhoToa;
        private Tile[] radiance;
        private float[] sza;
        private float[] vza;
        private float[] saa;
        private float[] vaa;
        private float[] sins;
        private float[] sinv;
        private float[] coss;
        private float[] cosv;
        private float[] deltaAzimuth;
        private float[] windu;
        private float[] windv;
        private Tile l1Flags;

        public float[][] getRhoToa() {
            return rhoToa;
        }
    }

    private static class PixelInfo {
        int index;
        int x;
        int y;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(OccciMerisSeaiceClassificationOp.class);
        }
    }

}
