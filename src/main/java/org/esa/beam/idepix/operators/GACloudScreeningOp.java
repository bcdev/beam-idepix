package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
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
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.Rectangle;
import java.io.IOException;

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

    @SourceProduct(alias = "gal1b", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias = "cloud", optional = true)
    private Product cloudProduct;
    @SourceProduct(alias = "rayleigh", optional = true)
    private Product rayleighProduct;
    @SourceProduct(alias = "refl", optional = true)
    private Product rad2reflProduct;
    @SourceProduct(alias = "pressure", optional = true)
    private Product pressureProduct;
    @SourceProduct(alias = "pbaro", optional = true)
    private Product pbaroProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "false", label = "Copy input radiance bands")
    private boolean gaCopyRadiances;
    @Parameter(defaultValue = "false", label = "Compute only the flag band")
    private boolean gaComputeFlagsOnly;
    @Parameter(defaultValue = "false", label = "Copy pressure bands (MERIS)")
    private boolean gaCopyPressure;
    @Parameter(defaultValue = "false", label = "Copy input annotation bands (VGT)")
    private boolean gaCopyAnnotations;
    @Parameter(defaultValue = "true", label = "Use forward view for cloud flag determination (AATSR)")
    private boolean gaUseAatsrFwardForClouds;
    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int gaCloudBufferWidth;
    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = "Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    private int wmResolution;
    @Parameter(defaultValue = "true", label = "Use land-water flag from L1b product instead (faster)")
    private boolean gaUseL1bLandWaterFlag;
    @Parameter(defaultValue = "false", label = " Use the LC cloud buffer algorithm")
    private boolean gaLcCloudBuffer = false;


    public static final String GA_CLOUD_FLAGS = "cloud_classif_flags";

    private int sourceProductTypeId;
    private WatermaskClassifier classifier;

    // MERIS bands:
    private Band[] merisRadianceBands;
    private Band[] merisReflBands;
    private Band[] merisBrrBands;
    private Band brr442Band;
    private Band brr442ThreshBand;
    private Band p1Band;
    private Band pbaroBand;
    private Band pscattBand;

    private TiePointGrid latitudeTpg;
    private TiePointGrid longitudeTpg;
    private TiePointGrid altitudeTpg;

    // AATSR bands:
    private Band[] aatsrReflectanceBands;
    private Band[] aatsrBtempBands;

    // VGT bands:
    private Band[] vgtReflectanceBands;
    private Band temperatureBand;


    private Band cloudFlagBand;
    private Band brightBand;
    private Band whiteBand;
    private Band brightWhiteBand;
    private Band spectralFlatnessBand;
    private Band ndviBand;
    private Band ndsiBand;
    private Band glintRiskBand;
    private Band radioLandBand;
    private Band radioWaterBand;
    private Band pressureBand;
    private Band pbaroOutputBand;
    private Band p1OutputBand;
    private Band pscattOutputBand;
    private GeoCoding geoCoding;


    @Override
    public void initialize() throws OperatorException {
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // for debugging purpose
        setSourceProductTypeId();
        try {
            classifier = new WatermaskClassifier(wmResolution);
        } catch (IOException e) {
            getLogger().warning("Watermask classifier could not be initialized - fallback mode is used.");
        }

        switch (sourceProductTypeId) {
            case IdepixConstants.PRODUCT_TYPE_MERIS:
                merisRadianceBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                merisReflBands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                    merisRadianceBands[i] = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]);
                    merisReflBands[i] = rad2reflProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1));
                }
                brr442Band = rayleighProduct.getBand("brr_2");
                merisBrrBands = new Band[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                    merisBrrBands[i] = rayleighProduct.getBand(IdepixConstants.MERIS_BRR_BAND_NAMES[i]);
                }
                p1Band = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_P1);
                pbaroBand = pbaroProduct.getBand(BarometricPressureOp.PRESSURE_BAROMETRIC);
                pscattBand = pressureProduct.getBand(LisePressureOp.PRESSURE_LISE_PSCATT);
                brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");

                latitudeTpg = sourceProduct.getTiePointGrid("latitude");
                longitudeTpg = sourceProduct.getTiePointGrid("longitude");
                altitudeTpg = sourceProduct.getTiePointGrid("dem_alt");

                break;
            case IdepixConstants.PRODUCT_TYPE_AATSR:
                aatsrReflectanceBands = new Band[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                    aatsrReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i]);
                }
                aatsrBtempBands = new Band[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                    aatsrBtempBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i]);
                    if (aatsrBtempBands[i] == null) {
                        throw new OperatorException
                                ("AATSR temperature bands missing or incomplete in source product - cannot proceed.");
                    }
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

        cloudFlagBand = targetProduct.addBand(GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createGAFlagCoding(GA_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (!gaComputeFlagsOnly) {
            brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", IdepixConstants.NO_DATA_VALUE,
                    true);
            temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(temperatureBand, "Temperature", "K", IdepixConstants.NO_DATA_VALUE, true);
            spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl",
                    IdepixConstants.NO_DATA_VALUE, true);
            ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            glintRiskBand = targetProduct.addBand("glint_risk_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(glintRiskBand, "GLINT_RISK", "dl", IdepixConstants.NO_DATA_VALUE, true);
            radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", IdepixConstants.NO_DATA_VALUE,
                    true);
            radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "",
                    IdepixConstants.NO_DATA_VALUE, true);

            if (sourceProductTypeId == IdepixConstants.PRODUCT_TYPE_MERIS && gaCopyPressure) {
                pressureBand = targetProduct.addBand("pressure_value", ProductData.TYPE_FLOAT32);
                IdepixUtils.setNewBandProperties(pressureBand, "Pressure", "hPa", IdepixConstants.NO_DATA_VALUE, true);
                pbaroOutputBand = targetProduct.addBand("pbaro_value", ProductData.TYPE_FLOAT32);
                IdepixUtils.setNewBandProperties(pbaroOutputBand, "Barometric Pressure", "hPa",
                        IdepixConstants.NO_DATA_VALUE,
                        true);
                p1OutputBand = targetProduct.addBand("p1_value", ProductData.TYPE_FLOAT32);
                IdepixUtils.setNewBandProperties(p1OutputBand, "P1 Pressure", "hPa", IdepixConstants.NO_DATA_VALUE,
                        true);
                pscattOutputBand = targetProduct.addBand("pscatt_value", ProductData.TYPE_FLOAT32);
                IdepixUtils.setNewBandProperties(pscattOutputBand, "PScatt Pressure", "hPa",
                        IdepixConstants.NO_DATA_VALUE,
                        true);
            }
        }
        // new bit masks:
        int bitmaskIndex = IdepixUtils.setupGlobAlbedoCloudscreeningBitmasks(targetProduct);

        if (gaCopyRadiances) {
            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_MERIS:
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct,
                                              targetProduct);
                    }
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        ProductUtils.copyBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1), rad2reflProduct,
                                targetProduct);
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_AATSR:
                    for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                        ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                targetProduct);
                    }
                    for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                        ProductUtils.copyBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i], sourceProduct,
                                targetProduct);
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        // write the original reflectance bands:
                        ProductUtils.copyBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct,
                                targetProduct);
                    }
                    break;
                default:
                    break;
            }

            // copy flag bands
            ProductUtils.copyFlagBands(sourceProduct, targetProduct);
            for (Band sb : sourceProduct.getBands()) {
                if (sb.isFlagBand()) {
                    Band tb = targetProduct.getBand(sb.getName());
                    tb.setSourceImage(sb.getSourceImage());
                }
            }

        }

        if (gaCopyAnnotations) {
            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
                        ProductUtils.copyBand(bandName, sourceProduct, targetProduct);
                    }
                    break;
                default:
                    break;
            }
        }

    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();

        // MERIS variables
        Band merisL1bFlagBand;
        Tile merisL1bFlagTile = null;
        Band merisQwgCloudClassifFlagBand;
        Tile merisQwgCloudClassifFlagTile = null;
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
                brr442Tile = getSourceTile(brr442Band, rectangle);
                brr442ThreshTile = getSourceTile(brr442ThreshBand, rectangle);
                p1Tile = getSourceTile(p1Band, rectangle);
                pbaroTile = getSourceTile(pbaroBand, rectangle);
                pscattTile = getSourceTile(pscattBand, rectangle);

                merisL1bFlagBand = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
                merisL1bFlagTile = getSourceTile(merisL1bFlagBand, rectangle);
                merisQwgCloudClassifFlagBand = cloudProduct.getBand(IdepixCloudClassificationOp.CLOUD_FLAGS);
                merisQwgCloudClassifFlagTile = getSourceTile(merisQwgCloudClassifFlagBand, rectangle);

                merisBrrTiles = new Tile[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                merisBrr = new float[IdepixConstants.MERIS_BRR_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
                    merisBrrTiles[i] = getSourceTile(merisBrrBands[i], rectangle);
                }

                merisReflectanceTiles = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                merisReflectance = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
                for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                    merisReflectanceTiles[i] = getSourceTile(merisReflBands[i], rectangle);
                }
                break;
            case IdepixConstants.PRODUCT_TYPE_AATSR:

                aatsrL1bFlagBand = sourceProduct.getBand(EnvisatConstants.AATSR_L1B_CLOUD_FLAGS_NADIR_BAND_NAME);
                aatsrL1bFlagTile = getSourceTile(aatsrL1bFlagBand, rectangle);

                aatsrFlagBands = new Band[IdepixConstants.AATSR_FLAG_BAND_NAMES.length];
                aatsrFlagTiles = new Tile[IdepixConstants.AATSR_FLAG_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.AATSR_FLAG_BAND_NAMES.length; i++) {
                    aatsrFlagBands[i] = sourceProduct.getBand(IdepixConstants.AATSR_FLAG_BAND_NAMES[i]);
                    aatsrFlagTiles[i] = getSourceTile(aatsrFlagBands[i], rectangle);
                }

                aatsrReflectanceTiles = new Tile[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                aatsrReflectance = new float[IdepixConstants.AATSR_REFL_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                    aatsrReflectanceTiles[i] = getSourceTile(aatsrReflectanceBands[i], rectangle);
                }

                aatsrBtempTiles = new Tile[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                aatsrBtemp = new float[IdepixConstants.AATSR_TEMP_WAVELENGTHS.length];
                for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                    aatsrBtempTiles[i] = getSourceTile(aatsrBtempBands[i], rectangle);
                }

                break;
            case IdepixConstants.PRODUCT_TYPE_VGT:
                smFlagBand = sourceProduct.getBand("SM");
                smFlagTile = getSourceTile(smFlagBand, rectangle);

                vgtReflectanceTiles = new Tile[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                vgtReflectance = new float[IdepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                    vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle);
                }
                break;
            default:
                break;
        }

        GeoPos geoPos = null;
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    WatermaskStrategy strategy = null;

                    switch (sourceProductTypeId) {
                        // todo - put different sensor computations into different strategy modules
                        case IdepixConstants.PRODUCT_TYPE_MERIS:
                            strategy = new MerisWatermaskStrategy();
                            break;
                        case IdepixConstants.PRODUCT_TYPE_AATSR:
                            strategy = new MerisWatermaskStrategy();
                            break;
                        case IdepixConstants.PRODUCT_TYPE_VGT:
                            strategy = new MerisWatermaskStrategy();
                            break;
                    }

                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    if (!gaUseL1bLandWaterFlag) {
                        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                        if (geoCoding.canGetGeoPos()) {
                            geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                            waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                        }
                    }

                    // set up pixel properties for given instruments...
                    PixelProperties pixelProperties = null;
                    switch (sourceProductTypeId) {
                        case IdepixConstants.PRODUCT_TYPE_MERIS:
                            pixelProperties = createMerisPixelProperties(merisL1bFlagTile, merisQwgCloudClassifFlagTile,
                                    brr442Tile, p1Tile,
                                    pbaroTile, pscattTile, brr442ThreshTile,
                                    merisReflectanceTiles,
                                    merisReflectance,
                                    merisBrrTiles, merisBrr, waterMaskSample, y,
                                    x);

                            break;
                        case IdepixConstants.PRODUCT_TYPE_AATSR:
                            pixelProperties = createAatsrPixelProperties(band, targetTile, aatsrL1bFlagTile,
                                    aatsrReflectanceTiles, aatsrReflectance,
                                    aatsrBtempTiles,
                                    aatsrBtemp, waterMaskSample, y, x);
                            break;
                        case IdepixConstants.PRODUCT_TYPE_VGT:
                            pixelProperties = createVgtPixelProperties(band, smFlagTile, vgtReflectanceTiles,
                                    vgtReflectance,
                                    waterMaskSample, y, x);
                            break;
                        default:
                            break;
                    }

                    if (band == cloudFlagBand) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        targetTile.setSample(x, y, IdepixConstants.F_INVALID, pixelProperties.isInvalid());
                        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, pixelProperties.isCloud());
                        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, pixelProperties.isClearLand());
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, pixelProperties.isClearWater());
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, pixelProperties.isClearSnow());
                        targetTile.setSample(x, y, IdepixConstants.F_LAND, pixelProperties.isLand());
                        targetTile.setSample(x, y, IdepixConstants.F_WATER, pixelProperties.isWater());
                        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, pixelProperties.isBright());
                        targetTile.setSample(x, y, IdepixConstants.F_WHITE, pixelProperties.isWhite());
                        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, pixelProperties.isBrightWhite());
                        targetTile.setSample(x, y, IdepixConstants.F_COLD, pixelProperties.isCold());
                        targetTile.setSample(x, y, IdepixConstants.F_HIGH, pixelProperties.isHigh());
                        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, pixelProperties.isVegRisk());
                        targetTile.setSample(x, y, IdepixConstants.F_GLINT_RISK, pixelProperties.isGlintRisk());
                    }

                    // for given instrument, compute more pixel properties and write to distinct band
                    if (band == brightBand) {
                        targetTile.setSample(x, y, pixelProperties.brightValue());
                    } else if (band == whiteBand) {
                        targetTile.setSample(x, y, pixelProperties.whiteValue());
                    } else if (band == brightWhiteBand) {
                        targetTile.setSample(x, y, pixelProperties.brightValue() + pixelProperties.whiteValue());
                    } else if (band == temperatureBand) {
                        targetTile.setSample(x, y, pixelProperties.temperatureValue());
                    } else if (band == spectralFlatnessBand) {
                        targetTile.setSample(x, y, pixelProperties.spectralFlatnessValue());
                    } else if (band == ndviBand) {
                        targetTile.setSample(x, y, pixelProperties.ndviValue());
                    } else if (band == ndsiBand) {
                        targetTile.setSample(x, y, pixelProperties.ndsiValue());
                    } else if (band == glintRiskBand) {
                        targetTile.setSample(x, y, pixelProperties.glintRiskValue());
                    } else if (band == pressureBand) {
                        targetTile.setSample(x, y, pixelProperties.pressureValue());
                    } else if (band == pbaroOutputBand) {
                        targetTile.setSample(x, y, pbaroTile.getSampleFloat(x, y));
                    } else if (band == p1OutputBand) {
                        targetTile.setSample(x, y, p1Tile.getSampleFloat(x, y));
                    } else if (band == pscattOutputBand) {
                        targetTile.setSample(x, y, pscattTile.getSampleFloat(x, y));
                    } else if (band == radioLandBand) {
                        targetTile.setSample(x, y, pixelProperties.radiometricLandValue());
                    } else if (band == radioWaterBand) {
                        targetTile.setSample(x, y, pixelProperties.radiometricWaterValue());
                    }
                }
            }
            // set cloud buffer flags...
            if (gaLcCloudBuffer) {
                setCloudBufferLC(band, targetTile, rectangle);
            } else {
                setCloudBuffer(band, targetTile, rectangle);
            }

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private void setCloudBuffer(Band band, Tile targetTile, Rectangle rectangle) {
        if (band.isFlagBand() && band.getName().equals(GA_CLOUD_FLAGS)) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    if (targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD)) {
                        int LEFT_BORDER = Math.max(x - gaCloudBufferWidth, rectangle.x);
                        int RIGHT_BORDER = Math.min(x + gaCloudBufferWidth, rectangle.x + rectangle.width - 1);
                        int TOP_BORDER = Math.max(y - gaCloudBufferWidth, rectangle.y);
                        int BOTTOM_BORDER = Math.min(y + gaCloudBufferWidth, rectangle.y + rectangle.height - 1);
                        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                                targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private void setCloudBufferLC(Band band, Tile targetTile, Rectangle rectangle) {
        //  set alternative cloud buffer flag as used in LC-CCI project:
        // 1. use 2x2 square with reference pixel in upper left
        // 2. move this square row-by-row over the tile
        // 3. if reference pixel is not clouds, don't do anything
        // 4. if reference pixel is cloudy:
        //    - if 2x2 square only has cloud pixels, then set cloud buffer of two pixels
        //      in both x and y direction of reference pixel.
        //    - if 2x2 square also has non-cloudy pixels, do the same but with cloud buffer of only 1

        if (band.isFlagBand() && band.getName().equals(GA_CLOUD_FLAGS)) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height - 1; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                    if (targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD)) {
                        // reference pixel is upper left (x, y)
                        // first set buffer of 1 in each direction
                        int bufferWidth = 1;
                        int LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                        int RIGHT_BORDER = Math.min(x + bufferWidth, rectangle.x + rectangle.width - 1);
                        int TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                        int BOTTOM_BORDER = Math.min(y + bufferWidth, rectangle.y + rectangle.height - 1);
                        // now check if whole 2x2 square (x+1,y), (x, y+1), (x+1, y+1) is cloudy
                        if (targetTile.getSampleBit(x + 1, y, IdepixConstants.F_CLOUD) &&
                                targetTile.getSampleBit(x, y + 1, IdepixConstants.F_CLOUD) &&
                                targetTile.getSampleBit(x + 1, y + 1, IdepixConstants.F_CLOUD)) {
                            // set buffer of 2 in each direction
                            bufferWidth = 2;
                            LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                            RIGHT_BORDER = Math.min(x + 1 + bufferWidth, rectangle.x + rectangle.width - 1);
                            TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                            BOTTOM_BORDER = Math.min(y + 1 + bufferWidth, rectangle.y + rectangle.height - 1);
                        }
                        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                                targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                            }
                        }

                    }
                }
            }
            int bufferWidth = 1;

            // south tile boundary...
            final int ySouth = rectangle.y + rectangle.height - 1;
            for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                int LEFT_BORDER = Math.max(x - bufferWidth, rectangle.x);
                int RIGHT_BORDER = Math.min(x + bufferWidth, rectangle.x + rectangle.width - 1);
//                int TOP_BORDER = ySouth - bufferWidth;
                int TOP_BORDER = Math.max(rectangle.y, ySouth - bufferWidth);
                if (targetTile.getSampleBit(x, ySouth, IdepixConstants.F_CLOUD)) {
                    for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                        for (int j = TOP_BORDER; j <= ySouth; j++) {
                            targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                        }
                    }
                }
            }

            // east tile boundary...
            final int xEast = rectangle.x + rectangle.width - 1;
            for (int y = rectangle.y; y < rectangle.y + rectangle.height - 1; y++) {
//                int LEFT_BORDER = xEast - bufferWidth;
                int LEFT_BORDER = Math.max(rectangle.x, xEast - bufferWidth);
                int TOP_BORDER = Math.max(y - bufferWidth, rectangle.y);
                int BOTTOM_BORDER = Math.min(y + bufferWidth, rectangle.y + rectangle.height - 1);
                if (targetTile.getSampleBit(xEast, y, IdepixConstants.F_CLOUD)) {
                    for (int i = LEFT_BORDER; i <= xEast; i++) {
                        for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                            targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                        }
                    }
                }
            }
            // pixel in lower right corner...
            if (targetTile.getSampleBit(xEast, ySouth, IdepixConstants.F_CLOUD)) {
                for (int i = Math.max(rectangle.x, xEast - 1); i <= xEast; i++) {
                    for (int j = Math.max(rectangle.y, ySouth - 1); j <= ySouth; j++) {
                        targetTile.setSample(i, j, IdepixConstants.F_CLOUD_BUFFER, true);
                    }
                }
            }
        }
    }

    private VgtPixelProperties createVgtPixelProperties(Band band, Tile smFlagTile, Tile[] vgtReflectanceTiles,
                                                        float[] vgtReflectance, byte watermaskSample, int y, int x) {
        VgtPixelProperties pixelProperties = new VgtPixelProperties();
        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        pixelProperties.setRefl(vgtReflectanceSaturationCorrected);

        final boolean isLand = smFlagTile.getSampleBit(x, y, VgtPixelProperties.SM_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        pixelProperties.setSmLand(isLand);
        setIsWater(watermaskSample, pixelProperties);

        // specific threshold for polar regions:
        geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding != null) {
            final PixelPos pixelPos = new PixelPos();
            pixelPos.setLocation(x + 0.5f, y + 0.5f);
            final GeoPos geoPos = new GeoPos();
            geoCoding.getGeoPos(pixelPos, geoPos);
            final float latitude = geoPos.getLat();
            if (Math.abs(latitude) > 70.0f) {
                pixelProperties.setNdsiThresh(0.65f);  // works better for polar regions, e.g. at DomeC site
            }
        }

        return pixelProperties;
    }

    private AatsrPixelProperties createAatsrPixelProperties(Band band, Tile targetTile, Tile aatsrL1bFlagTile,
                                                            Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                            Tile[] aatsrBtempTiles, float[] aatsrBtemp,
                                                            byte watermaskSample, int y, int x) {
        AatsrPixelProperties pixelProperties = new AatsrPixelProperties();
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

        pixelProperties.setUseFwardViewForCloudMask(gaUseAatsrFwardForClouds);
        pixelProperties.setRefl(aatsrReflectance);
        pixelProperties.setBtemp1200(aatsrBtempTiles[2].getSampleFloat(x, y));
        final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, AatsrPixelProperties.L1B_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        pixelProperties.setL1FlagLand(isLand);
        pixelProperties.setL1FlagGlintRisk(aatsrL1bFlagTile.getSampleBit(x, y, AatsrPixelProperties.L1B_F_GLINT_RISK));
        setIsWater(watermaskSample, pixelProperties);
        return pixelProperties;
    }

    private PixelProperties createMerisPixelProperties(Tile merisL1bFlagTile, Tile merisQwgCloudClassifFlagTile,
                                                       Tile brr442Tile, Tile p1Tile,
                                                       Tile pbaroTile, Tile pscattTile, Tile brr442ThreshTile,
                                                       Tile[] merisReflectanceTiles,
                                                       float[] merisReflectance,
                                                       Tile[] merisBrrTiles, float[] merisBrr, byte watermask, int y,
                                                       int x) {
        MerisPixelProperties pixelProperties = new MerisPixelProperties();


        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
        }

        pixelProperties.setRefl(merisReflectance);
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
        }
        if (x == 360 && y == 500) {
            System.out.println("x = " + x);
        }
        pixelProperties.setBrr(merisBrr);
        pixelProperties.setBrr442(brr442Tile.getSampleFloat(x, y));
        pixelProperties.setBrr442Thresh(brr442ThreshTile.getSampleFloat(x, y));
        pixelProperties.setP1(p1Tile.getSampleFloat(x, y));
        pixelProperties.setPBaro(pbaroTile.getSampleFloat(x, y));
        pixelProperties.setPscatt(pscattTile.getSampleFloat(x, y));
        final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, MerisPixelProperties.L1B_F_LAND) &&
                !(watermask == WatermaskClassifier.WATER_VALUE);
        pixelProperties.setL1FlagLand(isLand);
        pixelProperties.setQwgCloudClassifFlagBrightRc
                (merisQwgCloudClassifFlagTile.getSampleBit(x, y, MerisPixelProperties.F_BRIGHT_RC));
        setIsWater(watermask, pixelProperties);

        // test for Globalbedo, Himalaya:
//        float latitude = latitudeTpg.getPixelFloat(x, y);
//        float altitude = altitudeTpg.getPixelFloat(x, y);
//        if (altitude > 4000) {
//            pixelProperties.setNdsiThresh(0.3f);
//        }

        return pixelProperties;
    }

    private void setIsWater(byte watermask, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermask == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermask == WatermaskClassifier.WATER_VALUE;
        }
        pixelProperties.setIsWater(isWater);
    }

    // currently not used
    private void printPixelFeatures(PixelProperties pixelProperties) {
        System.out.println("bright            = " + pixelProperties.brightValue());
        System.out.println("white             = " + pixelProperties.whiteValue());
        System.out.println("temperature       = " + pixelProperties.temperatureValue());
        System.out.println("spec_flat         = " + pixelProperties.spectralFlatnessValue());
        System.out.println("ndvi              = " + pixelProperties.ndviValue());
        System.out.println("ndsi              = " + pixelProperties.ndsiValue());
        System.out.println("pressure          = " + pixelProperties.pressureValue());
        System.out.println("cloudy            = " + pixelProperties.isCloud());
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

    private interface WatermaskStrategy {

        byte getWatermaskSample(float lat, float lon);
    }

    private class MerisWatermaskStrategy implements WatermaskStrategy {

        @Override
        public byte getWatermaskSample(float lat, float lon) {
            int waterMaskSample = WatermaskClassifier.INVALID_VALUE;
            if (classifier != null) {
                waterMaskSample = classifier.getWaterMaskSample(lat, lon);
            }
            return (byte) waterMaskSample;
        }
    }
}
