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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.idepix.util.NeuralNetWrapper;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Rectangle;
import java.io.IOException;

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
public class IdepixSchillerOp extends Operator {

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
    public void initialize() throws OperatorException {
        geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding == null) {
            throw new OperatorException("Source product has no geocoding");
        }
        if (!geoCoding.canGetGeoPos()) {
            throw new OperatorException("Source product has no usable geocoding");
        }
        Product targetProduct = new Product(sourceProduct.getName(),
                                            sourceProduct.getProductType(),
                                            sourceProduct.getSceneRasterWidth(),
                                            sourceProduct.getSceneRasterHeight());

        Band flagBand = targetProduct.addBand(GACloudScreeningOp.GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createGAFlagCoding(GACloudScreeningOp.GA_CLOUD_FLAGS);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        IdepixUtils.setupGlobAlbedoCloudscreeningBitmasks(targetProduct);

        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
        landNN = NeuralNetWrapper.create(this.getClass().getResourceAsStream(NN_LAND), 15, 1);
        waterNN = NeuralNetWrapper.create(this.getClass().getResourceAsStream(NN_WATER), 15, 1);

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile[] srcTiles = new Tile[16];
        for (int i = 0; i < 15; i++) {
            srcTiles[i] = getSourceTile(sourceProduct.getBand("reflec_" + (i+1)), rectangle);
        }
        srcTiles[15] = getSourceTile(sourceProduct.getBand("l1_flags"), rectangle);
        for (Tile.Pos pos : targetTile) {
            boolean isWater = true;
            try {
                GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pos.x + 0.5f, pos.y + 0.5f), null);
                isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
            } catch (IOException ignore) {
            }
    
            boolean isCloud;
            double nnResult;
            if (isWater) {
                nnResult = process(waterNN.get(), srcTiles, pos.x, pos.y);
                isCloud = nnResult > 1.35;
            } else {
                nnResult = process(landNN.get(), srcTiles, pos.x, pos.y);
                isCloud = nnResult > 1.25;
            }
    
            // snow
            double rhoToa13 = srcTiles[12].getSampleDouble(pos.x, pos.y);
            double rhoToa14 = srcTiles[13].getSampleDouble(pos.x, pos.y);
            double mdsi = (rhoToa13 - rhoToa14) / (rhoToa13 + rhoToa14);
            boolean isL1bBright = BitSetter.isFlagSet(srcTiles[15].getSampleInt(pos.x, pos.y), 5);
            boolean isSnow = mdsi > 0.01 && isL1bBright;

            // cloud flag
            int resultFlag = 0;
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLOUD, isCloud);
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_LAND, !isWater && !isCloud && !isSnow);
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_WATER, isWater && !isCloud && !isSnow);
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_CLEAR_SNOW, !isWater && !isCloud && isSnow);
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_LAND, !isWater);
            resultFlag = BitSetter.setFlag(resultFlag, IdepixConstants.F_WATER, isWater);
    
            targetTile.setSample(pos.x, pos.y, resultFlag);
        }
        GACloudScreeningOp.setCloudBufferLC(targetBand, targetTile, rectangle);
    }

    private double process(NeuralNetWrapper wrapper, Tile[] srcTiles, int x, int y) {
        double[] nnIn = wrapper.getInputVector();
        for (int i = 0; i < srcTiles.length - 1; i++) {
            nnIn[i] = Math.log(srcTiles[i].getSampleDouble(x,y));
        }
        double[] nnOut = wrapper.getOutputVector();
        wrapper.getNeuralNet().process(nnIn, nnOut);
        return nnOut[0];
    }

}
