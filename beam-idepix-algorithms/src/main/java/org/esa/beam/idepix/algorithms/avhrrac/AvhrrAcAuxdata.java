package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.framework.gpf.OperatorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 01.12.2014
 * Time: 18:00
 *
 * @author olafd
 */
public class AvhrrAcAuxdata {

    public static final int VZA_TABLE_LENGTH = 2048;
    public static final String VZA_FILE_NAME = "view_zenith.txt";

    private static AvhrrAcAuxdata instance;

    public static AvhrrAcAuxdata getInstance() {
        if (instance == null) {
            instance = new AvhrrAcAuxdata();
        }

        return instance;
    }


    public Line2ViewZenithTable createLine2ViewZenithTable() throws IOException {
        final InputStream inputStream = getClass().getResourceAsStream(VZA_FILE_NAME);
        Line2ViewZenithTable vzaTable = new Line2ViewZenithTable();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < VZA_TABLE_LENGTH) {
                line = line.trim();
                st = new StringTokenizer(line, "\t", false);

                if (st.hasMoreTokens()) {
                    // x (whatever that is)
                    vzaTable.setxIndex(i, Integer.parseInt(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // y
                    vzaTable.setVza(i, Double.parseDouble(st.nextToken()));
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load Cahalan Table: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load Cahalan Table: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
        return vzaTable;
    }

    /**
     * Class providing a temperature-radiance conversion data table
     */
    public class Line2ViewZenithTable {
        private int[] xIndex = new int[VZA_TABLE_LENGTH];
        private double[] vza = new double[VZA_TABLE_LENGTH];

        public int getxIndex(int index) {
            return xIndex[index];
        }

        public void setxIndex(int index, int xIndex) {
            this.xIndex[index] = xIndex;
        }

        public int[] getxIndex() {
            return xIndex;
        }

        public void setxIndex(int[] xIndex) {
            this.xIndex = xIndex;
        }

        public double getVza(int index) {
            return vza[index];
        }

        public void setVza(int index, double vza) {
            this.vza[index] = vza;
        }

        public double[] getVza() {
            return vza;
        }

        public void setVza(double[] vza) {
            this.vza = vza;
        }
    }
}
