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

package org.esa.beam.idepix.algorithms.scapem;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Operator for calculating cloud using Scape-M scheme from L. Guanter, FUB
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "idepix.scapem.classification",
                  version = "2.0.2-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Calculates cloud using Scape-M scheme from L. Guanter, FUB.")
public class FubScapeMClassificationOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    private WatermaskClassifier watermaskClassifier;
    private GeoCoding geoCoding;
    private Band cloudMaskBand;

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

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile cloudMaskTile = getSourceTile(cloudMaskBand, rectangle);
        for (Tile.Pos pos : targetTile) {
            boolean isCloud = cloudMaskTile.getSampleBoolean(pos.x, pos.y);
            GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pos.x + 0.5f, pos.y + 0.5f), null);
            boolean isWater = true;
            try {
                isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
            } catch (IOException ignore) {
            }
            int cloudFlag = 0;
            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_CLOUD, isCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_CLEAR_LAND, !isCloud && !isWater);
            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_CLEAR_WATER, !isCloud && isWater);
            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_LAND, !isWater);
            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_WATER, isWater);
            targetTile.setSample(pos.x, pos.y, cloudFlag);
        }
        IdepixUtils.setCloudBufferLC(targetBand.getName(), targetTile, rectangle);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FubScapeMClassificationOp.class, "idepix.scapem.classification");
        }
    }
}
