package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.util.Map;

/**
 * Operator for GlobAlbedo VGT cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.globalbedo.classification.vgt",
                  version = "2.1",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2008, 2012 by Brockmann Consult",
                  description = "This operator provides cloud screening from SPOT VGT data.")
public class GlobAlbedoVgtClassificationOp extends GlobAlbedoClassificationOp {

    // VGT bands:
    private Band[] vgtReflectanceBands;

    private static final int SM_F_LAND = 3;
    private static final int SM_F_MIR_GOOD = 4;
    private static final int SM_F_B3_GOOD = 5;
    private static final int SM_F_B2_GOOD = 6;
    private static final int SM_F_B0_GOOD = 7;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // VGT variables
        final Band smFlagBand = sourceProduct.getBand("SM");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile[] vgtReflectanceTiles = new Tile[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        float[] vgtReflectance = new float[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle);
        }

        GeoPos geoPos = null;
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        final Band nnTargetBand = targetProduct.getBand("vgt_nn_value");
        final Tile nnTargetTile = targetTiles.get(nnTargetBand);

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
                            waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                        }
                    }

                    // set up pixel properties for given instruments...
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createVgtAlgorithm(smFlagTile, vgtReflectanceTiles,
                                                                                 vgtReflectance,
                                                                                 waterMaskSample,
                                                                                 waterMaskFraction,
                                                                                 y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);

                    // apply improvement from Schiller NN approach...
                    final double[] nnOutput = ((GlobAlbedoVgtAlgorithm) globAlbedoAlgorithm).getNnOutput();
                    if (gaApplyVGTSchillerNN) {
                        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_INVALID)) {
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
                            if (nnOutput[0] > gaSchillerNNCloudAmbiguousLowerBoundaryValue &&
                                    nnOutput[0] <= gaSchillerNNCloudAmbiguousSureSeparationValue) {
                                // this would be as 'CLOUD_AMBIGUOUS'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                            if (nnOutput[0] > gaSchillerNNCloudAmbiguousSureSeparationValue &&
                                    nnOutput[0] <= gaSchillerNNCloudSureSnowSeparationValue) {
                                // this would be as 'CLOUD_SURE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                            if (nnOutput[0] > gaSchillerNNCloudSureSnowSeparationValue) {
                                // this would be as 'SNOW/ICE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, true);
                            }
                        }
                        nnTargetTile.setSample(x, y, nnOutput[0]);
                    }

                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, y, x, globAlbedoAlgorithm);
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
    public void setBands() {
        vgtReflectanceBands = new Band[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.VGT_REFLECTANCE_BAND_NAMES[i]);
        }
    }

    @Override
    public void extendTargetProduct() throws OperatorException {
        if (gaCopyToaReflectances) {
            copyReflectances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (gaCopyAnnotations) {
            copyAnnotations();
        }

        if (gaApplyVGTSchillerNN) {
            targetProduct.addBand("vgt_nn_value", ProductData.TYPE_FLOAT32);
        }
    }

    private void copyAnnotations() {
        for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
        }
    }

    private void copyReflectances() {
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            // write the original reflectance bands:
            ProductUtils.copyBand(IdepixConstants.VGT_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private GlobAlbedoAlgorithm createVgtAlgorithm(Tile smFlagTile, Tile[] vgtReflectanceTiles,
                                                   float[] vgtReflectance,
                                                   byte watermask, byte watermaskFraction,
                                                   int y, int x) {

        GlobAlbedoVgtAlgorithm gaAlgorithm = new GlobAlbedoVgtAlgorithm();

        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }

        checkVgtReflectanceQuality(vgtReflectance, smFlagTile, x, y);
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        gaAlgorithm.setRefl(vgtReflectanceSaturationCorrected);

        SchillerNeuralNetWrapper nnWrapper = vgtNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(vgtReflectanceSaturationCorrected[i]);
        }
        gaAlgorithm.setNnOutput(nnWrapper.getNeuralNet().calc(inputVector));

        if (gaUseL1bLandWaterFlag) {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
            gaAlgorithm.setSmLand(isLand);
            setIsWater(watermask, gaAlgorithm);
        } else {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            gaAlgorithm.setSmLand(isLand);
            setIsWaterByFraction(watermaskFraction, gaAlgorithm);
        }


        return gaAlgorithm;
    }

    private void checkVgtReflectanceQuality(float[] vgtReflectance, Tile smFlagTile, int x, int y) {
        final boolean isB0Good = smFlagTile.getSampleBit(x, y, SM_F_B0_GOOD);
        final boolean isB2Good = smFlagTile.getSampleBit(x, y, SM_F_B2_GOOD);
        final boolean isB3Good = smFlagTile.getSampleBit(x, y, SM_F_B3_GOOD);
        final boolean isMirGood = smFlagTile.getSampleBit(x, y, SM_F_MIR_GOOD) || vgtReflectance[3] <= 0.65; // MIR_refl
        if (!isB0Good || !isB2Good || !isB3Good || !isMirGood) {
            for (int i = 0; i < vgtReflectance.length; i++) {
                vgtReflectance[i] = Float.NaN;
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoVgtClassificationOp.class);
        }
    }

}
