/*
 * $Id: CloudClassificationOp.java,v 1.1 2007/03/27 12:51:41 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.idepix.algorithms.coastcolour;

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
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.operators.MerisClassificationOp;
import org.esa.beam.idepix.seaice.SeaIceClassification;
import org.esa.beam.idepix.seaice.SeaIceClassifier;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrection;
import org.esa.beam.meris.dpm.PixelId;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataException;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.io.IOException;
import java.util.Calendar;


/**
 * This class provides the Mepix QWG cloud classification.
 */
@OperatorMetadata(alias = "idepix.coastcolour.classification",
        version = "2.1-SNAPSHOT",
        internal = true,
        authors = "Marco Zühlke, Olaf Danne",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "MERIS L2 cloud classification (version from MEPIX processor).")
public class CoastColourClassificationOp extends MerisBasisOp {

    public static final String CLOUD_FLAGS = MerisClassificationOp.CLOUD_FLAGS;
    public static final String PRESSURE_CTP = MerisClassificationOp.PRESSURE_CTP;
    public static final String PRESSURE_SURFACE = MerisClassificationOp.PRESSURE_SURFACE;
    public static final String SCATT_ANGLE = MerisClassificationOp.SCATT_ANGLE;
    public static final String RHO_THRESH_TERM = MerisClassificationOp.RHO_THRESH_TERM;
    public static final String MDSI = MerisClassificationOp.MDSI;
    public static final String CLOUD_PROBABILITY_VALUE = MerisClassificationOp.CLOUD_PROBABILITY_VALUE;

    public static final int F_LAND = 13;
    public static final int F_COASTLINE = 14;
    public static final int F_CLOUD = 0;
    public static final int F_CLOUD_AMBIGUOUS = 4;
    public static final int F_CLOUD_BUFFER = 11;
    public static final int F_CLOUD_SHADOW = 12;
    public static final int F_SNOW_ICE = 9;
    public static final int F_GLINTRISK = 10;
    public static final int F_MIXED_PIXEL = 15;

    private static final int BAND_BRIGHT_N = MerisClassificationOp.BAND_BRIGHT_N;
    private static final int BAND_SLOPE_N_1 = MerisClassificationOp.BAND_SLOPE_N_1;
    private static final int BAND_SLOPE_N_2 = MerisClassificationOp.BAND_SLOPE_N_2;

    private static final int BAND_FLH_7 = MerisClassificationOp.BAND_FLH_7;
    private static final int BAND_FLH_8 = MerisClassificationOp.BAND_FLH_8;
    private static final int BAND_FLH_9 = MerisClassificationOp.BAND_FLH_9;

    private static final double CC_RHO_TOA_442_THRESHOLD = 0.03;
    private static final double CC_DELTA_RHO_TOA_442_THRESHOLD = 0.03;
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
    private Band ctpOutputBand;
    private Band psurfOutputBand;
    private Band scattAngleOutputBand;
    private Band rhoThreshOutputBand;
    private Band seaIceClimatologyOutputBand;
    private Band cloudProbabilityValueOutputBand;
    private Band mdsiOutputBand;
    private SeaIceClassifier seaIceClassifier;
    private Band ctpBand;
    private Band liseP1Band;
    private Band lisePScattBand;
    private Band landWaterBand;


    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;
    @SourceProduct(alias = "pressureOutputLise")
    private Product lisePressureProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @SuppressWarnings({"FieldCanBeLocal"})
    @TargetProduct
    private Product targetProduct;

    @Parameter(label = " Sea Ice Climatology Value", defaultValue = "false")
    private boolean ccOutputSeaIceClimatologyValue;

    @Parameter(label = "Cloud screening 'ambiguous' threshold", defaultValue = "1.4")
    private double cloudScreeningAmbiguous;     // Schiller

    @Parameter(label = "Cloud screening 'sure' threshold", defaultValue = "1.8")
    private double cloudScreeningSure;         // Schiller

    @Parameter(label = " Cloud Probability Feature Value", defaultValue = "true")
    private boolean ccOutputCloudProbabilityFeatureValue;    // Schiller


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }
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

        cloudFlagBand = targetProduct.addBand(CLOUD_FLAGS, ProductData.TYPE_INT32);
        FlagCoding flagCoding = createFlagCoding(CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        ctpOutputBand = targetProduct.addBand(PRESSURE_CTP, ProductData.TYPE_FLOAT32);
        psurfOutputBand = targetProduct.addBand(PRESSURE_SURFACE, ProductData.TYPE_FLOAT32);
        scattAngleOutputBand = targetProduct.addBand(SCATT_ANGLE, ProductData.TYPE_FLOAT32);
        rhoThreshOutputBand = targetProduct.addBand(RHO_THRESH_TERM, ProductData.TYPE_FLOAT32);
        mdsiOutputBand = targetProduct.addBand(MDSI, ProductData.TYPE_FLOAT32);
        if (ccOutputSeaIceClimatologyValue) {
            seaIceClimatologyOutputBand = targetProduct.addBand("sea_ice_climatology_value", ProductData.TYPE_FLOAT32);
        }
        if (ccOutputCloudProbabilityFeatureValue) {
            cloudProbabilityValueOutputBand = targetProduct.addBand(CLOUD_PROBABILITY_VALUE, ProductData.TYPE_FLOAT32);
        }
    }

    public static FlagCoding createFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, F_LAND), null);
        flagCoding.addFlag("F_COASTLINE", BitSetter.setFlag(0, F_COASTLINE), null);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, F_CLOUD), null);
        flagCoding.addFlag("F_CLOUD_AMBIGUOUS", BitSetter.setFlag(0, F_CLOUD_AMBIGUOUS), null);
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, F_CLOUD_BUFFER), null);
        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, F_CLOUD_SHADOW), null);
        flagCoding.addFlag("F_SNOW_ICE", BitSetter.setFlag(0, F_SNOW_ICE), null);
        flagCoding.addFlag("F_GLINTRISK", BitSetter.setFlag(0, F_GLINTRISK), null);
        flagCoding.addFlag("F_MIXED_PIXEL", BitSetter.setFlag(0, F_MIXED_PIXEL), null);
        return flagCoding;
    }

    private static void createBitmaskDefs(ProductNodeGroup<Mask> maskGroup) {
        int w = maskGroup.getProduct().getSceneRasterWidth();
        int h = maskGroup.getProduct().getSceneRasterHeight();
        maskGroup.add(Mask.BandMathsType.create("cc_land", "IDEPIX CC land flag", w, h,
                CLOUD_FLAGS + ".F_LAND",
                Color.GREEN.darker(), 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_coastline", "IDEPIX CC coastline flag", w, h,
                CLOUD_FLAGS + ".F_COASTLINE",
                Color.GREEN, 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_cloud", "IDEPIX CC cloud flag", w, h,
                CLOUD_FLAGS + ".F_CLOUD",
                Color.YELLOW.darker(), 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_cloud_ambiguous", "IDEPIX CC cloud ambiguous flag", w, h,
                CLOUD_FLAGS + ".F_CLOUD_AMBIGUOUS",
                Color.YELLOW, 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_cloud_buffer", "IDEPIX CC cloud buffer flag", w, h,
                CLOUD_FLAGS + ".F_CLOUD_BUFFER",
                new Color(204, 255, 204), 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_cloud_shadow", "IDEPIX CC cloud shadow flag", w, h,
                CLOUD_FLAGS + ".F_CLOUD_SHADOW",
                Color.BLUE, 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_snow_ice", "IDEPIX CC snow/ice flag", w, h,
                CLOUD_FLAGS + ".F_SNOW_ICE", Color.CYAN, 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_glint_risk", "IDEPIX CC glint risk flag", w, h,
                CLOUD_FLAGS + ".F_GLINTRISK", Color.PINK, 0.5f));
        maskGroup.add(Mask.BandMathsType.create("cc_mixed_pixel", "IDEPIX CC mixed pixel flag", w, h,
                CLOUD_FLAGS + ".F_MIXED_PIXEL",
                Color.GREEN.darker().darker(), 0.5f));
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

        sd.detectorIndex = (short[]) getSourceTile(
                l1bProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME),
                rectangle).getRawSamples().getElems();
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
        RasterDataNode altitudeRDN;
        if (l1bProduct.getProductType().equals(EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME)) {
            altitudeRDN = l1bProduct.getBand("altitude");
        } else {
            altitudeRDN = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
        }

        sd.altitude = getSourceTile(altitudeRDN, rectangle).getSamplesFloat();

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
                        final boolean isLand;
                        final boolean isCoastline;
                        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
                        if (getGeoPos(pixelInfo).lat > -58f) {
                            final int waterFraction = waterFractionTile.getSampleInt(pixelInfo.x, pixelInfo.y);
                            // values bigger than 100 indicate no data
                            if (waterFraction <= 100) {
                                isCoastline = waterFraction < 100 && waterFraction > 0;
                                isLand = waterFraction == 0;
                            } else {
                                isCoastline = false;
                                isLand = sd.l1Flags.getSampleBit(x, y, Constants.L1_F_LAND);
                            }
                        } else {
                            isCoastline = false;
                            isLand = sd.l1Flags.getSampleBit(x, y, Constants.L1_F_LAND);
                        }

                        pixelInfo.airMass = HelperFunctions.calculateAirMass(sd.vza[i], sd.sza[i]);
                        if (isLand) {
                            // ECMWF pressure is only corrected for positive
                            // altitudes and only for land pixels
                            pixelInfo.ecmwfPressure = HelperFunctions.correctEcmwfPressure(sd.ecmwfPressure[i],
                                    sd.altitude[i],
                                    auxData.press_scale_height);
                        } else {
                            pixelInfo.ecmwfPressure = sd.ecmwfPressure[i];
                        }
                        pixelInfo.p1Pressure = liseP1Tile.getSampleFloat(x, y);
                        pixelInfo.pscattPressure = lisePScattTile.getSampleFloat(x, y);
                        pixelInfo.ctp = ctpTile.getSampleFloat(x, y);

                        if (band == cloudFlagBand) {
                            classifyCloud(sd, pixelInfo, targetTile, isLand, isCoastline);
                        }
                        if (band == psurfOutputBand) {
                            setCloudPressureSurface(sd, pixelInfo, targetTile);
                        }
                        if (band == ctpOutputBand) {
                            setCloudTopPressure(pixelInfo, targetTile);
                        }
                        if (band == scattAngleOutputBand) {
                            final double thetaScatt = calcScatteringAngle(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, thetaScatt);
                        }
                        if (band == rhoThreshOutputBand) {
                            final double rhoThreshOffsetTerm = calcRhoToa442ThresholdTerm(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, rhoThreshOffsetTerm);
                        }
                        if (band == mdsiOutputBand) {
                            setMdsi(sd, pixelInfo, targetTile);
                        }
                        if (ccOutputCloudProbabilityFeatureValue && band == cloudProbabilityValueOutputBand) {
                            final float probabilityValue = computeCloudProbabilityValue(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, probabilityValue);
                        }
                        if (ccOutputSeaIceClimatologyValue && band == seaIceClimatologyOutputBand) {
                            final float seaIceMaxValue = computeSeaiceClimatologyValue(pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, seaIceMaxValue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
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

    public void setCloudPressureSurface(SourceData sd, PixelInfo pixelInfo, Tile targetTile) {
        PixelId.Pressure press = pixelId.computePressure(sd.rhoToa[Constants.bb753][pixelInfo.index],
                sd.rhoToa[Constants.bb760][pixelInfo.index],
                pixelInfo.airMass,
                sd.detectorIndex[pixelInfo.index]);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, Math.max(0.0, press.value));
    }

    public void setCloudTopPressure(PixelInfo pixelInfo, Tile targetTile) {
        targetTile.setSample(pixelInfo.x, pixelInfo.y, pixelInfo.ctp);
    }

    public void classifyCloud(SourceData sd, PixelInfo pixelInfo, Tile targetTile, boolean isLand, boolean isCoastline) {
        final boolean[] resultFlags = new boolean[6];

        // Compute slopes- step 2.1.7
        spec_slopes(sd, pixelInfo, resultFlags, isLand);
        final boolean bright_f = resultFlags[0];
//        final boolean slope_1_f = resultFlags[1];
//        final boolean slope_2_f = resultFlags[2];
//        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_BRIGHT, bright_f);
//        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SLOPE_1, slope_1_f);
//        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SLOPE_2, slope_2_f);

        // table-driven classification- step 2.1.8
        // DPM #2.1.8-1
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_LAND, isLand || isCoastline);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_COASTLINE, isCoastline);

        final boolean high_mdsi = resultFlags[4];
        final boolean bright_rc = resultFlags[5];    // bright_1

        final boolean land_coast = isLand || isCoastline;

        boolean is_snow_ice = false;
        boolean is_glint_risk = !land_coast && isGlintRisk(sd, pixelInfo);
        if (!land_coast) {
            // over water
            final GeoPos geoPos = getGeoPos(pixelInfo);
            if (isPixelClassifiedAsSeaice(geoPos)) {
                is_snow_ice = bright_rc && high_mdsi;
            }
            // glint makes sense only if we have no sea ice
            is_glint_risk = is_glint_risk && !isPixelClassifiedAsSeaice(geoPos);

        } else {
            // over land
            is_snow_ice = (high_mdsi && bright_f);
        }

        double ambiguousThresh = cloudScreeningAmbiguous;
        double sureThresh = cloudScreeningSure;
        // this seems to avoid false cloud flagging in glint regions:
        if (is_glint_risk) {
            ambiguousThresh += 0.1;
            sureThresh += 0.1;
        }

        final float cloudProbValue = computeCloudProbabilityValue(landWaterNN, sd, pixelInfo);

        boolean isCloud = cloudProbValue > ambiguousThresh;
        boolean isCloudAmbiguous = cloudProbValue > ambiguousThresh && cloudProbValue < sureThresh;

        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_GLINTRISK, is_glint_risk && !isCloud);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_CLOUD, isCloud);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_CLOUD_AMBIGUOUS, isCloudAmbiguous);


//        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_LOW_PSCATT, low_p_pscatt);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SNOW_ICE, is_snow_ice && !isCloud);
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

    private float computeCloudProbabilityValue(SourceData sd, PixelInfo pixelInfo) {
        final boolean glintRisk = isGlintRisk(sd, pixelInfo);
        double ambiguousThresh = cloudScreeningAmbiguous;
        double sureThresh = cloudScreeningSure;
        // this seems to avoid false cloud flagging in glint regions:
        if (glintRisk) {
            ambiguousThresh += 0.1;
            sureThresh += 0.1;
        }

        float cloudProbValue = computeCloudProbabilityValue(landWaterNN, sd, pixelInfo);
        boolean isCloudAmbiguous = cloudProbValue > ambiguousThresh && cloudProbValue < sureThresh;
        if (glintRisk && isCloudAmbiguous) {
            cloudProbValue = Float.NaN;
        }

        return cloudProbValue;
    }

    private float computeSeaiceClimatologyValue(PixelInfo pixelInfo) {
        final GeoPos geoPos = getGeoPos(pixelInfo);
        geoPos.lon += 180;
        geoPos.lat = 90.0f - geoPos.lat;
        final SeaIceClassification classification = seaIceClassifier.getClassification(geoPos.lat, geoPos.lon);
        return (float) classification.max;
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

    private void setMdsi(SourceData sd, PixelInfo pixelInfo, Tile targetTile) {
        final float mdsi = computeMdsi(sd.rhoToa[Constants.bb865][pixelInfo.index], sd.rhoToa[Constants.bb890][pixelInfo.index]);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, mdsi);
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

    public static void addBitmasks(Product targetProduct) {
        createBitmaskDefs(targetProduct.getMaskGroup());
    }

    private static class SourceData {

        private float[][] rhoToa;
        private Tile[] radiance;
        private short[] detectorIndex;
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
        private float[] altitude;
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
            super(CoastColourClassificationOp.class);
        }
    }
}
