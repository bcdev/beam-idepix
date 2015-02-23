package org.esa.beam.idepix.algorithms.cawa;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;

import java.awt.*;

/**
 * MERIS water/land merge operator for CAWA.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "idepix.cawa.merge.landwater",
        version = "2.2.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2015 by Brockmann Consult",
        description = "MERIS water/land merge operator for CAWA.")
public class CawaMergeLandWaterOp extends MerisBasisOp {

    private Band waterClassifBand;
    private Band landClassifBand;

    @Override
    public void initialize() throws OperatorException {
        // todo
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();

        final Tile waterClassifTile = getSourceTile(waterClassifBand, rectangle);
        final Tile landClassifTile = getSourceTile(landClassifBand, rectangle);

        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            checkForCancellation();
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                boolean isLand = landClassifTile.getSampleBit(x, y, CoastColourClassificationOp.F_LAND);
                boolean isWater = waterClassifTile.getSampleBit(x, y, CoastColourClassificationOp.F_LAND);

                if (isLand && !isWater) {
                    targetTile.setSample(x, y, landClassifTile.getSampleInt(x, y));
                } else if (!isLand && isWater) {
                    targetTile.setSample(x, y, waterClassifTile.getSampleInt(x, y));
                } else {
                    targetTile.setSample(x, y, Float.NaN);
                }
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CawaMergeLandWaterOp.class);
        }
    }

}
