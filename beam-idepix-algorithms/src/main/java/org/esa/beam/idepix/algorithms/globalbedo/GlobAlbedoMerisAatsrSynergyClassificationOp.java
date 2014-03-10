package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.BarometricPressureOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Rectangle;
import java.util.Map;

/**
 * Idepix operator for cloud/sea ice discrimination from MERIS/AATSR synergistic approach.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.globalbedo.classification.merisaatsr",
                  version = "2.2-EVOLUTION-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Pixel identification and classification with GlobAlbedo algorithm.")
public class GlobAlbedoMerisAatsrSynergyClassificationOp extends GlobAlbedoClassificationOp {

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

    private Band[] merisReflBands;
    private Band[] merisBrrBands;
    private Band merisBrr442Band;
    private Band merisBrr442ThreshBand;
    private Band merisP1Band;
    private Band merisPbaroBand;
    private Band merisPscattBand;

    private Band[] aatsrReflectanceBands;
    private Band[] aatsrBtempBands;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // MERIS variables
        final Tile merisBrr442Tile = getSourceTile(merisBrr442Band, rectangle);
        final Tile merisBrr442ThreshTile = getSourceTile(merisBrr442ThreshBand, rectangle);
        final Tile merisP1Tile = getSourceTile(merisP1Band, rectangle);
        final Tile merisPbaroTile = getSourceTile(merisPbaroBand, rectangle);
        final Tile merisPscattTile = getSourceTile(merisPscattBand, rectangle);

        final Band merisL1bFlagBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        final Tile merisL1bFlagTile = getSourceTile(merisL1bFlagBand, rectangle);

        final Tile merisVzaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                                rectangle);

        Tile[] merisBrrTiles = new Tile[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
        float[] merisBrr = new float[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrrTiles[i] = getSourceTile(merisBrrBands[i], rectangle);
        }

        Tile[] merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        float[] merisReflectance = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectanceTiles[i] = getSourceTile(merisReflBands[i], rectangle);
        }

        // AATSR variables
        Tile[] aatsrReflectanceTiles = new Tile[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        float[] aatsrReflectance = new float[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle);
        }

        Tile[] aatsrBtempTiles = new Tile[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        float[] aatsrBtemp = new float[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            aatsrBtempTiles[i] = getSourceTile(aatsrBtempBands[i], rectangle);
        }

        GeoPos geoPos = null;
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    if (!gaUseL1bLandWaterFlag) {
                        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                        if (geoCoding.canGetGeoPos()) {
                            geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                            waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                            if (gaUseWaterMaskFraction) {
                                waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                            }
                        }
                    }

                    // set up pixel properties for given instruments...
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createMerisAatsrSynergyAlgorithm(merisL1bFlagTile,
                                                                                               merisVzaTile,
                                                                                               merisBrr442Tile, merisP1Tile,
                                                                                               merisPbaroTile, merisPscattTile, merisBrr442ThreshTile,
                                                                                               merisReflectanceTiles,
                                                                                               merisReflectance,
                                                                                               merisBrrTiles, merisBrr,
                                                                                               aatsrReflectanceTiles, aatsrReflectance,
                                                                                               aatsrBtempTiles, aatsrBtemp,
                                                                                               waterMaskSample,
                                                                                               waterMaskFraction,
                                                                                               y,
                                                                                               x);

                    setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);
                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, merisP1Tile, merisPbaroTile, merisPscattTile, y, x, globAlbedoAlgorithm);
                    }
                }
            }
            // set cloud buffer flags...
            setCloudBuffer(IdepixUtils.IDEPIX_CLOUD_FLAGS, cloudFlagTargetTile, rectangle);

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    @Override
    void setBands() {
        // MERIS:
        merisReflBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflBands[i] = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
        }
        merisBrr442Band = rayleighProduct.getBand("brr_2");
        merisBrrBands = new Band[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrrBands[i] = rayleighProduct.getBand(IdepixConstants.MERIS_BRR_BAND_NAMES[i]);
        }
        merisP1Band = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
        merisPbaroBand = pbaroProduct.getBand(BarometricPressureOp.PRESSURE_BAROMETRIC);
        merisPscattBand = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
        merisBrr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");

        // AATSR:
        aatsrReflectanceBands = new Band[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            aatsrReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]);
        }
        aatsrBtempBands = new Band[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            aatsrBtempBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i]);
            if (aatsrBtempBands[i] == null) {
                throw new OperatorException
                        ("AATSR temperature bands missing or incomplete in source product - cannot proceed.");
            }
        }

    }

    @Override
    void extendTargetProduct() {
        if (!gaComputeFlagsOnly && gaCopyPressure) {
            pressureBand = targetProduct.addBand("pressure_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(pressureBand, "Pressure", "hPa", IdepixConstants.NO_DATA_VALUE, true);
            pbaroOutputBand = targetProduct.addBand("pbaro_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(pbaroOutputBand, "Barometric Pressure", "hPa",
                                             IdepixConstants.NO_DATA_VALUE,
                                             true);
            p1OutputBand = targetProduct.addBand("p1_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(p1OutputBand, "P1 Pressure", "hPa", IdepixConstants.NO_DATA_VALUE,
                                             true);
            pscattOutputBand = targetProduct.addBand("pscatt_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(pscattOutputBand, "PScatt Pressure", "hPa",
                                             IdepixConstants.NO_DATA_VALUE,
                                             true);
        }

        // L1 flags (MERIS), confid flags, cloud flags (AATSR)
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);

        if (gaCopyRadiances) {
            copyRadiances();
        }
        if (gaCopyRayleigh) {
            copyRayleighReflectances();
        }
        if (!gaCopyRadiances && gaCopySubsetOfRadiances) {
            copySubsetOfRadiances();
        }
        if (gaCopyMerisToaReflectances) {
            copyMerisToaReflectances();
        }
    }

    private void copyRadiances() {
        if (!gaCopyMerisToaReflectances) {
            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct,
                                      targetProduct, true);
            }
        }
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
        for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
            ProductUtils.copyBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private void copyRayleighReflectances() {
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (i != 10 && i != 14) {
                ProductUtils.copyBand(RayleighCorrectionOp.BRR_BAND_PREFIX + "_" + (i + 1), rayleighProduct,
                                      targetProduct, true);
            }
        }
    }


    private void copySubsetOfRadiances() {
        // for performance, copy just a subset of radiances/reflectances to allow RGB image creation
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i].equals("radiance_13") ||
                EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i].equals("radiance_5") ||
                EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i].equals("radiance_1")) {
                ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct,
                                      targetProduct, true);
            }
        }
        for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
            if (IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i].startsWith("reflec_nadir")) {
                ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                      targetProduct, true);
            }
        }
    }

    private void copyMerisToaReflectances() {
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final Band merisToaReflBand = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
            ProductUtils.copyBand(merisToaReflBand.getName(), rad2reflProduct, targetProduct, true);
        }
    }

    private GlobAlbedoAlgorithm createMerisAatsrSynergyAlgorithm(Tile merisL1bFlagTile,
                                                                 Tile vzaTile,
                                                                 Tile brr442Tile, Tile p1Tile,
                                                                 Tile pbaroTile, Tile pscattTile, Tile brr442ThreshTile,
                                                                 Tile[] merisReflectanceTiles,
                                                                 float[] merisReflectance,
                                                                 Tile[] merisBrrTiles, float[] merisBrr,
                                                                 Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                                 Tile[] aatsrBtempTiles, float[] aatsrBtemp,
                                                                 byte watermask,
                                                                 byte watermaskFraction,
                                                                 int y,
                                                                 int x) {
        GlobAlbedoMerisAatsrSynergyAlgorithm gaAlgorithm = new GlobAlbedoMerisAatsrSynergyAlgorithm();

        // MERIS part:
        double[] seaiceNeuralNetInput = new double[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
            seaiceNeuralNetInput[i] = merisReflectance[i];
        }

        double[] seaiceNeuralNetOutput;
//        if (vzaTile.getSampleFloat(x, y) < 7.0) {
//            seaiceNeuralNetOutput = seaiceInnerNeuralNet.calc(seaiceNeuralNetInput);
//        } else {
        seaiceNeuralNetOutput = seaiceOuterNeuralNet.calc(seaiceNeuralNetInput);
//        }

        gaAlgorithm.setReflMeris(merisReflectance);
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
        }
        gaAlgorithm.setBrrMeris(merisBrr);
        gaAlgorithm.setBrr442Meris(brr442Tile.getSampleFloat(x, y));
        gaAlgorithm.setBrr442ThreshMeris(brr442ThreshTile.getSampleFloat(x, y));
        gaAlgorithm.setP1Meris(p1Tile.getSampleFloat(x, y));
        gaAlgorithm.setPBaro(pbaroTile.getSampleFloat(x, y));
        gaAlgorithm.setPscattMeris(pscattTile.getSampleFloat(x, y));
        gaAlgorithm.setRefl1600ThreshAatsr(gaRefl1600SeaIceThresh);
        gaAlgorithm.setSchillerRefl1600Meris(seaiceNeuralNetOutput[0]);
        gaAlgorithm.setSchillerSeaiceCloudProb(seaiceNeuralNetOutput[1]);

        // AATSR part:
        for (int i = 0; i < IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES.length; i++) {
            aatsrReflectance[i] = aatsrReflectanceTiles[i].getSampleFloat(x, y);
        }
        for (int i = 0; i < IdepixConstants.AATSR_BTEMP_BAND_NAMES.length; i++) {
            aatsrBtemp[i] = aatsrBtempTiles[i].getSampleFloat(x, y);
        }

        final int length = aatsrReflectance.length / 2;
        float[] refl = new float[length];
        if (gaUseAatsrFwardForClouds) {
            System.arraycopy(aatsrReflectance, length, refl, 0, length);
        } else {
            System.arraycopy(aatsrReflectance, 0, refl, 0, length);
        }
        gaAlgorithm.setReflAatsr(refl);
        gaAlgorithm.setBtempAatsr(aatsrBtemp);

        float[] merisAatsrReflectance = concatMerisAatsrReflectanceArrays(merisReflectance, aatsrReflectance);
        gaAlgorithm.setRefl(merisAatsrReflectance);
        gaAlgorithm.setIstomena(gaUseIstomenaSeaIceAlgorithm);
        gaAlgorithm.setSchillerSeaice(gaUseSchillerSeaIceAlgorithm);

        // water mask part
        if (gaUseL1bLandWaterFlag) {
            final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, MERIS_L1B_F_LAND);
            gaAlgorithm.setL1FlagLandMeris(isLand);
            setIsWater(watermask, gaAlgorithm);
        } else {
            if (gaUseWaterMaskFraction) {
                final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, MERIS_L1B_F_LAND) &&
                                       watermaskFraction < WATERMASK_FRACTION_THRESH;
                gaAlgorithm.setL1FlagLandMeris(isLand);
                setIsWaterByFraction(watermaskFraction, gaAlgorithm);
            } else {
                final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, MERIS_L1B_F_LAND) &&
                                       !(watermask == WatermaskClassifier.WATER_VALUE);
                gaAlgorithm.setL1FlagLandMeris(isLand);
                setIsWater(watermask, gaAlgorithm);
            }
        }

        return gaAlgorithm;
    }

    private float[] concatMerisAatsrReflectanceArrays(float[] merisReflectance, float[] aatsrReflectance) {
        float[] merisAatsrReflectance = new float[merisReflectance.length + aatsrReflectance.length];
        System.arraycopy(merisReflectance, 0, merisAatsrReflectance, 0, merisReflectance.length);
        System.arraycopy(aatsrReflectance, 0, merisAatsrReflectance, merisReflectance.length, aatsrReflectance.length);
        return merisAatsrReflectance;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoMerisAatsrSynergyClassificationOp.class, "idepix.globalbedo.classification.merisaatsr");
        }
    }
}
