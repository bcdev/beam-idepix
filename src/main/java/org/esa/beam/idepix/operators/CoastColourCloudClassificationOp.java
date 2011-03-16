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
package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrection;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataException;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.MathUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;

import static org.esa.beam.meris.l2auxdata.Constants.*;


/**
 * This class provides the Mepix QWG cloud classification.
 */
@OperatorMetadata(alias = "Meris.CoastColourCloudClassification",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zühlke, Olaf Danne",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "MERIS L2 cloud classification (version from MEPIX processor).")
public class CoastColourCloudClassificationOp extends MerisBasisOp {

    public static final String CLOUD_FLAGS = "cloud_classif_flags";
    public static final String PRESSURE_CTP = "cloud_top_press";
    public static final String PRESSURE_SURFACE = "surface_press";
    public static final String SCATT_ANGLE = "scattering_angle";
    public static final String RHO_THRESH_TERM = "rho442_thresh_term";
    public static final String RHO_GLINT = "rho_glint";
    public static final String RHO_AG = "rho_ag";
    public static final String MDSI = "mdsi";

    private static final int BAND_BRIGHT_N = 0;
    private static final int BAND_SLOPE_N_1 = 1;
    private static final int BAND_SLOPE_N_2 = 2;

    public static final int F_CLOUD = 0;
    public static final int F_BRIGHT = 1;
    public static final int F_BRIGHT_RC = 2;
    public static final int F_LOW_P_PSCATT = 3;
    public static final int F_LOW_P_P1 = 4;
    public static final int F_SLOPE_1 = 5;
    public static final int F_SLOPE_2 = 6;
    public static final int F_BRIGHT_TOA = 7;
    public static final int F_HIGH_MDSI = 8;
    public static final int F_SNOW_ICE = 9;

    private L2AuxData auxData;

    private RayleighCorrection rayleighCorrection;

    HashMap<Integer, Integer> merisWavelengthIndexMap;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;
    @SourceProduct(alias = "pressureOutputLise")
    private Product lisePressureProduct;
    @SourceProduct(alias = "pressureBaro")
    private Product pbaroProduct;

    @SuppressWarnings({"FieldCanBeLocal"})
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "If 'true' the algorithm will compute L2 Pressures.", defaultValue = "true")
    private boolean l2Pressures;
    // todo (mp 2010/10/04)- parameter is never used
    @Parameter(description = "If 'true' the algorithm will compute L2 Cloud detection flags.", defaultValue = "true")
    private boolean l2CloudDetection;
    @Parameter(label = "L2 Cloud Detection Flags with LISE 'PScatt'", defaultValue = "false")
    private boolean pressureOutputL2CloudDetectionLisePScatt;
    @Parameter(description = "User Defined P1 Pressure Threshold.", defaultValue = "125.0")
    private double userDefinedP1PressureThreshold;
    @Parameter(description = "User Defined PScatt Pressure Threshold.", defaultValue = "700.0")
    private double userDefinedPScattPressureThreshold;
    @Parameter(description = "User Defined RhoTOA442 Threshold.", defaultValue = "0.185")
    private double userDefinedRhoToa442Threshold;

    @Parameter(description = "User Defined Delta RhoTOA442 Threshold.", defaultValue = "0.03")
    private double userDefinedDeltaRhoToa442Threshold;
    @Parameter(description="User Defined Glint Threshold.", defaultValue="0.015")
    public double userDefinedGlintThreshold;

    @Parameter(description = " Rho AG Reference Wavelength [nm]", defaultValue = "442",
               valueSet = {"412", "442", "490", "510", "560", "620", "665", "681", "705", "753", "760", "775", "865", "890", "900"})
    private int rhoAgReferenceWavelength;

    @Parameter(description = "User Defined RhoTOA753 Threshold.", defaultValue = "0.1")
    private double userDefinedRhoToa753Threshold;
    @Parameter(description = "User Defined RhoTOA Ratio 753/775 Threshold.", defaultValue = "0.15")
    private double userDefinedRhoToaRatio753775Threshold;
    @Parameter(description = "User Defined MDSI Threshold.", defaultValue = "0.01")
    private double userDefinedMDSIThreshold;
    @Parameter(description = "User Defined NDVI Threshold.", defaultValue = "0.1")
    private double userDefinedNDVIThreshold;


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }
        rayleighCorrection = new RayleighCorrection(auxData);
        createTargetProduct();

        if (merisWavelengthIndexMap == null) {
            merisWavelengthIndexMap = IdepixUtils.setupMerisWavelengthIndexMap();
        }
    }

    private void createTargetProduct() {
        targetProduct = createCompatibleProduct(l1bProduct, "MER", "MER_L2");

        Band cloudFlagBand = targetProduct.addBand(CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        targetProduct.addBand(PRESSURE_CTP, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(PRESSURE_SURFACE, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(SCATT_ANGLE, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(RHO_THRESH_TERM, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(RHO_GLINT, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(RHO_AG + "_" + rhoAgReferenceWavelength, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(MDSI, ProductData.TYPE_FLOAT32);
    }

    public static FlagCoding createFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, F_CLOUD), null);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, F_BRIGHT), null);
        flagCoding.addFlag("F_BRIGHT_RC", BitSetter.setFlag(0, F_BRIGHT_RC), null);
        flagCoding.addFlag("F_LOW_P_PSCATT", BitSetter.setFlag(0, F_LOW_P_PSCATT), null);
        flagCoding.addFlag("F_LOW_P_P1", BitSetter.setFlag(0, F_LOW_P_P1), null);
        flagCoding.addFlag("F_SLOPE_1", BitSetter.setFlag(0, F_SLOPE_1), null);
        flagCoding.addFlag("F_SLOPE_2", BitSetter.setFlag(0, F_SLOPE_2), null);
        flagCoding.addFlag("F_BRIGHT_TOA", BitSetter.setFlag(0, F_BRIGHT_TOA), null);
        flagCoding.addFlag("F_HIGH_MDSI", BitSetter.setFlag(0, F_HIGH_MDSI), null);
        flagCoding.addFlag("F_SNOW_ICE", BitSetter.setFlag(0, F_SNOW_ICE), null);
        return flagCoding;
    }

    private static Mask[] createBitmaskDefs(Product sourceProduct) {

        Mask[] bitmaskDefs = new Mask[10];

        int w = sourceProduct.getSceneRasterWidth();
        int h = sourceProduct.getSceneRasterHeight();

        bitmaskDefs[0] = Mask.BandMathsType.create("f_cloud", "IDEPIX final cloud flag", w, h, CLOUD_FLAGS + ".F_CLOUD",
                                                   Color.CYAN, 0.5f);
        bitmaskDefs[1] = Mask.BandMathsType.create("f_bright", "IDEPIX combined of old and second bright test", w, h,
                                                   CLOUD_FLAGS + ".F_BRIGHT", new Color(0, 153, 153), 0.5f);
        bitmaskDefs[2] = Mask.BandMathsType.create("f_bright_rc", "IDEPIX old bright test", w, h,
                                                   CLOUD_FLAGS + ".F_BRIGHT_RC", new Color(204, 255, 204), 0.5f);
        bitmaskDefs[3] = Mask.BandMathsType.create("f_low_p_pscatt", "IDEPIX test on apparent scattering (over ocean)",
                                                   w, h, CLOUD_FLAGS + ".F_LOW_P_PSCATT", new Color(153, 153, 0), 0.5f);
        bitmaskDefs[4] = Mask.BandMathsType.create("f_low_p_p1", "IDEPIX test on P1 (over land)", w, h,
                                                   CLOUD_FLAGS + ".F_LOW_P_P1", Color.GRAY, 0.5f);
        bitmaskDefs[5] = Mask.BandMathsType.create("f_slope_1", "IDEPIX old slope 1 test", w, h,
                                                   CLOUD_FLAGS + ".F_SLOPE_1", Color.PINK, 0.5f);
        bitmaskDefs[6] = Mask.BandMathsType.create("f_slope_2", "IDEPIX old slope 2 test", w, h,
                                                   CLOUD_FLAGS + ".F_SLOPE_2", new Color(153, 0, 153), 0.5f);
        bitmaskDefs[7] = Mask.BandMathsType.create("f_bright_toa", "IDEPIX second bright test", w, h,
                                                   CLOUD_FLAGS + ".F_BRIGHT_TOA", Color.LIGHT_GRAY, 0.5f);
        bitmaskDefs[8] = Mask.BandMathsType.create("f_high_mdsi",
                                                   "IDEPIX MDSI above threshold (warning: not sufficient for snow detection)",
                                                   w, h, CLOUD_FLAGS + ".F_HIGH_MDSI", Color.blue, 0.5f);
        bitmaskDefs[9] = Mask.BandMathsType.create("f_snow_ice", "IDEPIX snow/ice flag", w, h,
                                                   CLOUD_FLAGS + ".F_SNOW_ICE", Color.DARK_GRAY, 0.5f);

        return bitmaskDefs;
    }


    private SourceData loadSourceTiles(Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        SourceData sd = new SourceData();
        sd.rhoToa = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS][0];
        sd.radiance = new Tile[3];

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
        sd.altitude = (float[]) getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME),
                                              rectangle).getRawSamples().getElems();
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

        Rectangle rectangle = targetTile.getRectangle();
        try {
            SourceData sd = loadSourceTiles(rectangle, pm);

            Tile ctpTile = getSourceTile(ctpProduct.getBand("cloud_top_press"), rectangle);
            Tile pbaroTile = getSourceTile(pbaroProduct.getBand(BarometricPressureOp.PRESSURE_BAROMETRIC), rectangle);
            Tile liseP1Tile = getSourceTile(lisePressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1), rectangle);
            Tile lisePScattTile = getSourceTile(lisePressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT),
                                                rectangle);

            PixelInfo pixelInfo = new PixelInfo();
            int i = 0;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                pixelInfo.y = y;
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    if (!sd.l1Flags.getSampleBit(x, y, L1_F_INVALID)) {
                        pixelInfo.x = x;
                        pixelInfo.index = i;
                        pixelInfo.airMass = HelperFunctions.calculateAirMass(sd.vza[i], sd.sza[i]);
                        if (sd.l1Flags.getSampleBit(x, y, L1_F_LAND)) {
                            // ECMWF pressure is only corrected for positive
                            // altitudes and only for land pixels
                            pixelInfo.ecmwfPressure = HelperFunctions.correctEcmwfPressure(sd.ecmwfPressure[i],
                                                                                           sd.altitude[i],
                                                                                           auxData.press_scale_height);
                        } else {
                            pixelInfo.ecmwfPressure = sd.ecmwfPressure[i];
                        }
                        pixelInfo.pbaroPressure = pbaroTile.getSampleFloat(x, y);
                        pixelInfo.p1Pressure = liseP1Tile.getSampleFloat(x, y);
                        pixelInfo.pscattPressure = lisePScattTile.getSampleFloat(x, y);
                        pixelInfo.ctp = ctpTile.getSampleFloat(x, y);
                        if (band.getName().equals(CLOUD_FLAGS)) {
                            classifyCloud(sd, pixelInfo, targetTile);
                        }
                        if (band.getName().equals(PRESSURE_SURFACE) && l2Pressures) {
                            setCloudPressureSurface(sd, pixelInfo, targetTile);
                        }
                        if (band.getName().equals(PRESSURE_CTP) && l2Pressures) {
                            setCloudTopPressure(pixelInfo, targetTile);
                        }

                        if (band.getName().equals(SCATT_ANGLE)) {
                            final double thetaScatt = calcScatteringAngle(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, thetaScatt);
                        }
                        if (band.getName().equals(RHO_THRESH_TERM)) {
                            final double rhoThreshOffsetTerm = calcRhoToa442ThresholdTerm(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, rhoThreshOffsetTerm);
                        }

                        if (band.getName().equals(MDSI)) {
                            setMdsi(sd, pixelInfo, targetTile);
                        }

                        if (band.getName().equals(RHO_GLINT)) {
                            final double rhoGlint = computeRhoGlint(sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, rhoGlint);
                        }

                        if (band.getName().equals(RHO_AG + "_" + rhoAgReferenceWavelength)) {
                            final Integer wavelengthIndex = merisWavelengthIndexMap.get(rhoAgReferenceWavelength);
                            final double rhoAg = computeRhoAg(wavelengthIndex, sd, pixelInfo);
                            targetTile.setSample(pixelInfo.x, pixelInfo.y, rhoAg);
                        }
                    }
                    i++;
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    public void setCloudPressureSurface(SourceData sd, PixelInfo pixelInfo, Tile targetTile) {
        final ReturnValue press = new ReturnValue();

        Comp_Pressure(sd, pixelInfo, press);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, Math.max(0.0, press.value));
    }

    public void setCloudTopPressure(PixelInfo pixelInfo, Tile targetTile) {
        targetTile.setSample(pixelInfo.x, pixelInfo.y, pixelInfo.ctp);
    }

    public void classifyCloud(SourceData sd, PixelInfo pixelInfo, Tile targetTile) {
        final ReturnValue press = new ReturnValue();
        float inputPressure;
        if (pressureOutputL2CloudDetectionLisePScatt) {
            inputPressure = pixelInfo.pscattPressure;
        } else {
            inputPressure = pixelInfo.ctp;
        }

        final boolean[] resultFlags = new boolean[6];

        Comp_Pressure(sd, pixelInfo, press);

        /* apply thresholds on pressure- step 2.1.2 */
        press_thresh(sd, pixelInfo, press.value, inputPressure, resultFlags);

        // Compute slopes- step 2.1.7
        spec_slopes(sd, pixelInfo, resultFlags);
        boolean bright_f = resultFlags[0];
        boolean slope_1_f = resultFlags[1];
        boolean slope_2_f = resultFlags[2];
//        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_BRIGHT, bright_f);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SLOPE_1, slope_1_f);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SLOPE_2, slope_2_f);

        // table-driven classification- step 2.1.8
        // DPM #2.1.8-1
        boolean land_f = sd.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, L1_F_LAND);
        boolean is_cloud = false;
        boolean is_snow_ice = false;

        boolean bright_toa_f = resultFlags[3];  // bright_2
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_BRIGHT_TOA, bright_toa_f);
        boolean high_mdsi = resultFlags[4];
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_HIGH_MDSI, high_mdsi);
        boolean bright_rc = resultFlags[5];    // bright_1
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_BRIGHT_RC, bright_rc);

        boolean bright = bright_rc || bright_toa_f;
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_BRIGHT, bright);

        double p1Scaled = 1.0 - pixelInfo.p1Pressure / 1000.0;
        boolean is_glint = p1Scaled < 0.15;

        final float rhoGlint = (float) computeRhoGlint(sd, pixelInfo);
        final boolean is_glint_2 = (rhoGlint >= userDefinedGlintThreshold);

        boolean low_p_pscatt = (pixelInfo.pscattPressure < userDefinedPScattPressureThreshold) &&
                               (sd.rhoToa[bb753][pixelInfo.index] > userDefinedRhoToa753Threshold);
        if (!land_f) {
            // over water
            if (is_glint && is_glint_2) {
                is_snow_ice = bright_rc && high_mdsi;
                is_cloud = (bright_rc || low_p_pscatt);
            } else {
                is_snow_ice = bright_rc && high_mdsi;
                is_cloud = (bright || low_p_pscatt) && !(is_snow_ice);
            }
        } else {
            is_snow_ice = (high_mdsi && bright_f);
            is_cloud = (bright_f || low_p_pscatt) && !(is_snow_ice);
        }
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_LOW_P_PSCATT, low_p_pscatt);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_SNOW_ICE, is_snow_ice);
        targetTile.setSample(pixelInfo.x, pixelInfo.y, F_CLOUD, is_cloud);
    }

    private double computeRhoGlint(SourceData sd, PixelInfo pixelInfo) {
        final double windm = Math.sqrt(sd.windu[pixelInfo.index] * sd.windu[pixelInfo.index] +
                                 sd.windv[pixelInfo.index] * sd.windv[pixelInfo.index]);
        /* then wind azimuth */
        final double phiw = azimuth(sd.windu[pixelInfo.index], sd.windv[pixelInfo.index]);
        /* and "scattering" angle */
        final double chiw = MathUtils.RTOD * (Math.acos(Math.cos(sd.saa[pixelInfo.index] - phiw)));
        final double deltaAzimuth = HelperFunctions.computeAzimuthDifference(sd.vaa[pixelInfo.index],
                                                                       sd.saa[pixelInfo.index]);
        /* allows to retrieve Glint reflectance for wurrent geometry and wind */
        return glintRef(sd.sza[pixelInfo.index], sd.vza[pixelInfo.index], deltaAzimuth, windm, chiw);
    }

    private void setMdsi(SourceData sd, PixelInfo pixelInfo, Tile targetTile) {
        final float mdsi = computeMdsi(sd.rhoToa[bb865][pixelInfo.index], sd.rhoToa[bb890][pixelInfo.index]);
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
        double rhoGlint = Interp.interpolate(auxData.rog.getJavaArray(), rogIndex);
        return rhoGlint;
    }

    /**
     * Computes the pressure.
     * <p/>
     * <b>Input:</b> {@link org.esa.beam.meris.brr.dpm.DpmPixel#rho_toa},
     * {@link org.esa.beam.meris.brr.dpm.DpmPixel#mus},
     * {@link org.esa.beam.meris.brr.dpm.DpmPixel#muv}<br> <b>Output:</b>
     * {@link org.esa.beam.meris.brr.dpm.DpmPixel#TOAR} (exceptional)<br> <b>DPM ref.:</b> section 3.5 step
     * 2.1.4<br> <b>MEGS ref.:</b> <code>pixelid.c</code>, function <code>Comp_Pressure</code><br>
     *
     * @param pixelInfo the pixel structure
     * @param press     the resulting pressure
     */
    private void Comp_Pressure(SourceData sd, PixelInfo pixelInfo, ReturnValue press) {
        double eta; // Ratio TOAR(11)/TOAR(10)
        press.error = false;
        final FractIndex spectralShiftIndex = new FractIndex();
        final FractIndex[] cIndex = FractIndex.createArray(2);
        final ReturnValue press_1 = new ReturnValue();
        final ReturnValue press_2 = new ReturnValue();

        /* DPM #2.1.3-1 */
        /* get spectral_shift from detector id in order to use pressure polynomials */
        Interp.interpCoord(auxData.central_wavelength[bb760][sd.detectorIndex[pixelInfo.index]],
                           auxData.spectral_shift_wavelength,
                           spectralShiftIndex);

        // DPM #2.1.3-2, DPM #2.1.3-3, DPM #2.1.3-4
        // when out of bands, spectral_shift is set to 0 or PPOL_NUM_SHIFT with a null weight,
        // so it works fine with the following.

        /* reflectance ratio computation, DPM #2.1.12-2 */
        if (sd.rhoToa[bb753][pixelInfo.index] > 0.0) {
            eta = sd.rhoToa[bb760][pixelInfo.index] / sd.rhoToa[bb753][pixelInfo.index];
        } else {
            // DPM section 3.5.3
            eta = 0.0;        /* DPM #2.1.12-3 */
            press.error = true;                /* raise PCD */
        }
        // DPM #2.1.12-4
        Interp.interpCoord(pixelInfo.airMass, auxData.C.getTab(1), cIndex[0]);
        Interp.interpCoord(sd.rhoToa[bb753][pixelInfo.index], auxData.C.getTab(2), cIndex[1]);

        float[][][] c_lut = (float[][][]) auxData.C.getJavaArray();
        // coefficient used in the pressure estimation
        double C_res = Interp.interpolate(c_lut[VOLC_NONE], cIndex);

        // DPM #2.1.12-5, etha * C
        double ethaC = eta * C_res;
        // DPM #2.1.12-5a
        pressure_func(ethaC, pixelInfo.airMass, spectralShiftIndex.index, press_1);
        // DPM #2.1.12-5b
        pressure_func(ethaC, pixelInfo.airMass, spectralShiftIndex.index + 1, press_2);
        if (press_1.error) {
            press.value = press_2.value; /* corrected by LB as DPM is flawed: press_1 --> press_2 */
        } else if (press_2.error) {
            press.value = press_1.value; /* corrected by LB as DPM is flawed: press_2 --> press_1 */
        } else {
            /* DPM #2.1.12-5c */
            press.value = (1 - spectralShiftIndex.fraction) * press_1.value + spectralShiftIndex.fraction * press_2.value;
        }

        /* DPM #2.1.12-12 */
        press.error = press.error || press_1.error || press_2.error;
    }

    /**
     * Computes surface pressure from corrected ratio b11/b10.
     * <p/>
     * <b>DPM ref.:</b> section 3.5 (step 2.1.12)<br> <b>MEGS ref.:</b> <code>pixelid.c</code>, function
     * <code>pressure_func</code><br>
     *
     * @param eta_C         ratio TOAR(B11)/TOAR(B10) corrected
     * @param airMass       air mass
     * @param spectralShift
     * @param press         the resulting pressure
     */
    private void pressure_func(double eta_C,
                               double airMass,
                               int spectralShift,
                               ReturnValue press) {
        double P; /* polynomial accumulator */
        double koeff; /* powers of eta_c */
        press.error = false;
        final FractIndex polcoeffShiftIndex = new FractIndex();

        /* Interpoate polcoeff from spectral shift dependent table - DPM #2.1.16-1 */
        Interp.interpCoord(spectralShift, auxData.polcoeff.getTab(0), polcoeffShiftIndex);
        /* nearest neighbour interpolation */
        if (polcoeffShiftIndex.fraction > 0.5) {
            polcoeffShiftIndex.index++;
        }
        float[][] polcoeff = (float[][]) auxData.polcoeff.getArray().getJavaArray();

        /* DPM #2.1.16-2 */
        P = polcoeff[polcoeffShiftIndex.index][0];
        koeff = 1.0;
        for (int i = 1; i < PPOL_NUM_ORDER; i++) {
            koeff *= eta_C;
            P += polcoeff[polcoeffShiftIndex.index][i] * koeff;
        }
        /* CHANGED v7.0: polynomial now gives log10(m*P^2) (LB 15/12/2003) */
        if ((P <= 308.0) && (P >= -308.0)) {  /* MP2 would be out of double precision range  */
            double MP2 = Math.pow(10.0, P); /* retrieved product of air mass times square of pressure */
            press.value = Math.sqrt(MP2 / airMass); /* DPM 2.1.16-3 */
            if (press.value > auxData.maxPress) {
                press.value = auxData.maxPress; /* DPM #2.1.16-5 */
                press.error = true;     /* DPM #2.1.16-4  */
            }
        } else {
            press.value = 0.0;    /* DPM #2.1.16-6 */
            press.error = true;  /* DPM #2.1.16-7 */
        }
    }

    /**
     * Compares pressure estimates with ECMWF data.
     * <p/>
     * <b>Uses:</b> {@link org.esa.beam.meris.brr.dpm.DpmPixel#l2flags}, {@link org.esa.beam.meris.brr.dpm.DpmPixel#sun_zenith}, {@link org.esa.beam.meris.brr.dpm.DpmPixel#view_zenith},
     * {@link org.esa.beam.meris.brr.dpm.DpmPixel#press_ecmwf}, {@link org.esa.beam.meris.l2auxdata.L2AuxData#DPthresh_land},
     * {@link org.esa.beam.meris.l2auxdata.L2AuxData#DPthresh_ocean},
     * {@link org.esa.beam.meris.l2auxdata.L2AuxData#press_confidence}<br> <b>Sets:</b> nothing <br> <b>DPM
     * Ref.:</b> MERIS Level 2 DPM, step 2.1 <br> <b>MEGS Ref.:</b> file pixelid.c, function press_thresh  <br>
     *
     * @param pixelInfo     the pixel structure
     * @param pressure      the pressure of the pixel
     * @param inputPressure can be either cloud top pressure from CloudTopPressureOp,
     *                      or PScatt from {@link org.esa.beam.idepix.operators.LisePressureOp} (new!), or -1 if not given
     * @param result_flags  the return values, <code>resultFlags[0]</code> contains low NN pressure flag (low_P_nn),
     *                      <code>resultFlags[1]</code> contains low polynomial pressure flag (low_P_poly),
     *                      <code>resultFlags[2]</code> contains pressure range flag (delta_p).
     */
    private void press_thresh(SourceData sd, PixelInfo pixelInfo, double pressure, float inputPressure,
                              boolean[] result_flags) {
        double delta_press_thresh; /* absolute threshold on pressure difference */
        FractIndex[] DP_Index = FractIndex.createArray(2);

        /* get proper threshold - DPM #2.1.2-2 */
        if (sd.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, L1_F_LAND)) {
            delta_press_thresh = userDefinedP1PressureThreshold;
        } else {
            delta_press_thresh = userDefinedPScattPressureThreshold;
        }

        /* test NN pressure- DPM #2.1.2-4 */ // low_P_nn
        if (inputPressure != -1) {
            result_flags[0] = (inputPressure < pixelInfo.ecmwfPressure - delta_press_thresh); //changed in V7
        } else {
            result_flags[0] = (pixelInfo.ecmwfPressure < pixelInfo.ecmwfPressure - delta_press_thresh); //changed in V7
        }
        /* test polynomial pressure- DPM #2.1.2-3 */ // low_P_poly
        result_flags[1] = (pressure < pixelInfo.ecmwfPressure - delta_press_thresh);  //changed in V7
        /* test pressure range - DPM #2.1.2-5 */   // delta_p
        result_flags[2] = (Math.abs(pixelInfo.ecmwfPressure - pressure) > auxData.press_confidence); //changed in V7
    }

    private double calcScatteringAngle(SourceData dc, PixelInfo pixelInfo) {
        double sins = Math.sin(dc.sza[pixelInfo.index] * MathUtils.DTOR);
        double sinv = Math.sin(dc.vza[pixelInfo.index] * MathUtils.DTOR);
        double coss = Math.cos(dc.sza[pixelInfo.index] * MathUtils.DTOR);
        double cosv = Math.cos(dc.vza[pixelInfo.index] * MathUtils.DTOR);
        // delta azimuth in degree
        final double deltaAzimuth = HelperFunctions.computeAzimuthDifference(dc.vaa[pixelInfo.index],
                                                                             dc.saa[pixelInfo.index]);

        // Compute the geometric conditions
        final double cosphi = Math.cos(deltaAzimuth * MathUtils.DTOR);

        // scattering angle in degree
        return MathUtils.RTOD * Math.acos(-coss * cosv - sins * sinv * cosphi);
    }

    private double calcRhoToa442ThresholdTerm(SourceData dc, PixelInfo pixelInfo) {
        final double thetaScatt = calcScatteringAngle(dc, pixelInfo) * MathUtils.DTOR;
        return userDefinedRhoToa442Threshold + userDefinedDeltaRhoToa442Threshold *
//                                             userDefinedDeltaRhoToa442ThresholdFactor *
                                               Math.cos(thetaScatt) * Math.cos(thetaScatt);
    }

    /**
     * Computes the slope of Rayleigh-corrected reflectance.
     *
     * @param pixelInfo    the pixel structure
     * @param result_flags the return values, <code>resultFlags[0]</code> contains low NN pressure flag (low_P_nn),
     *                     <code>resultFlags[1]</code> contains low polynomial pressure flag (low_P_poly),
     *                     <code>resultFlags[2]</code> contains pressure range flag (delta_p).
     */
    private void spec_slopes(SourceData dc, PixelInfo pixelInfo, boolean[] result_flags) {
        double rhorc_442_thr;   /* threshold on rayleigh corrected reflectance */

        final double deltaAzimuth = HelperFunctions.computeAzimuthDifference(dc.vaa[pixelInfo.index],
                                                                             dc.saa[pixelInfo.index]);

        /* DPM #2.1.7-4 */
        double[] rhoAg = new double[L1_BAND_NUM];
        for (int band = bb412; band <= bb900; band++) {
            rhoAg[band] = computeRhoAg(band, dc, pixelInfo);
        }

        final FractIndex[] rhoRC442index = FractIndex.createArray(3);
        /* Interpolate threshold on rayleigh corrected reflectance - DPM #2.1.7-9 */
        if (dc.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, L1_F_LAND)) {   /* land pixel */
            Interp.interpCoord(dc.sza[pixelInfo.index], auxData.Rhorc_442_land_LUT.getTab(0), rhoRC442index[0]);
            Interp.interpCoord(dc.vza[pixelInfo.index], auxData.Rhorc_442_land_LUT.getTab(1), rhoRC442index[1]);
            Interp.interpCoord(deltaAzimuth, auxData.Rhorc_442_land_LUT.getTab(2), rhoRC442index[2]);
            rhorc_442_thr = Interp.interpolate(auxData.Rhorc_442_land_LUT.getJavaArray(), rhoRC442index);
        } else {    /* water  pixel */
            Interp.interpCoord(dc.sza[pixelInfo.index], auxData.Rhorc_442_ocean_LUT.getTab(0), rhoRC442index[0]);
            Interp.interpCoord(dc.vza[pixelInfo.index], auxData.Rhorc_442_ocean_LUT.getTab(1), rhoRC442index[1]);
            Interp.interpCoord(deltaAzimuth, auxData.Rhorc_442_ocean_LUT.getTab(2), rhoRC442index[2]);
            rhorc_442_thr = Interp.interpolate(auxData.Rhorc_442_ocean_LUT.getJavaArray(), rhoRC442index);
        }
        /* END CHANGE 01 */

        /* Derive bright flag by reflectance comparison to threshold - DPM #2.1.7-10 */
        boolean slope1_f;
        boolean slope2_f;
        boolean bright_f;
        // TODO (20.12.2010) - assignment is never used
//        boolean bright_f = (rhoAg[auxData.band_bright_n] > rhorc_442_thr)
//                           || isSaturated(dc, pixelInfo.x, pixelInfo.y, BAND_BRIGHT_N, auxData.band_bright_n);

        /* Spectral slope processor.brr 1 */
        if (rhoAg[auxData.band_slope_d_1] <= 0.0) {
            /* negative reflectance exception */
            slope1_f = false; /* DPM #2.1.7-6 */
        } else {
            /* DPM #2.1.7-5 */
            double slope1 = rhoAg[auxData.band_slope_n_1] / rhoAg[auxData.band_slope_d_1];
            slope1_f = ((slope1 >= auxData.slope_1_low_thr) && (slope1 <= auxData.slope_1_high_thr))
                       || isSaturated(dc, pixelInfo.x, pixelInfo.y, BAND_SLOPE_N_1, auxData.band_slope_n_1);
        }

        /* Spectral slope processor.brr 2 */
        if (rhoAg[auxData.band_slope_d_2] <= 0.0) {
            /* negative reflectance exception */
            slope2_f = false; /* DPM #2.1.7-8 */
        } else {
            /* DPM #2.1.7-7 */
            double slope2 = rhoAg[auxData.band_slope_n_2] / rhoAg[auxData.band_slope_d_2];
            slope2_f = ((slope2 >= auxData.slope_2_low_thr) && (slope2 <= auxData.slope_2_high_thr))
                       || isSaturated(dc, pixelInfo.x, pixelInfo.y, BAND_SLOPE_N_2, auxData.band_slope_n_2);
        }

        boolean bright_toa_f = false;
        // todo implement DPM 8, new #2.1.7-10, #2.1.7-11
        boolean bright_rc = (rhoAg[auxData.band_bright_n] > rhorc_442_thr)
                            || isSaturated(dc, pixelInfo.x, pixelInfo.y, BAND_BRIGHT_N, auxData.band_bright_n);
        if (dc.l1Flags.getSampleBit(pixelInfo.x, pixelInfo.y, L1_F_LAND)) {   /* land pixel */
            bright_f = bright_rc && slope1_f && slope2_f;
        } else {
            final double rhoThreshOffsetTerm = calcRhoToa442ThresholdTerm(dc, pixelInfo);
//            bright_toa_f = (dc.rhoToa[bb442][pixelInfo.index] > rhoThreshOffsetTerm);
//            bright_toa_f = (rhoAg[bb442] > rhoThreshOffsetTerm);
            final Integer wavelengthIndex = merisWavelengthIndexMap.get(rhoAgReferenceWavelength);
            final double ndvi = (rhoAg[bb10] - rhoAg[bb7])/(rhoAg[bb10] + rhoAg[bb7]);
            bright_toa_f = (rhoAg[wavelengthIndex] > rhoThreshOffsetTerm) &&
                            ndvi > userDefinedNDVIThreshold;
            bright_f = bright_rc || bright_toa_f;
        }

        final float mdsi = computeMdsi(dc.rhoToa[bb865][pixelInfo.index], dc.rhoToa[bb890][pixelInfo.index]);
        boolean high_mdsi = (mdsi > userDefinedMDSIThreshold);

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

    private double computeRhoAg(int band, SourceData dc, PixelInfo pixelInfo) {
        //Rayleigh phase function coefficients, PR in DPM
        final double[] phaseR = new double[RAYSCATT_NUM_SER];
        //Rayleigh optical thickness, tauR0 in DPM
        final double[] tauR = new double[L1_BAND_NUM];
        //Rayleigh correction
        final double[] rhoRay = new double[L1_BAND_NUM];

        double sins = Math.sin(dc.sza[pixelInfo.index] * MathUtils.DTOR);
        double sinv = Math.sin(dc.vza[pixelInfo.index] * MathUtils.DTOR);
        double coss = Math.cos(dc.sza[pixelInfo.index] * MathUtils.DTOR);
        double cosv = Math.cos(dc.vza[pixelInfo.index] * MathUtils.DTOR);
        final double deltaAzimuth = HelperFunctions.computeAzimuthDifference(dc.vaa[pixelInfo.index],
                                                                             dc.saa[pixelInfo.index]);

        // scattering angle
        // TODO (mp 20.12.2010) - result is never used
//        final double thetaScatt = calcScatteringAngle(dc, pixelInfo);

        /* Rayleigh phase function Fourier decomposition */
        rayleighCorrection.phase_rayleigh(coss, cosv, sins, sinv, phaseR);

        double press = pixelInfo.ecmwfPressure; /* DPM #2.1.7-1 v1.1 */

        /* Rayleigh optical thickness */
        rayleighCorrection.tau_rayleigh(press, tauR); /* DPM #2.1.7-2 */

        /* Rayleigh reflectance - DPM #2.1.7-3 - v1.3 */
        rayleighCorrection.ref_rayleigh(deltaAzimuth, dc.sza[pixelInfo.index], dc.vza[pixelInfo.index],
                                        coss, cosv, pixelInfo.airMass, phaseR, tauR, rhoRay);

        return (dc.rhoToa[band][pixelInfo.index] - rhoRay[band]);
    }

    /**
     * Table driven cloud classification decision.
     * <p/>
     * <b>DPM Ref.:</b> Level 2, Step 2.1.8 <br> <b>MEGS Ref.:</b> file classcloud.c, function class_cloud  <br>
     *
     * @param land_f
     * @param bright_f
     * @param low_P_nn
     * @param low_P_poly
     * @param delta_p
     * @param slope_1_f
     * @param slope_2_f
     * @param pcd_nn
     * @param pcd_poly
     *
     * @return <code>true</code> if cloud flag shall be set
     */
    private boolean is_cloudy(boolean land_f, boolean bright_f,
                              boolean low_P_nn, boolean low_P_poly,
                              boolean delta_p, boolean slope_1_f,
                              boolean slope_2_f, boolean pcd_nn,
                              boolean pcd_poly) {
        boolean is_cloud;
        int index = 0;

        /* set bits of index according to inputs */
        index = BitSetter.setFlag(index, CC_BRIGHT, bright_f);
        index = BitSetter.setFlag(index, CC_LOW_P_NN, low_P_nn);
        index = BitSetter.setFlag(index, CC_LOW_P_PO, low_P_poly);
        index = BitSetter.setFlag(index, CC_DELTA_P, delta_p);
        index = BitSetter.setFlag(index, CC_PCD_NN, pcd_nn);
        index = BitSetter.setFlag(index, CC_PCD_PO, pcd_poly);
        index = BitSetter.setFlag(index, CC_SLOPE_1, slope_1_f);
        index = BitSetter.setFlag(index, CC_SLOPE_2, slope_2_f);
        index &= 0xff;

        /* readRecord decision table */
        if (land_f) {
            is_cloud = auxData.land_decision_table[index]; /* DPM #2.1.8-1 */
        } else {
            is_cloud = auxData.water_decision_table[index]; /* DPM #2.1.8-2 */
        }

        return is_cloud;
    }

    private boolean isSaturated(SourceData sd, int x, int y, int radianceBandId, int bandId) {
        return sd.radiance[radianceBandId].getSampleFloat(x, y) > auxData.Saturation_L[bandId];
    }

//    public static void addBitmasks(Product product) {
//        for (BitmaskDef bitmaskDef : BITMASK_DEFINITIONS) {
//            // need a copy, cause the BitmaskDefs are otherwise disposed
//            // if the outputProduct gets disposed after processing
//            product.addBitmaskDef(bitmaskDef.createCopy());
//        }
//    }

    public static void addBitmasks(Product sourceProduct, Product targetProduct) {
        Mask[] bitmaskDefs = createBitmaskDefs(sourceProduct);

        int index = 0;
        for (Mask bitmaskDef : bitmaskDefs) {
            targetProduct.getMaskGroup().add(index++, bitmaskDef);
        }
    }


    private static class SourceData {

        private float[][] rhoToa;
//        private float[][] brr;
        private Tile[] radiance;
        private short[] detectorIndex;
        private float[] sza;
        private float[] vza;
        private float[] saa;
        private float[] vaa;
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
        float pbaroPressure;
        float p1Pressure;
        float pscattPressure;
        float ctp;
    }

    private static class ReturnValue {

        public double value;
        public boolean error;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourCloudClassificationOp.class);
        }
    }
}
