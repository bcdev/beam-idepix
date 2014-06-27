package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.util.StringUtils;

/**
 * Operator for OC-CCI MODIS cloud screening
 * todo: implement (see GlobAlbedoMerisClassificationOp)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification",
                  version = "3.0-EVOLUTION-SNAPSHOT",
                  copyright = "(c) 2014 by Brockmann Consult",
                  description = "OC-CCI pixel classification operator.",
                  internal = true)
public class OccciClassificationOp extends PixelOperator {

    @SourceProduct(alias = "refl", description = "MODIS/SeaWiFS L1b reflectance product")
    private Product reflProduct;

    @Parameter(description = "Defines the sensor type to use. If the parameter is not set, the product type defined by the input file is used.")
    String sensorTypeString;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
               description = "Resolution in m/pixel")
    private int wmResolution;

    private SensorContext sensorContext;

    private SchillerAlgorithm waterNN;

    @Override
    protected void prepareInputs() throws OperatorException {
        sensorContext = SensorContextFactory.fromTypeString(getSensorTypeString());
        sensorContext.init(reflProduct);

        waterNN = new SchillerAlgorithm(SchillerAlgorithm.Net.WATER);
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        OccciAlgorithm algorithm;
        switch (sensorContext.getSensor()) {
            case MODIS:
                algorithm = createOccciModisAlgorithm(sourceSamples);
                break;
            case SEAWIFS:
                algorithm = createOccciSeaWifsAlgorithm(sourceSamples);
                break;
            default:
                throw new IllegalArgumentException("Invalid sensor: " + sensorContext.getSensor());
        }
        setClassifFlag(targetSamples, y, x, algorithm);
    }

    private void setClassifFlag(WritableSample[] targetSamples, int y, int x, OccciAlgorithm algorithm) {
        targetSamples[0].set(Constants.F_INVALID, algorithm.isInvalid());
        targetSamples[0].set(Constants.F_CLOUD, algorithm.isCloud());
        targetSamples[0].set(Constants.F_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(Constants.F_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(Constants.F_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(Constants.F_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(Constants.F_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(Constants.F_MIXED_PIXEL, algorithm.isMixedPixel());
        targetSamples[0].set(Constants.F_GLINT_RISK, algorithm.isGlintRisk());
        targetSamples[0].set(Constants.F_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(Constants.F_LAND, algorithm.isLand());
    }

    private OccciSeawifsAlgorithm createOccciSeaWifsAlgorithm(Sample[] sourceSamples) {
        OccciSeawifsAlgorithm occciAlgorithm = new OccciSeawifsAlgorithm();

        float[] seawifsReflectance = new float[sensorContext.getNumSpectralInputBands()];
        for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
            seawifsReflectance[i] = sourceSamples[Constants.SEAWIFS_SRC_RAD_OFFSET + i].getFloat();
        }

        occciAlgorithm.setRefl(seawifsReflectance);

        return occciAlgorithm;
    }

    private OccciModisAlgorithm createOccciModisAlgorithm(Sample[] sourceSamples) {
        OccciModisAlgorithm occciAlgorithm = new OccciModisAlgorithm();

        final float[] modisReflectance = new float[sensorContext.getNumSpectralInputBands()];
        for (int i = 0; i < sensorContext.getNumSpectralInputBands(); i++) {
            modisReflectance[i] = sourceSamples[Constants.MODIS_SRC_RAD_OFFSET + i].getFloat();
        }

        occciAlgorithm.setWaterNN(null);
        occciAlgorithm.setAccessor(null);
        // activate this once we got a NN for MODIS from Schiller...
//        SchillerAlgorithm.Accessor accessor = new SchillerAlgorithm.Accessor() {
//            @Override
//            public double get(int index) {
//                return modisReflectance[index];
//            }
//        };
//        occciAlgorithm.setWaterNN(waterNN);
//        occciAlgorithm.setAccessor(accessor);

        occciAlgorithm.setRefl(modisReflectance);

        return occciAlgorithm;
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sensorContext.configureSourceSamples(sampleConfigurer, false);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        // the only band:
        sampleConfigurer.defineSample(0, Constants.CLASSIF_BAND_NAME);
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);
        Band classifFlagBand = productConfigurer.addBand(Constants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = OccciUtils.createOccciFlagCoding(Constants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        OccciUtils.setupOccciClassifBitmask(getTargetProduct());
    }

    String getSensorTypeString() {
        if (StringUtils.isNotNullAndNotEmpty(sensorTypeString)) {
            return sensorTypeString;
        } else {
            return reflProduct.getProductType();
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OccciClassificationOp.class);
        }
    }

}
