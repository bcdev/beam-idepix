package org.esa.beam.idepix.algorithms.globalbedo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGeoCoding;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.CloudBuffer;
import org.esa.beam.idepix.algorithms.CloudShadowFronts;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.Rectangle;
import java.util.HashMap;

//import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;

/**
 * Operator used to consolidate cloud flag for GlobAlbedo:
 * - coastline refinement
 *
 * @author olafd
 * @since Idepix 2.1
 */
@OperatorMetadata(alias = "idepix.globalbedo.postprocess",
                  version = "2.1",
                  internal = true,
                  authors = "Marco Peters",
                  copyright = "(c) 2011 by Brockmann Consult",
                  description = "Refines the cloud classification of Meris.GlobAlbedoCloudClassification operator.")
public class GlobAlbedoPostProcessOp extends MerisBasisOp {

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "merisCloud")
    private Product merisCloudProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;

    private Band waterFractionBand;
    private Band origCloudFlagBand;
    private Band ctpBand;
    private TiePointGrid szaTPG;
    private TiePointGrid saaTPG;
    private TiePointGrid altTPG;
    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = createCompatibleProduct(merisCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");

        HashMap<String, Object> waterParameters = new HashMap<String, Object>();
        waterParameters.put("resolution", 50);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, l1bProduct);
        waterFractionBand = waterMaskProduct.getBand("land_water_fraction");

        geoCoding = l1bProduct.getGeoCoding();

        origCloudFlagBand = merisCloudProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        szaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        saaTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        altTPG = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);
        ctpBand = ctpProduct.getBand("cloud_top_press");
        int extendedWidth;
        int extendedHeight;
        if (l1bProduct.getProductType().startsWith("MER_F")) {
            extendedWidth = 64;
            extendedHeight = 64;
        } else {
            extendedWidth = 16;
            extendedHeight = 16;
        }

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               extendedWidth, extendedHeight
        );


        ProductUtils.copyBand(CloudClassificationOp.CLOUD_FLAGS, merisCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, extendedRectangle);
        Tile szaTile = getSourceTile(szaTPG, extendedRectangle);
        Tile saaTile = getSourceTile(saaTPG, extendedRectangle);
        Tile ctpTile = getSourceTile(ctpBand, extendedRectangle);
        Tile altTile = getSourceTile(altTPG, targetRectangle);
        final Tile waterFractionTile = getSourceTile(waterFractionBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                    combineFlags(x, y, sourceFlagTile, targetTile);

                    if (isNearCoastline(x, y, waterFractionTile, extendedRectangle)) {
                        targetTile.setSample(x, y, IdepixConstants.F_COASTLINE, true);
                        refineSnowIceFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
                        if (isCloud) {
                            refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, waterFractionTile, targetTile, extendedRectangle);
                        }
                    }
                    boolean isCloudAfterRefinement = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                    if (isCloudAfterRefinement) {
                        // set the CLEAR_* flags to false to have consistent flagging
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, false);
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
                        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, false);

                        // switched to lc cloud buffer for land
//                        CloudBuffer.computeSimpleCloudBuffer(x, y, sourceFlagTile, targetTile, cloudBufferWidth,
//                                                             IdepixConstants.F_CLOUD,
//                                                             IdepixConstants.F_CLOUD_BUFFER);
                    }
                }
            }
        }

        CloudBuffer.computeCloudBufferLC(targetTile, IdepixConstants.F_CLOUD, IdepixConstants.F_CLOUD_BUFFER);

        CloudShadowFronts cloudShadowFronts = new CloudShadowFronts(
                IdepixConstants.F_CLOUD, IdepixConstants.F_CLOUD_SHADOW,
                geoCoding,
                targetTile,
                extendedRectangle,
                szaTile, saaTile, ctpTile, altTile) {

            @Override
            protected boolean isCloudForShadow(int x, int y) {
                final boolean is_cloud_current;
                if (!targetTile.getRectangle().contains(x, y)) {
                    is_cloud_current = sourceFlagTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                } else {
                    is_cloud_current = targetTile.getSampleBit(x, y, IdepixConstants.F_CLOUD);
                }
                if (is_cloud_current) {
                    final boolean isNearCoastline = isNearCoastline(x, y, waterFractionTile, extendedRectangle);
                    if (!isNearCoastline) {
                        return true;
                    }
                }
                return false;
            }
        };
        cloudShadowFronts.computeCloudShadow();
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private boolean isCoastlinePixel(int x, int y, Tile waterFractionTile) {
        boolean isCoastline = false;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            final int waterFraction = waterFractionTile.getSampleInt(x, y);
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                isCoastline = waterFraction < 100 && waterFraction > 0;
            }
        }
        return isCoastline;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private boolean isNearCoastline(int x, int y, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        final int waterFractionCenter = waterFractionTile.getSampleInt(x, y);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (rectangle.contains(i, j)) {
                    if (!(l1bProduct.getGeoCoding() instanceof TiePointGeoCoding) &&
                            !(l1bProduct.getGeoCoding() instanceof CrsGeoCoding)) {
                        if (waterFractionTile.getSampleInt(i, j) != waterFractionCenter) {
                            return true;
                        }
                    } else {
                        if (isCoastlinePixel(i, j, waterFractionTile)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Tile targetTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        boolean removeCloudFlag = true;
        if (CloudShadowFronts.isPixelSurrounded(x, y, sourceFlagTile, rectangle, IdepixConstants.F_CLOUD)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, IdepixConstants.F_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, waterFractionTile, rectangle)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, IdepixConstants.F_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
        }
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, IdepixConstants.F_CLEAR_SNOW);
        if (isSnowIce) {
            targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(GlobAlbedoPostProcessOp.class);
        }
    }
}
