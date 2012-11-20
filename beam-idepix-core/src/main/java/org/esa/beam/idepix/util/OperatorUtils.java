package org.esa.beam.idepix.util;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class OperatorUtils {
   private static final int[] ALL_BANDS = new int[]{};

    private OperatorUtils() {
    }

    public static Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        return createCompatibleProduct(sourceProduct, name, type, false);
    }
    /**
     * Creates a new product with the same size.
     * Copies geocoding and the start and stop time.
     */
    public static Product createCompatibleProduct(Product sourceProduct, String name, String type, boolean includeTiepoints) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        if (includeTiepoints) {
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        }
        copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies geocoding and the start and stop time.
     */
    public static void copyProductBase(Product sourceProduct, Product targetProduct) {
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    public static Band[] addBandGroup(Product srcProduct, int numSrcBands, int[] bandsToSkip, Product targetProduct, String targetPrefix, double noDataValue, boolean compactTargetArray) {
        int numTargetBands = numSrcBands;
        if (compactTargetArray && bandsToSkip.length > 0) {
            numTargetBands -= bandsToSkip.length;
        }
        Band[] targetBands = new Band[numTargetBands];
        int targetIndex = 0;
        for (int srcIndex = 0; srcIndex < numSrcBands; srcIndex++) {
                Band srcBand = srcProduct.getBandAt(srcIndex);
                Band targetBand = targetProduct.addBand(targetPrefix + "_" + (srcIndex + 1), ProductData.TYPE_FLOAT32);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                targetBand.setNoDataValueUsed(true);
                targetBand.setNoDataValue(noDataValue);
                targetBands[targetIndex] = targetBand;
                targetIndex++;
        }
        return targetBands;
    }

    public static Tile[] getSourceTiles(Operator op, Product srcProduct, String bandPrefix, int numBands, Rectangle rect, ProgressMonitor pm) throws OperatorException {
        Tile[] tiles = new Tile[numBands];
        for (int i = 0; i < tiles.length; i++) {
            String bandName = bandPrefix + "_" + (i + 1);
            Band srcBand = srcProduct.getBand(bandName);
            tiles[i] = op.getSourceTile(srcBand, rect, pm);
        }
        return tiles;
    }

    public static Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands) {
        return getTargetTiles(targetTiles, bands, ALL_BANDS);
    }

    public static Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands, int[] bandsToSkip) {
        Tile[] tiles = new Tile[bands.length];
        for (int i = 0; i < bands.length; i++) {
            tiles[i] = targetTiles.get(bands[i]);
        }
        return tiles;
    }

    public static ProgressMonitor subPm1(ProgressMonitor pm) {
        return SubProgressMonitor.create(pm, 1);
    }
}
