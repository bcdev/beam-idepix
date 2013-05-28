package org.esa.beam.classif.algorithm;


import org.junit.Test;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AlgorithmFactoryTest {

    @Test
    public void testPublicConstants() {
        assertEquals("Algo_2013-03-01", AlgorithmFactory.ALGORITHM_2013_03_01);
        assertEquals("Algo_2013-05-09", AlgorithmFactory.ALGORITHM_2013_05_09);
    }

    @Test
    public void testGet() {
        CCAlgorithm algorithm = AlgorithmFactory.get(AlgorithmFactory.ALGORITHM_2013_03_01);
        assertNotNull(algorithm);
        assertTrue(algorithm instanceof CC_2013_03_01);

        algorithm = AlgorithmFactory.get(AlgorithmFactory.ALGORITHM_2013_05_09);
        assertNotNull(algorithm);
        assertTrue(algorithm instanceof CC_2013_05_09);
    }

    @Test
    public void testGetThrowsOnIllegalAlgorithmName() {
        try {
            AlgorithmFactory.get("Wurstnasenmann");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid algorithm name: Wurstnasenmann", expected.getMessage());
        }
    }

    @Test
    public void testCallingGetTwiceReturnsSameObject() {
        final CCAlgorithm algorithm_1 = AlgorithmFactory.get(AlgorithmFactory.ALGORITHM_2013_03_01);
        assertNotNull(algorithm_1);

        final CCAlgorithm algorithm_2 = AlgorithmFactory.get(AlgorithmFactory.ALGORITHM_2013_03_01);
        assertNotNull(algorithm_2);

        assertSame(algorithm_1, algorithm_2);
    }
}
