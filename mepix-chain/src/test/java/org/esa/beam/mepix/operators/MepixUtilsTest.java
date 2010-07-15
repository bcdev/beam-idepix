package org.esa.beam.mepix.operators;

import com.bc.jnn.Jnn;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import junit.framework.TestCase;
import org.esa.beam.mepix.util.MepixUtils;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.util.math.MathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Tests for class {@link org.esa.beam.mepix.util.MepixUtils}.
 *
 * @author Olaf Danne
 * @version $Revision: 1.1 $ $Date: 2008-10-09 16:43:53 $
 */
public class MepixUtilsTest extends TestCase {

    public void testCorrectSaturatedReflectances() {
        float[] reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, 12.3f};

        float[] reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertNotNull(reflCorr);
        assertEquals(4, reflCorr.length);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(12.3f, reflCorr[0]);

        reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(true, Float.isNaN(reflCorr[3]));
        assertEquals(true, Float.isNaN(reflCorr[2]));
        assertEquals(true, Float.isNaN(reflCorr[1]));
        assertEquals(true, Float.isNaN(reflCorr[0]));

        reflOrig = new float[]{9.2f, 10.3f, 11.4f, 12.5f};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.5f, reflCorr[3]);
        assertEquals(11.4f, reflCorr[2]);
        assertEquals(10.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, Float.NaN, 12.3f};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, Float.NaN, Float.NaN};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(9.2f, reflCorr[3]);
        assertEquals(9.2f, reflCorr[2]);
        assertEquals(9.2f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, 12.3f, Float.NaN};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{Float.NaN, 9.2f, Float.NaN, 12.3f};
        reflCorr = MepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(9.2f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);
    }

    public void testAreReflectancesValid() {
        float[] reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, 12.3f};
        assertTrue(MepixUtils.areReflectancesValid(reflOrig));

        reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        assertFalse(MepixUtils.areReflectancesValid(reflOrig));
    }

    public void testSpectralSlope() {
        float wvl1 = 450.0f;
        float wvl2 = 460.0f;
        float refl1 = 50.0f;
        float refl2 = 100.0f;
        assertEquals(5.0f, MepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2));

        wvl1 = 450.0f;
        wvl2 = 460.0f;
        refl1 = 500.0f;
        refl2 = 100.0f;
        assertEquals(-40.0f, MepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2));

        wvl1 = 450.0f;
        wvl2 = 450.0f;
        refl1 = 50.0f;
        refl2 = 100.0f;
        final float slope = MepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2);
        assertTrue(Float.isInfinite(slope));
    }

    public void testScaleVgtSlope() {
        float wvl1 = 450.0f;
        float wvl2 = 460.0f;
        float refl1 = 1.0f;
        float refl2 = 2.0f;
        double vgtSlope = MepixUtils.scaleVgtSlope(refl1, refl2, wvl1, wvl2);
        assertEquals(0.0, vgtSlope);

        wvl1 = 450.0f;
        wvl2 = 460.0f;
        refl1 = 1.0f;
        refl2 = 1.002f;
        vgtSlope = MepixUtils.scaleVgtSlope(refl1, refl2, wvl1, wvl2);
        assertEquals(0.6, vgtSlope, 1.E-5);
    }
}