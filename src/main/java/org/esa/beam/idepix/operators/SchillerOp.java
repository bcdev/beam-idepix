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

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeFilter;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.SampleOperator;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.idepix.util.NeuralNetWrapper;
import org.esa.beam.meris.radiometry.MerisRadiometryCorrectionOp;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Operator for calculating cloud using neural nets from Schiller
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "SchillerCloud",
                  version = "1.0",
                  authors = "Marco Zuehlke",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Computed a cloud mask using neural nets from Schiller.")
public class SchillerOp extends SampleOperator {

    private static final String NN_LAND = "schiller_7x3_1047.0_land.nna";
    private static final String NN_WATER = "schiller_7x3_526.2_water.nna";
    private static final String NN_LAND_WATER = "schiller_8x3_1706.7_lawat.nna";

    @SourceProduct
    private Product sourceProduct;

    private ThreadLocal<NeuralNetWrapper> landNN;
    private ThreadLocal<NeuralNetWrapper> waterNN;
    private ThreadLocal<NeuralNetWrapper> landWaterNN;

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
        final Product targetProduct = productConfigurer.getTargetProduct();
        targetProduct.setName(sourceProduct.getName());

        productConfigurer.copyBands(new ProductNodeFilter<Band>() {
            @Override
            public boolean accept(Band productNode) {
                return !targetProduct.containsBand(productNode.getName());
            }
        });

        productConfigurer.addBand("all_net", ProductData.TYPE_FLOAT32);
        productConfigurer.addBand("land_net", ProductData.TYPE_FLOAT32);
        productConfigurer.addBand("water_net", ProductData.TYPE_FLOAT32);
        productConfigurer.addBand("water_fraction", ProductData.TYPE_FLOAT32);

        addMask(targetProduct, "LAND", "water_fraction == 0", Color.GREEN);
        addMask(targetProduct, "WATER", "water_fraction == 100", Color.BLUE);
        addMask(targetProduct, "LC_CLOUD", "water_fraction > 0 ? water_net > 1.35 : land_net > 1.25", Color.YELLOW);

        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
        landNN = NeuralNetWrapper.create(this.getClass().getResourceAsStream(NN_LAND), 15, 1);
        waterNN = NeuralNetWrapper.create(this.getClass().getResourceAsStream(NN_WATER), 15, 1);
        landWaterNN = NeuralNetWrapper.create(this.getClass().getResourceAsStream(NN_LAND_WATER), 15, 1);
    }
    
    private static void addMask(Product product, String name, String expression, Color color) {
        int w = product.getSceneRasterWidth();
        int h = product.getSceneRasterHeight();
        product.getMaskGroup().add(Mask.BandMathsType.create(name, "", w, h, expression, color, 0.5f));
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(0, "all_net");
        sampleConfigurer.defineSample(1, "land_net");
        sampleConfigurer.defineSample(2, "water_net");
        sampleConfigurer.defineSample(3, "water_fraction");
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        Map<String, Object> rhoThoaParameters = new HashMap<String, Object>(3);
        rhoThoaParameters.put("doRadToRefl", true);
        Product rhoToaProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisRadiometryCorrectionOp.class),
                                                  rhoThoaParameters, sourceProduct);
        for (int i = 0; i < 15; i++) {
            sampleConfigurer.defineSample(i, "reflec_" + (i + 1), rhoToaProduct);
        }
    }

    @Override
    protected void computeSample(int x, int y, Sample[] sourceSamples, WritableSample targetSample) {
        float result = Float.NaN;
        switch (targetSample.getIndex()) {
            case 0:
                result = process(landWaterNN.get(), sourceSamples);
                break;
            case 1:
                result = process(landNN.get(), sourceSamples);
                break;
            case 2:
                result = process(waterNN.get(), sourceSamples);
                break;
            case 3:
                result = watermaskClassifier.getWaterMaskFraction(geoCoding, new PixelPos(x, y), 3, 3);
                break;
        }
        targetSample.set(result);
    }

    private float process(NeuralNetWrapper wrapper, Sample[] sourceSamples) {
        double[] nnIn = wrapper.getInputVector();
        for (int i = 0; i < sourceSamples.length; i++) {
            nnIn[i] = Math.log(sourceSamples[i].getDouble());
        }
        double[] nnOut = wrapper.getOutputVector();
        wrapper.getNeuralNet().process(nnIn, nnOut);
        return (float)nnOut[0];
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(SchillerOp.class);
        }
    }

}
