package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * Operator used to consolidate cloud flag for CoastColour
 *
 * @author Marco
 * @since Idepix 1.3.1
 */
@OperatorMetadata(alias = "Meris.CoastColourPostProcessCloud",
                  version = "1.1",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Refines the cloud classification of Meris.CoastColourCloudClassification operator.")
public class CoastColourPostProcessCloudOp extends MerisBasisOp {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "merisCloud")
    private Product merisCloudProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;


    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private Band origCloudFlagBand;
    private TiePointGrid szaTPG;
    private TiePointGrid vzaTPG;
    private TiePointGrid saaTPG;
    private TiePointGrid vaaTPG;
    private Band ctpBand;
    private RasterDataNode altitudeRDN;

    private RectangleExtender rectCalculator;


    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = createCompatibleProduct(merisCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");
        origCloudFlagBand = merisCloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS);
        szaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        vzaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
        saaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        vaaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME);
        ctpBand = ctpProduct.getBand("cloud_top_press");
        int shadowWidth;
        if (l1bProduct.getProductType().equals(EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME)) {
            altitudeRDN = l1bProduct.getBand("altitude");
            shadowWidth = 16;
        } else {
            altitudeRDN = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
            shadowWidth = 64;

        }

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               shadowWidth, shadowWidth);


        ProductUtils.copyBand(CloudClassificationOp.CLOUD_FLAGS, merisCloudProduct, postProcessedCloudProduct);
        setTargetProduct(postProcessedCloudProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        Tile sourceFlagTile = getSourceTile(origCloudFlagBand, extendedRectangle);
        Tile szaTile = getSourceTile(szaTPG, extendedRectangle);
        Tile vzaTile = getSourceTile(vzaTPG, extendedRectangle);
        Tile saaTile = getSourceTile(saaTPG, extendedRectangle);
        Tile vaaTile = getSourceTile(vaaTPG, extendedRectangle);
        Tile altitudeTile = getSourceTile(altitudeRDN, extendedRectangle);
        Tile ctpTile = getSourceTile(ctpBand, extendedRectangle);


        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {
                computeCloudShadow(x, y, szaTile, vzaTile, saaTile, vaaTile, altitudeTile, ctpTile,
                                   sourceFlagTile, targetTile);

                if (targetRectangle.contains(x, y)) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD);
                    if (is_cloud) {
                        computeCloudBuffer(x, y, sourceFlagTile, targetTile);
                    }
                    combineFlags(x, y, sourceFlagTile, targetTile);
                }
            }

        }

    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private void computeCloudBuffer(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        Rectangle rectangle = targetTile.getRectangle();
        int LEFT_BORDER = Math.max(x - cloudBufferWidth, rectangle.x);
        int RIGHT_BORDER = Math.min(x + cloudBufferWidth, rectangle.x + rectangle.width - 1);
        int TOP_BORDER = Math.max(y - cloudBufferWidth, rectangle.y);
        int BOTTOM_BORDER = Math.min(y + cloudBufferWidth, rectangle.y + rectangle.height - 1);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                boolean is_already_cloud = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_CLOUD);
                boolean is_land = sourceFlagTile.getSampleBit(i, j, CoastColourCloudClassificationOp.F_LAND);
                if (!is_already_cloud && !is_land && targetTile.getRectangle().contains(i, j)) {
                    targetTile.setSample(i, j, CoastColourCloudClassificationOp.F_CLOUD_BUFFER, true);
                }
            }
        }
    }

    private void computeCloudShadow(int x, int y, Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile,
                                    Tile altitudeTile, Tile ctpTile, Tile sourceFlagTile, Tile targetTile) {
        float ctp = ctpTile.getSampleFloat(x, y);
        if (ctp > 0) {
            boolean is_cloud = sourceFlagTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD);
            if (is_cloud) {
                GeoCoding geoCoding = l1bProduct.getGeoCoding();
                GeoPos cloudGeoPos = geoCoding.getGeoPos(new PixelPos(x + 0.5f, y + 0.5f), null);

                float cloudHeight = computeHeightFromPressure(ctp);
                GeoPos shadowGeoPos = getCloudShadowPosition(x, y, szaTile, vzaTile, saaTile, vaaTile, altitudeTile,
                                                             cloudHeight, geoCoding, cloudGeoPos);
                if (shadowGeoPos != null) {
                    PixelPos shadowPixelPos = geoCoding.getPixelPos(shadowGeoPos, null);
                    final int shadowPixelX = MathUtils.floorInt(shadowPixelPos.x);
                    final int shadowPixelY = MathUtils.floorInt(shadowPixelPos.y);
                    Rectangle rectangle = targetTile.getRectangle();
                    boolean isAlreadyCloud = isPixelAlreadyMarkedAsCloud(shadowPixelX, shadowPixelY, sourceFlagTile);
                    if (!isAlreadyCloud && rectangle.contains(shadowPixelX, shadowPixelY)) {
                        targetTile.setSample(shadowPixelX, shadowPixelY,
                                             CoastColourCloudClassificationOp.F_CLOUD_SHADOW, true);
                    }
                }
            }
        }
    }

    private boolean isPixelAlreadyMarkedAsCloud(int pixelX, int pixelY, Tile sourceFlagTile) {
        boolean is_already_cloud = false;
        if (sourceFlagTile.getRectangle().contains(pixelX, pixelY)) {
            is_already_cloud = sourceFlagTile.getSampleBit(pixelX, pixelY, CoastColourCloudClassificationOp.F_CLOUD);
        }
        return is_already_cloud;
    }

    private GeoPos getCloudShadowPosition(int x, int y, Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile,
                                          Tile altitudeTile, float cloudHeight, GeoCoding geoCoding, GeoPos geoPos) {
        float sza = szaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float vza = vzaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float saa = saaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        float vaa = vaaTile.getSampleFloat(x, y) * MathUtils.DTOR_F;
        return IdepixCloudShadowOp.getCloudShadow(altitudeTile, geoCoding, sza, saa, vza, vaa, cloudHeight, geoPos);
    }

    private float computeHeightFromPressure(float pressure) {
        return (float) (-8000 * Math.log(pressure / 1013.0f));
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CoastColourPostProcessCloudOp.class);
        }
    }
}
