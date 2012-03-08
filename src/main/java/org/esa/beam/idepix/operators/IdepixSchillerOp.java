/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.idepix.operators;

import com.bc.jnn.Jnn;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.gpf.operators.standard.BandMathsOp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Operator for calculating cloud using neural nets from Schiller
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "idepix.schiller",
                  version = "1.0",
                  authors = "Marco Zuehlke",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Computed a cloud mask using neural nets from Schiller.")
public class IdepixSchillerOp extends PixelOperator {

    private static final String NN_LAND = "schiller_7x3_1047.0_land.nna";
    private static final String NN_WATER = "schiller_7x3_526.2_water.nna";
    private static final String NN_LAND_WATER = "schiller_8x3_1706.7_lawat.nna";

    @SourceProduct
    private Product sourceProduct;

    private ThreadLocal<JnnNetWrapper> landNN;
    private ThreadLocal<JnnNetWrapper> waterNN;
    private ThreadLocal<JnnNetWrapper> landWaterNN;

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);

        productConfigurer.addBand("one_net", ProductData.TYPE_FLOAT32);
        productConfigurer.addBand("two_nets", ProductData.TYPE_FLOAT32);
        productConfigurer.addBand("snow", ProductData.TYPE_FLOAT32);

        productConfigurer.copyBands("reflec_7", "reflec_5", "reflec_3", "l1_flags");

        landNN = createWrapper(loadNeuralNet(NN_LAND), 15, 1);
        waterNN = createWrapper(loadNeuralNet(NN_WATER), 15, 1);
        landWaterNN = createWrapper(loadNeuralNet(NN_LAND_WATER), 15, 1);
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int bandId = 0; bandId < 15; bandId++) {
            sampleConfigurer.defineSample(bandId, "reflec_" + (bandId + 1));
        }
        BandMathsOp landOp = BandMathsOp.createBooleanExpressionBand("l1_flags.LAND_OCEAN", sourceProduct);
        Product landMaskProduct = landOp.getTargetProduct();
        sampleConfigurer.defineSample(15, landMaskProduct.getBandAt(0).getName(), landMaskProduct);
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "one_net");
        sampleConfigurer.defineSample(1, "two_nets");
        sampleConfigurer.defineSample(2, "snow");
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        targetSamples[0].set(process(landWaterNN.get(), sourceSamples));
        if (sourceSamples[15].getBoolean()) {
            targetSamples[1].set(process(landNN.get(), sourceSamples));
        } else {
            targetSamples[1].set(process(waterNN.get(), sourceSamples));
        }
        double rhoToa13 = sourceSamples[12].getDouble();
        double rhoToa14 = sourceSamples[13].getDouble();
        targetSamples[2].set((rhoToa13 - rhoToa14) / (rhoToa13 + rhoToa14));
    }

    private double process(JnnNetWrapper wrapper, Sample[] sourceSamples) {
        double[] nnIn = wrapper.nnIn;
        for (int i = 0; i < sourceSamples.length - 1; i++) {
            Sample sourceSample = sourceSamples[i];
            nnIn[i] = Math.log(sourceSample.getDouble());
        }
        double[] nnOut = wrapper.nnOut;
        wrapper.neuralNet.process(nnIn, nnOut);
        return nnOut[0];
    }

    private ThreadLocal<JnnNetWrapper> createWrapper(final JnnNet neuralNet, final int in, final int out) {
        return new ThreadLocal<JnnNetWrapper>() {
            @Override
            protected JnnNetWrapper initialValue() {
                return new JnnNetWrapper(neuralNet.clone(), in, out);
            }
        };
    }

    private JnnNet loadNeuralNet(String nnName) {
        try {
            InputStream inputStream = IdepixSchillerOp.class.getResourceAsStream(nnName);
            final InputStreamReader reader = new InputStreamReader(inputStream);

            try {
                Jnn.setOptimizing(true);
                return Jnn.readNna(reader);
            } finally {
                reader.close();
            }
        } catch (JnnException jnne) {
            throw new OperatorException(jnne);
        } catch (IOException ioe) {
            throw new OperatorException(ioe);
        }
    }

    private static class JnnNetWrapper {
        final JnnNet neuralNet;
        final double[] nnIn;
        final double[] nnOut;

        private JnnNetWrapper(JnnNet neuralNet, int in, int out) {
            this.neuralNet = neuralNet;
            this.nnIn = new double[in];
            this.nnOut = new double[out];
        }
    }
}
