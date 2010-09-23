/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.idepix.operators;

import com.bc.ceres.glevel.MultiLevelImage;
import com.sun.media.jai.util.SunTileCache;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.meris.cloud.CloudTopPressureOp;
import org.esa.beam.util.StopWatch;

import javax.media.jai.JAI;
import java.awt.Rectangle;
import java.awt.image.Raster;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Thomas Storm
 */
public class ComputeChainOpPerformanceTest {

    private static ComputeChainOp.Spi operatorSpi = new ComputeChainOp.Spi();
    private static Map<String, Object> params = new HashMap<String, Object>();

    public static void setUp() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(operatorSpi);
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new Rad2ReflOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new BarometricPressureOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new CloudTopPressureOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new LisePressureOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new MepixCloudClassificationOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new SurfacePressureFubOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new GaseousCorrectionOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new LandClassificationOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new RayleighCorrectionOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new GACloudScreeningOp.Spi());
        params.put("ipfOutputRad2Refl", true);
        params.put("ipfOutputGaseous", true);
        params.put("ipfOutputLandWater", true);
        params.put("ipfOutputRayleigh", true);
        params.put("algorithm", CloudScreeningSelector.GlobAlbedo);
    }

    public static void main(String[] args) throws IOException, URISyntaxException, IllegalAccessException,
                                                  InstantiationException {
        if (args.length < 1 || args.length > 5) {
            System.out.println(
                    "Usage: ComputeChainOpPerformanceTest <product> [width, height] [factor_to_increase_cache] [print_values]");
            System.exit(-1);
        }

        setUp();
        final Product idepixProduct = computeIdepixProduct(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : idepixProduct.getSceneRasterWidth();
        int height = args.length > 2 ? Integer.parseInt(args[2]) : width;
        float factor = args.length > 3 ? Float.parseFloat(args[3]) : 1.0f;
        increaseCache(factor);

        Raster data = testTimeToGetData(idepixProduct, new Rectangle(0, 0, width, height));
        if (args.length > 4 && Boolean.parseBoolean(args[4])) {
            print(data);
        }
    }

    private static void increaseCache(float increaseCacheFactor) {
        final SunTileCache cache = (SunTileCache) JAI.getDefaultInstance().getTileCache();
        cache.setMemoryCapacity((long) (cache.getMemoryCapacity() * increaseCacheFactor));
    }

    private static Raster testTimeToGetData(Product product, Rectangle bounds) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        System.out.println("stopWatch started for bounds:");
        System.out.println(bounds.toString());
        MultiLevelImage image = product.getBandAt(0).getSourceImage();
        Raster data = image.getData(bounds);
        stopWatch.stop();
        System.out.println("stopWatch = " + stopWatch.getTimeDiffString());
        return data;
    }

    private static Product computeIdepixProduct(String resource) throws IOException, URISyntaxException {
        Product product = ProductIO.readProduct(resource);
        return GPF.createProduct("idepix.ComputeChain", params, product);
    }

    private static void print(Raster data) {
        for (int x = 0; x < data.getWidth(); x++) {
            for (int y = 0; y < data.getHeight(); y++) {
                System.out.print(data.getSample(x, y, 0) + " ");
            }
            System.out.println("");
        }
    }

}
