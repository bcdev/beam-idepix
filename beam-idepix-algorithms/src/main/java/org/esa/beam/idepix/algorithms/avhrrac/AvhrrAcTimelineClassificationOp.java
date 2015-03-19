package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.idepix.util.SchillerNeuralNetWrapper;

import java.io.IOException;

/**
 * Basic operator for GlobAlbedo pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.timeline.classification",
        version = "2.2",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Basic operator for pixel classification from AVHRR L1b data.")
public class AvhrrAcTimelineClassificationOp extends AbstractAvhrrAcClassificationOp {

    // todo: we have misclassifications as snow/ice (i.e. very high Schiller values close to 5.0) for
    // high SZA values > ~70deg. Check what we can do.

    @SourceProduct(alias = "aacl1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    // AvhrrAc parameters
    @Parameter(defaultValue = "false", label = " Copy input radiance bands (with albedo1/2 converted)")
    boolean aacCopyRadiances = false;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    int aacCloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    boolean aacUseWaterMaskFraction = true;

    @Parameter(defaultValue = "false", label = " Flip source images (check before if needed!)")
    private boolean flipSourceImages;

    @Parameter(defaultValue = "2.15",
            label = " Schiller NN cloud ambiguous lower boundary ",
            description = " Schiller NN cloud ambiguous lower boundary ")
    double avhrracSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " Schiller NN cloud ambiguous/sure separation value ",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
            label = " Schiller NN cloud sure/snow separation value ",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double avhrracSchillerNNCloudSureSnowSeparationValue;


    @Parameter(defaultValue = "20.0",
            label = " Reflectance 1 'brightness' threshold ",
            description = " Reflectance 1 'brightness' threshold ")
    double reflCh1Thresh;

    @Parameter(defaultValue = "20.0",
            label = " Reflectance 2 'brightness' threshold ",
            description = " Reflectance 2 'brightness' threshold ")
    double reflCh2Thresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 2/1 ratio threshold ",
            description = " Reflectance 2/1 ratio threshold ")
    double r2r1RatioThresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 3/1 ratio threshold ",
            description = " Reflectance 3/1 ratio threshold ")
    double r3r1RatioThresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 4 brightness temperature threshold (C)",
            description = " Channel 4 brightness temperature threshold (C)")
    double btCh4Thresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 5 brightness temperature threshold (C)",
            description = " Channel 5 brightness temperature threshold (C)")
    double btCh5Thresh;


    @Override
    public void prepareInputs() throws OperatorException {
        if (flipSourceImages) {
            flipSourceImages();
        }
        setNoaaId();
        readSchillerNets();
        createTargetProduct();
        computeSunPosition();

        if (sourceProduct.getGeoCoding() == null) {
            sourceProduct.setGeoCoding(
                    new TiePointGeoCoding(sourceProduct.getTiePointGrid("latitude"),
                                          sourceProduct.getTiePointGrid("longitude"))
            );
        }

        try {
            vzaTable = AvhrrAcAuxdata.getInstance().createLine2ViewZenithTable();
        } catch (IOException e) {
            throw new OperatorException("Failed to get VZA from auxdata - cannot proceed: ", e);
        }
    }

    @Override
    void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAcAlgorithm aacAlgorithm = new AvhrrAcAlgorithm();

        final double sza = sourceSamples[AvhrrAcConstants.SRC_TL_SZA].getDouble();
        final double vza = sourceSamples[AvhrrAcConstants.SRC_TL_VZA].getDouble();
        final double saa = sourceSamples[AvhrrAcConstants.SRC_TL_SAA].getDouble();
        final double vaa = sourceSamples[AvhrrAcConstants.SRC_TL_VAA].getDouble();
        final double groundHeight = sourceSamples[AvhrrAcConstants.SRC_TL_GROUND_HEIGHT].getDouble();

        final double relAzi = saa - vaa;

        double[] avhrrRadiance = new double[AvhrrAcConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length];
        final double refl1 = sourceSamples[AvhrrAcConstants.SRC_TL_REFL_1].getDouble();            // %
        final double refl2 = sourceSamples[AvhrrAcConstants.SRC_TL_REFL_2].getDouble();            // %
        final double refl3 = sourceSamples[AvhrrAcConstants.SRC_TL_REFL_3].getDouble();            // K
        final double rad3 = sourceSamples[AvhrrAcConstants.SRC_TL_RAD_3].getDouble();              // mW*cm/(m^2*sr)
        final double refl4 = sourceSamples[AvhrrAcConstants.SRC_TL_REFL_4].getDouble();            // K
        final double refl5 = sourceSamples[AvhrrAcConstants.SRC_TL_REFL_5].getDouble();            // K

        if (refl1 >= 0.0 && refl2 >= 0.0 && !AvhrrAcUtils.anglesInvalid(sza, vza, saa, vaa)) {

            avhrrRadiance[0] = convertBetweenAlbedoAndRadiance(refl1, sza, ALBEDO_TO_RADIANCE,0);
            avhrrRadiance[1] = convertBetweenAlbedoAndRadiance(refl2, sza, ALBEDO_TO_RADIANCE, 1);
            avhrrRadiance[2] = rad3;
            avhrrRadiance[3] = AvhrrAcUtils.convertBtToRadiance(refl4, 4);
            avhrrRadiance[4] = AvhrrAcUtils.convertBtToRadiance(refl5, 5);
            aacAlgorithm.setRadiance(avhrrRadiance);

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[AvhrrAcConstants.SRC_TL_WATERFRACTION].getFloat();
            }

            SchillerNeuralNetWrapper nnWrapper = avhrracNeuralNet.get();
            double[] inputVector = nnWrapper.getInputVector();
            inputVector[0] = sza;
            inputVector[1] = vza;
            inputVector[2] = relAzi;
            inputVector[3] = Math.sqrt(avhrrRadiance[0]);
            inputVector[4] = Math.sqrt(avhrrRadiance[1]);
            inputVector[5] = Math.sqrt(avhrrRadiance[3]);
            inputVector[6] = Math.sqrt(avhrrRadiance[4]);
            aacAlgorithm.setRadiance(avhrrRadiance);
            aacAlgorithm.setWaterFraction(waterFraction);

            double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

            aacAlgorithm.setNnOutput(nnOutput);
            aacAlgorithm.setAmbiguousLowerBoundaryValue(avhrracSchillerNNCloudAmbiguousLowerBoundaryValue);
            aacAlgorithm.setAmbiguousSureSeparationValue(avhrracSchillerNNCloudAmbiguousSureSeparationValue);
            aacAlgorithm.setSureSnowSeparationValue(avhrracSchillerNNCloudSureSnowSeparationValue);

            aacAlgorithm.setReflCh1(refl1);
            aacAlgorithm.setReflCh2(refl2);
            aacAlgorithm.setReflCh3(refl3);
            final double btCh4 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[3], 4) - 273.15;
            aacAlgorithm.setBtCh4(btCh4);
            final double btCh5 = AvhrrAcUtils.convertRadianceToBt(avhrrRadiance[4], 5) - 273.15;
            aacAlgorithm.setBtCh5(btCh5);

//            aacAlgorithm.setReflCh1Thresh(reflCh1Thresh);
//            aacAlgorithm.setReflCh2Thresh(reflCh2Thresh);
//            aacAlgorithm.setR2r1RatioThresh(r2r1RatioThresh);
//            aacAlgorithm.setR3r1RatioThresh(r3r1RatioThresh);
//            aacAlgorithm.setBtCh4Thresh(btCh4Thresh);
//            aacAlgorithm.setBtCh5Thresh(btCh5Thresh);

            setClassifFlag(targetSamples, aacAlgorithm);
            targetSamples[1].set(nnOutput[0]);
        } else {
            targetSamples[0].set(AvhrrAcConstants.F_INVALID, true);
            targetSamples[1].set(Float.NaN);
        }

        if (aacCopyRadiances) {
            targetSamples[2].set(sza);
            targetSamples[3].set(saa);
            targetSamples[4].set(vza);
            targetSamples[5].set(vaa);
            targetSamples[6].set(groundHeight);
            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_REFL_TL_BAND_NAMES.length; i++) {
                targetSamples[7 + i].set(sourceSamples[7 + i].getDouble());
            }
        }
    }

    @Override
    void setClassifFlag(WritableSample[] targetSamples, AvhrrAcAlgorithm algorithm) {
        targetSamples[0].set(AvhrrAcConstants.F_INVALID, algorithm.isInvalid());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD, algorithm.isCloud());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(AvhrrAcConstants.F_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(AvhrrAcConstants.F_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(AvhrrAcConstants.F_GLINT_RISK, algorithm.isGlintRisk());
        targetSamples[0].set(AvhrrAcConstants.F_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(AvhrrAcConstants.F_LAND, algorithm.isLand());
        // test:
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 1, algorithm.isReflCh1Bright());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 2, algorithm.isReflCh2Bright());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 3, algorithm.isR2R1RatioAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 4, algorithm.isR3R1RatioAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 5, algorithm.isCh4BtAboveThresh());
//        targetSamples[0].set(AvhrrAcConstants.F_LAND + 6, algorithm.isCh5BtAboveThresh());
    }

    @Override
    String getProductDatestring() {
//        TL-L1B-AVHRR_NOAA-20010509154840-fv0001
        // provides datestring as DDMMYY !!!
        final int productNameStartIndex = sourceProduct.getName().indexOf("TL");
        // allow names such as subset_of_TL-L1B-AVHRR_NOAA-20010509154840-fv0001.dim
        final String yy = sourceProduct.getName().substring(productNameStartIndex + 20, productNameStartIndex + 22);
        final String mm = sourceProduct.getName().substring(productNameStartIndex + 22, productNameStartIndex + 24);
        final String dd = sourceProduct.getName().substring(productNameStartIndex + 24, productNameStartIndex + 26);
        return dd + mm + yy;
    }

    @Override
    void setNoaaId() {
        noaaId = "14";
        // todo: identify from product metadata
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, "latitude");
        sampleConfigurer.defineSample(index++, "longitude");
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_SZA_TL_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_VZA_TL_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_SAA_TL_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_VAA_TL_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_GROUND_HEIGHT_TL_BAND_NAME);
        for (int i = 0; i < 6; i++) {
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_REFL_TL_BAND_NAMES[i]);
        }
        sampleConfigurer.defineSample(index, AvhrrAcConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        // the only standard band:
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.CLASSIF_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrAcConstants.SCHILLER_NN_OUTPUT_BAND_NAME);

        // radiances:
        if (aacCopyRadiances) {
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_SZA_TL_BAND_NAME);
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_VZA_TL_BAND_NAME);
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_SAA_TL_BAND_NAME);
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_VAA_TL_BAND_NAME);
            sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_GROUND_HEIGHT_TL_BAND_NAME);
            for (int i = 0; i < 6; i++) {
                sampleConfigurer.defineSample(index++, AvhrrAcConstants.AVHRR_AC_REFL_TL_BAND_NAMES[i]);
            }
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(AvhrrAcConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = AvhrrAcUtils.createAvhrrAcFlagCoding(AvhrrAcConstants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        productConfigurer.copyGeoCoding();
        AvhrrAcUtils.setupAvhrrAcClassifBitmask(getTargetProduct());
        Band nnValueBand = productConfigurer.addBand(AvhrrAcConstants.SCHILLER_NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        nnValueBand.setDescription("Schiller NN output value");
        nnValueBand.setUnit("dl");
        nnValueBand.setNoDataValue(Float.NaN);
        nnValueBand.setNoDataValueUsed(true);

        // reflectances and other source variables:
        if (aacCopyRadiances) {
            Band szaBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_SZA_TL_BAND_NAME, ProductData.TYPE_FLOAT32);
            szaBand.setDescription("sun zenith angle");
            szaBand.setUnit("deg");
            szaBand.setNoDataValue(Float.NaN);
            szaBand.setNoDataValueUsed(true);

            Band saaBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_SAA_TL_BAND_NAME, ProductData.TYPE_FLOAT32);
            saaBand.setDescription("sun azimuth angle");
            saaBand.setUnit("deg");
            saaBand.setNoDataValue(Float.NaN);
            saaBand.setNoDataValueUsed(true);

            Band vzaBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_VZA_TL_BAND_NAME, ProductData.TYPE_FLOAT32);
            vzaBand.setDescription("view zenith angle");
            vzaBand.setUnit("deg");
            vzaBand.setNoDataValue(Float.NaN);
            vzaBand.setNoDataValueUsed(true);

            Band vaaBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_VAA_TL_BAND_NAME, ProductData.TYPE_FLOAT32);
            vaaBand.setDescription("view azimuth angle");
            vaaBand.setUnit("deg");
            vaaBand.setNoDataValue(Float.NaN);
            vaaBand.setNoDataValueUsed(true);

            Band groundHeightBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_GROUND_HEIGHT_TL_BAND_NAME, ProductData.TYPE_FLOAT32);
            groundHeightBand.setDescription("ground height");
            groundHeightBand.setUnit("m");
            groundHeightBand.setNoDataValue(Float.NaN);
            groundHeightBand.setNoDataValueUsed(true);

            for (int i = 0; i < AvhrrAcConstants.AVHRR_AC_REFL_TL_BAND_NAMES.length; i++) {
                Band radianceBand = productConfigurer.addBand(AvhrrAcConstants.AVHRR_AC_REFL_TL_BAND_NAMES[i], ProductData.TYPE_FLOAT32);

                radianceBand.setNoDataValue(Float.NaN);
                radianceBand.setNoDataValueUsed(true);
                int reflIndex = 1;
                if (i == 3) {
                    radianceBand.setDescription("TOA radiance band 3b");
                    radianceBand.setUnit("mW/(m^2 sr cm^-1)");
                } else {
                    radianceBand.setDescription("TOA reflectance band " + (reflIndex++));     // todo: this index is wrong
                    radianceBand.setUnit("%");
                }
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrAcTimelineClassificationOp.class);
        }
    }
}
