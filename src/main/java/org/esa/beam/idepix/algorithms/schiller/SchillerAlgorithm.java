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

package org.esa.beam.idepix.algorithms.schiller;

import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.idepix.util.NeuralNetWrapper;

import java.io.InputStream;

public class SchillerAlgorithm {

    public enum Net {
        LAND("schiller_7x3_1047.0_land.nna"),
        WATER("schiller_7x3_526.2_water.nna"),
        ALL("schiller_8x3_1706.7_lawat.nna");

        private final String netName;

        Net(String netName) {
            this.netName = netName;
        }
    }

    private final ThreadLocal<NeuralNetWrapper> schillerNet;

    public SchillerAlgorithm(Net net) {
        InputStream inputStream = this.getClass().getResourceAsStream(net.netName);
        schillerNet = NeuralNetWrapper.create(inputStream, 15, 1);
    }

    public float compute(Accessor accessor) {
        NeuralNetWrapper wrapper = schillerNet.get();
        double[] nnIn = wrapper.getInputVector();
        for (int i = 0; i < nnIn.length; i++) {
            nnIn[i] = Math.log(accessor.get(i));
        }
        double[] nnOut = wrapper.getOutputVector();
        wrapper.getNeuralNet().process(nnIn, nnOut);
        return (float)nnOut[0];
    }

    public interface Accessor {
        double get(int index);
    }

    public static class SourceSampleAccessor implements Accessor {

        private final int offset;
        private final Sample[] sourceSamples;

        public SourceSampleAccessor(Sample[] sourceSamples) {
            this(sourceSamples, 0);
        }

        public SourceSampleAccessor(Sample[] sourceSamples, int offset) {
            this.sourceSamples = sourceSamples;
            this.offset = offset;
        }


        @Override
        public double get(int index) {
            return sourceSamples[offset + index].getDouble();
        }
    }

}
