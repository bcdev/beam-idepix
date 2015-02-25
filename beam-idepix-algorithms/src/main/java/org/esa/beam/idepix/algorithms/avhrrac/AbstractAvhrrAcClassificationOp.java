package org.esa.beam.idepix.algorithms.avhrrac;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.idepix.util.*;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.util.math.RsMathUtils;

import javax.media.jai.RenderedOp;
import javax.media.jai.operator.TransposeDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Basic operator for GlobAlbedo pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.avhrrac.abstract.classification",
        version = "2.2",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Abstract basic operator for pixel classification from AVHRR L1b data.")
public abstract class AbstractAvhrrAcClassificationOp extends PixelOperator {

    @SourceProduct(alias = "aacl1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

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
    boolean flipSourceImages;

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


    static final int ALBEDO_TO_RADIANCE = 0;
    static final int RADIANCE_TO_ALBEDO = 1;

    static final String SCHILLER_AVHRRAC_NET_NAME = "6x3_114.1.net";

    ThreadLocal<SchillerNeuralNetWrapper> avhrracNeuralNet;

    AvhrrAcAuxdata.Line2ViewZenithTable vzaTable;

    SunPosition sunPosition;

    String noaaId;


    public Product getSourceProduct() {
        return sourceProduct;
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        runAvhrrAcAlgorithm(x, y, sourceSamples, targetSamples);
    }

    void flipSourceImages() {
        for (Band b : sourceProduct.getBands()) {
            final RenderedOp flippedImage = flipImage(b.getSourceImage());
            b.setSourceImage(flippedImage);
        }
        for (TiePointGrid tpg : sourceProduct.getTiePointGrids()) {
            final RenderedOp flippedImage = flipImage(tpg.getSourceImage());
            tpg.setSourceImage(flippedImage);
        }
    }

    private RenderedOp flipImage(MultiLevelImage sourceImage) {
        final RenderedOp verticalFlippedImage = TransposeDescriptor.create(sourceImage, TransposeDescriptor.FLIP_VERTICAL, null);
        return TransposeDescriptor.create(verticalFlippedImage, TransposeDescriptor.FLIP_HORIZONTAL, null);
    }

    void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(SCHILLER_AVHRRAC_NET_NAME)) {
            avhrracNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    GeoPos computeSatPosition(int y) {
        return getGeoPos(sourceProduct.getSceneRasterWidth() / 2, y);
    }

    void computeSunPosition() {
        final Calendar calendar = AvhrrAcUtils.getProductDateAsCalendar(getProductDatestring());
        sunPosition = SunPositionCalculator.calculate(calendar);
    }

    int getDoy() {
        return IdepixUtils.getDoyFromYYMMDD(getProductDatestring());
    }

    GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    double convertBetweenAlbedoAndRadiance(double input, double sza, int mode, int bandIndex) {
        // follows GK formula
        final double distanceCorr = 1.0 + 0.033 * Math.cos(2.0 * Math.PI * getDoy() / 365.0);
        float[] integrSolarSpectralIrrad = new float[3];     // F
        float[] spectralResponseWidth = new float[3];        // W
        switch (noaaId) {
            case "11":
                // NOAA 11
                integrSolarSpectralIrrad[0] = 184.1f;
                integrSolarSpectralIrrad[1] = 241.1f;
                integrSolarSpectralIrrad[2] = 241.1f;  // todo: we don't have this for band 3, might be useless
                spectralResponseWidth[0] = 0.1130f;
                spectralResponseWidth[1] = 0.229f;
                spectralResponseWidth[2] = 0.229f;     // s.a.
                break;
            case "14":
                // NOAA 14
                integrSolarSpectralIrrad[0] = 221.42f;
                integrSolarSpectralIrrad[1] = 252.29f;
                integrSolarSpectralIrrad[2] = 252.29f;  // s.a.
                spectralResponseWidth[0] = 0.136f;
                spectralResponseWidth[1] = 0.245f;
                spectralResponseWidth[2] = 0.245f;      // s.a.
                break;
            default:
                throw new OperatorException("Cannot parse source product name " + sourceProduct.getName() + " properly.");
        }
        // GK: R=A (F/(100 PI W  cos(sun_zenith)  abstandkorrektur))
        final double conversionFactor = integrSolarSpectralIrrad[bandIndex] /
                (100.0 * Math.PI * spectralResponseWidth[bandIndex] * Math.cos(sza * MathUtils.DTOR) * distanceCorr);
        double result;
        if (mode == ALBEDO_TO_RADIANCE) {
            result = input * conversionFactor;
        } else if (mode == RADIANCE_TO_ALBEDO) {
            result = input / conversionFactor;
        } else {
            throw new IllegalArgumentException("wrong mode " + mode + " for albedo/radance converison");
        }
        return result;
    }


    abstract void setClassifFlag(WritableSample[] targetSamples, AvhrrAcAlgorithm algorithm);

    abstract void runAvhrrAcAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples);

    abstract void setNoaaId();

    abstract String getProductDatestring();

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AbstractAvhrrAcClassificationOp.class);
        }
    }
}
