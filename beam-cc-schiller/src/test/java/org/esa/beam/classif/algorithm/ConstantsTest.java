package org.esa.beam.classif.algorithm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConstantsTest {

    @Test
    public void testConstants() {
         assertEquals(15, Constants.NUM_RADIANCE_BANDS);

         assertEquals(0x01, Constants.CLEAR_MASK);
         assertEquals(0x02, Constants.SPAMX_MASK);
         assertEquals(0x02, Constants.SPAMX_OR_NONCL_MASK);
         assertEquals(0x04, Constants.NONCL_MASK);
         assertEquals(0x08, Constants.CLOUD_MASK);
         assertEquals(0x10, Constants.UNPROCESSD_MASK);
    }
}
