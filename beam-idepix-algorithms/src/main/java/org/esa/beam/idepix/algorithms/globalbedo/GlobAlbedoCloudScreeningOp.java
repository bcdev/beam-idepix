package org.esa.beam.idepix.algorithms.globalbedo;

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
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.schiller.SchillerAlgorithm;
import org.esa.beam.idepix.operators.*;
import org.esa.beam.idepix.pixel.AbstractPixelProperties;
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
public class GlobAlbedoCloudScreeningOp extends Operator {

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
    @Parameter(defaultValue = "false", label = " Use the NN based Schiller cloud algorithm")
    private boolean gaComputeSchillerClouds = false;
    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    private boolean gaUseWaterMaskFraction = true;


    private int sourceProductTypeId;

    private WatermaskClassifier classifier;
    private static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water

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
    //    private GeoCoding geoCoding;
    private WatermaskStrategy strategy = null;
    private SchillerAlgorithm landNN = null;


    @Override
    public void initialize() throws OperatorException {
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // for debugging purpose
        setSourceProductTypeId();
        try {
            classifier = new WatermaskClassifier(wmResolution, 3, 3);
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

                if (gaComputeSchillerClouds) {
                    landNN = new SchillerAlgorithm(SchillerAlgorithm.Net.LAND);
                }
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

        cloudFlagBand = targetProduct.addBand(IdepixUtils.GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixUtils.createGAFlagCoding(IdepixUtils.GA_CLOUD_FLAGS);
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
                                              targetProduct, true);
                    }
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        ProductUtils.copyBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i + 1), rad2reflProduct,
                                              targetProduct, true);
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_AATSR:
                    for (int i = 0; i < IdepixConstants.AATSR_REFL_WAVELENGTHS.length; i++) {
                        ProductUtils.copyBand(IdepixConstants.AATSR_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                              targetProduct, true);
                    }
                    for (int i = 0; i < IdepixConstants.AATSR_TEMP_WAVELENGTHS.length; i++) {
                        ProductUtils.copyBand(IdepixConstants.AATSR_BTEMP_BAND_NAMES[i], sourceProduct,
                                              targetProduct, true);
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        // write the original reflectance bands:
                        ProductUtils.copyBand(IdepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct,
                                              targetProduct, true);
                    }
                    break;
                default:
                    break;
            }
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (gaCopyAnnotations) {
            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
                        ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
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

                    byte waterMaskSample = WatermaskClassifier.INVALID_VALUE;
                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    if (!gaUseL1bLandWaterFlag) {
                        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
                        if (geoCoding.canGetGeoPos()) {
                            geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                            waterMaskSample = strategy.getWatermaskSample(geoPos.lat, geoPos.lon);
                            waterMaskFraction = strategy.getWatermaskFraction(geoCoding, x, y);
                        }
                    }

                    // set up pixel properties for given instruments...
                    GlobAlbedoAlgorithm globAlbedoAlgorithm = null;
                    switch (sourceProductTypeId) {
                        case IdepixConstants.PRODUCT_TYPE_MERIS:
                            globAlbedoAlgorithm = createMerisAlgorithm(merisL1bFlagTile, merisQwgCloudClassifFlagTile,
                                                                       brr442Tile, p1Tile,
                                                                       pbaroTile, pscattTile, brr442ThreshTile,
                                                                       merisReflectanceTiles,
                                                                       merisReflectance,
                                                                       merisBrrTiles, merisBrr, waterMaskSample,
                                                                       waterMaskFraction,
                                                                       y,
                                                                       x);

                            break;
                        case IdepixConstants.PRODUCT_TYPE_AATSR:
                            globAlbedoAlgorithm = createAatsrAlgorithm(band, targetTile, aatsrL1bFlagTile,
                                                                       aatsrReflectanceTiles, aatsrReflectance,
                                                                       aatsrBtempTiles,
                                                                       aatsrBtemp, waterMaskSample, y, x);
                            break;
                        case IdepixConstants.PRODUCT_TYPE_VGT:
                            globAlbedoAlgorithm = createVgtAlgorithm(band, smFlagTile, vgtReflectanceTiles,
                                                                     vgtReflectance,
                                                                     waterMaskSample, y, x);
                            break;
                        default:
                            break;
                    }

                    if (band == cloudFlagBand) {
                        // for given instrument, compute boolean pixel properties and write to cloud flag band
                        targetTile.setSample(x, y, IdepixConstants.F_INVALID, globAlbedoAlgorithm.isInvalid());
                        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, globAlbedoAlgorithm.isCloud());
                        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, globAlbedoAlgorithm.isClearLand());
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, globAlbedoAlgorithm.isClearWater());
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, globAlbedoAlgorithm.isClearSnow());
                        targetTile.setSample(x, y, IdepixConstants.F_LAND, globAlbedoAlgorithm.isLand());
                        targetTile.setSample(x, y, IdepixConstants.F_WATER, globAlbedoAlgorithm.isWater());
                        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, globAlbedoAlgorithm.isBright());
                        targetTile.setSample(x, y, IdepixConstants.F_WHITE, globAlbedoAlgorithm.isWhite());
                        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, globAlbedoAlgorithm.isBrightWhite());
                        targetTile.setSample(x, y, IdepixConstants.F_COLD, globAlbedoAlgorithm.isCold());
                        targetTile.setSample(x, y, IdepixConstants.F_HIGH, globAlbedoAlgorithm.isHigh());
                        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, globAlbedoAlgorithm.isVegRisk());
                        targetTile.setSample(x, y, IdepixConstants.F_GLINT_RISK, globAlbedoAlgorithm.isGlintRisk());

                        if (landNN != null && !targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD)) {
                            final int finalX = x;
                            final int finalY = y;
                            final Tile[] finalMerisRefl = merisReflectanceTiles;
                            SchillerAlgorithm.Accessor accessor = new SchillerAlgorithm.Accessor() {
                                @Override
                                public double get(int index) {
                                    return finalMerisRefl[index].getSampleDouble(finalX, finalY);
                                }
                            };
                            float schillerCloud = landNN.compute(accessor);
                            if (schillerCloud > 1.4) {
                                targetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                        }
                    }

                    // for given instrument, compute more pixel properties and write to distinct band
                    if (band == brightBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue());
                    } else if (band == whiteBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.whiteValue());
                    } else if (band == brightWhiteBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.brightValue() + globAlbedoAlgorithm.whiteValue());
                    } else if (band == temperatureBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.temperatureValue());
                    } else if (band == spectralFlatnessBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.spectralFlatnessValue());
                    } else if (band == ndviBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.ndviValue());
                    } else if (band == ndsiBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.ndsiValue());
                    } else if (band == glintRiskBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.glintRiskValue());
                    } else if (band == pressureBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.pressureValue());
                    } else if (band == pbaroOutputBand) {
                        targetTile.setSample(x, y, pbaroTile.getSampleFloat(x, y));
                    } else if (band == p1OutputBand) {
                        targetTile.setSample(x, y, p1Tile.getSampleFloat(x, y));
                    } else if (band == pscattOutputBand) {
                        targetTile.setSample(x, y, pscattTile.getSampleFloat(x, y));
                    } else if (band == radioLandBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricLandValue());
                    } else if (band == radioWaterBand) {
                        targetTile.setSample(x, y, globAlbedoAlgorithm.radiometricWaterValue());
                    }
                }
            }
            // set cloud buffer flags...
            if (gaLcCloudBuffer) {
                IdepixUtils.setCloudBufferLC(band, targetTile, rectangle);
            } else {
                setCloudBuffer(band, targetTile, rectangle);
            }

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private void setCloudBuffer(Band band, Tile targetTile, Rectangle rectangle) {
        if (band.isFlagBand() && band.getName().equals(IdepixUtils.GA_CLOUD_FLAGS)) {
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

    private GlobAlbedoAlgorithm createVgtAlgorithm(Band band, Tile smFlagTile, Tile[] vgtReflectanceTiles,
                                                        float[] vgtReflectance, byte watermaskSample, int y, int x) {

        GlobAlbedoVgtAlgorithm gaAlgorithm = new GlobAlbedoVgtAlgorithm();

        for (int i = 0; i < IdepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        gaAlgorithm.setRefl(vgtReflectanceSaturationCorrected);

        final boolean isLand = smFlagTile.getSampleBit(x, y, GlobAlbedoVgtAlgorithm.SM_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        gaAlgorithm.setSmLand(isLand);
        setIsWater(watermaskSample, gaAlgorithm);

        // specific threshold for polar regions:
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();
        if (geoCoding != null) {
            final PixelPos pixelPos = new PixelPos();
            pixelPos.setLocation(x + 0.5f, y + 0.5f);
            final GeoPos geoPos = new GeoPos();
            geoCoding.getGeoPos(pixelPos, geoPos);
            final float latitude = geoPos.getLat();
            if (Math.abs(latitude) > 70.0f) {
                gaAlgorithm.setNdsiThresh(0.65f);  // works better for polar regions, e.g. at DomeC site
            }
        }

        return gaAlgorithm;
    }

    private GlobAlbedoAlgorithm createAatsrAlgorithm(Band band, Tile targetTile, Tile aatsrL1bFlagTile,
                                                            Tile[] aatsrReflectanceTiles, float[] aatsrReflectance,
                                                            Tile[] aatsrBtempTiles, float[] aatsrBtemp,
                                                            byte watermaskSample, int y, int x) {

        GlobAlbedoAatsrAlgorithm gaAlgorithm = new GlobAlbedoAatsrAlgorithm();

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

        gaAlgorithm.setUseFwardViewForCloudMask(gaUseAatsrFwardForClouds);
        gaAlgorithm.setRefl(aatsrReflectance);
        gaAlgorithm.setBtemp1200(aatsrBtempTiles[2].getSampleFloat(x, y));
        final boolean isLand = aatsrL1bFlagTile.getSampleBit(x, y, GlobAlbedoAatsrAlgorithm.L1B_F_LAND) &&
                !(watermaskSample == WatermaskClassifier.WATER_VALUE);
        gaAlgorithm.setL1FlagLand(isLand);
        gaAlgorithm.setL1FlagGlintRisk(aatsrL1bFlagTile.getSampleBit(x, y, GlobAlbedoAatsrAlgorithm.L1B_F_GLINT_RISK));
        setIsWater(watermaskSample, gaAlgorithm);

        return gaAlgorithm;
    }


    private GlobAlbedoAlgorithm createMerisAlgorithm(Tile merisL1bFlagTile, Tile merisQwgCloudClassifFlagTile,
                                                       Tile brr442Tile, Tile p1Tile,
                                                       Tile pbaroTile, Tile pscattTile, Tile brr442ThreshTile,
                                                       Tile[] merisReflectanceTiles,
                                                       float[] merisReflectance,
                                                       Tile[] merisBrrTiles, float[] merisBrr,
                                                       byte watermask,
                                                       byte watermaskFraction,
                                                       int y,
                                                       int x) {
        GlobAlbedoMerisAlgorithm gaAlgorithm = new GlobAlbedoMerisAlgorithm();

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            merisReflectance[i] = merisReflectanceTiles[i].getSampleFloat(x, y);
        }

        gaAlgorithm.setRefl(merisReflectance);
        for (int i = 0; i < IdepixConstants.MERIS_BRR_BAND_NAMES.length; i++) {
            merisBrr[i] = merisBrrTiles[i].getSampleFloat(x, y);
        }
        gaAlgorithm.setBrr(merisBrr);
        gaAlgorithm.setBrr442(brr442Tile.getSampleFloat(x, y));
        gaAlgorithm.setBrr442Thresh(brr442ThreshTile.getSampleFloat(x, y));
        gaAlgorithm.setP1(p1Tile.getSampleFloat(x, y));
        gaAlgorithm.setPBaro(pbaroTile.getSampleFloat(x, y));
        gaAlgorithm.setPscatt(pscattTile.getSampleFloat(x, y));
        if (gaUseWaterMaskFraction) {
            final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, GlobAlbedoAlgorithm.L1B_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            gaAlgorithm.setL1FlagLand(isLand);
            setIsWaterByFraction(watermaskFraction, gaAlgorithm);
        } else {
            final boolean isLand = merisL1bFlagTile.getSampleBit(x, y, GlobAlbedoAlgorithm.L1B_F_LAND) &&
                    !(watermask == WatermaskClassifier.WATER_VALUE);
            gaAlgorithm.setL1FlagLand(isLand);
            setIsWater(watermask, gaAlgorithm);
        }

        return gaAlgorithm;
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

    private void setIsWaterByFraction(byte watermaskFraction, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermaskFraction == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermaskFraction >= WATERMASK_FRACTION_THRESH;
        }
        pixelProperties.setIsWater(isWater);
    }

    // currently not used
    private void printPixelFeatures(GlobAlbedoAlgorithm algorithm) {
        System.out.println("bright            = " + algorithm.brightValue());
        System.out.println("white             = " + algorithm.whiteValue());
        System.out.println("temperature       = " + algorithm.temperatureValue());
        System.out.println("spec_flat         = " + algorithm.spectralFlatnessValue());
        System.out.println("ndvi              = " + algorithm.ndviValue());
        System.out.println("ndsi              = " + algorithm.ndsiValue());
        System.out.println("pressure          = " + algorithm.pressureValue());
        System.out.println("cloudy            = " + algorithm.isCloud());
        System.out.println("clear snow        = " + algorithm.isClearSnow());
        System.out.println("radiometric_land  = " + algorithm.radiometricLandValue());
        System.out.println("radiometric_water = " + algorithm.radiometricWaterValue());
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoCloudScreeningOp.class, "idepix.GACloudScreening");
        }
    }

    private interface WatermaskStrategy {

        byte getWatermaskSample(float lat, float lon);

        byte getWatermaskFraction(GeoCoding geoCoding, int x, int y);
    }

    private class MerisWatermaskStrategy implements WatermaskStrategy {

        @Override
        public byte getWatermaskSample(float lat, float lon) {
            int waterMaskSample = WatermaskClassifier.INVALID_VALUE;
            if (classifier != null && lat > -60f) {
                //TODO the watermask does not work below -60 degree (mz, 2012-03-06)
                waterMaskSample = classifier.getWaterMaskSample(lat, lon);
            }
            return (byte) waterMaskSample;
        }

        @Override
        public byte getWatermaskFraction(GeoCoding geoCoding, int x, int y) {
            int waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
            final GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x, y), null);
            if (classifier != null && geoPos.getLat() > -60f) {
                waterMaskFraction = classifier.getWaterMaskFraction(geoCoding, x, y);
            }
            return (byte) waterMaskFraction;
        }
    }
}
