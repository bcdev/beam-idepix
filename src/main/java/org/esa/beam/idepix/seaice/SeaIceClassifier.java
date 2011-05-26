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

import org.esa.beam.util.io.CsvReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * API Class which provides access to static data maps for sea ice classification.
 *
 * @author Thomas Storm
 */
public class SeaIceClassifier {

    private ZipFile zip;

    /**
     * Creates a new instance of SeaIceClassifier and loads the classification file.
     *
     * @throws java.io.IOException If resource cannot be found and/or read from.
     */
    public SeaIceClassifier() throws IOException {
        final String path = getClass().getResource("classification.zip").getFile();
        zip = new ZipFile(path);
    }

    /**
     * Returns a new instance of SeaIceClassification for given latitude, longitude, and month.
     *
     * @param lat   The latitude value of the classification, in the range [0..89.9999].
     * @param lon   The longitude value of the classification, in the range [0..359.9999].
     * @param month The month value of the classification, in the range [1..12] (i.e., 1 is January).
     *
     * @return a new instance of SeaIceClassification.
     */
    public SeaIceClassification getClassification(double lat, double lon, int month) {
        validateParameters(lat, lon, month);
        final String[] entry = getEntry(lat, lon, month);
        final double mean = Double.parseDouble(entry[2]);
        final double min = Double.parseDouble(entry[3]);
        final double max = Double.parseDouble(entry[4]);
        final double stdDev = Double.parseDouble(entry[5]);
        return SeaIceClassification.create(mean, min, max, stdDev);
    }

    String[] getEntry(double lat, double lon, int month) {
        final List<String[]> classifications = tryAndReadClassifications(month);
        for (String[] entry : classifications) {
            final double latitude = Double.parseDouble(entry[0]);
            if ((int) latitude == (int) lat) {
                final double longitude = Double.parseDouble(entry[1]);
                if ((int) longitude == (int) lon) {
                    return entry;
                }
            }
        }
        throw new IllegalArgumentException(
                MessageFormat.format("No entry found for latitude ''{0}'', longitude ''{1}'' in month ''{2}''.", lat, lon, month));
    }

    void validateParameters(double lat, double lon, int month) {
        if (lat >= 90 || lat < 0) {
            throw new IllegalArgumentException("lat must be >= 0 and < 90, was '" + lat + "'.");
        }
        if (lon >= 360 || lon < 0) {
            throw new IllegalArgumentException("lon must be >= 0 and < 360, was '" + lon + "'.");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be >= 1 and <= 12, was '" + month + "'.");
        }
    }

    private List<String[]> tryAndReadClassifications(int month) {
        final List<String[]> classifications;
        try {
            classifications = readClassifications(month);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read classifications.", e);
        }
        return classifications;
    }

    private List<String[]> readClassifications(int month) throws IOException {
        InputStream inputStream = null;
        try {
            final String fileName = String.format("classification_%d.csv", month);
            inputStream = zip.getInputStream(zip.getEntry(fileName));
            final InputStreamReader reader = new InputStreamReader(inputStream);
            final CsvReader csvReader = new CsvReader(reader, new char[]{' '}, true, "#");
            return csvReader.readStringRecords();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

}
