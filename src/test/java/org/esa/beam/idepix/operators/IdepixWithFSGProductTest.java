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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.OperatorSpiRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Set;

import static junit.framework.Assert.*;

public class IdepixWithFSGProductTest {


    private static Product sourceProduct;

    @BeforeClass
    public static void beforeClass() throws Exception {
        sourceProduct = createFSGProduct();
    }

    @Before
    public void setUp() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    @After
    public void tearDown() throws Exception {
        final OperatorSpiRegistry operatorSpiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        final Set<OperatorSpi> services = operatorSpiRegistry.getServiceRegistry().getServices();
        for (OperatorSpi service : services) {
            operatorSpiRegistry.removeOperatorSpi(service);
        }
    }

    @Test
    public void testCreatingTargetProduct_QWG_Algo() {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("algorithm", "QWG");
        final Product product = GPF.createProduct("idepix.ComputeChain", parameters, sourceProduct);
        assertNotNull(product);
        assertNotNull(product.getGeoCoding());
        assertTrue(product.getGeoCoding() instanceof PixelGeoCoding);
    }

    @Test
    public void testCreatingTargetProduct_GlobAlbedo_Algo() {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("algorithm", "GlobAlbedo");
        final Product product = GPF.createProduct("idepix.ComputeChain", parameters, sourceProduct);
        assertNotNull(product);
        assertNotNull(product.getGeoCoding());
        assertTrue(product.getGeoCoding() instanceof PixelGeoCoding);
    }

    @Test
    public void testCreatingTargetProduct_CoastColour_Algo() {
        final HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("algorithm", "CoastColour");
        parameters.put("ccOutputRayleigh", false);
        final Product product = GPF.createProduct("idepix.ComputeChain", parameters, sourceProduct);
        assertNotNull(product);
        assertNotNull(product.getGeoCoding());
        assertTrue(product.getGeoCoding() instanceof PixelGeoCoding);
    }

    private static Product createFSGProduct() throws Exception {
        final int width = 10;
        final int height = 10;
        final Product pcgProduct = new Product("PCG", EnvisatConstants.MERIS_FSG_L1B_PRODUCT_TYPE_NAME, width, height);
        pcgProduct.setStartTime(ProductData.UTC.parse("23-MAR-2006 13:45:12"));
        final short[] shortElemes = new short[width * height];
        for (String l1bBandName : EnvisatConstants.MERIS_L1B_BAND_NAMES) {
            final Band band = pcgProduct.addBand(l1bBandName, ProductData.TYPE_UINT16);
            band.setSpectralWavelength((float) IdepixConstants.SMA_ENDMEMBER_WAVELENGTHS[0]);
            band.setSpectralBandwidth((float) IdepixConstants.SMA_ENDMEMBER_BANDWIDTHS[0]);
            band.setDataElems(shortElemes);
        }
        FlagCoding l1_flags = new FlagCoding("l1_flags");
        l1_flags.addFlag("INVALID", 0x01, "No Description.");
        pcgProduct.getBand("l1_flags").setSampleCoding(l1_flags);
        pcgProduct.getFlagCodingGroup().add(l1_flags);

        final float[] tiePoints = new float[width * height];
        for (String l1bTpgName : EnvisatConstants.MERIS_TIE_POINT_GRID_NAMES) {
            final TiePointGrid tpg = new TiePointGrid(l1bTpgName, width, height, 0, 0, 1, 1, tiePoints);
            pcgProduct.addTiePointGrid(tpg);
        }
        final double[] doubleElemes = new double[width * height];
        final Band longitude = pcgProduct.addBand("corr_longitude", ProductData.TYPE_FLOAT64);
        longitude.setDataElems(doubleElemes);
        final Band latitude = pcgProduct.addBand("corr_latitude", ProductData.TYPE_FLOAT64);
        latitude.setDataElems(doubleElemes);
        final Band altitude = pcgProduct.addBand("altitude", ProductData.TYPE_INT16);
        altitude.setDataElems(shortElemes);
        final PixelGeoCoding pixelGeoCoding = new PixelGeoCoding(latitude, longitude, null, 6, ProgressMonitor.NULL);
        pcgProduct.setGeoCoding(pixelGeoCoding);
        return pcgProduct;
    }


}
