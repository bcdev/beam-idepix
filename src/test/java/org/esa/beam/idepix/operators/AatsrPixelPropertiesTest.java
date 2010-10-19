package org.esa.beam.idepix.operators;

import junit.framework.TestCase;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class AatsrPixelPropertiesTest extends TestCase {

    private AatsrPixelProperties aatsrPixelProperties;

    public void setUp() {
        aatsrPixelProperties = new AatsrPixelProperties();
    }


    public void testBrightValue() {
        float[] refl = setSpectralFlatnessTestValues();
        aatsrPixelProperties.setRefl(refl);
        assertEquals(7.06667f, aatsrPixelProperties.brightValue(), 1.0E-3);
    }

    public void testSpectralFlatnessValue() {
        float[] refl = setSpectralFlatnessTestValues();
        aatsrPixelProperties.setRefl(refl);
        assertEquals(0.015f, aatsrPixelProperties.spectralFlatnessValue(), 1.0E-3);
    }

    public void testWhiteValue() {
        float[] refl = setSpectralFlatnessTestValues();
        aatsrPixelProperties.setRefl(refl);

        // bright value > BRIGHT_FOR_WHITE_THRESH
        assertEquals(0.015f, aatsrPixelProperties.whiteValue(), 1.0E-3);

        // bright value = 0
        refl = new float[IdepixConstants.AATSR_WAVELENGTHS.length];
        aatsrPixelProperties.setRefl(refl);
        assertEquals(0.0f, aatsrPixelProperties.whiteValue(), 1.0E-3);
    }

    public void testTemperature() {
        aatsrPixelProperties.setBtemp1200(200.0f);
        assertEquals(0.9f, aatsrPixelProperties.temperatureValue());
        aatsrPixelProperties.setBtemp1200(240.0f);
        assertEquals(0.767f, aatsrPixelProperties.temperatureValue(), 1.E-3);
        aatsrPixelProperties.setBtemp1200(255.0f);
        assertEquals(0.633f, aatsrPixelProperties.temperatureValue(), 1.E-3);
        aatsrPixelProperties.setBtemp1200(270.0f);
        assertEquals(0.5f, aatsrPixelProperties.temperatureValue());
        aatsrPixelProperties.setBtemp1200(275.0f);
        assertEquals(0.3f, aatsrPixelProperties.temperatureValue());
        aatsrPixelProperties.setBtemp1200(285.0f);
        assertEquals(0.1f, aatsrPixelProperties.temperatureValue());
    }

    public void testNdsi() {
        float[] refl = setSpectralFlatnessTestValues();
        aatsrPixelProperties.setRefl(refl);
        assertEquals(0.333f, aatsrPixelProperties.ndsiValue(), 1.E-3);
    }

    public void testNdvi() {
        float[] refl = setSpectralFlatnessTestValues();
        aatsrPixelProperties.setRefl(refl);
        assertEquals(0.2275f, aatsrPixelProperties.ndviValue(), 1.E-3);
    }

    public void testPressure() {
        assertEquals(0.5f, aatsrPixelProperties.pressureValue());
    }

    public void testAPrioriLandValue() {
        // land:
        aatsrPixelProperties.setRefl(new float[IdepixConstants.AATSR_WAVELENGTHS.length]);
        aatsrPixelProperties.setL1FlagLand(true);
        assertEquals(1.0f, aatsrPixelProperties.aPrioriLandValue());
        // water:
        aatsrPixelProperties.setL1FlagLand(false);
        assertEquals(0.0f, aatsrPixelProperties.aPrioriLandValue());
    }

    public void testAPrioriWaterValue() {
        // water:
        aatsrPixelProperties.setRefl(new float[IdepixConstants.AATSR_WAVELENGTHS.length]);
        aatsrPixelProperties.setL1FlagLand(false);
        assertEquals(1.0f, aatsrPixelProperties.aPrioriWaterValue());
        // land:
        aatsrPixelProperties.setL1FlagLand(true);
        assertEquals(0.0f, aatsrPixelProperties.aPrioriWaterValue());
    }

    public void testRadiometricLandValue() {
        assertEquals(0.5f, aatsrPixelProperties.radiometricLandValue());
    }

    public void testRadiometricWaterValue() {
        assertEquals(0.5f, aatsrPixelProperties.radiometricWaterValue());
    }

    private float[] setSpectralFlatnessTestValues() {
        float[] refl = new float[IdepixConstants.AATSR_WAVELENGTHS.length];

        // flatness_0 := 1.0:
        refl[0] = 450.0f;
        refl[1] = 645.0f;
        // flatness_1 := 2.0:
        refl[2] = 1025.0f;

        refl[3] = 512.5f;
        return refl;
    }

}
