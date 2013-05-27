package org.esa.beam.classif;


import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.nn.NNffbpAlphaTabFast;

import java.io.IOException;
import java.io.InputStream;

class NnThreadLocal extends ThreadLocal<NNffbpAlphaTabFast> {

    private final String nnPath;

    NnThreadLocal(String nnPath) {
        this.nnPath = nnPath;
    }

    @Override
    protected NNffbpAlphaTabFast initialValue() {
        try {
            final InputStream inputStream = this.getClass().getResourceAsStream(nnPath);
            final NNffbpAlphaTabFast nn = new NNffbpAlphaTabFast(inputStream);
            inputStream.close();
            return nn;
        } catch (IOException e) {
            throw new OperatorException("Unable to load neural net: " + nnPath);
        }
    }
}
