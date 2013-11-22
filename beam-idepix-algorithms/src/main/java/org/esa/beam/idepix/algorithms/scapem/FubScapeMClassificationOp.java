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
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;

/**
 * Operator for calculating cloud using Scape-M scheme from L. Guanter, FUB
 *
 * @author MarcoZ
 */
@OperatorMetadata(alias = "idepix.scapem.classification",
                  version = "2.0.2-SNAPSHOT",
                  internal = true,
                  authors = "Olaf Danne, Tonio Fincke",
                  copyright = "(c) 2013 by Brockmann Consult",
                  description = "Calculates cloud using Scape-M scheme from L. Guanter, FUB.")
public class FubScapeMClassificationOp extends Operator {

    public static final String RHO_TOA_BAND_PREFIX = "rho_toa";

    @SourceProduct
    private Product sourceProduct;

    private WatermaskClassifier watermaskClassifier;
    private GeoCoding geoCoding;
    private Product rad2reflProduct;
    private static final int num_of_visible_bands = 8;

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
        FlagCoding flagCoding = createScapeMFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        setupCloudScreeningBitmasks(targetProduct);

        try {
            watermaskClassifier = new WatermaskClassifier(50);
        } catch (IOException e) {
            throw new OperatorException("Failed to init water mask", e);
        }
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);
//        rad2reflProduct.addBand(new VirtualBand(RHO_TOA_BAND_PREFIX + "_AVG_VIS", ProductData.TYPE_FLOAT32,
//                                                rad2reflProduct.getSceneRasterWidth(),
//                                                rad2reflProduct.getSceneRasterHeight(),
//                                                ));
//        for(int i=0; i< rad2reflProduct.getNumBands(); i++) {
//            final Band reflecBand = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + (i + 1));
//
//        }
//        BandMathsOp cloudOp = BandMathsOp.createBooleanExpressionBand(cloudExpression, sourceProduct);
//        Product cloudMaskProduct = cloudOp.getTargetProduct();
//        cloudMaskBand = cloudMaskProduct.getBandAt(0);

        setTargetProduct(targetProduct);
    }

    private static void setupCloudScreeningBitmasks(Product gaCloudProduct) {
        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;

        mask = Mask.BandMathsType.create("F_CLOUD_1", "Presumably cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_1",
                                         Color.red.darker(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_2", "Certainly cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_2",
                                         Color.red.brighter(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_LAKES", "pixels over lakes", w, h,
                                         "cloud_classif_flags.F_LAKES", Color.blue, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
    }

    private static FlagCoding createScapeMFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_CLOUD_1", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("F_CLOUD_2", BitSetter.setFlag(0, 1), null);
        flagCoding.addFlag("F_LAKES", BitSetter.setFlag(0, 2), null);
        return flagCoding;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
//        Tile cloudMaskTile = getSourceTile(cloudMaskBand, rectangle);
//        final Band radiance1Band = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME);
//        final Band radiance1Band = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME);
//        final Band radiance1Band = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME);
//        getSourceTile(EnvisatConstants.MERIS_L1B_RADIANCE_1_BAND_NAME, rectangle);
        Tile[] reflectanceTiles = new Tile[num_of_visible_bands + 2];
        for(int i = 1; i <= num_of_visible_bands + 1; i++) {
            final Band reflBand = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + i);
            reflectanceTiles[i - 1] = getSourceTile(reflBand, rectangle);
//            pAvTOA += reflBand.getSampleFloat(pos.x, pos.y);
        }
        final Band refl13Band = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + 13);
        reflectanceTiles[num_of_visible_bands + 1] = getSourceTile(refl13Band, rectangle);

        final TiePointGrid altitudeGrid = sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
        Tile altitudeTile = getSourceTile(altitudeGrid, rectangle);
        final Band l1FlagsBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        final Tile l1FlagsTile = getSourceTile(l1FlagsBand, rectangle);

//        final Band reflec1Band = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + 1);
//        final Tile reflec1Tile = getSourceTile(reflec1Band, rectangle);
//        final Band reflec8Band = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + 8);
//        final Tile reflec8Tile = getSourceTile(reflec8Band, rectangle);
//        final Band reflec9Band = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + 9);
//        final Tile reflec9Tile = getSourceTile(reflec9Band, rectangle);
        for (Tile.Pos pos : targetTile) {
//            boolean isCloud = cloudMaskTile.getSampleBoolean(pos.x, pos.y);
            float pAvTOA = 0;
            for(int i = 0; i < num_of_visible_bands; i++) {
                pAvTOA += reflectanceTiles[i].getSampleFloat(pos.x, pos.y);
//                final Band reflBand = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + i);
//                pAvTOA += reflBand.getSampleFloat(pos.x, pos.y);
            }
            pAvTOA /= num_of_visible_bands;
            float p1TOA = reflectanceTiles[0].getSampleFloat(pos.x, pos.y);
            float p8TOA = reflectanceTiles[7].getSampleFloat(pos.x, pos.y);
            float p9TOA = reflectanceTiles[8].getSampleFloat(pos.x, pos.y);
            float p13TOA = reflectanceTiles[9].getSampleFloat(pos.x, pos.y);
            final float altitude = altitudeTile.getSampleFloat(pos.x, pos.y);
            boolean isLand = l1FlagsTile.getSampleBit(pos.x, pos.y, Constants.L1_F_LAND);
            boolean isInvalid = l1FlagsTile.getSampleBit(pos.x, pos.y, Constants.L1_F_INVALID);
            boolean isCoast = l1FlagsTile.getSampleBit(pos.x, pos.y, Constants.L1_F_COAST);

            GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(pos.x + 0.5f, pos.y + 0.5f), null);
            boolean isWater = true;
            try {
                isWater = watermaskClassifier.isWater(geoPos.lat, geoPos.lon);
            } catch (IOException ignore) {
            }

//            boolean isPresumablyCloud = pAvTOA > 0.27 || altitude > 2500 || isInvalid || p1TOA > 0.2 && p1TOA > p8TOA;
            boolean isPresumablyCloud = pAvTOA > 0.27 || altitude > 2500 || isInvalid || (p1TOA > 0.2 && p1TOA > p8TOA);
//            boolean isCertainlyCloud = pAvTOA > 0.3 && p1TOA > 0.23 && p1TOA > p9TOA;
            boolean isCertainlyCloud = pAvTOA > 0.3 || altitude > 2500 || isInvalid || (p1TOA > 0.23 && p1TOA > p9TOA);

//            boolean isLake = isWater && p13TOA < refl_water_threshold;

            int cloudFlag = 0;
            cloudFlag = BitSetter.setFlag(cloudFlag, 0, isPresumablyCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, 1, isCertainlyCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, 2, true);
//            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_LAND, !isWater);
//            cloudFlag = BitSetter.setFlag(cloudFlag, IdepixConstants.F_WATER, isWater);
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
