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

package org.esa.beam.idepix.algorithms.schiller;

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
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.CloudBuffer;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.SchillerAlgorithm;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Rectangle;
import java.io.IOException;

/**
 * Operator for calculating cloud using neural nets from Schiller
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "idepix.schiller.classification",
                  version = "2.2",
                  internal = true,
                  authors = "Marco Zuehlke",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Computed a cloud mask using neural nets from H. Schiller.")
public class SchillerClassificationOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    private SchillerAlgorithm landNN;
    private SchillerAlgorithm waterNN;

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

        Band flagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createIdepixFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
        landNN = new SchillerAlgorithm(SchillerAlgorithm.Net.LAND);
        waterNN = new SchillerAlgorithm(SchillerAlgorithm.Net.WATER);

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        final Tile[] srcTiles = new Tile[16];
        for (int i = 0; i < 15; i++) {
            srcTiles[i] = getSourceTile(sourceProduct.getBand("reflec_" + (i + 1)), rectangle);
        }
        srcTiles[15] = getSourceTile(sourceProduct.getBand("l1_flags"), rectangle);
        for (final Tile.Pos pos : targetTile) {
            boolean isWater = true;
            try {
                GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pos.x + 0.5f, pos.y + 0.5f), null);
                isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
            } catch (IOException ignore) {
            }

            boolean isCloud;
            SchillerAlgorithm.Accessor accessor = new SchillerAlgorithm.Accessor() {
                @Override
                public double get(int index) {
                    return srcTiles[index].getSampleDouble(pos.x, pos.y);
                }
            };
            if (isWater) {
                isCloud = (double) waterNN.compute(accessor) > 1.35;
            } else {
                isCloud = (double) landNN.compute(accessor) > 1.25;
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
        CloudBuffer.computeCloudBufferLC(targetTile, IdepixConstants.F_CLOUD, IdepixConstants.F_CLOUD_BUFFER);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(SchillerClassificationOp.class);
        }
    }
}
