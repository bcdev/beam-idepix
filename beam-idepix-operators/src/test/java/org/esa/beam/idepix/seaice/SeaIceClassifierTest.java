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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class SeaIceClassifierTest {


    @Test
    public void testGetClassificationMarch() throws Exception {
        SeaIceClassifier seaIceClassifier = new SeaIceClassifier(3);
        SeaIceClassification classification = seaIceClassifier.getClassification(45.5436, 53.4321);
        assertEquals(0, classification.mean, 0.0);
        assertEquals(0, classification.min, 0.0);
        assertEquals(0, classification.max, 0.0);
        assertEquals(0, classification.standardDeviation, 0.0);
    }

    @Test
    public void testGetClassificationJanuary() throws Exception {
        SeaIceClassifier seaIceClassifier = new SeaIceClassifier(1);
        SeaIceClassification classification = seaIceClassifier.getClassification(0.156, 16.856);
        assertEquals(99.6923, classification.mean, 0.0);
        assertEquals(99, classification.min, 0.0);
        assertEquals(100, classification.max, 0.0);
        assertEquals(0.6794, classification.standardDeviation, 0.0);
    }

    @Test
    public void testValidateParameters() throws Exception {
        SeaIceClassifier.validateParameters(0, 0);
        SeaIceClassifier.validateParameters(180, 360);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateParameters_FailNegativeLat() throws Exception {
        SeaIceClassifier.validateParameters(-1, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateParameters_FailHighLat() throws Exception {
        SeaIceClassifier.validateParameters(180.1, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateParameters_FailNegativeLon() throws Exception {
        SeaIceClassifier.validateParameters(10, -10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateParameters_FailHighLon() throws Exception {
        SeaIceClassifier.validateParameters(10, 360.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_FailHighMonth() throws Exception {
        new SeaIceClassifier(13);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_FailLowMonth() throws Exception {
        new SeaIceClassifier(0);
    }

}
