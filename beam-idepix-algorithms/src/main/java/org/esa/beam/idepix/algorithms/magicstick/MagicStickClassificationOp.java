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

package org.esa.beam.idepix.algorithms.magicstick;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Operator for calculating cloud using a "Magic" expression
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "idepix.magicstick.classification",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zuehlke",
                  copyright = "(c) 2012 by Brockmann Consult",
                  description = "Computed a cloud mask using an expression.")
public class MagicStickClassificationOp extends Operator {

    @SourceProduct
    private Product sourceProduct;
    @Parameter(defaultValue = "(inrange(radiance_1,radiance_2,radiance_3,radiance_4,radiance_5,radiance_6,radiance_7,radiance_8,radiance_9,radiance_10,radiance_12,radiance_13,radiance_14,110.95469861477613,110.69702932052314,101.59082958102226,94.62313067913055,79.51220783032477,68.6891217874363,65.62491676956415,63.876969987526536,58.34850308345631,55.207481268793344,51.78550277114846,41.99686771351844,40.45033756317571,489.89222188759595,544.0888976398855,573.7061238512397,569.9747566282749,520.7842015372589,480.1853818874806,449.7885620817542,455.67995167709887,414.458504260052,401.41873468086123,239.06330769439228,233.93376255314797,295.2540603126399)) && !(inrange(radiance_1,radiance_2,radiance_3,radiance_4,radiance_5,radiance_6,radiance_7,radiance_8,radiance_9,radiance_10,radiance_12,radiance_13,radiance_14,109.0123566025868,111.7785839997232,105.85386314988136,99.39786380529404,85.87481799721718,76.61522564012557,73.87684653699398,72.18865965120494,66.22840957343578,63.43021900951862,59.61015165760182,48.66481214854866,47.01183261256665,120.68616730719805,126.78905058186501,126.28453273698688,121.63159835338593,109.90139073040336,100.73953618761152,99.39998243004084,97.87489380128682,90.23410519957542,87.59435298293829,83.00624355231412,68.54670364782214,66.31586839398369))")
    private String cloudExpression;

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

        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
        BandMathsOp cloudOp = BandMathsOp.createBooleanExpressionBand(cloudExpression, sourceProduct);
        Product cloudMaskProduct = cloudOp.getTargetProduct();
        cloudMaskBand = cloudMaskProduct.getBandAt(0);

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
            super(MagicStickClassificationOp.class, "idepix.magicstick.classification");
        }
    }
}
