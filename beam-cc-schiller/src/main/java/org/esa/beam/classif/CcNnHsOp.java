package org.esa.beam.classif;

import org.esa.beam.classif.algorithm.AlgorithmFactory;
import org.esa.beam.classif.algorithm.CCAlgorithm;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.*;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;

import java.awt.*;

@OperatorMetadata(alias = "Meris.CCNNHS",
        version = "1.0",
        authors = "Tom Block",
        copyright = "(c) 2013 by Brockmann Consult",
        description = "Computing cloud masks using neural networks by H.Schiller")
public class CcNnHsOp extends PixelOperator {

    @SourceProduct
    private Product sourceProduct;

    @Parameter(defaultValue = "NOT l1_flags.INVALID",
            description = "A flag expression that defines pixels to be processed.")
    private String validPixelExpression;

    @Parameter(defaultValue = AlgorithmFactory.ALGORITHM_2013_03_01,
            valueSet = {AlgorithmFactory.ALGORITHM_2013_03_01,
                    AlgorithmFactory.ALGORITHM_2013_05_09},
            description = "Select algorithm for processing.")
    private String algorithmName;

    private VirtualBandOpImage validOpImage;

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
        return validOpImage.getData(localRect).getSample(x, y, 0) != 0;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CcNnHsOp.class);
        }
    }
}
