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
