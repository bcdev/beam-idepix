package org.esa.beam.mepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
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

import java.awt.Rectangle;

/**
 * Operator for cloud screening from SPOT VGT data
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

    @SourceProduct(alias="vgtl1b", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias="cloud", optional=true)
    private Product cloudProduct;
    @SourceProduct(alias="rayleigh", optional=true)
    private Product rayleighProduct;
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
    public static final int F_HIGH = 10;
    public static final int F_VEG_RISK = 11;

    public static final String GA_CLOUD_FLAGS = "cloud_classif_flags";
    private int sceneWidth;
    private int sceneHeight;

    private int sourceProductTypeId;

    // MERIS bands:
    private Band brr442Band;
    private Band brr442ThreshBand;

    // AATSR bands:
    private Band reflecNadir0550Band;
    private Band reflecNadir0670Band;
    private Band reflecNadir0870Band;
    private Band reflecNadir1600Band;

    // VGT bands:
    private Band[] vgtRadianceBands;
    private Band b0Band;
    private Band b2Band;
    private Band b3Band;
    private Band mirBand;


    @Override
    public void initialize() throws OperatorException {
        if (sourceProduct != null) {
            setSourceProductTypeId();

            switch (sourceProductTypeId) {
                case MepixConstants.PRODUCT_TYPE_MERIS:
                    brr442Band = rayleighProduct.getBand("brr_2");
                    brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");
                    break;
                case MepixConstants.PRODUCT_TYPE_AATSR:
                    reflecNadir0550Band = sourceProduct.getBand("reflec_nadir_0550");
                    reflecNadir0670Band = sourceProduct.getBand("reflec_nadir_0670");
                    reflecNadir0870Band = sourceProduct.getBand("reflec_nadir_0870");
                    reflecNadir1600Band = sourceProduct.getBand("reflec_nadir_1600");
                    break;
                case MepixConstants.PRODUCT_TYPE_VGT:
                    vgtRadianceBands = new Band[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                    for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                        vgtRadianceBands[i] = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i]);
                    }

                    b0Band = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[0]);
                    b2Band = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[1]);
                    b3Band = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[2]);
                    mirBand = sourceProduct.getBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[3]);
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
        sceneWidth = sourceProduct.getSceneRasterWidth();
        sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        Band cloudFlagBand = targetProduct.addBand(GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(GA_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        Band brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
        Band whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
        Band spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
        Band ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
        Band ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
        Band pressureBand = targetProduct.addBand("pressure_value", ProductData.TYPE_FLOAT32);

        if (gaCopyRadiances) {
            switch (sourceProductTypeId) {
                case MepixConstants.PRODUCT_TYPE_MERIS:
                    break;
                case MepixConstants.PRODUCT_TYPE_AATSR:
                    ProductUtils.copyBand(reflecNadir0550Band.getName(), sourceProduct, targetProduct);
                    ProductUtils.copyBand(reflecNadir0670Band.getName(), sourceProduct, targetProduct);
                    ProductUtils.copyBand(reflecNadir0870Band.getName(), sourceProduct, targetProduct);
                    ProductUtils.copyBand(reflecNadir1600Band.getName(), sourceProduct, targetProduct);
                    break;
                case MepixConstants.PRODUCT_TYPE_VGT:
                    for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
//                        ProductUtils.copyBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i], sourceProduct, targetProduct);
                        targetProduct.addBand(MepixConstants.VGT_RADIANCE_BAND_NAMES[i], ProductData.TYPE_FLOAT32);
                    }
                    ProductUtils.copyFlagBands(sourceProduct, targetProduct);
                    break;
                default:
                    break;
            }
        }
    }

    public static FlagCoding createFlagCoding(String flagIdentifier) {
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
        flagCoding.addFlag("F_HIGH", BitSetter.setFlag(0, F_HIGH), null);
        flagCoding.addFlag("F_VEG_RISK", BitSetter.setFlag(0, F_VEG_RISK), null);

        return flagCoding;
    }


    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);

        // MERIS variables
        Tile brr442Tile = null;
        Tile brr442ThreshTile = null;

        // AATSR variables
        Tile reflecNadir0550Tile = null;
        Tile reflecNadir0670Tile = null;
        Tile reflecNadir0870Tile = null;
        Tile reflecNadir1600Tile = null;

        // VGT variables
        Band smFlagBand = null;
//        Tile b0Tile = null;
//        Tile b2Tile = null;
//        Tile b3Tile = null;
//        Tile mirTile = null;
        Tile smFlagTile = null;

        Tile[] vgtReflectanceTiles = null;
        float[] vgtReflectance = null;

        switch (sourceProductTypeId) {
            case MepixConstants.PRODUCT_TYPE_MERIS:
                brr442Band = rayleighProduct.getBand("brr_2");
                brr442ThreshBand = cloudProduct.getBand("rho442_thresh_term");
                brr442Tile = getSourceTile(brr442Band, rectangle, pm);
                brr442ThreshTile = getSourceTile(brr442ThreshBand, rectangle, pm);
                break;
            case MepixConstants.PRODUCT_TYPE_AATSR:
                reflecNadir0550Tile = getSourceTile(reflecNadir0550Band, rectangle, pm);
                reflecNadir0670Tile = getSourceTile(reflecNadir0670Band, rectangle, pm);
                reflecNadir0870Tile = getSourceTile(reflecNadir0870Band, rectangle, pm);
                reflecNadir1600Tile = getSourceTile(reflecNadir1600Band, rectangle, pm);
                break;
            case MepixConstants.PRODUCT_TYPE_VGT:
                smFlagBand = sourceProduct.getBand("SM");
                smFlagTile = getSourceTile(smFlagBand, rectangle, pm);

                vgtReflectanceTiles = new Tile[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                vgtReflectance = new float[MepixConstants.VGT_RADIANCE_BAND_NAMES.length];
                for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                    vgtReflectanceTiles[i] = getSourceTile(vgtRadianceBands[i], rectangle, pm);
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

//                    if (x == 1912 && y == 987) {
//                        System.out.println("");
//                    }
//                    if (x == 1737 && y == 1172) {
//                        System.out.println("");
//                    }

                    // set up pixel properties for given instruments...
                    PixelProperties pixelProperties = null;
                    switch (sourceProductTypeId) {
                        case MepixConstants.PRODUCT_TYPE_MERIS:
                            pixelProperties = new MerisPixelProperties();
                            ((MerisPixelProperties) pixelProperties).setBrr442(brr442Tile.getSampleFloat(x, y));
                            ((MerisPixelProperties) pixelProperties).setBrr442Thresh(brr442ThreshTile.getSampleFloat(x, y));
                            break;
                        case MepixConstants.PRODUCT_TYPE_AATSR:
                            pixelProperties = new AatsrPixelProperties();
                            ((AatsrPixelProperties) pixelProperties).setReflecNadir0550(reflecNadir0550Tile.getSampleFloat(x, y));
                            ((AatsrPixelProperties) pixelProperties).setReflecNadir0670(reflecNadir0670Tile.getSampleFloat(x, y));
                            ((AatsrPixelProperties) pixelProperties).setReflecNadir0870(reflecNadir0870Tile.getSampleFloat(x, y));
                            ((AatsrPixelProperties) pixelProperties).setReflecNadir1600(reflecNadir1600Tile.getSampleFloat(x, y));
                            break;
                        case MepixConstants.PRODUCT_TYPE_VGT:
                            pixelProperties = new VgtPixelProperties();
                            for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                                vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
                            }
                            float[] vgtReflectanceSaturationCorrected = MepixUtils.correctSaturatedReflectances(vgtReflectance);
                            ((VgtPixelProperties) pixelProperties).setRefl(vgtReflectance);

                            ((VgtPixelProperties) pixelProperties).setSmLand(smFlagTile.getSampleBit(x, y, VgtPixelProperties.SM_F_LAND));
                            if (gaCopyRadiances) {
                                for (int i = 0; i < MepixConstants.VGT_RADIANCE_BAND_NAMES.length; i++) {
                                    if (band.getName().equals(MepixConstants.VGT_RADIANCE_BAND_NAMES[i])) {
                                        // copy reflectances corrected for saturation
                                        if (MepixUtils.areReflectancesValid(vgtReflectance)) {
                                        targetTile.setSample(x, y, vgtReflectanceSaturationCorrected[i]);
                                        } else {
                                            targetTile.setSample(x, y, Float.NaN);
                                        }
                                    }
                                }
                                if (band.isFlagBand() && band.getName().equals(MepixConstants.VGT_SM_FLAG_BAND_NAME)) {
                                    targetTile.setSample(x, y, smFlagTile.getSampleInt(x, y));
                                }
                            }
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
                        targetTile.setSample(x, y, F_HIGH, pixelProperties.isHigh());
                        targetTile.setSample(x, y, F_VEG_RISK, pixelProperties.isVegRisk());
                    }

                    // for given instrument, compute more pixel properties and write to distinct band
                    if (band.getName().equals("bright_value")) {
                        targetTile.setSample(x, y, pixelProperties.brightValue());
                    } else if (band.getName().equals("white_value")) {
                        targetTile.setSample(x, y, pixelProperties.whiteValue());
                    } else if (band.getName().equals("spectral_flatness_value")) {
                        targetTile.setSample(x, y, pixelProperties.spectralFlatnessValue());
                    }else if (band.getName().equals("ndvi_value")) {
                        targetTile.setSample(x, y, pixelProperties.ndviValue());
                    } else if (band.getName().equals("ndsi_value")) {
                        targetTile.setSample(x, y, pixelProperties.ndsiValue());
                    } else if (band.getName().equals("pressure_value")) {
                        targetTile.setSample(x, y, pixelProperties.pressureValue());
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
