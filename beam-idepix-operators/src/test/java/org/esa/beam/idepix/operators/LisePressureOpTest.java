package org.esa.beam.idepix.operators;

import junit.framework.TestCase;

public class LisePressureOpTest extends TestCase {

    public void testGetNearestGaussIndex() throws Exception {
        LisePressureOp lisePressureOp = new LisePressureOp();

        double thetaS = -3.0;
        int index = lisePressureOp.getNearestGaussIndex(thetaS);
        assertEquals(0, index);

        thetaS = 999.0;
        index = lisePressureOp.getNearestGaussIndex(thetaS);
        assertEquals(22, index);

        thetaS = 23.4;
        index = lisePressureOp.getNearestGaussIndex(thetaS);
        assertEquals(6, index);

        thetaS = 88.0;
        index = lisePressureOp.getNearestGaussIndex(thetaS);
        assertEquals(22, index);
    }

    public void testGetNearestFilterIndex() throws Exception {
        LisePressureOp lisePressureOp = new LisePressureOp();

        double wvl = 759.0;
        int index = lisePressureOp.getNearestFilterIndex(wvl);
        assertEquals(0, index);

        wvl = 761.3;
        index = lisePressureOp.getNearestFilterIndex(wvl);
        assertEquals(5, index);

        wvl = 762.59;
        index = lisePressureOp.getNearestFilterIndex(wvl);
        assertEquals(18, index);

        wvl = 770.0;
        index = lisePressureOp.getNearestFilterIndex(wvl);
        assertEquals(19, index);
    }
}
