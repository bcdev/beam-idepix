package org.esa.beam.idepix.algorithms.classif;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CloudClassifierTest {

    @Test
    public void testToFlag_all_var1() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_all_var1(0.0));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_all_var1(1.64));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var1(1.65));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var1(1.66));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var1(2.39));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var1(2.4));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var1(2.56));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var1(3.19));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_all_var1(3.2));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_all_var1(3.99));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_all_var1(-1.0));
    }

    @Test
    public void testToFlag_all_var2() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_all_var2(0.2));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_all_var2(1.69));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var2(1.7));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var2(1.79));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_all_var2(2.34));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var2(2.35));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var2(2.89));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_all_var2(3.29));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_all_var2(3.3));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_all_var2(3.78));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_all_var2(-0.8));
    }

    @Test
    public void testToFlag_ter_var1() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_ter_var1(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_ter_var1(1.74));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(1.75));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(1.83));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(2.44));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var1(2.45));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var1(2.63));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var1(3.39));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_ter_var1(3.4));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_ter_var1(3.87));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_ter_var1(-0.7));
    }

    @Test
    public void testToFlag_ter_var2() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_ter_var2(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_ter_var2(1.74));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(1.75));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(1.83));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(2.49));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var2(2.5));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var2(2.52));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_ter_var2(3.44));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_ter_var2(3.45));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_ter_var2(3.92));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_ter_var2(-0.6));
    }

    @Test
    public void testToFlag_wat_var1() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_var1(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_var1(1.64));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(1.65));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(1.83));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(2.39));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var1(2.4));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var1(2.72));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var1(3.44));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_var1(3.45));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_var1(3.91));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_var1(-0.5));
    }

    @Test
    public void testToFlag_wat_var2() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_var2(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_var2(1.64));

        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(1.65));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(1.83));
        assertEquals(CloudClassifier.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(2.34));

        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var2(2.35));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var2(2.72));
        assertEquals(CloudClassifier.NONCL_MASK, CloudClassifier.toFlag_wat_var2(3.44));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_var2(3.45));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_var2(3.91));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_var2(-0.4));
    }

    @Test
    public void testToFlag_wat_simple_var1() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var1(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var1(1.54));

        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(1.55));
        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(1.83));
        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(2.49));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var1(2.5));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var1(3.44));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_simple_var1(-0.3));
    }

    @Test
    public void testToFlag_wat_simple_var2() {
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var2(0.4));
        assertEquals(CloudClassifier.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var2(1.44));

        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(1.45));
        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(1.83));
        assertEquals(CloudClassifier.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(2.49));

        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var2(2.5));
        assertEquals(CloudClassifier.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var2(3.44));

        assertEquals(CloudClassifier.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_simple_var1(-0.2));
    }
}
