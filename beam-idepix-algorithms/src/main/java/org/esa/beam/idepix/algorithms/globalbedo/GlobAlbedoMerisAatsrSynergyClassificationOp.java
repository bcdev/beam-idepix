package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.operators.BarometricPressureOp;
import org.esa.beam.idepix.operators.LisePressureOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;

/**
 * Idepix operator for cloud/sea ice discrimination from MERIS/AATSR synergistic approach.
 *
 * @author Olaf Danne
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.globalbedo.classification.merisaatsr",
                  version = "1.4-SNAPSHOT",
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

    @Parameter(defaultValue = "_M", label = "Colocation master product band names extension")
    private boolean bandExtensionMaster;
    @Parameter(defaultValue = "_S", label = "Colocation slave product band names extension")
    private boolean bandExtensionSlave;

    @Parameter(defaultValue = "true", label = "Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;

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
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();

        // todo
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
        // L1 flags (MERIS), confid flags, cloud flags (AATSR)
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
    }


    private GlobAlbedoAlgorithm createMerisAatsrSynergyAlgorithm(Tile merisL1bFlagTile,
                                                     Tile brr442Tile, Tile p1Tile,
                                                     Tile pbaroTile, Tile pscattTile, Tile brr442ThreshTile,
                                                     Tile[] merisReflectanceTiles,
                                                     float[] merisReflectance,
                                                     Tile[] merisBrrTiles, float[] merisBrr,
                                                     Tile aatsrL1bFlagTile,
                                                     Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                     Tile[] aatsrBtempTiles, float[] aatsrBtemp,
                                                     byte watermask,
                                                     byte watermaskFraction,
                                                     int y,
                                                     int x) {
        GlobAlbedoMerisAatsrSynergyAlgorithm gaAlgorithm = new GlobAlbedoMerisAatsrSynergyAlgorithm();

        // MERIS part:
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
        }

        gaAlgorithm.setRefl(merisReflectance);
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
        }
        gaAlgorithm.setBrrMeris(merisBrr);
        gaAlgorithm.setBrr442Meris(brr442Tile.getSampleFloat(x, y));
        gaAlgorithm.setBrr442ThreshMeris(brr442ThreshTile.getSampleFloat(x, y));
        gaAlgorithm.setP1Meris(p1Tile.getSampleFloat(x, y));
        gaAlgorithm.setPBaro(pbaroTile.getSampleFloat(x, y));
        gaAlgorithm.setPscattMeris(pscattTile.getSampleFloat(x, y));
        if (gaUseWaterMaskFraction) {
            final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, GlobAlbedoAlgorithm.L1B_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            gaAlgorithm.setL1FlagLandMeris(isLand);
            setIsWaterByFraction(watermaskFraction, gaAlgorithm);
        } else {
            final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, GlobAlbedoAlgorithm.L1B_F_LAND) &&
                    !(watermask == WatermaskClassifier.WATER_VALUE);
            gaAlgorithm.setL1FlagLandMeris(isLand);
            setIsWater(watermask, gaAlgorithm);
        }

        // AATSR part:
        gaAlgorithm.setUseFwardViewForCloudMaskAatsr(gaUseAatsrFwardForClouds);
        gaAlgorithm.setRefl(aatsrReflectance);
        gaAlgorithm.setBtemp1200Aatsr(aatsrBtempTiles[2].getSampleFloat(x, y));

        return gaAlgorithm;
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
