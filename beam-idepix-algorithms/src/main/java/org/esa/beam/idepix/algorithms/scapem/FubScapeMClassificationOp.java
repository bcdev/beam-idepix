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
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
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

import java.awt.Color;
import java.awt.Rectangle;

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

    @Parameter(description = "The thickness of the coastline in kilometers.")
    private float thicknessOfCoast;

    @Parameter(description = "The minimal size for a water region to be acknowledged as an ocean in km².")
    private float minimumOceanSize;

    @Parameter(description = "Whether or not to calculate a lake mask")
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

        Operator operator = new FubScapeMLakesOp();
        operator.setSourceProduct(sourceProduct);
        if (thicknessOfCoast > 0) {
            operator.setParameter("thicknessOfCoast", thicknessOfCoast);
        }
        if (minimumOceanSize > 0) {
            operator.setParameter("minimumOceanSize", minimumOceanSize);
        }
        if (!calculateLakes) {
            operator.setParameter("calculateLakes", calculateLakes);
        }
        waterProduct = operator.getTargetProduct();

        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct);

        setTargetProduct(targetProduct);
    }

    private void setupCloudScreeningBitmasks(Product gaCloudProduct) {
        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;

        mask = Mask.BandMathsType.create("F_INVALID", "invalid pixels", w, h,
                                         "cloud_classif_flags.F_INVALID", Color.gray, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_1", "Presumably cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_1",
                                         Color.red.darker(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_2", "Certainly cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD_2",
                                         Color.red.brighter(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_MASK_1", "Pixels not over ocean which are presumably cloud free", w, h,
                                         "cloud_classif_flags.F_CLOUD_MASK_1", Color.green.darker(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_MASK_2", "Pixels not over ocean which are certainly cloud free", w, h,
                                         "cloud_classif_flags.F_CLOUD_MASK_2", Color.green.brighter(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_OCEAN", "pixels over ocean", w, h,
                                         "cloud_classif_flags.F_OCEAN", Color.blue.darker(), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        if (calculateLakes) {
            mask = Mask.BandMathsType.create("F_LAKES", "pixels over lakes or along coastlines", w, h,
                                             "cloud_classif_flags.F_LAKES", Color.blue.brighter(), 0.5f);
            gaCloudProduct.getMaskGroup().add(index, mask);
        }
    }

    private FlagCoding createScapeMFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("F_CLOUD_1", BitSetter.setFlag(0, 1), null);
        flagCoding.addFlag("F_CLOUD_2", BitSetter.setFlag(0, 2), null);
        flagCoding.addFlag("F_CLOUD_MASK_1", BitSetter.setFlag(0, 3), null);
        flagCoding.addFlag("F_CLOUD_MASK_2", BitSetter.setFlag(0, 4), null);
        flagCoding.addFlag("F_OCEAN", BitSetter.setFlag(0, 5), null);
        if (calculateLakes) {
            flagCoding.addFlag("F_LAKES", BitSetter.setFlag(0, 6), null);
        }
        return flagCoding;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {


//        PRO cloud_mask_gen_lakes, path_data, cloud_flg, ncols, nrows, d, cal_coef, hs_gr, lake_flg, FR_flg
//
//        print, 'entering "cloud_mask_gen_lakes"'
//        solirr = [1662.3145, 1815.1012, 1865.6931, 1868.3597, 1746.0757, 1598.6753, 1482.616, 1424.7742, $
//        1362.4156, 1225.6102, 1214.0282, 1139.2915,  927.7276,  900.3337,  866.7724]
//
//        sza = fltarr(ncols, nrows, /nozero)
//        openr, 1, path_data + 'tie_sun_zenith.img', /swap_if_little_endian
//        readu, 1, sza
//        close, 1
//
//        fac = !pi / cos(sza * !dtor) / d / d
//        sza = 0
//
//        ind_rfl = [0, 7, 8, 12]
//        num_bd_rfl = n_elements(ind_rfl)
//        num_bd_alb = 15;9
//        refl_toa = fltarr(ncols, nrows, num_bd_rfl, /nozero)
//        tmp = uintarr(ncols, nrows, /nozero)
//        alb_toa = 0
//        icont = 0
//        for i = 0, num_bd_alb - 2 do begin
//        openr, 1, path_data + 'radiance_' + strtrim(i+1, 2) + '.img', /swap_if_little_endian
//        readu, 1, tmp
//        close, 1
//        rfl = tmp * fac * cal_coef[i] / solirr[i]
//        alb_toa = alb_toa + rfl
//        if where(ind_rfl - i eq 0) ne -1 then begin
//        refl_toa[*, *, icont] = rfl
//                icont = icont + 1
//        endif
//                endfor
//        tmp = 0 & rfl = 0
//
//        alb_toa = alb_toa / (num_bd_alb-1)
//
//        dem_img = fltarr(ncols, nrows, /nozero)
//        openr, 1, path_data + 'dem_elevation.img', /swap_if_little_endian
//        readu, 1, dem_img
//        close, 1
//
//        mus_il_img = fltarr(ncols, nrows, /nozero)
//        openr, 1, path_data + 'mus_il.img', /swap_if_little_endian
//        readu, 1, mus_il_img
//        close, 1
//
//        l1_flags = bytarr(ncols, nrows, /nozero)
//        openr, 1, path_data + 'l1_flags.img', /swap_if_little_endian
//        readu, 1, l1_flags
//        close, 1
//
//        l1_flags_bin = reform(string(l1_flags, format='(B8.8)'), ncols, nrows)
//
//        n_bit_land = 4
//        mask_water=bytarr(ncols, nrows)
//        wh_ocean = where(strmid(l1_flags_bin, 7 - n_bit_land, 1) eq '0', cnt_ocean)
//        if cnt_ocean gt 0 then mask_water[wh_ocean] = 1
//        mask_water = ~mask_water
//
//        n_bit_invalid = 7
//        mask_invalid=bytarr(ncols, nrows)
//        wh_invalid = where(strmid(l1_flags_bin, 7 - n_bit_invalid, 1) eq '1', cnt_invalid)
//        if cnt_invalid gt 0 then mask_invalid[wh_invalid] = 1
//
//        n_bit_coast = 6
//        mask_coast=bytarr(ncols, nrows)
//        wh_coast = where(strmid(l1_flags_bin, 7 - n_bit_coast, 1) eq '1', cnt_coast)
//        if cnt_coast gt 0 then mask_coast[wh_coast] = 1
//        ;************
//
//        dim_hs = n_elements(hs_gr)
//
//        ; Discriminate Land&Water from Others (clouds, invalid, shadowed, height > 2500m,..)
//        mask_land_all = replicate(1, ncols, nrows)
//        wh_no_land_all = where(alb_toa gt 0.30 or dem_img ge (hs_gr[dim_hs - 1] * 1000.) or mask_invalid eq 1 or $
//        (refl_toa[*, *, 0] gt 0.23 and refl_toa[*, *, 2] lt refl_toa[*, *, 0]) or mus_il_img lt 0., cont_land_all) ; $
//        if cont_land_all gt 0 then mask_land_all[wh_no_land_all] = 0
//
//        mask_land_bri = replicate(1, ncols, nrows)
//        wh_no_land_bri = where(alb_toa gt 0.27 or dem_img ge (hs_gr[dim_hs - 1] * 1000.) or mask_invalid eq 1 or $;
//        (refl_toa[*, *, 0] gt 0.20 and refl_toa[*, *, 1] lt refl_toa[*, *, 0]) or mus_il_img lt 0., cont_land_bri)
//        if cont_land_bri gt 0 then mask_land_bri[wh_no_land_bri] = 0
//
//        ; Discriminate Land from Water
//        refl_water_thre = 0.08
//        if lake_flg eq 1 then begin
//        km_coast = 20.;200.;20. ;thickness (km) of coast line
//                extens_lim = 1600.;10500.;1600.  ; min area (km^2) for a water body be ocean
//        if FR_flg eq 1 then kmxpix = 0.3 else kmxpix = 1.2
//        num_sm = fix(km_coast / kmxpix)
//        mask_ocean = ~mask_water
//        reg_wat = label_region(mask_ocean)
//        for ind = 0L, max(reg_wat) do begin
//                wh_reg = where(reg_wat eq ind, cnt_reg)
//        if (long(cnt_reg)*kmxpix) lt extens_lim then mask_ocean[wh_reg] = 0 ;Quito el agua 'peque�'
//        endfor
//        sm_coast = smooth(float(mask_coast), num_sm, /EDGE_MIRROR)
//        wh_coast = where(sm_coast gt 0, cnt_coast)
//        if cnt_coast gt 0 then mask_ocean[wh_coast] = 0 ;Quito la l�ea de costa
//        mask_lake = ~mask_ocean and (refl_toa[*, *, 3] lt refl_water_thre)
//
//        endif else mask_ocean = refl_toa[*, *, 3] lt refl_water_thre or mask_water eq 0 or mask_coast eq 1
//
//        mask_land_all = byte(mask_land_all and (~mask_ocean))
//        mask_land_bri = byte(mask_land_bri and (~mask_ocean))
//
//        openw, 1, path_data + 'mask_land.img' ;cloud_flg = 0:rel/tight, 1:rel/rel, 2:tight/tight
//        if cloud_flg le 1 then writeu, 1, mask_land_all else writeu, 1, mask_land_bri
//        if cloud_flg eq 0 or cloud_flg eq 2 then writeu, 1, mask_land_bri else writeu, 1, mask_land_all
//        if lake_flg eq 1 then writeu, 1, mask_lake
//        close, 1
//
//        print, 'leaving "cloud_mask_gen_lakes"'
//        END


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
        TiePointGrid sunAzimuthGrid = sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        final Tile sunAzimuthTile = getSourceTile(sunAzimuthGrid, rectangle);
        final Tile waterTile = getSourceTile(waterProduct.getRasterDataNode("water_flags"), rectangle);

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
            final float sunAzimuth = sunAzimuthTile.getSampleFloat(pos.x, pos.y);
            final double musil = Math.cos(sunAzimuth * MathUtils.DTOR);

            boolean isInvalid = l1FlagsTile.getSampleBit(pos.x, pos.y, Constants.L1_F_INVALID);
            boolean isOcean = waterTile.getSampleBit(pos.x, pos.y, 1);
            boolean isLakeOrCoastline = false;
            if (calculateLakes) {
                isLakeOrCoastline = (waterTile.getSampleBit(pos.x, pos.y, 0) ||
                        waterTile.getSampleBit(pos.x, pos.y, 1)) && p13TOA < reflectance_water_threshold;
            } else {
                isOcean = isOcean || p13TOA < reflectance_water_threshold && !isInvalid;
            }
            boolean isPresumablyCloud = pAvTOA > 0.27 || altitude > 2500 || (p1TOA > 0.2 && p1TOA > p8TOA) || musil > 0;
            boolean isCertainlyCloud = pAvTOA > 0.3 || altitude > 2500 || (p1TOA > 0.23 && p1TOA > p9TOA) || musil > 0;
            boolean cloudMask1 = !isOcean && !isCertainlyCloud;
            boolean cloudMask2 = !isOcean && !isPresumablyCloud;

            int cloudFlag = 0;
            cloudFlag = BitSetter.setFlag(cloudFlag, 0, isInvalid);
            cloudFlag = BitSetter.setFlag(cloudFlag, 1, isPresumablyCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, 2, isCertainlyCloud);
            cloudFlag = BitSetter.setFlag(cloudFlag, 3, cloudMask1);
            cloudFlag = BitSetter.setFlag(cloudFlag, 4, cloudMask2);
            cloudFlag = BitSetter.setFlag(cloudFlag, 5, isOcean);
            cloudFlag = BitSetter.setFlag(cloudFlag, 6, isLakeOrCoastline);
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
