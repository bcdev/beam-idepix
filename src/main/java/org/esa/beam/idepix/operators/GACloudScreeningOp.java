package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Random;

/**
 * Operator for GlobAlbedo cloud screening
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "idepix.GACloudScreening",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2008 by Brockmann Consult",
        description = "This operator provides cloud screening from SPOT VGT data.")
public class GACloudScreeningOp extends Operator {

    @SourceProduct(alias="gal1b", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias="cloud", optional=true)
    private Product cloudProduct;
    @SourceProduct(alias="cloudshadow", optional=true)
    private Product cloudShadowProduct;
    @SourceProduct(alias="rayleigh", optional=true)
    private Product rayleighProduct;
    @SourceProduct(alias="refl", optional=true)
    private Product rad2reflProduct;
    @SourceProduct(alias="pressure", optional=true)
    private Product pressureProduct;
    @SourceProduct(alias="pbaro", optional=true)
    private Product pbaroProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue="false", label = "Copy input radiance bands")
    private boolean gaCopyRadiances;
    @Parameter(defaultValue="false", label = "Compute only the flag band")
    private boolean gaComputeFlagsOnly;
    @Parameter(defaultValue="false", label = "Copy input annotation bands (VGT)")
    private boolean gaCopyAnnotations;
    @Parameter(defaultValue="true", label = "Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_SHADOW = 2;
    public static final int F_CLEAR_LAND = 3;
    public static final int F_CLEAR_WATER = 4;
    public static final int F_CLEAR_SNOW = 5;
    public static final int F_LAND = 6;
    public static final int F_WATER = 7;
    public static final int F_BRIGHT = 8;
    public static final int F_WHITE = 9;
    private static final int F_BRIGHTWHITE = 10;
    private static final int F_COLD = 11;
    public static final int F_HIGH = 12;
    public static final int F_VEG_RISK = 13;
    public static final int F_GLINT_RISK = 14;

    public static final String GA_CLOUD_FLAGS = "cloud_classif_flags";

    private int sourceProductTypeId;

    // MERIS bands:
    private Band[] merisRadianceBands;
    private Band[] merisReflBands;
    private Band[] merisBrrBands;
    private Band brr442Band;
    private Band brr442ThreshBand;
    private Band p1Band;
    private Band pbaroBand;
    private Band pscattBand;

    // AATSR bands:
    private Band[] aatsrReflectanceBands;
    private Band[] aatsrBtempBands;

    // VGT bands:
    private Band[] vgtReflectanceBands;


    @Override
    public void initialize() throws OperatorException {
        if (sourceProduct != null) {
            setSourceProductTypeId();

            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_MERIS:
                    merisRadianceBands= new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                    merisReflBands= new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        merisRadianceBands[i] = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]);
                        merisReflBands[i] = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
                    }
                    brr442Band = rayleighProduct.getBand("brr_2");
                    merisBrrBands= new Band[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                    for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                        merisBrrBands[i] = rayleighProduct.getBand(IdepixConstants.MERIS_BRR_BAND_NAMES[i]);
                    }
                    p1Band = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
                    pbaroBand = pbaroProduct.getBand(BarometricPressureOp.PRESSURE_BAROMETRIC);
                    pscattBand = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
                    brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");



                    break;
                case IdepixConstants.PRODUCT_TYPE_AATSR:
                    aatsrReflectanceBands = new Band[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                    for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                        aatsrReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]);
                    }
                    aatsrBtempBands = new Band[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                    for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                        aatsrBtempBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i]);
                    }

                    break;
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    vgtReflectanceBands = new Band[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                    for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        vgtReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i]);
                    }

                    break;
                default:
                    break;
            }

            createTargetProduct();
        }

    }

    private void setSourceProductTypeId() {
        if (sourceProduct.getProductType().startsWith("MER")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_MERIS;
        } else if (sourceProduct.getProductType().startsWith("ATS")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_AATSR;
        } else if (sourceProduct.getProductType().startsWith("VGT")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_VGT;
        } else {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_INVALID;
        }
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);
        targetProduct.setPreferredTileSize(256,256);

        Band cloudFlagBand = targetProduct.addBand(GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(GA_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (!gaComputeFlagsOnly) {
            Band brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(temperatureBand, "Temperature", "K", IdepixConstants.NO_DATA_VALUE, true);
            Band spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            Band pressureBand = targetProduct.addBand("pressure_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(pressureBand, "Pressure", "hPa", IdepixConstants.NO_DATA_VALUE, true);
            Band radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", IdepixConstants.NO_DATA_VALUE, true);
            Band radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "", IdepixConstants.NO_DATA_VALUE, true);
        }
        // new bit masks:
        int bitmaskIndex = setupGlobAlbedoCloudscreeningBitmasks();

        if (gaCopyRadiances) {
            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_MERIS:
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        Band b = ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct, targetProduct);
//                        b.setSourceImage(sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_AATSR:
                    for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                        Band b = ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                                       targetProduct);
//                        b.setSourceImage(sourceProduct.getBand(
//                                IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]).getSourceImage());
                    }
                    for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                        Band b = ProductUtils.copyBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i], sourceProduct,
                                                       targetProduct);
//                        b.setSourceImage(sourceProduct.getBand(
//                                IdepixConstants.AATSR_BTEMP_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        // write the original reflectance bands:
                        Band b = ProductUtils.copyBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct, targetProduct);
                        b.setSourceImage(sourceProduct.getBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                default:
                    break;
            }

            // copy flag bands
            ProductUtils.copyFlagBands(sourceProduct, targetProduct);
            for (Band sb:sourceProduct.getBands()) {
                if (sb.isFlagBand()) {
                    Band tb = targetProduct.getBand(sb.getName());
                    tb.setSourceImage(sb.getSourceImage());
                }
            }

            // copy bit masks from source product:
            for (int i=0; i<sourceProduct.getMaskGroup().getNodeCount(); i++) {
                Mask mask = sourceProduct.getMaskGroup().get(i);
                targetProduct.getMaskGroup().add(bitmaskIndex + i, mask);
            }
        }

        if (gaCopyAnnotations) {
            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
                        Band b = ProductUtils.copyBand(bandName, sourceProduct, targetProduct);
                        if (b != null) {
                            b.setSourceImage(sourceProduct.getBand(bandName).getSourceImage());
                        }
                    }
                    break;
                default:
                    break;
            }
        }

    }

    private int setupGlobAlbedoCloudscreeningBitmasks() {

        int index = 0;
        int w = sourceProduct.getSceneRasterWidth();
        int h = sourceProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

        mask = Mask.BandMathsType.create("INVALID", "Invalid pixels", w, h, "cloud_classif_flags.F_INVALID", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("CLOUD", "Cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("CLOUD_SHADOW", "Cloud shadow pixels", w, h, "cloud_classif_flags.F_CLOUD_SHADOW", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("CLEAR_LAND", "Clear sky pixels over land", w, h, "cloud_classif_flags.F_CLEAR_LAND", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("CLEAR_WATER", "Clear sky pixels over water", w, h, "cloud_classif_flags.F_CLEAR_WATER", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("CLEAR_SNOW", "Clear sky pixels, snow covered ", w, h, "cloud_classif_flags.F_CLEAR_SNOW", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("LAND", "Pixels over land", w, h, "cloud_classif_flags.F_LAND", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("WATER", "Pixels over water", w, h, "cloud_classif_flags.F_WATER", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("BRIGHT", "Pixels classified as bright", w, h, "cloud_classif_flags.F_BRIGHT", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("WHITE", "Pixels classified as white", w, h, "cloud_classif_flags.F_WHITE", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("BRIGHTWHITE", "Pixels classified as 'brightwhite'", w, h, "cloud_classif_flags.F_BRIGHTWHITE", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("COLD", "Cold pixels", w, h, "cloud_classif_flags.F_COLD", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("HIGH", "High pixels", w, h, "cloud_classif_flags.F_HIGH", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("VEG_RISK", "Pixels may contain vegetation", w, h, "cloud_classif_flags.F_VEG_RISK", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("GLINT_RISK", "Pixels may contain glint", w, h, "cloud_classif_flags.F_GLINT_RISK", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);

        return index;
    }

    private Color getRandomColour(Random random) {
        int rColor = random.nextInt(256);
        int gColor = random.nextInt(256);
        int bColor = random.nextInt(256);
        return new Color(rColor, gColor, bColor);
    }

    private FlagCoding createFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag("F_INVALID", BitSetter.setFlag(0, F_INVALID), null);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, F_CLOUD), null);
        flagCoding.addFlag("F_CLOUD_SHADOW", BitSetter.setFlag(0, F_CLOUD_SHADOW), null);
        flagCoding.addFlag("F_CLEAR_LAND", BitSetter.setFlag(0, F_CLEAR_LAND), null);
        flagCoding.addFlag("F_CLEAR_WATER", BitSetter.setFlag(0, F_CLEAR_WATER), null);
        flagCoding.addFlag("F_CLEAR_SNOW", BitSetter.setFlag(0, F_CLEAR_SNOW), null);
        flagCoding.addFlag("F_LAND", BitSetter.setFlag(0, F_LAND), null);
        flagCoding.addFlag("F_WATER", BitSetter.setFlag(0, F_WATER), null);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, F_BRIGHT), null);
        flagCoding.addFlag("F_WHITE", BitSetter.setFlag(0, F_WHITE), null);
        flagCoding.addFlag("F_BRIGHTWHITE", BitSetter.setFlag(0, F_BRIGHTWHITE), null);
        flagCoding.addFlag("F_COLD", BitSetter.setFlag(0, F_COLD), null);
        flagCoding.addFlag("F_HIGH", BitSetter.setFlag(0, F_HIGH), null);
        flagCoding.addFlag("F_VEG_RISK", BitSetter.setFlag(0, F_VEG_RISK), null);
        flagCoding.addFlag("F_GLINT_RISK", BitSetter.setFlag(0, F_GLINT_RISK), null);

        return flagCoding;
    }


    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);

        // MERIS variables
        Band merisL1bFlagBand;
        Tile merisL1bFlagTile = null;
        Band merisCombinedCloudFlagBand = null;
        Tile merisCombinedCloudFlagTile = null;
        Tile brr442Tile = null;
        Tile p1Tile = null;
        Tile pbaroTile = null;
        Tile pscattTile = null;
        Tile brr442ThreshTile = null;
        Tile[] merisReflectanceTiles = null;
        float[] merisReflectance = null;
        Tile[] merisBrrTiles = null;
        float[] merisBrr = null;

        // AATSR variables
        Band aatsrL1bFlagBand;
        Tile aatsrL1bFlagTile = null;
        Band[] aatsrFlagBands;
        Tile[] aatsrFlagTiles;
        Tile[] aatsrReflectanceTiles = null;
        float[] aatsrReflectance = null;
        Tile[] aatsrBtempTiles = null;
        float[] aatsrBtemp = null;

        // VGT variables
        Band smFlagBand;
        Tile smFlagTile = null;
        Tile[] vgtReflectanceTiles = null;
        float[] vgtReflectance = null;

        switch (sourceProductTypeId) {
            case IdepixConstants.PRODUCT_TYPE_MERIS:
                brr442Tile = getSourceTile(brr442Band, rectangle, pm);
                brr442ThreshTile = getSourceTile(brr442ThreshBand, rectangle, pm);
                p1Tile = getSourceTile(p1Band, rectangle, pm);
                pbaroTile = getSourceTile(pbaroBand, rectangle, pm);
                pscattTile = getSourceTile(pscattBand, rectangle, pm);

                merisL1bFlagBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
                merisL1bFlagTile = getSourceTile(merisL1bFlagBand, rectangle, pm);

                if (cloudShadowProduct != null) {
                    merisCombinedCloudFlagBand = cloudShadowProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME);
                    merisCombinedCloudFlagTile = getSourceTile(merisCombinedCloudFlagBand, rectangle, pm);
                }

                merisBrrTiles = new Tile[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                merisBrr = new float[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                    merisBrrTiles[i] = getSourceTile(merisBrrBands[i], rectangle, pm);
                }

                merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                merisReflectance = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                    merisReflectanceTiles[i] = getSourceTile(merisReflBands[i], rectangle, pm);
                }
                break;
            case IdepixConstants.PRODUCT_TYPE_AATSR:

                aatsrL1bFlagBand = sourceProduct.getBand(EnvisatConstants.AATSR_L1B_CLOUD_FLAGS_NADIR_BAND_NAME);
                aatsrL1bFlagTile = getSourceTile(aatsrL1bFlagBand, rectangle, pm);

                aatsrFlagBands = new Band[IdepixConstants.AATSR_FLAG_BAND_NAMES.length];
                aatsrFlagTiles = new Tile[IdepixConstants.AATSR_FLAG_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.AATSR_FLAG_BAND_NAMES.length; i++) {
                    aatsrFlagBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_FLAG_BAND_NAMES[i]);
                    aatsrFlagTiles[i] = getSourceTile(aatsrFlagBands[i], rectangle, pm);
                }

                aatsrReflectanceTiles = new Tile[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                aatsrReflectance = new float[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                    aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle, pm);
                }

                aatsrBtempTiles= new Tile[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                aatsrBtemp= new float[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                    aatsrBtempTiles[i] = getSourceTile(aatsrBtempBands[i], rectangle, pm);
                }

                break;
            case IdepixConstants.PRODUCT_TYPE_VGT:
                smFlagBand = sourceProduct.getBand("SM");
                smFlagTile = getSourceTile(smFlagBand, rectangle, pm);

                vgtReflectanceTiles = new Tile[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                vgtReflectance = new float[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                    vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle, pm);
                }
                break;
            default:
                break;
        }

        try {
			for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
					if (pm.isCanceled()) {
						break;
					}

                    // set up pixel properties for given instruments...
                    PixelProperties pixelProperties = null;
                    switch (sourceProductTypeId) {
                        case IdepixConstants.PRODUCT_TYPE_MERIS:
                            pixelProperties = new MerisPixelProperties();
                            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                                merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
                                if (band.getName().equals(EnvisatConstants.MERIS_L1B_BAND_NAMES[i])) {
                                    targetTile.setSample(x, y, merisReflectance[i]);
                                }
                            }
                            ((MerisPixelProperties) pixelProperties).setRefl(merisReflectance);
                            for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                                merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
                            }
                            ((MerisPixelProperties) pixelProperties).setBrr(merisBrr);
                            ((MerisPixelProperties) pixelProperties).setBrr442(brr442Tile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setBrr442Thresh(brr442ThreshTile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setP1(p1Tile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setPBaro(pbaroTile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setPscatt(pscattTile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setL1FlagLand(merisL1bFlagTile.getSampleBit(x, y, MerisPixelProperties.L1B_F_LAND));
                            final int isShadowBitIndex = (int) (Math.log((double)CombinedCloudOp.FLAG_CLOUD_SHADOW)/Math.log(2.0));
                            if (merisCombinedCloudFlagTile != null) {
                             ((MerisPixelProperties) pixelProperties).setCombinedCloudFlagShadow(merisCombinedCloudFlagTile.getSampleBit(x, y, isShadowBitIndex));
                            }

                            break;
                        case IdepixConstants.PRODUCT_TYPE_AATSR:
                            pixelProperties = new AatsrPixelProperties();
                            for (int i = 0; i < IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES.length; i++) {
                                aatsrReflectance[i] = aatsrReflectanceTiles[i].getSampleFloat(x, y);
                                if (band.getName().equals(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i])) {
                                    targetTile.setSample(x, y, aatsrReflectance[i]);
                                }
                            }
                            for (int i = 0; i < IdepixConstants.AATSR_BTEMP_BAND_NAMES.length; i++) {
                                aatsrBtemp[i] = aatsrBtempTiles[i].getSampleFloat(x, y);
                                if (band.getName().equals(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i])) {
                                    targetTile.setSample(x, y, aatsrBtemp[i]);
                                }
                            }

                            ((AatsrPixelProperties) pixelProperties).setUseFwardViewForCloudMask(gaUseAatsrFwardForClouds);
                            ((AatsrPixelProperties) pixelProperties).setRefl(aatsrReflectance);
                            ((AatsrPixelProperties) pixelProperties).setBtemp1200(aatsrBtempTiles[2].getSampleFloat(x, y));
                            ((AatsrPixelProperties) pixelProperties).setL1FlagLand(aatsrL1bFlagTile.getSampleBit(x, y, AatsrPixelProperties.L1B_F_LAND));
                            ((AatsrPixelProperties) pixelProperties).setL1FlagGlintRisk(aatsrL1bFlagTile.getSampleBit(x, y, AatsrPixelProperties.L1B_F_GLINT_RISK));
                            break;
                        case IdepixConstants.PRODUCT_TYPE_VGT:
                            pixelProperties = new VgtPixelProperties();
                            for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                                vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
                            }
                            float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
                            ((VgtPixelProperties) pixelProperties).setRefl(vgtReflectanceSaturationCorrected);

                            ((VgtPixelProperties) pixelProperties).setSmLand(smFlagTile.getSampleBit(x, y, VgtPixelProperties.SM_F_LAND));
                            break;
                        default:
                            break;
                    }

                    if (band.isFlagBand() && band.getName().equals(GA_CLOUD_FLAGS)) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        targetTile.setSample(x, y, F_INVALID, pixelProperties.isInvalid());
                        targetTile.setSample(x, y, F_CLOUD, pixelProperties.isCloud());
                        targetTile.setSample(x, y, F_CLOUD_SHADOW, pixelProperties.isCloudShadow());
                        targetTile.setSample(x, y, F_CLEAR_LAND, pixelProperties.isClearLand());
                        targetTile.setSample(x, y, F_CLEAR_WATER, pixelProperties.isClearWater());
                        targetTile.setSample(x, y, F_CLEAR_SNOW, pixelProperties.isClearSnow());
                        targetTile.setSample(x, y, F_LAND, pixelProperties.isLand());
                        targetTile.setSample(x, y, F_WATER, pixelProperties.isWater());
                        targetTile.setSample(x, y, F_BRIGHT, pixelProperties.isBright());
                        targetTile.setSample(x, y, F_WHITE, pixelProperties.isWhite());
                        targetTile.setSample(x, y, F_BRIGHTWHITE, pixelProperties.isBrightWhite());
                        targetTile.setSample(x, y, F_COLD, pixelProperties.isCold());
                        targetTile.setSample(x, y, F_HIGH, pixelProperties.isHigh());
                        targetTile.setSample(x, y, F_VEG_RISK, pixelProperties.isVegRisk());
                        targetTile.setSample(x, y, F_GLINT_RISK, pixelProperties.isGlintRisk());
                    }

                    // for given instrument, compute more pixel properties and write to distinct band
                    if (band.getName().equals("bright_value")) {
                        targetTile.setSample(x, y, pixelProperties.brightValue());
                    } else if (band.getName().equals("white_value")) {
                        targetTile.setSample(x, y, pixelProperties.whiteValue());
                    } else if (band.getName().equals("bright_white_value")) {
                        targetTile.setSample(x, y, pixelProperties.brightValue() + pixelProperties.whiteValue());
                    }else if (band.getName().equals("temperature_value")) {
                        targetTile.setSample(x, y, pixelProperties.temperatureValue());
                    }else if (band.getName().equals("spectral_flatness_value")) {
                        targetTile.setSample(x, y, pixelProperties.spectralFlatnessValue());
                    }else if (band.getName().equals("ndvi_value")) {
                        targetTile.setSample(x, y, pixelProperties.ndviValue());
                    } else if (band.getName().equals("ndsi_value")) {
                        targetTile.setSample(x, y, pixelProperties.ndsiValue());
                    } else if (band.getName().equals("pressure_value")) {
                        targetTile.setSample(x, y, pixelProperties.pressureValue());
                    } else if (band.getName().equals("radiometric_land_value")) {
                        targetTile.setSample(x, y, pixelProperties.radiometricLandValue());
                    } else if (band.getName().equals("radiometric_water_value")) {
                        targetTile.setSample(x, y, pixelProperties.radiometricWaterValue());
                    }
				}
				pm.worked(1);
			}
        } catch (Exception e) {
        	throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
		} finally {
            pm.done();
        }
    }

    private void printCombinedCloudFlag(Tile merisCombinedCloudFlagTile, int y, int x) {
        final int cfInt = merisCombinedCloudFlagTile.getSampleInt(x, y);
        final boolean isInvalid = merisCombinedCloudFlagTile.getSampleBit(x, y, CombinedCloudOp.FLAG_INVALID);
        final boolean isClear = merisCombinedCloudFlagTile.getSampleBit(x, y, CombinedCloudOp.FLAG_CLEAR);
        final boolean isSnow = merisCombinedCloudFlagTile.getSampleBit(x, y, CombinedCloudOp.FLAG_SNOW);
        final boolean isCloudEdge = merisCombinedCloudFlagTile.getSampleBit(x, y, CombinedCloudOp.FLAG_CLOUD_EDGE);
        final int isCloudBitIndex = (int) (Math.log((double)CombinedCloudOp.FLAG_CLOUD)/Math.log(2.0));
        final int isShadowBitIndex = (int) (Math.log((double)CombinedCloudOp.FLAG_CLOUD_SHADOW)/Math.log(2.0));
        final boolean isCloud = merisCombinedCloudFlagTile.getSampleBit(x, y, isCloudBitIndex);
        final boolean isShadow = merisCombinedCloudFlagTile.getSampleBit(x, y, isShadowBitIndex);
        System.out.println("cf          = " + cfInt);
        System.out.println("isInvalid   = " + isInvalid);
        System.out.println("isClear     = " + isClear);
        System.out.println("isCloud     = " + isCloud);
        System.out.println("isSnow      = " + isSnow);
        System.out.println("isCloudEdge = " + isCloudEdge);
        System.out.println("isShadow    = " + isShadow);
        System.out.println("isCloud INDEX    = " + isCloudBitIndex);
        System.out.println("isShadow INDEX    = " + isShadowBitIndex);
    }

    private void printPixelFeatures(PixelProperties pixelProperties) {
        System.out.println("bright            = " + pixelProperties.brightValue());
        System.out.println("white             = " + pixelProperties.whiteValue());
        System.out.println("temperature       = " + pixelProperties.temperatureValue());
        System.out.println("spec_flat         = " + pixelProperties.spectralFlatnessValue());
        System.out.println("ndvi              = " + pixelProperties.ndviValue());
        System.out.println("ndsi              = " + pixelProperties.ndsiValue());
        System.out.println("pressure          = " + pixelProperties.pressureValue());
        System.out.println("cloudy            = " + pixelProperties.isCloud());
        System.out.println("cloud shadow      = " + pixelProperties.isCloudShadow());
        System.out.println("clear snow        = " + pixelProperties.isClearSnow());
        System.out.println("radiometric_land  = " + pixelProperties.radiometricLandValue());
        System.out.println("radiometric_water = " + pixelProperties.radiometricWaterValue());
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(GACloudScreeningOp.class, "idepix.GACloudScreening");
        }
    }
}
