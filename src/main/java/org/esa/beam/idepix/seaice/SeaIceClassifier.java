/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
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

import org.esa.beam.util.io.CsvReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * API class which provides access to static data maps for sea ice classification.
 *
 * @author Thomas Storm
 */
public class SeaIceClassifier {

    private final Map<Integer, Map<Integer, String[]>> latLonMap = new HashMap<Integer, Map<Integer, String[]>>();

    /**
     * Creates a new instance of SeaIceClassifier and loads the classification file.
     *
     * @param monthIndex The month the data shall be loaded for.
     *
     * @throws java.io.IOException If resource cannot be found or read from.
     */
    public SeaIceClassifier(int monthIndex) throws IOException {
        final String path = getClass().getResource("classification.zip").getFile();
        ZipFile zip = new ZipFile(path);
        loadClassifications(monthIndex, zip);
    }

    /**
     * Returns a new instance of SeaIceClassification for given latitude, longitude, and month.
     *
     * @param lat The latitude value of the classification, in the range [0..89.9999].
     * @param lon The longitude value of the classification, in the range [0..359.9999].
     *
     * @return A new instance of SeaIceClassification.
     */
    public SeaIceClassification getClassification(double lat, double lon) {
        validateParameters(lat, lon);
        final String[] entry = getEntry(lat, lon);
        final double mean = Double.parseDouble(entry[2]);
        final double min = Double.parseDouble(entry[3]);
        final double max = Double.parseDouble(entry[4]);
        final double stdDev = Double.parseDouble(entry[5]);
        return SeaIceClassification.create(mean, min, max, stdDev);
    }

    String[] getEntry(double lat, double lon) {
        final Map<Integer, String[]> lonMap = latLonMap.get((int) lat);
        return lonMap.get((int) lon);
    }

    void validateParameters(double lat, double lon) {
        if (lat >= 180 || lat < 0) {
            throw new IllegalArgumentException("lat must be >= 0 and < 180, was '" + lat + "'.");
        }
        if (lon >= 360 || lon < 0) {
            throw new IllegalArgumentException("lon must be >= 0 and < 360, was '" + lon + "'.");
        }
    }

    private void loadClassifications(int month, ZipFile zip) throws IOException {
        InputStream inputStream = null;
        try {
            final String fileName = String.format("classification_%d.csv", month);
            inputStream = zip.getInputStream(zip.getEntry(fileName));
            final InputStreamReader reader = new InputStreamReader(inputStream);
            final CsvReader csvReader = new CsvReader(reader, new char[]{' '}, true, "#");
            List<String[]> classifications = csvReader.readStringRecords();
            for (String[] classification : classifications) {
                final int latitude = Integer.parseInt(classification[0]);
                Map<Integer, String[]> lonMap = latLonMap.get(latitude);
                if (lonMap == null) {
                    lonMap = new HashMap<Integer, String[]>();
                    latLonMap.put(latitude, lonMap);
                }
                final int longitude = Integer.parseInt(classification[1]);
                lonMap.put(longitude, classification);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

}
