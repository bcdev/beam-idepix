package org.esa.beam.classif;


import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class NnThreadLocalTest {

    @Test
    public void testGetReturnsSameObjectForSameThread() {
        final NnThreadLocal nnThreadLocal = new NnThreadLocal("NN4all/clind/varin1/11x8x5x3_2440.4.net");

        final NNffbpAlphaTabFast nn_1 = nnThreadLocal.get();
        assertNotNull(nn_1);

        final NNffbpAlphaTabFast nn_2 = nnThreadLocal.get();
        assertNotNull(nn_2);

        assertSame(nn_1, nn_2);
    }
}
