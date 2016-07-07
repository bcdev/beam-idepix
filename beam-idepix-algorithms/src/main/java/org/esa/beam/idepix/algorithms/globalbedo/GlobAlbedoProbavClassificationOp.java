package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.lang.ArrayUtils;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
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

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.util.ArrayList;
import java.util.List;
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
            label = " NN cloud ambiguous lower boundary (VGT/Proba-V only)",
            description = " NN cloud ambiguous lower boundary (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value (VGT/Proba-V only)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value (VGT/Proba-V only)",
            description = " NN cloud ambiguous cloud sure/snow separation value (has only effect for VGT/Proba-V L1b products)")
    private double gaSchillerNNCloudSureSnowSeparationValue;

    // VGT bands:
    private Band[] probavReflectanceBands;

    protected static final int SM_F_CLEAR = 0;
    protected static final int SM_F_UNDEFINED = 1;
    protected static final int SM_F_CLOUD = 2;
    protected static final int SM_F_SNOWICE = 3;
    protected static final int SM_F_CLOUDSHADOW = 4;
    protected static final int SM_F_LAND = 5;
    protected static final int SM_F_SWIR_GOOD = 6;
    protected static final int SM_F_NIR_GOOD = 7;
    protected static final int SM_F_RED_GOOD = 8;
    protected static final int SM_F_BLUE_GOOD = 9;

    ElevationModel getasseElevationModel;

    private int[] listOfPixelTimes;

    @Override
    public void initialize() throws OperatorException {
        super.initialize();

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(
                demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        // extract the pixel sampling times
        // todo: clarify with GK/JM how to further use this information
        if (isProbaVDailySynthesisProduct(sourceProduct.getName())) {
            final Band timeBand = sourceProduct.getBand("TIME");
            final RenderedImage timeImage = timeBand.getSourceImage().getImage(0);
            listOfPixelTimes = getListOfPixelTimes(timeImage);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // PROBA-V variables
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

                    // apply improvement from NN approach...
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
    void setCloudFlag(Tile targetTile, int y, int x, GlobAlbedoAlgorithm globAlbedoAlgorithm) {
        super.setCloudFlag(targetTile, y, x, globAlbedoAlgorithm);
        // currently DO NOT set haze flag (JM 20160707)
        // targetTile.setSample(x, y, IdepixConstants.F_HAZE, ((GlobAlbedoProbavAlgorithm) globAlbedoAlgorithm).isHaze());
    }

    @Override
    public void setBands() {
        probavReflectanceBands = new Band[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
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

    // package local for testing
    static int[] getListOfPixelTimes(RenderedImage timeImage) {

        // Set up the parameters for the Histogram object.
        // bin the histogram into the minutes of one day.
        // we consider daily products only todo: clarify
        int[] bins = {1440};
        double[] low = {0.0};
        double[] high = {1440.0};

        // Create the parameter block.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(timeImage);           // Specify the source image
        pb.add(null);                      // No ROI
        pb.add(1);                         // Sampling
        pb.add(1);                         // periods
        pb.add(bins);                      // bins
        pb.add(low);                       // low
        pb.add(high);                      // high

        // Perform the histogram operation.
        final RenderedOp histoImage = JAI.create("histogram", pb, null);

        // Retrieve the histogram data.
        Histogram hist = (Histogram) histoImage.getProperty("histogram");
        final int[][] histBins = hist.getBins();
        List<Integer> histBinList = new ArrayList();
        for (int i = 1; i < histBins[0].length; i++) {   // skip the 0 (no data value)
            if (histBins[0][i] > 0) {
                histBinList.add(i);
            }
        }
        return ArrayUtils.toPrimitive(histBinList.toArray(new Integer[histBinList.size()]));
    }

    private static boolean isProbaVDailySynthesisProduct(String productName) {
        return productName.toUpperCase().startsWith("PROBAV_S1_") &&
                productName.toUpperCase().endsWith(".HDF5");
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

        final double altitude = computeGetasseAltitude(x, y);
        gaAlgorithm.setElevation(altitude);

        checkProbavReflectanceQuality(gaAlgorithm, probavReflectance, smFlagTile, x, y);
        gaAlgorithm.setRefl(probavReflectance);

        SchillerNeuralNetWrapper nnWrapper = vgtNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(probavReflectance[i]);
        }
        gaAlgorithm.setNnOutput(nnWrapper.getNeuralNet().calc(inputVector));

        if (gaUseL1bLandWaterFlag) {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
            gaAlgorithm.setL1bLand(isLand);
            setIsWater(watermask, gaAlgorithm);
        } else {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            gaAlgorithm.setL1bLand(isLand);
            setIsWaterByFraction(watermaskFraction, gaAlgorithm);
        }

        return gaAlgorithm;
    }

    private void checkProbavReflectanceQuality(GlobAlbedoProbavAlgorithm gaAlgorithm, float[] probavReflectance, Tile smFlagTile, int x, int y) {
        final boolean isBlueGood = smFlagTile.getSampleBit(x, y, SM_F_BLUE_GOOD);
        final boolean isRedGood = smFlagTile.getSampleBit(x, y, SM_F_RED_GOOD);
        final boolean isNirGood = smFlagTile.getSampleBit(x, y, SM_F_NIR_GOOD);
        final boolean isSwirGood = smFlagTile.getSampleBit(x, y, SM_F_SWIR_GOOD);
        final boolean isProcessingLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
        gaAlgorithm.setIsBlueGood(isBlueGood);
        gaAlgorithm.setIsRedGood(isRedGood);
        gaAlgorithm.setIsNirGood(isNirGood);
        gaAlgorithm.setIsSwirGood(isSwirGood);
        gaAlgorithm.setProcessingLand(isProcessingLand);

        if (!isBlueGood || !isRedGood || !isNirGood || !isSwirGood || !isProcessingLand) {
            for (int i = 0; i < probavReflectance.length; i++) {
                probavReflectance[i] = Float.NaN;
            }
        }
    }

    private double computeGetasseAltitude(float x, float y) {
        final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
        GeoPos geoPos = sourceProduct.getGeoCoding().getGeoPos(pixelPos, null);
        double altitude;
        try {
            altitude = getasseElevationModel.getElevation(geoPos);
        } catch (Exception e) {
            // todo
            e.printStackTrace();
            altitude = 0.0;
        }
        return altitude;
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
