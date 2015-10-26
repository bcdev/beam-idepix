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
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.operators.MerisClassificationOp;
import org.esa.beam.idepix.seaice.SeaIceClassification;
import org.esa.beam.idepix.seaice.SeaIceClassifier;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrection;
import org.esa.beam.meris.dpm.PixelId;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataException;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
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
@OperatorMetadata(alias = "idepix.occci.classification.meris",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "MERIS water pixel classification operator for OCCCI.")
public class OccciMerisClassificationOp extends MerisBasisOp {

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
    private static final double CC_MDSI_THRESHOLD = 0.01;
    private static final double CC_NDVI_THRESHOLD = 0.1;
    private static final double CC_SEA_ICE_THRESHOLD = 10.0;

    private SchillerAlgorithm landWaterNN;
    private L2AuxData auxData;
    private PixelId pixelId;
    private RayleighCorrection rayleighCorrection;

    private Band cloudFlagBand;
    private SeaIceClassifier seaIceClassifier;
    private Band ctpBand;
    private Band liseP1Band;
    private Band lisePScattBand;
    private Band landWaterBand;
    private Band nnOutputBand;
    private Band meris1600Band;
    private Band merisAatsrCloudProbBand;


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

    @Parameter(label = " Cloud Probability Feature Value", defaultValue = "false")
    private boolean outputCloudProbabilityFeatureValue;    // Schiller

    //    @Parameter(defaultValue = "true",
//            label = " Apply Schiller NN for cloud classification",
//            description = " Apply Schiller NN for cloud classification ")
    private boolean applyMERISSchillerNN = true;   // seems actually the best we have

    @Parameter(defaultValue = "true",
            label = " Use Schiller 'MERIS/AATSR' NN (MERIS) ",
            description = " Use Schiller 'MERIS/AATSR' NN (instead of standard CC 'WATER' NN) ")
    private boolean useSchillerMerisAatsrNN = true;   // seems actually the best we have

    @Parameter(defaultValue = "20.0",
            label = " Schiller 'MERIS1600' threshold (MERIS) ",
            description = " Schiller 'MERIS1600' threshold value ")
    double schillerMeris1600Threshold;

    @Parameter(defaultValue = "0.5",
            label = " Schiller 'MERIS/AATSR' cloud/ice separation value (MERIS) ",
            description = " Schiller 'MERIS/AATSR' cloud/ice separation value ")
    double schillerMerisAatsrCloudIceSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Write Schiller NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = " Schiller NN cloud ambiguous lower boundary ",
            description = " Schiller NN cloud ambiguous lower boundary ")
    double schillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " Schiller NN cloud ambiguous/sure separation value ",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double schillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " Schiller NN cloud sure/snow separation value ",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double schillerNNCloudSureSnowSeparationValue;

    //    @Parameter(defaultValue = "true",
//            label = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)",
//            description = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean applyMERISSchillerNNPure = true;     // previous approach flags many coastlines and thin cloud edges as 'cloud sure'

    public static final String SCHILLER_MERIS_WATER_NET_NAME = "11x8x5x3_876.8_water.net";
    public static final String SCHILLER_MERIS_ALL_NET_NAME = "11x8x5x3_1409.7_all.net";
    public static final String SCHILLER_MERIS_AATSR_OUTER_NET_NAME = "6_912.1.net";  // VZA > 14deg
    public static final String SCHILLER_MERIS_AATSR_INNER_NET_NAME = "6_1271.6.net"; // VZA < 7deg

    ThreadLocal<SchillerNeuralNetWrapper> merisWaterNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAllNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAatsrOuterNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> merisAatsrInnerNeuralNet;


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }

        readSchillerNets();

        landWaterNN = new SchillerAlgorithm(SchillerAlgorithm.Net.ALL);
        pixelId = new PixelId(auxData);
        rayleighCorrection = new RayleighCorrection(auxData);
        createTargetProduct();

        initSeaIceClassifier();

        ctpBand = ctpProduct.getBand("cloud_top_press");
        liseP1Band = lisePressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
        lisePScattBand = lisePressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    private void readSchillerNets() {
        try (InputStream isWater = getClass().getResourceAsStream(SCHILLER_MERIS_WATER_NET_NAME);
             InputStream isAll = getClass().getResourceAsStream(SCHILLER_MERIS_ALL_NET_NAME);
             InputStream isMerisAatsrOuter = getClass().getResourceAsStream(SCHILLER_MERIS_AATSR_OUTER_NET_NAME);
             InputStream isMerisAatsrInner = getClass().getResourceAsStream(SCHILLER_MERIS_AATSR_INNER_NET_NAME)) {
            merisWaterNeuralNet = SchillerNeuralNetWrapper.create(isWater);
            merisAllNeuralNet = SchillerNeuralNetWrapper.create(isAll);
            merisAatsrOuterNeuralNet = SchillerNeuralNetWrapper.create(isMerisAatsrOuter);
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

        if (outputSchillerNNValue && applyMERISSchillerNN) {
            nnOutputBand = targetProduct.addBand(OccciConstants.SCHILLER_NN_OUTPUT_BAND_NAME,
                                                 ProductData.TYPE_FLOAT32);
        }

//        if (useSchillerMerisAatsrNN) {
//            ProductUtils.copyFlagBands(l1bProduct, targetProduct, true);
            meris1600Band = targetProduct.addBand("meris_1600", ProductData.TYPE_FLOAT32);
            merisAatsrCloudProbBand = targetProduct.addBand("meris_aatsr_cloud_prob", ProductData.TYPE_FLOAT32);
//        }
    }

    private SourceData loadSourceTiles(Rectangle rectangle) throws OperatorException {

        SourceData sd = new SourceData();
        sd.rhoToa = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS][0];
        sd.radiance = new Tile[6];

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            sd.rhoToa[i] = (float[]) getSourceTile(
                    rhoToaProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1)),
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

        sd.ecmwfPressure = (float[]) getSourceTile(l1bProduct.getTiePointGrid("atm_press"),
                                                   rectangle).getRawSamples().getElems();

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

            final Tile ctpTile = getSourceTile(ctpBand, sourceRectangle);
            final Tile liseP1Tile = getSourceTile(liseP1Band, sourceRectangle);
            final Tile lisePScattTile = getSourceTile(lisePScattBand, sourceRectangle);
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
                            pixelInfo.ecmwfPressure = sd.ecmwfPressure[i];
                            pixelInfo.p1Pressure = liseP1Tile.getSampleFloat(x, y);
                            pixelInfo.pscattPressure = lisePScattTile.getSampleFloat(x, y);
                            pixelInfo.ctp = ctpTile.getSampleFloat(x, y);

                            if (band == cloudFlagBand) {
                                classifyCloud(sd, pixelInfo, targetTile, waterFraction);
                            }
                            if (outputSchillerNNValue && applyMERISSchillerNN && band == nnOutputBand) {
                                final double[] nnOutput = getMerisNNOutput(sd, pixelInfo);
                                targetTile.setSample(pixelInfo.x, pixelInfo.y, nnOutput[0]);
                            }
                            if (useSchillerMerisAatsrNN) {
                                final double[] nnOutput = getMerisNNOutput(sd, pixelInfo);
                                if (band == meris1600Band) {
                                    targetTile.setSample(pixelInfo.x, pixelInfo.y, nnOutput[0]);
                                }
                                if (band == merisAatsrCloudProbBand) {
                                    targetTile.setSample(pixelInfo.x, pixelInfo.y, nnOutput[1]);
                                }
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
        final boolean[] resultFlags = new boolean[6];

        // Compute slopes- step 2.1.7
        spec_slopes(sd, pixelInfo, resultFlags, false);
        final boolean bright_f = resultFlags[0];

        // table-driven classification- step 2.1.8
        // DPM #2.1.8-1
        final boolean isCoastline = isCoastlinePixel(pixelInfo, waterFraction);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_COASTLINE, isCoastline);

        final boolean high_mdsi = resultFlags[4];
        final boolean bright_rc = resultFlags[5];    // bright_1

        boolean is_snow_ice = false;
        boolean is_glint_risk = !isCoastline && isGlintRisk(sd, pixelInfo);
        boolean checkForSeaIce = false;
        if (!isCoastline) {
            // over water
            final GeoPos geoPos = getGeoPos(pixelInfo);
            checkForSeaIce = ignoreSeaIceClimatology || isPixelClassifiedAsSeaice(geoPos);
            if (checkForSeaIce) {
                is_snow_ice = bright_rc && high_mdsi;
            }

            // glint makes sense only if we have no sea ice
            is_glint_risk = is_glint_risk && !isPixelClassifiedAsSeaice(geoPos);

        } else {
            // over land
            is_snow_ice = (high_mdsi && bright_f);
        }

        double sureThresh = cloudScreeningSure;
        // this seems to avoid false cloud flagging in glint regions:
        if (is_glint_risk) {
            sureThresh += glintCloudThresholdAddition;
        }

        boolean isCloudSure = false;
        boolean isCloudAmbiguous;

        if (applyMERISSchillerNN) {
            double[] nnOutput = getMerisNNOutput(sd, pixelInfo);
            if (!targetTile.getSampleBit(pixelInfo.x, pixelInfo.y, IdepixConstants.F_INVALID)) {
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, false);
                targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, false);
                isCloudAmbiguous = nnOutput[0] > schillerNNCloudAmbiguousLowerBoundaryValue &&
                        nnOutput[0] <= schillerNNCloudAmbiguousSureSeparationValue;
                if (isCloudAmbiguous) {
                    // this would be as 'CLOUD_AMBIGUOUS'...
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, true);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, true);
                }
                // check for snow_ice separation below if needed, first set all to cloud
                isCloudSure = nnOutput[0] > schillerNNCloudAmbiguousSureSeparationValue;
                if (isCloudSure) {
                    // this would be as 'CLOUD_SURE'...
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, true);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, true);
                }

                is_snow_ice = false;
                if (checkForSeaIce) {
                    if (useSchillerMerisAatsrNN) {
                        // new approach using 'meris1600' obtained from MERIS/AATSR-trained NN:
                        final double reflMeris1600 = nnOutput[0];
                        final double merisAatsrCloudProb = nnOutput[1];
                        is_snow_ice = reflMeris1600 < schillerMeris1600Threshold &&
                                merisAatsrCloudProb > schillerMerisAatsrCloudIceSeparationValue;
                    } else {
                        is_snow_ice = nnOutput[0] > schillerNNCloudSureSnowSeparationValue; // old
                    }


                }
                if (is_snow_ice) {
                    // this would be as 'SNOW/ICE'...
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, true);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, false);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, false);
                }

                if (!applyMERISSchillerNNPure && !isCloudSure && !isCloudAmbiguous) {
                    if (useSchillerMerisAatsrNN) {
                        // todo
                    } else {
                        final float cloudProbValue = computeCloudProbabilityValue(landWaterNN, sd, pixelInfo);
                        isCloudSure = cloudProbValue > cloudScreeningAmbiguous;
                        // special case: set very bright clouds misclassified as snow_ice from NN but
                        // outside seaice climatology range to cloud
                        if (!checkForSeaIce && nnOutput[0] > schillerNNCloudSureSnowSeparationValue) {
                            isCloudSure = true;
                        }
                        isCloudAmbiguous = !isCloudSure && cloudProbValue > cloudScreeningAmbiguous && cloudProbValue < sureThresh;
                    }

                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, isCloudSure);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, isCloudAmbiguous);
                    targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, isCloudAmbiguous || isCloudSure);
                }
            }
        } else {
            final float cloudProbValue = computeCloudProbabilityValue(landWaterNN, sd, pixelInfo);
            isCloudSure = cloudProbValue > cloudScreeningAmbiguous;
            isCloudAmbiguous = cloudProbValue > cloudScreeningAmbiguous && cloudProbValue < sureThresh;

            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_SURE, isCloudSure);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD_AMBIGUOUS, isCloudAmbiguous);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_CLOUD, isCloudAmbiguous || isCloudSure);
            targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_SNOW_ICE, is_snow_ice && !isCloudSure);
        }

        targetTile.setSample(pixelInfo.x, pixelInfo.y, OccciConstants.F_GLINT_RISK, is_glint_risk && !isCloudSure);
    }

    private double[] getMerisNNOutput(SourceData sd, PixelInfo pixelInfo) {
//        if (useMERISSchillerAllNN) {
//            return getMerisNNOutputImpl(sd, pixelInfo, merisAllNeuralNet.get());
//        } else {
//            return getMerisNNOutputImpl(sd, pixelInfo, merisWaterNeuralNet.get());
//        }
        if (useSchillerMerisAatsrNN) {
            if (sd.vza[pixelInfo.index] > 7.0f) {
                return getMerisNNOutputImpl(sd, pixelInfo, merisAatsrOuterNeuralNet.get());
            } else {
                return getMerisNNOutputImpl(sd, pixelInfo, merisAatsrInnerNeuralNet.get());
            }
        } else {
            return getMerisNNOutputImpl(sd, pixelInfo, merisAllNeuralNet.get());
        }
    }

    private double[] getMerisNNOutputImpl(SourceData sd, PixelInfo pixelInfo, SchillerNeuralNetWrapper nnWrapper) {
        double[] nnInput = nnWrapper.getInputVector();
        for (int i = 0; i < nnInput.length; i++) {
            nnInput[i] = Math.sqrt(sd.rhoToa[i][pixelInfo.index]);
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
        double p1Scaled = 1.0 - pixelInfo.p1Pressure / 1000.0;
        boolean is_glint = p1Scaled < CC_P1_SCALED_THRESHOLD;

        final float rhoGlint = (float) computeRhoGlint(sd, pixelInfo);
        final boolean is_glint_2 =
                (rhoGlint >= CC_GLINT_THRESHOLD * sd.rhoToa[Constants.bb865][pixelInfo.index]);

        return is_glint && is_glint_2;
    }


    private float computeCloudProbabilityValue(SchillerAlgorithm schillerAlgorithm, final SourceData sd, final PixelInfo pixelInfo) {
        return schillerAlgorithm.compute(new SchillerAlgorithm.Accessor() {
            @Override
            public double get(int index) {
                return sd.rhoToa[index][pixelInfo.index];
            }
        });
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

    private double calcScatteringAngle(SourceData dc, PixelInfo pixelInfo) {
        final double sins = dc.sins[pixelInfo.index];
        final double sinv = dc.sinv[pixelInfo.index];
        final double coss = dc.coss[pixelInfo.index];
        final double cosv = dc.cosv[pixelInfo.index];

        // delta azimuth in degree
        final double deltaAzimuth = dc.deltaAzimuth[pixelInfo.index];

        // Compute the geometric conditions
        final double cosphi = Math.cos(deltaAzimuth * MathUtils.DTOR);

        // scattering angle in degree
        return MathUtils.RTOD * Math.acos(-coss * cosv - sins * sinv * cosphi);
    }

    private double calcRhoToa442ThresholdTerm(SourceData dc, PixelInfo pixelInfo) {
        final double thetaScatt = calcScatteringAngle(dc, pixelInfo) * MathUtils.DTOR;
        double cosThetaScatt = Math.cos(thetaScatt);
        return CC_RHO_TOA_442_THRESHOLD + CC_DELTA_RHO_TOA_442_THRESHOLD *
                cosThetaScatt * cosThetaScatt;
    }

    /**
     * Computes the slope of Rayleigh-corrected reflectance.
     *
     * @param pixelInfo    the pixel structure
     * @param result_flags the return values, <code>resultFlags[0]</code> contains low NN pressure flag (low_P_nn),
     *                     <code>resultFlags[1]</code> contains low polynomial pressure flag (low_P_poly),
     *                     <code>resultFlags[2]</code> contains pressure range flag (delta_p).
     * @param isLand       land/water flag
     */
    private void spec_slopes(SourceData dc, PixelInfo pixelInfo, boolean[] result_flags, boolean isLand) {
        final double deltaAzimuth = dc.deltaAzimuth[pixelInfo.index];

        //Rayleigh phase function coefficients, PR in DPM
        final double[] phaseR = new double[Constants.RAYSCATT_NUM_SER];
        //Rayleigh optical thickness, tauR0 in DPM
        final double[] tauR = new double[Constants.L1_BAND_NUM];
        //Rayleigh correction
        final double[] rhoRay = new double[Constants.L1_BAND_NUM];

        final double sins = dc.sins[pixelInfo.index];
        final double sinv = dc.sinv[pixelInfo.index];
        final double coss = dc.coss[pixelInfo.index];
        final double cosv = dc.cosv[pixelInfo.index];

        /* Rayleigh phase function Fourier decomposition */
        rayleighCorrection.phase_rayleigh(coss, cosv, sins, sinv, phaseR);

        double press = pixelInfo.ecmwfPressure; /* DPM #2.1.7-1 v1.1 */

        /* Rayleigh optical thickness */
        rayleighCorrection.tau_rayleigh(press, tauR); /* DPM #2.1.7-2 */

        /* Rayleigh reflectance - DPM #2.1.7-3 - v1.3 */
        rayleighCorrection.ref_rayleigh(deltaAzimuth, dc.sza[pixelInfo.index], dc.vza[pixelInfo.index],
                                        coss, cosv, pixelInfo.airMass, phaseR, tauR, rhoRay);
        /* DPM #2.1.7-4 */
        double[] rhoAg = new double[Constants.L1_BAND_NUM];
        for (int band = Constants.bb412; band <= Constants.bb900; band++) {
            rhoAg[band] = (dc.rhoToa[band][pixelInfo.index] - rhoRay[band]);
        }

        /* Interpolate threshold on rayleigh corrected reflectance - DPM #2.1.7-9 */
        double rhorc_442_thr = pixelId.getRhoRC442thr(dc.sza[pixelInfo.index], dc.vza[pixelInfo.index], deltaAzimuth, isLand);


        /* Derive bright flag by reflectance comparison to threshold - DPM #2.1.7-10 */
        boolean bright_f;

        /* Spectral slope processor.brr 1 */
        boolean slope1_f = pixelId.isSpectraSlope1Flag(rhoAg, dc.radiance[BAND_SLOPE_N_1].getSampleFloat(pixelInfo.x, pixelInfo.y));
        /* Spectral slope processor.brr 2 */
        boolean slope2_f = pixelId.isSpectraSlope2Flag(rhoAg, dc.radiance[BAND_SLOPE_N_2].getSampleFloat(pixelInfo.x, pixelInfo.y));

        boolean bright_toa_f = false;
        boolean bright_rc = (rhoAg[auxData.band_bright_n] > rhorc_442_thr)
                || isSaturated(dc, pixelInfo.x, pixelInfo.y, BAND_BRIGHT_N, auxData.band_bright_n);
        if (isLand) {   /* land pixel */
            bright_f = bright_rc && slope1_f && slope2_f;
        } else {
            final double rhoThreshOffsetTerm = calcRhoToa442ThresholdTerm(dc, pixelInfo);
            final double ndvi = (rhoAg[Constants.bb10] - rhoAg[Constants.bb7]) / (rhoAg[Constants.bb10] + rhoAg[Constants.bb7]);
            bright_toa_f = (rhoAg[CC_RHO_AG_REFERENCE_WAVELENGTH_INDEX] > rhoThreshOffsetTerm) &&
                    ndvi > CC_NDVI_THRESHOLD;
            bright_f = bright_rc || bright_toa_f;
        }

        final float mdsi = computeMdsi(dc.rhoToa[Constants.bb865][pixelInfo.index], dc.rhoToa[Constants.bb890][pixelInfo.index]);
        boolean high_mdsi = (mdsi > CC_MDSI_THRESHOLD);

        result_flags[0] = bright_f;
        result_flags[1] = slope1_f;
        result_flags[2] = slope2_f;
        result_flags[3] = bright_toa_f;
        result_flags[4] = high_mdsi;
        result_flags[5] = bright_rc;
    }

    private float computeMdsi(float rhoToa865, float rhoToa885) {
        return (rhoToa865 - rhoToa885) / (rhoToa865 + rhoToa885);
    }

    private boolean isSaturated(SourceData sd, int x, int y, int radianceBandId, int bandId) {
        return sd.radiance[radianceBandId].getSampleFloat(x, y) > auxData.Saturation_L[bandId];
    }

    private static class SourceData {

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
        private float[] ecmwfPressure;
        private Tile l1Flags;
    }

    private static class PixelInfo {

        int index;
        int x;
        int y;
        double airMass;
        float ecmwfPressure;
        float p1Pressure;
        float pscattPressure;
        float ctp;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(OccciMerisClassificationOp.class);
        }
    }

}
