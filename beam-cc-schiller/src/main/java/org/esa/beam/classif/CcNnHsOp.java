package org.esa.beam.classif;

import org.esa.beam.classif.algorithm.AlgorithmFactory;
import org.esa.beam.classif.algorithm.CCAlgorithm;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;

import java.awt.Rectangle;

// todo nf - discuss operator action
// todo nf - discuss unit-level testing of metadata values

@OperatorMetadata(alias = "Meris.CCNNHS",
                  version = "2.0.2",
                  authors = "H. Schiller (Algorithm), T. Block, N. Fomferra (Implementation)",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Computing cloud masks using neural networks by H.Schiller")
public class CcNnHsOp extends PixelOperator {

    public static final String ALGORITHM_2013_03_01 = AlgorithmFactory.ALGORITHM_2013_03_01;
    public static final String ALGORITHM_2013_05_09 = AlgorithmFactory.ALGORITHM_2013_05_09;

    @SourceProduct
    private Product sourceProduct;

    @Parameter(defaultValue = "NOT l1_flags.INVALID",
               description = "A flag expression that defines pixels to be processed.")
    private String validPixelExpression;

    @Parameter(defaultValue = ALGORITHM_2013_03_01,
               valueSet = {ALGORITHM_2013_03_01, ALGORITHM_2013_05_09},
               description = "Select algorithm for processing.")
    private String algorithmName;

    // todo nf - Introduce this parameter (not easily done in current design)
    // @Parameter(defaultValue = "true", description = "Whether to output TOA reflectances")
    // private boolean outputToaReflectances;

    // todo nf - discuss faster and easier ways to access mask values
    private VirtualBandOpImage validOpImage;

    public void setValidPixelExpression(String validPixelExpression) {
        this.validPixelExpression = validPixelExpression;
    }

    public void setAlgorithmName(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    private ThreadLocal<Rectangle> sampleRegion = new ThreadLocal<Rectangle>() {
        @Override
        protected Rectangle initialValue() {
            return new Rectangle(0, 0, 1, 1);
        }
    };

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        final CCAlgorithm ccAlgorithm = AlgorithmFactory.get(algorithmName);
        if (isSampleValid(x, y)) {
            ccAlgorithm.computePixel(sourceSamples, targetSamples);
        } else {
            ccAlgorithm.setToUnprocessed(targetSamples);
        }
    }

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();

        final CCAlgorithm ccAlgorithm = AlgorithmFactory.get(algorithmName);
        ccAlgorithm.prepareInputs(sourceProduct);

        // todo nf - must dispose this!
        validOpImage = VirtualBandOpImage.createMask(validPixelExpression,
                                                     sourceProduct,
                                                     ResolutionLevel.MAXRES);
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        final CCAlgorithm ccAlgorithm = AlgorithmFactory.get(algorithmName);
        ccAlgorithm.configureSourceSamples(sampleConfigurer);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        final CCAlgorithm ccAlgorithm = AlgorithmFactory.get(algorithmName);
        ccAlgorithm.configureTargetSamples(sampleConfigurer);
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        final CCAlgorithm ccAlgorithm = AlgorithmFactory.get(algorithmName);
        ccAlgorithm.configureTargetProduct(sourceProduct, productConfigurer);
    }

    private boolean isSampleValid(int x, int y) {
        final Rectangle localRect = sampleRegion.get();
        localRect.setLocation(x, y);
        // todo nf - optimize me: this is a very slow pixel access here
        return validOpImage.getData(localRect).getSample(x, y, 0) != 0;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CcNnHsOp.class);
        }
    }
}
