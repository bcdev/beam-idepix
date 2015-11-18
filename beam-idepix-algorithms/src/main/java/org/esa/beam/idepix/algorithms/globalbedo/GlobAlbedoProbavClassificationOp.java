package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
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
@OperatorMetadata(alias = "idepix.globalbedo.classification.probav",
                  version = "2.2",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2008, 2012 by Brockmann Consult",
                  description = "This operator provides cloud screening from PROBA-V data.")
public class GlobAlbedoProbavClassificationOp extends GlobAlbedoClassificationOp {

    @Parameter(defaultValue = "1.1",
            label = " Schiller NN cloud ambiguous lower boundary (VGT/Proba-V only)",
            description = " Schiller NN cloud ambiguous lower boundary (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " Schiller NN cloud ambiguous/sure separation value (VGT/Proba-V only)",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " Schiller NN cloud sure/snow separation value (VGT/Proba-V only)",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudSureSnowSeparationValue;

    // VGT bands:
    private Band[] probavReflectanceBands;

    private static final int SM_F_LAND = 5;
    private static final int SM_F_SWIR_GOOD = 6;
    private static final int SM_F_NIR_GOOD = 7;
    private static final int SM_F_RED_GOOD = 8;
    private static final int SM_F_BLUE_GOOD = 9;

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // VGT variables
        final Band smFlagBand = sourceProduct.getBand("SM_FLAGS");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile[] probavReflectanceTiles = new Tile[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        float[] probavReflectance = new float[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectanceTiles[i] = getSourceTile(probavReflectanceBands[i], rectangle);
        }

        GeoPos geoPos = null;
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        final Band nnTargetBand = targetProduct.getBand("probav_nn_value");
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
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = createProbavAlgorithm(smFlagTile, probavReflectanceTiles,
                                                                                    probavReflectance,
                                                                                    waterMaskSample,
                                                                                    waterMaskFraction,
                                                                                    y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, globAlbedoAlgorithm);

                    // apply improvement from Schiller NN approach...
                    final double[] nnOutput = ((GlobAlbedoProbavAlgorithm) globAlbedoAlgorithm).getNnOutput();
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
        probavReflectanceBands= new Band[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES[i]);
        }
    }

    @Override
    public void extendTargetProduct() throws OperatorException {
        if (gaCopyToaReflectances) {
            copyProbavReflectances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (gaCopyAnnotations) {
            copyProbavAnnotations();
        }

        if (gaApplyVGTSchillerNN) {
            targetProduct.addBand("probav_nn_value", ProductData.TYPE_FLOAT32);
        }
    }

    private void copyProbavAnnotations() {
        for (String bandName : IdepixConstants.PROBAV_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
        }
    }

    private void copyProbavReflectances() {
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            // write the original reflectance bands:
            ProductUtils.copyBand(IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private GlobAlbedoAlgorithm createProbavAlgorithm(Tile smFlagTile, Tile[] probavReflectanceTiles,
                                                      float[] probavReflectance,
                                                      byte watermask, byte watermaskFraction,
                                                      int y, int x) {

        GlobAlbedoProbavAlgorithm gaAlgorithm = new GlobAlbedoProbavAlgorithm();

        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectance[i] = probavReflectanceTiles[i].getSampleFloat(x, y);
        }

        checkProbavReflectanceQuality(probavReflectance, smFlagTile, x, y);
        float[] probavReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(probavReflectance);
        gaAlgorithm.setRefl(probavReflectanceSaturationCorrected);

        SchillerNeuralNetWrapper nnWrapper = vgtNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(probavReflectanceSaturationCorrected[i]);
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

    private void checkProbavReflectanceQuality(float[] probavReflectance, Tile smFlagTile, int x, int y) {
        final boolean isBlueGood = smFlagTile.getSampleBit(x, y, SM_F_BLUE_GOOD);
        final boolean isRedGood = smFlagTile.getSampleBit(x, y, SM_F_RED_GOOD);
        final boolean isNirGood = smFlagTile.getSampleBit(x, y, SM_F_NIR_GOOD);
        final boolean isSwirGood = smFlagTile.getSampleBit(x, y, SM_F_SWIR_GOOD);
        if (!isBlueGood || !isRedGood || !isNirGood || !isSwirGood) {
            for (int i = 0; i < probavReflectance.length; i++) {
                probavReflectance[i] = Float.NaN;
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoProbavClassificationOp.class);
        }
    }

}
