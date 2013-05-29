package org.esa.beam.classif;


import org.esa.beam.classif.algorithm.Constants;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class CloudClassifierTest {

    @Test
    public void testToFlag_all_var1() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_all_var1(0.0));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_all_var1(1.64));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var1(1.65));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var1(1.66));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var1(2.39));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var1(2.4));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var1(2.56));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var1(3.19));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_all_var1(3.2));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_all_var1(3.99));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_all_var1(-1.0));
    }

    @Test
    public void testToFlag_all_var2() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_all_var2(0.2));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_all_var2(1.69));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var2(1.7));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var2(1.79));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_all_var2(2.34));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var2(2.35));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var2(2.89));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_all_var2(3.29));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_all_var2(3.3));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_all_var2(3.78));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_all_var2(-0.8));
    }

    @Test
    public void testToFlag_ter_var1() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_ter_var1(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_ter_var1(1.74));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(1.75));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(1.83));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var1(2.44));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var1(2.45));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var1(2.63));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var1(3.39));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_ter_var1(3.4));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_ter_var1(3.87));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_ter_var1(-0.7));
    }

    @Test
    public void testToFlag_ter_var2() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_ter_var2(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_ter_var2(1.74));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(1.75));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(1.83));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_ter_var2(2.49));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var2(2.5));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var2(2.52));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_ter_var2(3.44));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_ter_var2(3.45));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_ter_var2(3.92));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_ter_var2(-0.6));
    }

    @Test
    public void testToFlag_wat_var1() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_var1(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_var1(1.64));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(1.65));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(1.83));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var1(2.39));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var1(2.4));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var1(2.72));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var1(3.44));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_var1(3.45));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_var1(3.91));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_var1(-0.5));
    }

    @Test
    public void testToFlag_wat_var2() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_var2(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_var2(1.64));

        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(1.65));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(1.83));
        assertEquals(Constants.SPAMX_MASK, CloudClassifier.toFlag_wat_var2(2.34));

        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var2(2.35));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var2(2.72));
        assertEquals(Constants.NONCL_MASK, CloudClassifier.toFlag_wat_var2(3.44));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_var2(3.45));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_var2(3.91));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_var2(-0.4));
    }

    @Test
    public void testToFlag_wat_simple_var1() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var1(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var1(1.54));

        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(1.55));
        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(1.83));
        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var1(2.49));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var1(2.5));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var1(3.44));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_simple_var1(-0.3));
    }

    @Test
    public void testToFlag_wat_simple_var2() {
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var2(0.4));
        assertEquals(Constants.CLEAR_MASK, CloudClassifier.toFlag_wat_simple_var2(1.44));

        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(1.45));
        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(1.83));
        assertEquals(Constants.SPAMX_OR_NONCL_MASK, CloudClassifier.toFlag_wat_simple_var2(2.49));

        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var2(2.5));
        assertEquals(Constants.CLOUD_MASK, CloudClassifier.toFlag_wat_simple_var2(3.44));

        assertEquals(Constants.UNPROCESSD_MASK, CloudClassifier.toFlag_wat_simple_var1(-0.2));
    }
}
