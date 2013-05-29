package org.esa.beam.classif;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

import java.util.HashMap;

public class TestSampleConfigurer implements SampleConfigurer {

    private HashMap<Integer, String> sampleMap = new HashMap<Integer, String>();

    @Override
    public void defineSample(int index, String name) {
        sampleMap.put(index, name);
    }

    public HashMap<Integer, String> getSampleMap() {
        return sampleMap;
    }

    @Override
    public void defineSample(int index, String name, Product product) {
    }
}
