package org.esa.beam.idepix.algorithms.cawa;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.ProductUtils;

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

    @SourceProduct(alias = "landClassif")
    private Product landClassifProduct;

    @SourceProduct(alias = "waterClassif")
    private Product waterClassifProduct;

    @SourceProduct(optional = true)
    private Product eraInterimProduct;

    @Parameter(defaultValue = "true",
            label = " Write NN value to the target product.",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    private Band waterClassifBand;
    private Band landClassifBand;
    private Band landNNBand;
    private Band waterNNBand;

    private Band mergedClassifBand;
    private Band mergedNNBand;

    private Band wsBand;

    private boolean hasNNOutput;

    @Override
    public void initialize() throws OperatorException {
        Product mergedClassifProduct = createCompatibleProduct(landClassifProduct,
                                                               "mergedClassif", "mergedClassif");

        landClassifBand = landClassifProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        waterClassifBand = waterClassifProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS);

        mergedClassifBand = mergedClassifProduct.addBand(IdepixUtils.IDEPIX_CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = CawaUtils.createCawaFlagCoding(IdepixUtils.IDEPIX_CLOUD_FLAGS);
        mergedClassifBand.setSampleCoding(flagCoding);
        mergedClassifProduct.getFlagCodingGroup().add(flagCoding);

        if (eraInterimProduct != null) {
            ProductUtils.copyBand(CawaConstants.ERA_INTERIM_T2M_BAND_NAME, eraInterimProduct, mergedClassifProduct, true);
            ProductUtils.copyBand(CawaConstants.ERA_INTERIM_MSLP_BAND_NAME, eraInterimProduct, mergedClassifProduct, true);
            ProductUtils.copyBand(CawaConstants.ERA_INTERIM_TCWV_BAND_NAME, eraInterimProduct, mergedClassifProduct, true);
            wsBand = mergedClassifProduct.addBand(CawaConstants.ERA_INTERIM_WINDSPEED_BAND_NAME, ProductData.TYPE_FLOAT32);
        }

        hasNNOutput = landClassifProduct.containsBand(CawaConstants.SCHILLER_NN_OUTPUT_BAND_NAME) &&
                waterClassifProduct.containsBand(CawaConstants.SCHILLER_NN_OUTPUT_BAND_NAME);
        if (hasNNOutput) {
            landNNBand = landClassifProduct.getBand(CawaConstants.SCHILLER_NN_OUTPUT_BAND_NAME);
            waterNNBand = waterClassifProduct.getBand(CawaConstants.SCHILLER_NN_OUTPUT_BAND_NAME);
            mergedNNBand = mergedClassifProduct.addBand(CawaConstants.SCHILLER_NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        }

        setTargetProduct(mergedClassifProduct);
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();

        final Tile waterClassifTile = getSourceTile(waterClassifBand, rectangle);
        final Tile landClassifTile = getSourceTile(landClassifBand, rectangle);

        Tile u10Tile = null;
        Tile v10Tile = null;
        if (eraInterimProduct != null) {
           u10Tile = getSourceTile(eraInterimProduct.getBand(CawaConstants.ERA_INTERIM_U10_BAND_NAME), rectangle);
           v10Tile = getSourceTile(eraInterimProduct.getBand(CawaConstants.ERA_INTERIM_V10_BAND_NAME), rectangle);
        }

        Tile waterNNTile = null;
        Tile landNNTile = null;
        if (hasNNOutput) {
            waterNNTile = getSourceTile(waterNNBand, rectangle);
            landNNTile = getSourceTile(landNNBand, rectangle);
        }

        if (targetBand == mergedClassifBand) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    boolean isLand = landClassifTile.getSampleBit(x, y, CawaConstants.F_LAND);
                    final int sample = isLand ? landClassifTile.getSampleInt(x, y) : waterClassifTile.getSampleInt(x, y);
                    targetTile.setSample(x, y, sample);
                }
            }
        } else if (targetBand == wsBand) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    float u10;
                    float v10;
                    if (u10Tile != null&& v10Tile != null) {
                        u10 = u10Tile.getSampleFloat(x, y);
                        v10 = v10Tile.getSampleFloat(x, y);
                    } else {
                        u10 = 10.0f;
                        v10 = 10.0f;
                    }
                    final float ws = (float) Math.sqrt(u10*u10 + v10*v10);
                    targetTile.setSample(x, y, ws);
                }
            }
        }else if (hasNNOutput && targetBand == mergedNNBand) {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    boolean isLand = landClassifTile.getSampleBit(x, y, CawaConstants.F_LAND);
                    final float sample = isLand ? landNNTile.getSampleFloat(x, y) : waterNNTile.getSampleFloat(x, y);
                    targetTile.setSample(x, y, sample);
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
