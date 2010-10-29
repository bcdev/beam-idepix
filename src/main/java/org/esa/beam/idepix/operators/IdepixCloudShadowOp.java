package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.cloud.CombinedCloudOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class IdepixCloudShadowOp extends MerisBasisOp {
   private static final int MEAN_EARTH_RADIUS = 6372000;

    private static final int MAX_ITER = 5;

    private static final double DIST_THRESHOLD = 1 / 740.0;

    private RectangleExtender rectCalculator;
    private GeoCoding geoCoding;
    private RasterDataNode altitudeRDN;

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="cloud")
    private Product cloudProduct;
    @SourceProduct(alias="ctp", optional=true)
    private Product ctpProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private int shadowWidth;
    @Parameter(description = "CTP constant value", defaultValue = "500.0")
    private float ctpConstantValue;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(cloudProduct, "MER_CLOUD_SHADOW", "MER_L2");
        Band cloudBand = ProductUtils.copyBand(CombinedCloudOp.FLAG_BAND_NAME, cloudProduct, targetProduct);
        FlagCoding sourceFlagCoding = cloudProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME).getFlagCoding();
        ProductUtils.copyFlagCoding(sourceFlagCoding, targetProduct);
        cloudBand.setFlagCoding(targetProduct.getFlagCoding(sourceFlagCoding.getName()));

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
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), shadowWidth, shadowWidth);
        geoCoding = l1bProduct.getGeoCoding();
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", sourceRectangle.height);
        try {
        	Tile szaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), sourceRectangle, pm);
        	Tile saaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile vzaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), sourceRectangle, pm);
        	Tile vaaTile = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile cloudTile = getSourceTile(cloudProduct.getBand(CombinedCloudOp.FLAG_BAND_NAME), sourceRectangle, pm);
        	Tile altTile = getSourceTile(altitudeRDN, sourceRectangle, pm);
            Tile ctpTile = null;
            if (ctpProduct != null) {
                ctpTile = getSourceTile(ctpProduct.getBand("cloud_top_press"), sourceRectangle, pm);
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
                    if ((cloudTile.getSampleInt(x, y) & CombinedCloudOp.FLAG_CLOUD) != 0 ) {
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
                        if (ctp >0) {
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
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private float computeHeightFromPressure(float pressure) {
        return (float) (-8000 * Math.log(pressure / 1013f));
    }

    private GeoPos getCloudShadow2(Tile altTile, float sza, float saa, float vza,
                                   float vaa, float cloudAlt, GeoPos appCloud) {

        double surfaceAlt = getAltitude(altTile, appCloud);

        // deltaX and deltaY are the corrections to apply to get the
        // real cloud position from the apparent one
        // deltaX/deltyY are in meters
        final double deltaX = -(cloudAlt - surfaceAlt) * Math.tan(vza)
                * Math.sin(vaa);
        final double deltaY = -(cloudAlt - surfaceAlt) * Math.tan(vza)
                * Math.cos(vaa);

        // distLat and distLon are in degrees
        double distLat = -(deltaY / MEAN_EARTH_RADIUS) * MathUtils.RTOD;
        double distLon = -(deltaX / (MEAN_EARTH_RADIUS * Math.cos(appCloud
                .getLat()
                * MathUtils.DTOR)))
                * MathUtils.RTOD;

        double latCloud = appCloud.getLat() + distLat;
        double lonCloud = appCloud.getLon() + distLon;

        // once the cloud position is know, we iterate to get the shadow
        // position
        int iter = 0;
        double dist = 2 * DIST_THRESHOLD;
        surfaceAlt = 0;
        double lat0, lon0;
        double lat = latCloud;
        double lon = lonCloud;
        GeoPos pos = new GeoPos();

        while ((iter < MAX_ITER) && (dist > DIST_THRESHOLD)
                && (surfaceAlt < cloudAlt)) {
            lat0 = lat;
            lon0 = lon;
            pos.setLocation((float) lat, (float) lon);
            PixelPos pixelPos = geoCoding.getPixelPos(pos, null);
            if (!(pixelPos.isValid()&& altTile.getRectangle().contains(pixelPos))) {
                return null;
            }
            surfaceAlt = getAltitude(altTile, pos);

            double deltaProjX = (cloudAlt - surfaceAlt) * Math.tan(sza)
                    * Math.sin(saa);
            double deltaProjY = (cloudAlt - surfaceAlt) * Math.tan(sza)
                    * Math.cos(saa);

            // distLat and distLon are in degrees
            distLat = -(deltaProjY / MEAN_EARTH_RADIUS) * MathUtils.RTOD;
            lat = latCloud + distLat;
            distLon = -(deltaProjX / (MEAN_EARTH_RADIUS * Math.cos(lat
                    * MathUtils.DTOR)))
                    * MathUtils.RTOD;
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
        final int y = MathUtils.roundAndCrop(pixelPos.y, rectangle.y, rectangle.y + rectangle.height -1);
        return altTile.getSampleFloat(x, y);
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
             super(IdepixCloudShadowOp.class, "Idepix.CloudShadow");
        }
    }
}
