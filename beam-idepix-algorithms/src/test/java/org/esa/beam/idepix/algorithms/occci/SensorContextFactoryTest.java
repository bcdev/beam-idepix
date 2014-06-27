package org.esa.beam.idepix.algorithms.occci;

import org.junit.Test;

import static org.junit.Assert.*;

public class SensorContextFactoryTest {

    @Test
    public void testFromTypeString_MODIS() {
        assertTrue(SensorContextFactory.fromTypeString("MOD021KM") instanceof ModisSensorContext);
        assertTrue(SensorContextFactory.fromTypeString("MYD021KM") instanceof ModisSensorContext);
        assertTrue(SensorContextFactory.fromTypeString("MODIS Level 1B") instanceof ModisSensorContext);
    }

    @Test
    public void testFromTypeString_SEAWIFS() {
        assertTrue(SensorContextFactory.fromTypeString("Generic Level 1B") instanceof SeaWiFSSensorContext);
    }

    @Test
    public void testFromTypeString_invalidType() {
        try {
            SensorContextFactory.fromTypeString("Tonios private sensor");
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid Product Type: Tonios private sensor", expected.getMessage());
        }
    }
}
