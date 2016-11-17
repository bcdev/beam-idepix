package org.esa.beam.idepix.algorithms.occci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;

//import org.esa.beam.meris.l2auxdata.Constants;

/**
 * MERIS pixel classification operator for OCCCI.
 * Only water pixels are classified, following CC algorithm there, and same as current CAWA water processor.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.occci.classification.meris.seaice.edge",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "MERIS water pixel classification operator for OCCCI. SeaIce CCN mode.")
public class OccciMerisSeaiceEdgeOp extends Operator {

    @SourceProduct(alias = "l3")
    private Product l3Product;

    @TargetProduct
    private Product targetProduct;

    @Parameter(label = " Sea ice buffer width for ice edge determination ",
            defaultValue = "1")
    private int seaiceBufferWidth;

    @Parameter(label = " Minimum number of sea ice neighbours to classify water pixel as 'Mixed ice zone (MIZ)' ",
            defaultValue = "2")
    private int numSeaIceNeighboursThresh;

    private Band seaiceSourceBand;
    private Band latBand;
    private Band lonBand;
    private Band seaiceEdgeBand;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {
        rectCalculator = new RectangleExtender(new Rectangle(l3Product.getSceneRasterWidth(),
                                                             l3Product.getSceneRasterHeight()),
                                               seaiceBufferWidth, seaiceBufferWidth);
        createTargetProduct();
    }


    private void createTargetProduct() {
        targetProduct = new Product(l3Product.getName() + "_with_marginal_ice_zone", l3Product.getProductType(),
                                    l3Product.getSceneRasterWidth(), l3Product.getSceneRasterHeight());

        ProductUtils.copyMasks(l3Product, targetProduct);
        ProductUtils.copyMetadata(l3Product, targetProduct);
        ProductUtils.copyGeoCoding(l3Product, targetProduct);
        ProductUtils.copyTiePointGrids(l3Product, targetProduct);
        ProductUtils.copyFlagBands(l3Product, targetProduct, true);
        ProductUtils.copyFlagCodings(l3Product, targetProduct);
        ProductUtils.copyIndexCodings(l3Product, targetProduct);
        for (Band b : l3Product.getBands()) {
            if (!targetProduct.containsBand(b.getName())) {
                ProductUtils.copyBand(b.getName(), l3Product, targetProduct, true);
            }
            if (b.getName().startsWith("floating_ice_extent_KV")) {
                seaiceSourceBand = b;
            }
        }
        latBand = l3Product.getBand("lat");
        lonBand = l3Product.getBand("lon");
        seaiceEdgeBand = targetProduct.addBand("marginal_ice_zone", ProductData.TYPE_UINT8);
        seaiceEdgeBand.setNoDataValue(Float.NaN);
        seaiceEdgeBand.setNoDataValueUsed(true);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);

        final Tile seaiceSourceTile = getSourceTile(seaiceSourceBand, extendedRectangle);
        Tile latTile = null;
        if (latBand != null) {
            latTile = getSourceTile(latBand, extendedRectangle);
        }
        Tile lonTile = null;
        if (lonBand != null) {
            lonTile = getSourceTile(lonBand, extendedRectangle);
        }

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    if (seaiceSourceTile.getSampleInt(x, y) != 1) {
                        checkForSeaiceEdge(x, y, seaiceSourceTile, latTile, lonTile, targetTile, targetRectangle);
                    }
                }
            }
        }

    }

    private void checkForSeaiceEdge(int x, int y, Tile seaiceSourceTile, Tile latTile, Tile lonTile, Tile targetTile, Rectangle rectangle) {
        final int LEFT_BORDER = Math.max(x - seaiceBufferWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + seaiceBufferWidth, rectangle.x + rectangle.width - seaiceBufferWidth);
        final int TOP_BORDER = Math.max(y - seaiceBufferWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + seaiceBufferWidth, rectangle.y + rectangle.height - seaiceBufferWidth);

        int surroundingPixelCount = 0;
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (seaiceSourceTile.getRectangle().contains(i, j)) {
                    boolean isSeaice = seaiceSourceTile.getSampleInt(i, j) == 1;
                    if (isSeaice && rectangle.contains(i, j)) {
                        surroundingPixelCount++;
                    }
                }
            }
        }

        float lat = 0.0f;
        float lon = 0.0f;
        if (latTile != null && lonTile != null) {
            lat = latTile.getSampleFloat(x, y);
            lon = lonTile.getSampleFloat(x, y);
        } else {
            final GeoCoding geoCoding = l3Product.getGeoCoding();
            if (geoCoding != null && geoCoding.canGetGeoPos()) {
                final GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x, y), null);
                lat = geoPos.getLat();
                lon = geoPos.getLon();
            }
        }
        if (surroundingPixelCount >= numSeaIceNeighboursThresh && Math.abs(lat) < 86.0 && Math.abs(lon) < 179.9) {
            targetTile.setSample(x, y, 1);
        } else {
            targetTile.setSample(x, y, 0);
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(OccciMerisSeaiceEdgeOp.class);
        }
    }

}
