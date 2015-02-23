package org.esa.beam.idepix.algorithms.cawa;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.idepix.algorithms.globalbedo.GlobAlbedoAlgorithm;
import org.esa.beam.idepix.algorithms.globalbedo.GlobAlbedoMerisAlgorithm;
import org.esa.beam.idepix.operators.BarometricPressureOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrection;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataException;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * MERIS pixel classification operator for CAWA.
 * Only land pixels are classified, following CC algorithm there.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.cawa.classification.land",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "MERIS water pixel classification operator for CAWA.")
public class CawaLandClassificationOp extends Operator {

    // Globalbedo user options
    @Parameter(defaultValue = "true",
            label = " Apply alternative Schiller NN for MERIS cloud classification",
            description = " Apply Schiller NN for MERIS cloud classification (has only effect for MERIS L1b products)")
    boolean gaApplyMERISAlternativeSchillerNN;

    @Parameter(defaultValue = "false",
            label = " Apply alternative Schiller NN for MERIS cloud classification purely (not combined with previous approach)",
            description = " Apply Schiller NN for MERIS cloud classification purely (not combined with previous approach)")
    boolean gaApplyMERISAlternativeSchillerNNPure;

    @Parameter(defaultValue = "1.1",
            label = " Alternative Schiller NN cloud ambiguous lower boundary (MERIS only)",
            description = " Alternative Schiller NN cloud ambiguous lower boundary (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " Alternative Schiller NN cloud ambiguous/sure separation value (MERIS only)",
            description = " Alternative Schiller NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " Alternative Schiller NN cloud sure/snow separation value (MERIS only)",
            description = " Alternative Schiller NN cloud ambiguous cloud sure/snow separation value (has only effect for MERIS L1b products)")
    double gaAlternativeSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "1.1",
            label = " Schiller NN cloud ambiguous lower boundary (VGT only)",
            description = " Schiller NN cloud ambiguous lower boundary (has only effect for VGT L1b products)")
    double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " Schiller NN cloud ambiguous/sure separation value (VGT only)",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for VGT L1b products)")
    double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " Schiller NN cloud sure/snow separation value (VGT only)",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value (has only effect for VGT L1b products)")
    double gaSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "2",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    int gaCloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead",
            description = "Use land-water flag from L1b product instead")
    boolean gaUseL1bLandWaterFlag;


    @SourceProduct(alias = "gal1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "cloud", optional = true)
    private Product cloudProduct;
    @SourceProduct(alias = "rayleigh", optional = true)
    private Product rayleighProduct;
    @SourceProduct(alias = "refl", optional = true)
    private Product rad2reflProduct;
    @SourceProduct(alias = "pressure", optional = true)
    private Product pressureProduct;
    @SourceProduct(alias = "pbaro", optional = true)
    private Product pbaroProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    Band cloudFlagBand;
    Band temperatureBand;
    Band brightBand;
    Band whiteBand;
    Band brightWhiteBand;
    Band spectralFlatnessBand;
    Band ndviBand;
    Band ndsiBand;
    Band glintRiskBand;
    Band radioLandBand;
    Band radioWaterBand;

    private Band[] merisReflBands;
    private Band[] merisBrrBands;
    private Band brr442Band;
    private Band brr442ThreshBand;
    private Band p1Band;
    private Band pbaroBand;
    private Band pscattBand;
    private Band landWaterBand;

    private L2AuxData auxData;
    private RayleighCorrection rayleighCorrection;

    private SchillerAlgorithm landNN = null;

    public static final String SCHILLER_VGT_NET_NAME = "3x2x2_341.8.net";
    ThreadLocal<SchillerNeuralNetWrapper> vgtNeuralNet;

    public static final String SCHILLER_MERIS_LAND_NET_NAME = "11x8x5x3_1062.5_land.net";
    ThreadLocal<SchillerNeuralNetWrapper> merisLandNeuralNet;

    static final int MERIS_L1B_F_LAND = 4;

    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(sourceProduct);
        } catch (L2AuxDataException e) {
            throw new OperatorException("Could not load L2Auxdata", e);
        }

        setBands();

        readSchillerNeuralNets();
        createTargetProduct();

        rayleighCorrection = new RayleighCorrection(auxData);

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    private void readSchillerNeuralNets() {
        try (InputStream merisLandIS = getClass().getResourceAsStream(SCHILLER_MERIS_LAND_NET_NAME);
             InputStream vgtLandIS = getClass().getResourceAsStream(SCHILLER_VGT_NET_NAME)) {
            merisLandNeuralNet = SchillerNeuralNetWrapper.create(merisLandIS);
            vgtNeuralNet = SchillerNeuralNetWrapper.create(vgtLandIS);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    public void setBands() {
        merisReflBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflBands[i] = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
        }
        brr442Band = rayleighProduct.getBand("brr_2");
        merisBrrBands = new Band[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrrBands[i] = rayleighProduct.getBand(IdepixConstants.MERIS_BRR_BAND_NAMES[i]);
        }
        p1Band = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
        pbaroBand = pbaroProduct.getBand(BarometricPressureOp.PRESSURE_BAROMETRIC);
        pscattBand = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
        brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");

        landNN = new SchillerAlgorithm(SchillerAlgorithm.Net.LAND);
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        // shall be the only target band!!
        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT32);
        FlagCoding flagCoding = IdepixUtils.createIdepixFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        // new bit masks:
        IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

//        if (gaApplyMERISAlternativeSchillerNN) {
//            targetProduct.addBand("nn_value", ProductData.TYPE_FLOAT32);
//        }
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // MERIS variables
        final Tile p1Tile = getSourceTile(p1Band, rectangle);
        final Tile pbaroTile = getSourceTile(pbaroBand, rectangle);
        final Tile pscattTile = getSourceTile(pscattBand, rectangle);
        final Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        final Band merisL1bFlagBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        final Tile merisL1bFlagTile = getSourceTile(merisL1bFlagBand, rectangle);

        final Band merisSzaBand = sourceProduct.getBand(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        final Band merisSaaBand = sourceProduct.getBand(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        final Band merisVzaBand = sourceProduct.getBand(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
        final Band merisVaaBand = sourceProduct.getBand(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME);
        final TiePointGrid merisEcmwTpg = sourceProduct.getTiePointGrid("atm_press");
        final Tile merisSzaTile = getSourceTile(merisSzaBand, rectangle);
        final Tile merisSaaTile = getSourceTile(merisSaaBand, rectangle);
        final Tile merisVzaTile = getSourceTile(merisVzaBand, rectangle);
        final Tile merisVaaTile = getSourceTile(merisVaaBand, rectangle);
        final Tile merisEcmwfTile = getSourceTile(merisEcmwTpg, rectangle);

        Tile[] merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        float[] merisReflectance = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectanceTiles[i] = getSourceTile(merisReflBands[i], rectangle);
        }

        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        final Band nnTargetBand = targetProduct.getBand("meris_land_nn_value");
        final Tile nnTargetTile = targetTiles.get(nnTargetBand);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final int waterFraction = waterFractionTile.getSampleInt(x, y);
                    if (!isLandPixel(x, y, merisL1bFlagTile, waterFraction)) {
                        for (Band band : targetProduct.getBands()) {
                            final Tile targetTile = targetTiles.get(band);
                            if (band == cloudFlagTargetBand) {
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_LAND, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_WATER, false);

                            } else {
                                targetTile.setSample(x, y, Float.NaN);
                            }
                        }
                    } else {
                        // set up pixel properties for given instruments...
                        GlobAlbedoAlgorithm globAlbedoAlgorithm = createMerisAlgorithm(p1Tile,
                                                                                       pbaroTile,
                                                                                       pscattTile,
                                                                                       merisSzaTile,
                                                                                       merisVzaTile,
                                                                                       merisSaaTile,
                                                                                       merisVaaTile,
                                                                                       merisEcmwfTile,
                                                                                       merisReflectanceTiles,
                                                                                       merisReflectance,
                                                                                       y,
                                                                                       x);

                        setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);

                        // apply improvement from Schiller NN approach...
                        if (gaApplyMERISAlternativeSchillerNN) {
                            final double[] nnOutput = ((GlobAlbedoMerisAlgorithm) globAlbedoAlgorithm).getNnOutput();

                            // 'pure Schiller'
                            if (gaApplyMERISAlternativeSchillerNNPure) {
                                if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_INVALID)) {
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, false);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, false);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
                                    if (nnOutput[0] > gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue &&
                                            nnOutput[0] <= gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue) {
                                        // this would be as 'CLOUD_AMBIGUOUS'...
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, true);
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                    }
                                    if (nnOutput[0] > gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue &&
                                            nnOutput[0] <= gaAlternativeSchillerNNCloudSureSnowSeparationValue) {
                                        // this would be as 'CLOUD_SURE'...
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, true);
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                    }
                                    if (nnOutput[0] > gaAlternativeSchillerNNCloudSureSnowSeparationValue) {
                                        // this would be as 'SNOW/ICE'...
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, true);
                                    }
                                }
                            } else {
                                // 'refinement with Schiller', as with old net. // todo: what do we want??
                                if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD) &&
                                        !cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD_SURE)) {
                                    if (nnOutput[0] > gaAlternativeSchillerNNCloudAmbiguousLowerBoundaryValue &&
                                            nnOutput[0] <= gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue) {
                                        // this would be as 'CLOUD_AMBIGUOUS' in CC and makes many coastlines as cloud...
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, true);
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                    }
                                    if (nnOutput[0] > gaAlternativeSchillerNNCloudAmbiguousSureSeparationValue &&
                                            nnOutput[0] <= gaAlternativeSchillerNNCloudSureSnowSeparationValue) {
                                        //   'CLOUD_SURE' as in CC (20140424, OD)
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, true);
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                    }
                                }
                            }
                            nnTargetTile.setSample(x, y, nnOutput[0]);
                        } else {
                            if (landNN != null &&
                                    !cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD) &&
                                    !cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD_SURE)) {
                                final int finalX = x;
                                final int finalY = y;
                                final Tile[] finalMerisRefl = merisReflectanceTiles;
                                SchillerAlgorithm.Accessor accessor = new SchillerAlgorithm.Accessor() {
                                    @Override
                                    public double get(int index) {
                                        return finalMerisRefl[index].getSampleDouble(finalX, finalY);
                                    }
                                };
                                final float cloudProbValue = landNN.compute(accessor);
                                if (cloudProbValue > 1.4 && cloudProbValue <= 1.8) {
                                    // this would be as 'CLOUD_AMBIGUOUS' in CC and makes many coastlines as cloud...
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, true);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                }
                                if (cloudProbValue > 1.8) {
                                    //   'CLOUD_SURE' as in CC (20140424, OD)
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, true);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                                }
                            } else if (cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD_SURE)) {
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                        }

                        // for given instrument, compute more pixel properties and write to distinct band
                        for (Band band : targetProduct.getBands()) {
                            final Tile targetTile = targetTiles.get(band);
                            setPixelSamples(band, targetTile, y, x, globAlbedoAlgorithm);
                        }
                    }
                }
            }
            // set cloud buffer flags...
//            setCloudBuffer(IdepixUtils.IDEPIX_CLOUD_FLAGS, cloudFlagTargetTile, rectangle);

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean isLandPixel(int x, int y, Tile merisL1bFlagTile, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return merisL1bFlagTile.getSampleBit(x, y, MERIS_L1B_F_LAND);
            }
        } else {
            return merisL1bFlagTile.getSampleBit(x, y, MERIS_L1B_F_LAND);
        }
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private double calcScatteringAngle(double sza, double vza, double saa, double vaa) {
        final double sins = (float) Math.sin(sza * MathUtils.DTOR);
        ;
        final double sinv = (float) Math.sin(vza * MathUtils.DTOR);
        ;
        final double coss = (float) Math.cos(sza * MathUtils.DTOR);
        ;
        final double cosv = (float) Math.cos(vza * MathUtils.DTOR);
        ;

        // delta azimuth in degree
        final double deltaAzimuth = (float) HelperFunctions.computeAzimuthDifference(vaa, saa);

        // Compute the geometric conditions
        final double cosphi = Math.cos(deltaAzimuth * MathUtils.DTOR);

        // scattering angle in degree
        return MathUtils.RTOD * Math.acos(-coss * cosv - sins * sinv * cosphi);
    }

    private double calcRhoToa442ThresholdTerm(double sza, double vza, double saa, double vaa) {
        final double thetaScatt = calcScatteringAngle(sza, vza, saa, vaa) * MathUtils.DTOR;
        double cosThetaScatt = Math.cos(thetaScatt);
        return CawaWaterClassificationOp.CC_RHO_TOA_442_THRESHOLD +
                CawaWaterClassificationOp.CC_DELTA_RHO_TOA_442_THRESHOLD * cosThetaScatt * cosThetaScatt;
    }

    void setPixelSamples(Band band, Tile targetTile, int y, int x,
                         GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue());
        } else if (band == whiteBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.whiteValue());
        } else if (band == brightWhiteBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue() + globAlbedoAlgorithm.whiteValue());
        } else if (band == temperatureBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.temperatureValue());
        } else if (band == spectralFlatnessBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.spectralFlatnessValue());
        } else if (band == ndviBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.ndviValue());
        } else if (band == ndsiBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.ndsiValue());
        } else if (band == glintRiskBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.glintRiskValue());
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricWaterValue());
        }
    }

    void setCloudFlag(Tile targetTile, int y, int x, GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, IdepixConstants.F_INVALID, globAlbedoAlgorithm.isInvalid());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, globAlbedoAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, globAlbedoAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, globAlbedoAlgorithm.isClearLand());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, globAlbedoAlgorithm.isClearWater());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, globAlbedoAlgorithm.isClearSnow());
        targetTile.setSample(x, y, IdepixConstants.F_LAND, globAlbedoAlgorithm.isLand());
        targetTile.setSample(x, y, IdepixConstants.F_WATER, globAlbedoAlgorithm.isWater());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, globAlbedoAlgorithm.isBright());
        targetTile.setSample(x, y, IdepixConstants.F_WHITE, globAlbedoAlgorithm.isWhite());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, globAlbedoAlgorithm.isBrightWhite());
        targetTile.setSample(x, y, IdepixConstants.F_HIGH, globAlbedoAlgorithm.isHigh());
        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, globAlbedoAlgorithm.isVegRisk());
        targetTile.setSample(x, y, IdepixConstants.F_SEAICE, globAlbedoAlgorithm.isSeaIce());
    }

    private GlobAlbedoAlgorithm createMerisAlgorithm(Tile p1Tile,
                                                     Tile pbaroTile,
                                                     Tile pscattTile,
                                                     Tile merisSzaTile,
                                                     Tile merisVzaTile,
                                                     Tile merisSaaTile,
                                                     Tile merisVaaTile,
                                                     Tile merisEcmwfTile,
                                                     Tile[] merisReflectanceTiles,
                                                     float[] merisReflectance,
                                                     int y,
                                                     int x) {
        GlobAlbedoMerisAlgorithm gaAlgorithm = new GlobAlbedoMerisAlgorithm();

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
        }

        gaAlgorithm.setRefl(merisReflectance);

        SchillerNeuralNetWrapper nnWrapper = merisLandNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(merisReflectance[i]);
        }

        gaAlgorithm.setNnOutput(nnWrapper.getNeuralNet().calc(inputVector));

        // todo: we need Rayleigh correction term for 442nm here

        final double sza = merisSzaTile.getSampleDouble(x, y);
        final double sins = (float) Math.sin(sza * MathUtils.DTOR);
        final double vza = merisVzaTile.getSampleDouble(x, y);
        final double sinv = (float) Math.sin(vza * MathUtils.DTOR);
        final double coss = (float) Math.cos(sza * MathUtils.DTOR);
        final double cosv = (float) Math.cos(vza * MathUtils.DTOR);

        final double vaa = merisVaaTile.getSampleDouble(x, y);
        final double saa = merisSaaTile.getSampleDouble(x, y);
        final double deltaAzimuth = (float) HelperFunctions.computeAzimuthDifference(vaa, saa);

        //Rayleigh phase function coefficients, PR in DPM
        final double[] phaseR = new double[Constants.RAYSCATT_NUM_SER];
        //Rayleigh optical thickness, tauR0 in DPM
        final double[] tauR = new double[Constants.L1_BAND_NUM];
        //  Rayleigh correction
        final double[] rhoRay = new double[Constants.L1_BAND_NUM];

        /* Rayleigh phase function Fourier decomposition */
        rayleighCorrection.phase_rayleigh(coss, cosv, sins, sinv, phaseR);

        /* Rayleigh optical thickness */
        double press = merisEcmwfTile.getSampleDouble(x, y); /* DPM #2.1.7-1 v1.1 */
        rayleighCorrection.tau_rayleigh(press, tauR); /* DPM #2.1.7-2 */

        /* Rayleigh reflectance - DPM #2.1.7-3 - v1.3 */
        final double airMass = HelperFunctions.calculateAirMass((float) vza, (float) sza);
        rayleighCorrection.ref_rayleigh(deltaAzimuth, sza, vza, coss, cosv, airMass, phaseR, tauR, rhoRay);
        /* DPM #2.1.7-4 */
//        double[] rhoAg = new double[Constants.L1_BAND_NUM];
//        for (int band = Constants.bb412; band <= Constants.bb900; band++) {
//            rhoAg[band] = (dc.rhoToa[band][pixelInfo.index] - rhoRay[band]);
//        }

        float[] merisBrr = new float[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
        int brrIndex = 0;
        for (int band = Constants.bb412; band <= Constants.bb900; band++) {
            if (band != 10 && band != 14) {
                merisBrr[brrIndex] = (float) (merisReflectanceTiles[brrIndex].getSampleFloat(x, y) - rhoRay[band]);
            }
        }

        /* Interpolate threshold on rayleigh corrected reflectance - DPM #2.1.7-9 */
        final float brr442Thresh = (float) calcRhoToa442ThresholdTerm(sza, vza, saa, vaa);
//        double rhorc_442_thr = pixelId.getRhoRC442thr(dc.sza[pixelInfo.index], dc.vza[pixelInfo.index], deltaAzimuth, isLand);

        gaAlgorithm.setBrr(merisBrr);
        gaAlgorithm.setBrr442(merisBrr[1]);
        gaAlgorithm.setBrr442Thresh(brr442Thresh);
        gaAlgorithm.setP1(p1Tile.getSampleFloat(x, y));
        gaAlgorithm.setPBaro(pbaroTile.getSampleFloat(x, y));
        gaAlgorithm.setPscatt(pscattTile.getSampleFloat(x, y));
        gaAlgorithm.setIsWater(false);
        gaAlgorithm.setL1FlagLand(true);

        return gaAlgorithm;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CawaLandClassificationOp.class);
        }
    }

}
