package org.esa.beam.classif;


import org.esa.beam.nn.NNffbpAlphaTabFast;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class NnThreadLocalTest {

    @Test
    public void testGetReturnsSameObjectForSameThread() {
        final NnThreadLocal nnThreadLocal = new NnThreadLocal("ver2013_03_01/NN4all/clind/varin1/11x8x5x3_2440.4.net");

        final NNffbpAlphaTabFast nn_1 = nnThreadLocal.get();
        assertNotNull(nn_1);

        final NNffbpAlphaTabFast nn_2 = nnThreadLocal.get();
        assertNotNull(nn_2);

        assertSame(nn_1, nn_2);
    }

    @Test
    public void testCreateUsesCorrectNN() {
        NnThreadLocal nnThreadLocal = new NnThreadLocal("ver2013_03_01/NN4all/clind/varin1/11x8x5x3_2440.4.net");
        NNffbpAlphaTabFast nn = nnThreadLocal.get();

        assertEquals(15, nn.getInmax().length);
        assertEquals(1.331025, nn.getInmax()[0], 1e-8);
        assertEquals(2.002528, nn.getInmax()[14], 1e-8);

        nnThreadLocal = new NnThreadLocal("ver2013_03_01/NN4all/clind/varin2/11x8x5x3_2247.9.net");
        nn = nnThreadLocal.get();

        assertEquals(20, nn.getInmax().length);
        assertEquals(1.331025, nn.getInmax()[0], 1e-8);
        assertEquals(1.000000, nn.getInmax()[19], 1e-8);
    }
}
