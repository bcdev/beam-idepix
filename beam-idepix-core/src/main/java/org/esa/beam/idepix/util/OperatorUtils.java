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

import javax.media.jai.BorderExtender;
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
}
