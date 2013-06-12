package org.esa.beam.classif;


import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.nn.NNffbpAlphaTabFast;

import java.io.InputStream;

public class NnThreadLocal extends ThreadLocal<NNffbpAlphaTabFast> {

    private final String nnPath;

    public NnThreadLocal(String nnPath) {
        this.nnPath = nnPath;
    }

    @Override
    protected NNffbpAlphaTabFast initialValue() {
        try {
            final InputStream inputStream = this.getClass().getResourceAsStream(nnPath);
            final NNffbpAlphaTabFast nn = new NNffbpAlphaTabFast(inputStream);
            inputStream.close();
            return nn;
        } catch (Exception e) {
            throw new OperatorException("Unable to load neural net: " + nnPath);
        }
    }
}
