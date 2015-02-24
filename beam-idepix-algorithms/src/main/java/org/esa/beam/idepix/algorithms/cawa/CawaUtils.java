package org.esa.beam.idepix.algorithms.cawa;

import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.BitSetter;

import java.awt.*;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 24.02.2015
 * Time: 14:23
 *
 * @author olafd
 */
public class CawaUtils {
    // if possible, put here everything which is common for both land and water parts

    public static int setupCawaBitmasks(Product cloudProduct) {

        int index = 0;
        int w = cloudProduct.getSceneRasterWidth();
        int h = cloudProduct.getSceneRasterHeight();
        Mask mask;

        mask = Mask.BandMathsType.create("cawa_invalid",
                                         CawaConstants.F_INVALID_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_INVALID",
                                         Color.red.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_cloud",
                                         CawaConstants.F_CLOUD_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLOUD_SURE or cloud_classif_flags.F_CLOUD_AMBIGUOUS",
                                         Color.magenta, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_cloud_ambiguous",
                                         CawaConstants.F_CLOUD_AMBIGUOUS_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLOUD_AMBIGUOUS",
                                         Color.yellow, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_cloud_sure",
                                         CawaConstants.F_CLOUD_SURE_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLOUD_SURE",
                                         Color.red, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_cloud_buffer",
                                         CawaConstants.F_CLOUD_BUFFER_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLOUD_BUFFER",
                                         Color.orange, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_cloud_shadow",
                                         CawaConstants.F_CLOUD_SHADOW_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLOUD_SHADOW",
                                         Color.red.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_snow_ice",
                                         CawaConstants.F_SNOW_ICE_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_CLEAR_SNOW",
                                         Color.cyan, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_glint_risk",
                                         CawaConstants.F_GLINTRISK_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_GLINTRISK",
                                         Color.pink, 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_coastline",
                                         CawaConstants.F_COASTLINE_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_COASTLINE",
                                         Color.green.darker(), 0.5f);
        cloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cawa_land",
                                         CawaConstants.F_LAND_DESCR_TEXT, w, h,
                                         "cloud_classif_flags.F_LAND",
                                         Color.green.brighter(), 0.5f);
        cloudProduct.getMaskGroup().add(index, mask);

        return index;
    }

    public static FlagCoding createCawaFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, CawaConstants.F_INVALID), CawaConstants.F_INVALID_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, CawaConstants.F_CLOUD), CawaConstants.F_CLOUD_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD_AMBIGUOUS", BitSetter.setFlag(0, CawaConstants.F_CLOUD_AMBIGUOUS), CawaConstants.F_CLOUD_AMBIGUOUS_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD_SURE", BitSetter.setFlag(0, CawaConstants.F_CLOUD_SURE), CawaConstants.F_CLOUD_SURE_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, CawaConstants.F_CLOUD_BUFFER), CawaConstants.F_CLOUD_BUFFER_DESCR_TEXT);
        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, CawaConstants.F_CLOUD_SHADOW), CawaConstants.F_CLOUD_SHADOW_DESCR_TEXT);
        flagCoding.addFlag("F_SNOW_ICE", BitSetter.setFlag(0, CawaConstants.F_SNOW_ICE), CawaConstants.F_SNOW_ICE_DESCR_TEXT);
        flagCoding.addFlag("F_GLINTRISK", BitSetter.setFlag(0, CawaConstants.F_GLINTRISK), CawaConstants.F_GLINTRISK_DESCR_TEXT);
        flagCoding.addFlag("F_COASTLINE", BitSetter.setFlag(0, CawaConstants.F_COASTLINE), CawaConstants.F_COASTLINE_DESCR_TEXT);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, CawaConstants.F_LAND), CawaConstants.F_LAND_DESCR_TEXT);
        return flagCoding;
    }

}
