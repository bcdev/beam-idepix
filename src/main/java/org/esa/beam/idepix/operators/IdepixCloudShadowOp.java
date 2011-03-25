package org.esa.beam.idepix.operators;

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
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Random;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixCloudShadowOp extends MerisBasisOp {

     // todo:
    //  - call this Op just behind gaCloudProduct computation
    //  - take as input the gaCloudProduct and the ctp product
    //  - copy all bands to target product except cloud_classif_flags
    //  - add a new flag band with same entries as ga cloud_classif_flags but with additional cloud shadow bit
    //  - set cloud shadow bit as computed by this Op, set all other bits as in cloud_classif_flags


    private static final int MEAN_EARTH_RADIUS = 6372000;

    private static final int MAX_ITER = 5;

    private static final double DIST_THRESHOLD = 1 / 740.0;

    private RectangleExtender rectCalculator;
    private GeoCoding geoCoding;
    private RasterDataNode altitudeRDN;

    public static final String GA_CLOUD_FLAGS = "cloud_classif_flags";

    private Band cloudFlagBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "ctp", optional = true)
    private Product ctpProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private int shadowWidth;
    @Parameter(description = "CTP constant value", defaultValue = "500.0")
    private float ctpConstantValue;

    public static final int F_INVALID = 0;
    public static final int F_CLOUD = 1;
    public static final int F_CLOUD_BUFFER = 2;
    public static final int F_CLOUD_SHADOW = 3;
    public static final int F_CLEAR_LAND = 4;
    public static final int F_CLEAR_WATER = 5;
    public static final int F_CLEAR_SNOW = 6;
    public static final int F_LAND = 7;
    public static final int F_WATER = 8;
    public static final int F_BRIGHT = 9;
    public static final int F_WHITE = 10;
    private static final int F_BRIGHTWHITE = 11;
    private static final int F_COLD = 12;
    public static final int F_HIGH = 13;
    public static final int F_VEG_RISK = 14;
    public static final int F_GLINT_RISK = 15;

    private int sourceProductTypeId;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(cloudProduct, "MER_CLOUD_SHADOW", "MER_L2");
        Band cloudBand = ProductUtils.copyBand(CombinedCloudOp.FLAG_BAND_NAME, cloudProduct, targetProduct);
        FlagCoding sourceFlagCoding = cloudProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME).getFlagCoding();
        ProductUtils.copyFlagCoding(sourceFlagCoding, targetProduct);
        cloudBand.setSampleCoding(targetProduct.getFlagCodingGroup().get(sourceFlagCoding.getName()));

        if (l1bProduct.getProductType().equals(
                EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME)) {
            if (shadowWidth == 0) {
                shadowWidth = 16;
            }
            altitudeRDN = l1bProduct.getBand("altitude");
        } else {
            if (shadowWidth == 0) {
                shadowWidth = 64;
            }
            altitudeRDN = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
        }
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               shadowWidth, shadowWidth);
        geoCoding = l1bProduct.getGeoCoding();
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = l1bProduct.getSceneRasterWidth();
        int sceneHeight = l1bProduct.getSceneRasterHeight();

        targetProduct = new Product(l1bProduct.getName(), l1bProduct.getProductType(), sceneWidth, sceneHeight);

        cloudFlagBand = targetProduct.addBand(GA_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(GA_CLOUD_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(l1bProduct, targetProduct);

        ProductUtils.copyGeoCoding(l1bProduct, targetProduct);
        targetProduct.setStartTime(l1bProduct.getStartTime());
        targetProduct.setEndTime(l1bProduct.getEndTime());
        ProductUtils.copyMetadata(l1bProduct, targetProduct);


        // new bit masks:
        int bitmaskIndex = setupGlobAlbedoCloudscreeningBitmasks();

            switch (sourceProductTypeId) {
                case IdepixConstants.PRODUCT_TYPE_MERIS:
                    for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
                        Band b = ProductUtils.copyBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i], l1bProduct,
                                                       targetProduct);
                        b.setSourceImage(l1bProduct.getBand(
                                EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]).getSourceImage());
                    }
                    break;
                case IdepixConstants.PRODUCT_TYPE_AATSR:
                    // nothing to do yet
                    break;
                case IdepixConstants.PRODUCT_TYPE_VGT:
                    // nothing to do yet
                    break;
                default:
                    break;
            }

            // copy flag bands
            ProductUtils.copyFlagBands(l1bProduct, targetProduct);
            for (Band sb : l1bProduct.getBands()) {
                if (sb.isFlagBand()) {
                    Band tb = targetProduct.getBand(sb.getName());
                    tb.setSourceImage(sb.getSourceImage());
                }
            }


    }

    private void setSourceProductTypeId() {
        if (l1bProduct.getProductType().startsWith("MER")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_MERIS;
        } else if (l1bProduct.getProductType().startsWith("ATS")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_AATSR;
        } else if (l1bProduct.getProductType().startsWith("VGT")) {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_VGT;
        } else {
            sourceProductTypeId = IdepixConstants.PRODUCT_TYPE_INVALID;
        }
    }


    private int setupGlobAlbedoCloudscreeningBitmasks() {

        int index = 0;
        int w = l1bProduct.getSceneRasterWidth();
        int h = l1bProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random();

        mask = Mask.BandMathsType.create("F_INVALID", "Invalid pixels", w, h, "cloud_classif_flags.F_INVALID",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD", "Cloudy pixels", w, h, "cloud_classif_flags.F_CLOUD",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLOUD_BUFFER", "Cloud + cloud buffer pixels", w, h,
                                         "cloud_classif_flags.F_CLOUD_BUFFER", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_LAND", "Clear sky pixels over land", w, h,
                                         "cloud_classif_flags.F_CLEAR_LAND", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_WATER", "Clear sky pixels over water", w, h,
                                         "cloud_classif_flags.F_CLEAR_WATER", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_CLEAR_SNOW", "Clear sky pixels, snow covered ", w, h,
                                         "cloud_classif_flags.F_CLEAR_SNOW", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_LAND", "Pixels over land", w, h, "cloud_classif_flags.F_LAND",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_WATER", "Pixels over water", w, h, "cloud_classif_flags.F_WATER",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_BRIGHT", "Pixels classified as bright", w, h,
                                         "cloud_classif_flags.F_BRIGHT", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_WHITE", "Pixels classified as white", w, h, "cloud_classif_flags.F_WHITE",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_BRIGHTWHITE", "Pixels classified as 'brightwhite'", w, h,
                                         "cloud_classif_flags.F_BRIGHTWHITE", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_COLD", "Cold pixels", w, h, "cloud_classif_flags.F_COLD",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_HIGH", "High pixels", w, h, "cloud_classif_flags.F_HIGH",
                                         getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_VEG_RISK", "Pixels may contain vegetation", w, h,
                                         "cloud_classif_flags.F_VEG_RISK", getRandomColour(r), 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("F_GLINT_RISK", "Pixels may contain glint", w, h,
                                         "cloud_classif_flags.F_GLINT_RISK", getRandomColour(r), 0.5f);
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
        flagCoding.addFlag("F_CLOUD_BUFFER", BitSetter.setFlag(0, F_CLOUD_BUFFER), null);
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

        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        Tile szaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                     sourceRectangle);
        Tile saaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                                     sourceRectangle);
        Tile vzaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                     sourceRectangle);
        Tile vaaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                                     sourceRectangle);
        Tile cloudTile = getSourceTile(cloudProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME), sourceRectangle);
        Tile altTile = getSourceTile(altitudeRDN, sourceRectangle);
        Tile ctpTile = null;
        if (ctpProduct != null) {
            ctpTile = getSourceTile(ctpProduct.getBand("cloud_top_press"), sourceRectangle);
        }

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                targetTile.setSample(x, y, cloudTile.getSampleInt(x, y));
            }
        }

        for (int y = sourceRectangle.y; y < sourceRectangle.y + sourceRectangle.height; y++) {
            for (int x = sourceRectangle.x; x < sourceRectangle.x + sourceRectangle.width; x++) {
                if (x == 122 && y == 1014) {
                    System.out.println("x = " + x);
                }
                if (x == 123 && y == 1014) {
                    System.out.println("x = " + x);
                }
                if ((cloudTile.getSampleInt(x, y) & CombinedCloudOp.FLAG_CLOUD) != 0) {
                    final float sza = szaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
                    final float saa = saaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
                    final float vza = vzaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
                    final float vaa = vaaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;

                    PixelPos pixelPos = new PixelPos(x, y);
                    final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);
                    float ctp;
                    if (ctpTile != null) {
                        ctp = ctpTile.getSampleFloat(x, y);
                    } else {
                        ctp = ctpConstantValue;
                    }
                    if (ctp > 0) {
                        float cloudAlt = computeHeightFromPressure(ctp);
                        GeoPos shadowPos = getCloudShadow2(altTile, sza, saa, vza, vaa, cloudAlt, geoPos);
                        if (shadowPos != null) {
                            pixelPos = geoCoding.getPixelPos(shadowPos, pixelPos);

                            if (targetRectangle.contains(pixelPos)) {
                                final int pixelX = MathUtils.floorInt(pixelPos.x);
                                final int pixelY = MathUtils.floorInt(pixelPos.y);
                                if (pixelX == 120 && pixelY == 1012) {
                                    System.out.println("pixelX = " + pixelX);
                                    System.out.println("pixelY = " + pixelY);
                                }
                                int flagValue = cloudTile.getSampleInt(pixelX, pixelY);
                                if ((flagValue & CombinedCloudOp.FLAG_CLOUD_SHADOW) == 0) {
                                    flagValue += CombinedCloudOp.FLAG_CLOUD_SHADOW;
                                    targetTile.setSample(pixelX, pixelY, flagValue);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private float computeHeightFromPressure(float pressure) {
        return (float) (-8000 * Math.log(pressure / 1013.0f));
    }

    private GeoPos getCloudShadow2(Tile altTile, float sza, float saa, float vza,
                                   float vaa, float cloudAlt, GeoPos appCloud) {

        double surfaceAlt = getAltitude(altTile, appCloud);

        // deltaX and deltaY are the corrections to apply to get the
        // real cloud position from the apparent one
        // deltaX/deltaY are in meters
        final double deltaX = -(cloudAlt - surfaceAlt) * Math.tan(vza) * Math.sin(vaa);
        final double deltaY = -(cloudAlt - surfaceAlt) * Math.tan(vza) * Math.cos(vaa);

        // distLat and distLon are in degrees
        double distLat = -(deltaY / MEAN_EARTH_RADIUS) * MathUtils.RTOD;
        double distLon = -(deltaX / (MEAN_EARTH_RADIUS *
                                     Math.cos(appCloud.getLat() * MathUtils.DTOR))) * MathUtils.RTOD;

        double latCloud = appCloud.getLat() + distLat;
        double lonCloud = appCloud.getLon() + distLon;

        // once the cloud position is know, we iterate to get the shadow
        // position
        int iter = 0;
        double dist = 2 * DIST_THRESHOLD;
        surfaceAlt = 0;
        double lat = latCloud;
        double lon = lonCloud;
        GeoPos pos = new GeoPos();

        while ((iter < MAX_ITER) && (dist > DIST_THRESHOLD)
               && (surfaceAlt < cloudAlt)) {
            double lat0 = lat;
            double lon0 = lon;
            pos.setLocation((float) lat, (float) lon);
            PixelPos pixelPos = geoCoding.getPixelPos(pos, null);
            if (!(pixelPos.isValid() && altTile.getRectangle().contains(pixelPos))) {
                return null;
            }
            surfaceAlt = getAltitude(altTile, pos);

            double deltaProjX = (cloudAlt - surfaceAlt) * Math.tan(sza) * Math.sin(saa);
            double deltaProjY = (cloudAlt - surfaceAlt) * Math.tan(sza) * Math.cos(saa);

            // distLat and distLon are in degrees
            distLat = -(deltaProjY / MEAN_EARTH_RADIUS) * MathUtils.RTOD;
            lat = latCloud + distLat;
            distLon = -(deltaProjX / (MEAN_EARTH_RADIUS * Math.cos(lat * MathUtils.DTOR))) * MathUtils.RTOD;
            lon = lonCloud + distLon;

            dist = Math.max(Math.abs(lat - lat0), Math.abs(lon - lon0));
            iter++;
        }

        if (surfaceAlt < cloudAlt && iter < MAX_ITER && dist < DIST_THRESHOLD) {
            return new GeoPos((float) lat, (float) lon);
        }
        return null;
    }

    private float getAltitude(Tile altTile, GeoPos geoPos) {
        final PixelPos pixelPos = geoCoding.getPixelPos(geoPos, null);
        Rectangle rectangle = altTile.getRectangle();
        final int x = MathUtils.roundAndCrop(pixelPos.x, rectangle.x, rectangle.x + rectangle.width - 1);
        final int y = MathUtils.roundAndCrop(pixelPos.y, rectangle.y, rectangle.y + rectangle.height - 1);
        return altTile.getSampleFloat(x, y);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixCloudShadowOp.class, "Idepix.CloudShadow");
        }
    }
}
