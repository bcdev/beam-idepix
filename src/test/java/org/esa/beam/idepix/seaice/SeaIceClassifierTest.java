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

package org.esa.beam.idepix.seaice;

/**
 * @author Thomas Storm
 */
public class SeaIceClassifierTest {

//    private static SeaIceClassifier seaIceClassifier;
//
//    @BeforeClass
//    public static void setUp() throws Exception {
//        seaIceClassifier = new SeaIceClassifier();
//    }
//
//    @Test
//    public void testGetEntry() throws Exception {
//
//        String[] entry = seaIceClassifier.getEntry(0.156, 16.856, 1);
//        assertArrayEquals(new String[]{"0", "16", "99.6923", "99.0000", "100.0000", "0.6794"}, entry);
//
//        entry = seaIceClassifier.getEntry(0, 10, 2);
//        assertArrayEquals(new String[]{"0", "10", "100.0000", "100.0000", "100.0000", "0.0000"}, entry);
//
//        entry = seaIceClassifier.getEntry(45.5436, 53.4321, 3);
//        assertArrayEquals(new String[]{"45", "53", "0.0000", "0.0000", "0.0000", "0.0000"}, entry);
//
//        entry = seaIceClassifier.getEntry(0.156, 1, 4);
//        assertArrayEquals(new String[]{"0", "1", "99.9231", "99.0000", "100.0000", "0.5162"}, entry);
//
//        entry = seaIceClassifier.getEntry(0.156, 10.986, 5);
//        assertArrayEquals(new String[]{"0", "10", "99.6923", "98.0000", "100.0000", "0.8495"}, entry);
//
//        entry = seaIceClassifier.getEntry(0.156, 30.123, 6);
//        assertArrayEquals(new String[]{"0", "30", "99.6923", "98.0000", "100.0000", "0.8495"}, entry);
//
//        entry = seaIceClassifier.getEntry(0.156, 40.12345, 7);
//        assertArrayEquals(new String[]{"0", "40", "97.3846", "94.0000", "100.0000", "1.6088"}, entry);
//    }
//
//    @Test
//    public void testGetClassification() throws Exception {
//        SeaIceClassification classification = seaIceClassifier.getClassification(45.5436, 53.4321, 3);
//        assertEquals(0, classification.mean, 0.0);
//        assertEquals(0, classification.min, 0.0);
//        assertEquals(0, classification.max, 0.0);
//        assertEquals(0, classification.standardDeviation, 0.0);
//
//        classification = seaIceClassifier.getClassification(0.156, 16.856, 1);
//        assertEquals(99.6923, classification.mean, 0.0);
//        assertEquals(99, classification.min, 0.0);
//        assertEquals(100, classification.max, 0.0);
//        assertEquals(0.6794, classification.standardDeviation, 0.0);
//    }
//
//    @Test
//    public void testValidateParameters() throws Exception {
//        seaIceClassifier.validateParameters(0, 179.99, 1);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailNegativeLat() throws Exception {
//        seaIceClassifier.validateParameters(-1, 10, 1);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailHighLat() throws Exception {
//        seaIceClassifier.validateParameters(90, 10, 1);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailNegativeLon() throws Exception {
//        seaIceClassifier.validateParameters(10, -10, 1);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailHighLon() throws Exception {
//        seaIceClassifier.validateParameters(10, 360, 1);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailZeroMonth() throws Exception {
//        seaIceClassifier.validateParameters(-1, 10, 0);
//    }
//
//    @Test(expected = IllegalArgumentException.class)
//    public void testValidateParameters_FailHighMonth() throws Exception {
//        seaIceClassifier.validateParameters(90, 10, 13);
//    }
}
