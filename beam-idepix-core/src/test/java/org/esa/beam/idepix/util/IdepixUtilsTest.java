package org.esa.beam.idepix.util;

import junit.framework.TestCase;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ProductData;

/**
 * Tests for class {@link org.esa.beam.idepix.util.IdepixUtils}.
 *
 * @author Olaf Danne
 * @version $Revision: 1.1 $ $Date: 2008-10-09 16:43:53 $
 */
public class IdepixUtilsTest extends TestCase {

    public void testCorrectSaturatedReflectances() {
        float[] reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, 12.3f};

        float[] reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertNotNull(reflCorr);
        assertEquals(4, reflCorr.length);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(12.3f, reflCorr[0]);

        reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(true, Float.isNaN(reflCorr[3]));
        assertEquals(true, Float.isNaN(reflCorr[2]));
        assertEquals(true, Float.isNaN(reflCorr[1]));
        assertEquals(true, Float.isNaN(reflCorr[0]));

        reflOrig = new float[]{9.2f, 10.3f, 11.4f, 12.5f};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.5f, reflCorr[3]);
        assertEquals(11.4f, reflCorr[2]);
        assertEquals(10.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, Float.NaN, 12.3f};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, Float.NaN, Float.NaN};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(9.2f, reflCorr[3]);
        assertEquals(9.2f, reflCorr[2]);
        assertEquals(9.2f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{9.2f, Float.NaN, 12.3f, Float.NaN};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(12.3f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);

        reflOrig = new float[]{Float.NaN, 9.2f, Float.NaN, 12.3f};
        reflCorr = IdepixUtils.correctSaturatedReflectances(reflOrig);
        assertEquals(12.3f, reflCorr[3]);
        assertEquals(12.3f, reflCorr[2]);
        assertEquals(9.2f, reflCorr[1]);
        assertEquals(9.2f, reflCorr[0]);
    }

    public void testAreAllReflectancesValid() {
        float[] reflOrig = new float[]{12.3f, 12.3f, 12.3f, 12.3f};
        assertTrue(IdepixUtils.areAllReflectancesValid(reflOrig));

        reflOrig = new float[]{Float.NaN, 12.3f, Float.NaN, 12.3f};
        assertFalse(IdepixUtils.areAllReflectancesValid(reflOrig));
    }

    public void testIsNoReflectanceValid() {
        float[] reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, 12.3f};
        assertFalse(IdepixUtils.isNoReflectanceValid(reflOrig));

        reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        assertTrue(IdepixUtils.isNoReflectanceValid(reflOrig));
    }

    public void testSpectralSlope() {
        float wvl1 = 450.0f;
        float wvl2 = 460.0f;
        float refl1 = 50.0f;
        float refl2 = 100.0f;
        assertEquals(5.0f, IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2));

        wvl1 = 450.0f;
        wvl2 = 460.0f;
        refl1 = 500.0f;
        refl2 = 100.0f;
        assertEquals(-40.0f, IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2));

        wvl1 = 450.0f;
        wvl2 = 450.0f;
        refl1 = 50.0f;
        refl2 = 100.0f;
        final float slope = IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2);
        assertTrue(Float.isInfinite(slope));
    }

    public void testSetNewBandProperties() {
        Band band1 = new Band("test", ProductData.TYPE_FLOAT32, 10, 10);
        IdepixUtils.setNewBandProperties(band1, "bla", "km", -999.0, false);
        assertEquals("bla", band1.getDescription());
        assertEquals("km", band1.getUnit());
        assertEquals(-999.0, band1.getNoDataValue());
        assertEquals(false, band1.isNoDataValueUsed());

        Band band2 = new Band("test2", ProductData.TYPE_INT32, 10, 10);
        IdepixUtils.setNewBandProperties(band2, "blubb", "ton", -1, true);
        assertEquals("blubb", band2.getDescription());
        assertEquals("ton", band2.getUnit());
        assertEquals(-1.0, band2.getNoDataValue());
        assertEquals(true, band2.isNoDataValueUsed());
    }

    public void testConvertGeophysicalToMathematicalAngle() {
        double geoAngle = IdepixUtils.convertGeophysicalToMathematicalAngle(31.0);
        assertEquals(59.0, geoAngle, 1.0);
        geoAngle = IdepixUtils.convertGeophysicalToMathematicalAngle(134.0);
        assertEquals(316.0, geoAngle, 1.0);
        geoAngle = IdepixUtils.convertGeophysicalToMathematicalAngle(213.0);
        assertEquals(237.0, geoAngle, 1.0);
        geoAngle = IdepixUtils.convertGeophysicalToMathematicalAngle(301.0);
        assertEquals(149.0, geoAngle, 1.0);
        geoAngle = IdepixUtils.convertGeophysicalToMathematicalAngle(3100.0);
        assertTrue(Double.isNaN(geoAngle));
    }

    public void testIsAvhrrTimelineProduct() {
        // todo
    }

}