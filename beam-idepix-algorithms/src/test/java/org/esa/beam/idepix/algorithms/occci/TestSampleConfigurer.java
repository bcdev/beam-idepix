package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

import java.util.HashMap;

public class TestSampleConfigurer implements SampleConfigurer {
    private final HashMap<Integer, String> samples;

    TestSampleConfigurer() {
        samples = new HashMap<Integer, String>();
    }

    @Override
    public void defineSample(int index, String name) {
        samples.put(index, name);
    }

    @Override
    public void defineSample(int index, String name, Product product) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String get(int index) {
        return samples.get(index);
    }
}
