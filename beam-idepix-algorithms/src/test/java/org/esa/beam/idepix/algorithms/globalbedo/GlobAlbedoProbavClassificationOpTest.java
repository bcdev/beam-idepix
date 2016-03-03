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

package org.esa.beam.idepix.algorithms.globalbedo;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.ImageUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GlobAlbedoProbavClassificationOpTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetListOfPixelTimes() {
        // set up a 5x5 'TIME' band source image with 3 different time values
        final short[] timeImageData = {
                0, 0, 686, 686, 0,
                0, 686, 0, 695, 0,
                686, 695, 0, 0, 585,
                585, 585, 695, 695, 0,
                695, 0, 686, 686, 686};
        final RenderedImage image = createImage(ProductData.TYPE_UINT16, DataBuffer.TYPE_USHORT,
                                                timeImageData);
        final int[]  listOfPixelTimes = GlobAlbedoProbavClassificationOp.getListOfPixelTimes(image);
        assertNotNull(listOfPixelTimes);
        assertEquals(3, listOfPixelTimes.length);
        assertEquals(585, listOfPixelTimes[0]);
        assertEquals(686, listOfPixelTimes[1]);
        assertEquals(695, listOfPixelTimes[2]);
    }

    private RenderedImage createImage(int pdType, int dbType, Object data) {
        final ProductData floatData = ProductData.createInstance(pdType, data);
        final RenderedImage image = ImageUtils.createRenderedImage(5, 5, floatData);
        Assert.assertEquals(dbType, image.getSampleModel().getDataType());
        return image;
    }

    @Test
    public void testIsProbavUrbanProductValid() {
        Product sourceProduct = new Product("PROBAV_S1_TOA_X00Y01_20140621_333M_V003", "bla", 1, 1);

        Product urbanProduct = new Product("bla", "bla", 1, 1);
        assertFalse(GlobAlbedoOp.isProbavUrbanProductValid(sourceProduct, urbanProduct));

        urbanProduct = new Product("urban_mask_X00Y01.nc", "bla", 1, 1);
        assertTrue(GlobAlbedoOp.isProbavUrbanProductValid(sourceProduct, urbanProduct));

        urbanProduct = new Product("urban_mask_X12Y08.nc", "bla", 1, 1);
        assertFalse(GlobAlbedoOp.isProbavUrbanProductValid(sourceProduct, urbanProduct));
    }
}
