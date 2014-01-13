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
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.IdepixProducts;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;

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

    @Parameter(description = "Reflectance Threshold for reflectance 12", defaultValue = "0.08")
    private float reflectance_water_threshold;

    @Parameter(description = "The thickness of the coastline in kilometers.", defaultValue = "20.0")
    private float thicknessOfCoast;

    @Parameter(description = "The minimal size for a water region to be acknowledged as an ocean in km².", defaultValue = "1600")
    private float minimumOceanSize;

    @Parameter(description = "Whether or not to calculate a lake mask", defaultValue = "true")
    private boolean calculateLakes;

    private Product rad2reflProduct;
    private static final int num_of_visible_bands = 8;
    private Product waterProduct;

    @Override
    public void initialize() throws OperatorException {
        GeoCoding geoCoding = sourceProduct.getGeoCoding();
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

        if (calculateLakes) {
            Operator operator = new FubScapeMLakesOp();
            operator.setSourceProduct(sourceProduct);
            operator.setParameter("thicknessOfCoast", thicknessOfCoast);
            operator.setParameter("minimumOceanSize", minimumOceanSize);
            operator.setParameter("calculateLakes", calculateLakes);
            waterProduct = operator.getTargetProduct();
        }

        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);

        setTargetProduct(targetProduct);
    }

    private void setupCloudScreeningBitmasks(Product gaCloudProduct) {
        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;

        mask = Mask.BandMathsType.create("F_INVALID", "Invalid pixels", w, h,
                "cloud_classif_flags.F_INVALID", Color.yellow, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_CERTAIN", "Certainly cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_CERTAIN",
                Color.red.darker(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_PRESUMABLY", "Presumably cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_PRESUMABLY",
                Color.red.brighter(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        if (calculateLakes) {
            mask = Mask.BandMathsType.create("F_OCEAN", "Pixels over ocean (derived from SRTM land/sea mask)", w, h,
                    "cloud_classif_flags.F_OCEAN", Color.blue.darker(), 0.5f);
            gaCloudProduct.getMaskGroup().add(index++, mask);
            mask = Mask.BandMathsType.create("F_LAKES", "Pixels over lakes or along coastlines (SCAPE-M mask)", w, h,
                    "cloud_classif_flags.F_LAKES", Color.blue.brighter(), 0.5f);
            gaCloudProduct.getMaskGroup().add(index, mask);
        }
    }

    private FlagCoding createScapeMFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("F_CLOUD_CERTAIN", BitSetter.setFlag(0, 1), null);
        flagCoding.addFlag("F_CLOUD_PRESUMABLY", BitSetter.setFlag(0, 2), null);
        if (calculateLakes) {
            flagCoding.addFlag("F_OCEAN", BitSetter.setFlag(0, 3), null);
            flagCoding.addFlag("F_LAKES", BitSetter.setFlag(0, 4), null);
        }
        return flagCoding;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();

        Tile[] reflectanceTiles = new Tile[num_of_visible_bands + 2];
        for (int i = 1; i <= num_of_visible_bands + 1; i++) {
            final Band reflBand = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + i);
            reflectanceTiles[i - 1] = getSourceTile(reflBand, rectangle);
        }
        final Band refl13Band = rad2reflProduct.getBand(RHO_TOA_BAND_PREFIX + "_" + 13);
        reflectanceTiles[num_of_visible_bands + 1] = getSourceTile(refl13Band, rectangle);
        final TiePointGrid altitudeGrid = sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
        Tile altitudeTile = getSourceTile(altitudeGrid, rectangle);
        final Band l1FlagsBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        final Tile l1FlagsTile = getSourceTile(l1FlagsBand, rectangle);
        TiePointGrid sunZenithGrid = sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        final Tile sunZenithTile = getSourceTile(sunZenithGrid, rectangle);

        Tile waterTile = null;
        if (calculateLakes) {
            waterTile = getSourceTile(waterProduct.getRasterDataNode("water_flags"), rectangle);
        }

        for (Tile.Pos pos : targetTile) {
            float pAvTOA = 0;
            for (int i = 0; i < num_of_visible_bands; i++) {
                pAvTOA += reflectanceTiles[i].getSampleFloat(pos.x, pos.y);
            }
            pAvTOA /= num_of_visible_bands;
            float p1TOA = reflectanceTiles[0].getSampleFloat(pos.x, pos.y);
            float p8TOA = reflectanceTiles[7].getSampleFloat(pos.x, pos.y);
            float p9TOA = reflectanceTiles[8].getSampleFloat(pos.x, pos.y);
            float p13TOA = reflectanceTiles[9].getSampleFloat(pos.x, pos.y);
            final float altitude = altitudeTile.getSampleFloat(pos.x, pos.y);
            final float sunZenith = sunZenithTile.getSampleFloat(pos.x, pos.y);
            final double musil = Math.cos(sunZenith * MathUtils.DTOR);

            boolean isInvalid = l1FlagsTile.getSampleBit(pos.x, pos.y, Constants.L1_F_INVALID);
            boolean certainlyCloud = pAvTOA > 0.3 || altitude > 2500 || (p1TOA > 0.23 && p1TOA > p9TOA) || musil < 0;
            boolean presumablyCloud = pAvTOA > 0.27 || altitude > 2500 || (p1TOA > 0.2 && p1TOA > p8TOA) || musil < 0;

            int cloudFlag = 0;
            cloudFlag = BitSetter.setFlag(cloudFlag, 0, isInvalid);
            cloudFlag = BitSetter.setFlag(cloudFlag, 1, certainlyCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, 2, presumablyCloud);

            boolean isOcean = waterTile.getSampleBit(pos.x, pos.y, 1);
            boolean isLakeOrCoastline = false;
            if (calculateLakes) {
                isLakeOrCoastline = (waterTile.getSampleBit(pos.x, pos.y, 0) ||
                        waterTile.getSampleBit(pos.x, pos.y, 2)) && p13TOA < reflectance_water_threshold;
            } else {
                isOcean = isOcean || p13TOA < reflectance_water_threshold;
            }
            cloudFlag = BitSetter.setFlag(cloudFlag, 3, isOcean && !isInvalid);
            cloudFlag = BitSetter.setFlag(cloudFlag, 4, isLakeOrCoastline && !isInvalid);

            targetTile.setSample(pos.x, pos.y, cloudFlag);
        }
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
