package org.esa.beam.mepix.operators;

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
import org.esa.beam.mepix.util.MepixUtils;
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
@OperatorMetadata(alias = "mepix.GACloudScreening",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2008 by Brockmann Consult",
        description = "This operator provides cloud screening from SPOT VGT data.")
public class GACloudScreeningOp extends Operator {

    @SourceProduct(alias="gal1b", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias="cloud", optional=true)
    private Product cloudProduct;
    @SourceProduct(alias="rayleigh", optional=true)
    private Product rayleighProduct;
    @SourceProduct(alias="pressure", optional=true)
    private Product pressureProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue="true",
            label = "Copy input radiance bands")
    private boolean gaCopyRadiances;

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLEAR_LAND = 2;
    public static final int F_CLEAR_WATER = 3;
    public static final int F_CLEAR_SNOW = 4;
    public static final int F_LAND = 5;
    public static final int F_WATER = 6;
    public static final int F_BRIGHT = 7;
    public static final int F_WHITE = 8;
    private static final int F_BRIGHTWHITE = 9;
    private static final int F_COLD = 10;
    public static final int F_HIGH = 11;
    public static final int F_VEG_RISK = 12;
    public static final int F_GLINT_RISK = 13;

    public static final String GA_CLOUD_FLAGS = "cloud_classif_flags";

    private int sourceProductTypeId;

    // MERIS bands:
    private Band[] merisRadianceBands;
    private Band[] merisBrrBands;
    private Band brr442Band;
    private Band brr442ThreshBand;
    private Band p1Band;
    private Band pscattBand;

    // AATSR bands:
    private Band[] aatsrReflectanceBands;
    private Band btemp1200Band;

    // VGT bands:
    private Band[] vgtReflectanceBands;


    @Override
    public void initialize() throws OperatorException {
        if (sourceProduct != null) {
            setSourceProductTypeId();

            switch (sourceProductTypeId) {
                case MepixConstants.PRODUCT_TYPE_MERIS:
                    merisRadianceBands= new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        merisRadianceBands[i] = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]);
                    }
                    brr442Band = rayleighProduct.getBand("brr_2");
                    merisBrrBands= new Band[MepixConstants.MERIS_BRR_BAND_NAMES.length];
                    for (int i = 0; i < MepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                        merisBrrBands[i] = rayleighProduct.getBand(MepixConstants.MERIS_BRR_BAND_NAMES[i]);
                    }
                    p1Band = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
                    pscattBand = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
                    brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");
                    break;
                case MepixConstants.PRODUCT_TYPE_AATSR:
                    aatsrReflectanceBands = new Band[MepixConstants.AATSR_WAVELENGTHS.length];
                    for (int i = 0; i < MepixConstants.AATSR_WAVELENGTHS.length; i++) {
                        aatsrReflectanceBands[i] = sourceProduct.getBand(MepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]);
                    }
                    btemp1200Band = sourceProduct.getBand("btemp_nadir_1200");

                    break;
                case MepixConstants.PRODUCT_TYPE_VGT:
                    vgtReflectanceBands = new Band[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                    for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        vgtReflectanceBands[i] = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]);
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
            sourceProductTypeId = MepixConstants.PRODUCT_TYPE_MERIS;
        } else if (sourceProduct.getProductType().startsWith("ATS")) {
            sourceProductTypeId = MepixConstants.PRODUCT_TYPE_AATSR;
        } else if (sourceProduct.getProductType().startsWith("VGT")) {
            sourceProductTypeId = MepixConstants.PRODUCT_TYPE_VGT;
        } else {
            sourceProductTypeId = MepixConstants.PRODUCT_TYPE_INVALID;
        }
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        Band cloudFlagBand = targetProduct.addBand(GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(GA_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        Band brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", MepixConstants.NO_DATA_VALUE, true);
        Band whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", MepixConstants.NO_DATA_VALUE, true);
        Band temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(temperatureBand, "Temperature", "K", MepixConstants.NO_DATA_VALUE, true);
        Band spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl", MepixConstants.NO_DATA_VALUE, true);
        Band ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", MepixConstants.NO_DATA_VALUE, true);
        Band ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", MepixConstants.NO_DATA_VALUE, true);
        Band pressureBand = targetProduct.addBand("pressure_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(pressureBand, "Pressure", "hPa", MepixConstants.NO_DATA_VALUE, true);
        Band radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", MepixConstants.NO_DATA_VALUE, true);
        Band radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
        MepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "", MepixConstants.NO_DATA_VALUE, true);

        // new bit masks:
        int bitmaskIndex = setupGlobAlbedoCloudscreeningBitmasks();

        if (gaCopyRadiances) {
            switch (sourceProductTypeId) {
                case MepixConstants.PRODUCT_TYPE_MERIS:
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        Band b = ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct, targetProduct);
                        b.setSourceImage(sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                case MepixConstants.PRODUCT_TYPE_AATSR:
                     for (int i = 0; i < MepixConstants.AATSR_WAVELENGTHS.length; i++) {
                        Band b = ProductUtils.copyBand(MepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct, targetProduct);
                        b.setSourceImage(sourceProduct.getBand(MepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                case MepixConstants.PRODUCT_TYPE_VGT:
                    for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        // write the original reflectance bands:
                        Band b = ProductUtils.copyBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct, targetProduct);
                        b.setSourceImage(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getSourceImage());

                        // write new reflectance bands (corrected for saturation)
//                        Band reflBand = targetProduct.addBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i], ProductData.TYPE_FLOAT32);
//                        reflBand.setDescription(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getDescription());
//                        reflBand.setUnit(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getUnit());
//                        reflBand.setNoDataValue(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getNoDataValue());
//                        reflBand.setNoDataValueUsed(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).isNoDataValueUsed());
//                        reflBand.setValidPixelExpression(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getValidPixelExpression());
//                        reflBand.setSpectralWavelength(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getSpectralWavelength());
//                        reflBand.setSpectralBandwidth(sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]).getSpectralBandwidth());
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
        Tile brr442Tile = null;
        Tile p1Tile = null;
        Tile pscattTile = null;
        Tile brr442ThreshTile = null;
        Tile[] merisReflectanceTiles = null;
        float[] merisReflectance = null;
        Tile[] merisBrrTiles = null;
        float[] merisBrr = null;

        // AATSR variables
        Band[] aatsrFlagBands;
        Tile[] aatsrFlagTiles;
        Tile btemp1200Tile = null;
        Tile[] aatsrReflectanceTiles = null;
        float[] aatsrReflectance = null;

        // VGT variables
        Band smFlagBand;
        Tile smFlagTile = null;
        Tile[] vgtReflectanceTiles = null;
        float[] vgtReflectance = null;

        switch (sourceProductTypeId) {
            case MepixConstants.PRODUCT_TYPE_MERIS:
                brr442Tile = getSourceTile(brr442Band, rectangle, pm);
                brr442ThreshTile = getSourceTile(brr442ThreshBand, rectangle, pm);
                p1Tile = getSourceTile(p1Band, rectangle, pm);
                pscattTile = getSourceTile(pscattBand, rectangle, pm);

                merisL1bFlagBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
                merisL1bFlagTile = getSourceTile(merisL1bFlagBand, rectangle, pm);

                merisBrrTiles = new Tile[MepixConstants.MERIS_BRR_BAND_NAMES.length];
                merisBrr = new float[MepixConstants.MERIS_BRR_BAND_NAMES.length];
                for (int i = 0; i < MepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                    merisBrrTiles[i] = getSourceTile(merisBrrBands[i], rectangle, pm);
                }

                merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                merisReflectance = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                    merisReflectanceTiles[i] = getSourceTile(merisRadianceBands[i], rectangle, pm);
                }
                break;
            case MepixConstants.PRODUCT_TYPE_AATSR:

                aatsrFlagBands = new Band[MepixConstants.AATSR_FLAG_BAND_NAMES.length];
                aatsrFlagTiles = new Tile[MepixConstants.AATSR_FLAG_BAND_NAMES.length];
                for (int i = 0; i < MepixConstants.AATSR_FLAG_BAND_NAMES.length; i++) {
                    aatsrFlagBands[i] = sourceProduct.getBand(MepixConstants.AATSR_FLAG_BAND_NAMES[i]);
                    aatsrFlagTiles[i] = getSourceTile(aatsrFlagBands[i], rectangle, pm);
                }

                aatsrReflectanceTiles = new Tile[MepixConstants.AATSR_WAVELENGTHS.length];
                aatsrReflectance = new float[MepixConstants.AATSR_WAVELENGTHS.length];
                for (int i = 0; i < MepixConstants.AATSR_WAVELENGTHS.length; i++) {
                    aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle, pm);
                }

                btemp1200Tile = getSourceTile(btemp1200Band, rectangle, pm);
                break;
            case MepixConstants.PRODUCT_TYPE_VGT:
                smFlagBand = sourceProduct.getBand("SM");
                smFlagTile = getSourceTile(smFlagBand, rectangle, pm);

                vgtReflectanceTiles = new Tile[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                vgtReflectance = new float[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
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
                        case MepixConstants.PRODUCT_TYPE_MERIS:
                            pixelProperties = new MerisPixelProperties();
                            for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                                merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
                            }
                            ((MerisPixelProperties) pixelProperties).setRefl(merisReflectance);
                            for (int i = 0; i < MepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                                merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
                            }
                            ((MerisPixelProperties) pixelProperties).setBrr(merisBrr);
                            ((MerisPixelProperties) pixelProperties).setBrr442(brr442Tile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setBrr442Thresh(brr442ThreshTile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setP1(p1Tile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setPscatt(pscattTile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setL1FlagLand(merisL1bFlagTile.getSampleBit(x, y, MerisPixelProperties.L1B_F_LAND));
                            break;
                        case MepixConstants.PRODUCT_TYPE_AATSR:
                            pixelProperties = new AatsrPixelProperties();
                            for (int i = 0; i < MepixConstants.AATSR_REFLECTANCE_BAND_NAMES.length; i++) {
                                aatsrReflectance[i] = aatsrReflectanceTiles[i].getSampleFloat(x, y);
                            }
                            ((AatsrPixelProperties) pixelProperties).setRefl(aatsrReflectance);
                            ((AatsrPixelProperties) pixelProperties).setBtemp1200(btemp1200Tile.getSampleFloat(x, y));
                            break;
                        case MepixConstants.PRODUCT_TYPE_VGT:
                            pixelProperties = new VgtPixelProperties();
                            for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                                vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
                            }
                            float[] vgtReflectanceSaturationCorrected = MepixUtils.correctSaturatedReflectances(vgtReflectance);
                            ((VgtPixelProperties) pixelProperties).setRefl(vgtReflectanceSaturationCorrected);

                            ((VgtPixelProperties) pixelProperties).setSmLand(smFlagTile.getSampleBit(x, y, VgtPixelProperties.SM_F_LAND));
//                            if (gaCopyRadiances) {
//                                for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
//                                    if (band.getName().equals(MepixConstants.VGT_RADIANCE_BAND_NAMES[i])) {
//                                        // copy reflectances corrected for saturation
//                                        if (MepixUtils.areReflectancesValid(vgtReflectance)) {
//                                            targetTile.setSample(x, y, vgtReflectanceSaturationCorrected[i]);
//                                        } else {
//                                            targetTile.setSample(x, y, Float.NaN);
//                                        }
//                                    }
//                                }
//                            }
                            break;
                        default:
                            break;
                    }

                    if (band.isFlagBand() && band.getName().equals(GA_CLOUD_FLAGS)) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        targetTile.setSample(x, y, F_INVALID, pixelProperties.isInvalid());
                        targetTile.setSample(x, y, F_CLOUD, pixelProperties.isCloud());
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
                    } else if (band.getName().equals("temperature_value")) {
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

    

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(GACloudScreeningOp.class, "mepix.GACloudScreening");
        }
    }
}
