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

package org.esa.beam.idepix.algorithms.avhrrac;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;

public class AvhrrAcUtilsTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRadianceToBt() {
        final double c1 = 1.1910659E-5;
        final double c2 = 1.438833;
        double rIn = 20.0;

        double rad = 0.8412*rIn + 0.0008739*rIn*rIn + 7.21;
        System.out.println("rad = " + rad);

        double nu = 926.81;
        double T = c2*nu/(Math.log(1.0 + c1*nu*nu*nu/rad));
        System.out.println("nu = " + nu + "; T = " + T);

        nu = 927.36;
        T = c2*nu/(Math.log(1.0 + c1*nu*nu*nu/rad));
        System.out.println("nu = " + nu + "; T = " + T);

        nu = 927.83;
        T = c2*nu/(Math.log(1.0 + c1*nu*nu*nu/rad));
        System.out.println("nu = " + nu + "; T = " + T);
    }

    @Test
    public void testRadianceToBt_2() {
        double rIn = 100.0;
        String noaaId = "11";
        try {
            final AvhrrAcAuxdata.Rad2BTTable rad2BTTable = AvhrrAcAuxdata.getInstance().createRad2BTTable(noaaId);
            final double bt4 = AvhrrAcUtils.convertRadianceToBt(noaaId, rad2BTTable, rIn, 4, 50.0f);
            System.out.println("bt4 = " + bt4);
        }  catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testReadRadianceToBtTable() {
        String noaaId = "11";
        try {
            final AvhrrAcAuxdata.Rad2BTTable rad2BTTable = AvhrrAcAuxdata.getInstance().createRad2BTTable(noaaId);
            assertEquals(1.0, rad2BTTable.getA(3));
            assertEquals(0.946, rad2BTTable.getA(5));
            assertEquals(0.0008739, rad2BTTable.getB(4));
            assertEquals(2.92, rad2BTTable.getD(5));
            assertEquals(926.81, rad2BTTable.getNuLow(4));
            assertEquals(841.81, rad2BTTable.getNuMid(5));
            assertEquals(2671.4, rad2BTTable.getNuHighLand(3));
            assertEquals(927.83, rad2BTTable.getNuHighLand(4));
            assertEquals(927.75, rad2BTTable.getNuHighWater(4));
            assertEquals(842.14, rad2BTTable.getNuHighWater(5));
        } catch (IOException e) {
            fail(e.getMessage());
        }

        noaaId = "14";
        try {
            final AvhrrAcAuxdata.Rad2BTTable rad2BTTable = AvhrrAcAuxdata.getInstance().createRad2BTTable(noaaId);
            assertEquals(1.00359, rad2BTTable.getA(3));
            assertEquals(0.9619, rad2BTTable.getA(5));
            assertEquals(0.0003833, rad2BTTable.getB(4));
            assertEquals(2.0, rad2BTTable.getD(5));
            assertEquals(928.2603, rad2BTTable.getNuLow(4));
            assertEquals(834.8066, rad2BTTable.getNuMid(5));
            assertEquals(2645.899, rad2BTTable.getNuHighLand(3));
            assertEquals(929.3323, rad2BTTable.getNuHighLand(4));
            assertEquals(929.5878, rad2BTTable.getNuHighWater(4));
            assertEquals(835.374, rad2BTTable.getNuHighWater(5));
        } catch (IOException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testGetLToaFromRhoToc() throws Exception {
        // lPath, eg0, alb, trans are obtained from AVHRR-AC LUT with the following inputs
        // they are assumed to be constant
//        # model_type: 0
//        # aerosol_type: 0
//        # aerosol_depth: 0.1
//        # water_vapour: 500
//        # ozone_content: 0.33176
//        # co2_mixing_ratio: 380
//        # altitude: 0.5
//        # sun_zenith_angle: 60.0
//        # view_zenith_angle: 40.0
//        # relative_azimuth: 20.0

        double[] lPath = {0.001, 2.7E-4};
        double[] eg0 = {0.153, 0.099};
        double[] alb = {0.0445, 0.0213};
        double[] trans = {0.8836, 0.895};

        double[] rhoToc = {0.1, 0.2};
        double[] lToa = new double[2];

        lToa[0] = AvhrrAcUtils.getLToaFromRhoToc(lPath[0], eg0[0], alb[0], trans[0], rhoToc[0]);
        lToa[1] = AvhrrAcUtils.getLToaFromRhoToc(lPath[1], eg0[1], alb[1], trans[1], rhoToc[1]);
        System.out.println("lToa_1 = " + lToa[0]);
        System.out.println("lToa_2 = " + lToa[1]);

    }
}
