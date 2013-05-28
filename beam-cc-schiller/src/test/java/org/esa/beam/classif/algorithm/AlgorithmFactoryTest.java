package org.esa.beam.classif.algorithm;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AlgorithmFactoryTest {

    @Test
    public void testPublicConstants() {
        assertEquals("Algo_2013-03-01", AlgorithmFactory.ALGORITHM_2013_03_01);
        assertEquals("Algo_2013-05-09", AlgorithmFactory.ALGORITHM_2013_05_09);
    }
}
