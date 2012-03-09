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
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
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
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

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

    private WatermaskClassifier watermaskClassifier;
    private GeoCoding geoCoding;

    @Override
    protected void prepareInputs() throws OperatorException {
        geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding == null) {
            throw new OperatorException("Source product has no geocoding");
        }
        if (!geoCoding.canGetGeoPos()) {
            throw new OperatorException("Source product has no usable geocoding");
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);

        Product targetProduct = productConfigurer.getTargetProduct();
        Band flagBand = productConfigurer.addBand(GACloudScreeningOp.GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createGAFlagCoding(GACloudScreeningOp.GA_CLOUD_FLAGS);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        IdepixUtils.setupGlobAlbedoCloudscreeningBitmasks(targetProduct);

        landNN = createWrapper(loadNeuralNet(NN_LAND), 15, 1);
        waterNN = createWrapper(loadNeuralNet(NN_WATER), 15, 1);
        //landWaterNN = createWrapper(loadNeuralNet(NN_LAND_WATER), 15, 1);


        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        for (int bandId = 0; bandId < 15; bandId++) {
            sampleConfigurer.defineSample(bandId, "reflec_" + (bandId + 1));
        }
        sampleConfigurer.defineSample(15, "l1_flags");
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, GACloudScreeningOp.GA_CLOUD_FLAGS);
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        boolean isWater = true;
        try {
            GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x + 0.5f, y + 0.5f), null);
            isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
        } catch (IOException ignore) {
        }

        boolean isCloud;
        double nnResult;
        if (isWater) {
            nnResult = process(waterNN.get(), sourceSamples);
            isCloud = nnResult > 1.35;
        } else {
            nnResult = process(landNN.get(), sourceSamples);
            isCloud = nnResult > 1.25;
        }

        // snow
        double rhoToa13 = sourceSamples[12].getDouble();
        double rhoToa14 = sourceSamples[13].getDouble();
        double mdsi = (rhoToa13 - rhoToa14) / (rhoToa13 + rhoToa14);
        boolean isL1bBright = BitSetter.isFlagSet(sourceSamples[15].getInt(), 5);
        boolean isSnow = mdsi > 0.01 && isL1bBright;


        // cloud flag
        int resultFlag = 0;
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLOUD, isCloud);
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_LAND, !isWater && !isCloud && !isSnow);
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_WATER, isWater && !isCloud && !isSnow);
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_SNOW, !isWater && !isCloud && isSnow);
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_LAND, !isWater);
        resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_WATER, isWater);

        targetSamples[0].set(resultFlag);
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
