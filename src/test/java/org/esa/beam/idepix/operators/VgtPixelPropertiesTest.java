package org.esa.beam.idepix.operators;

import junit.framework.TestCase;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class VgtPixelPropertiesTest extends TestCase {

    private VgtPixelProperties vgtPixelProperties;

    public void setUp() {
        vgtPixelProperties = new VgtPixelProperties();
        float[] refl = setSpectralFlatnessTestValues();
        vgtPixelProperties.setRefl(refl);
    }

    public void testBrightValue() {

        assertEquals(1.0f, vgtPixelProperties.brightValue(), 1.0E-3);
    }

    public void testSpectralFlatnessValue() {
        assertEquals(0.0f, vgtPixelProperties.spectralFlatnessValue(), 1.0E-3);
    }

    public void testWhiteValue() {
        // bright value > BRIGHT_FOR_WHITE_THRESH
        assertEquals(0.0f, vgtPixelProperties.whiteValue(), 1.0E-3);

        // bright value = 0
        float [] refl = new float[IdepixConstants.VGT_WAVELENGTHS.length];
        vgtPixelProperties.setRefl(refl);
        assertEquals(0.0f, vgtPixelProperties.whiteValue(), 1.0E-3);
    }

    public void testTemperature() {
        assertEquals(0.5f, vgtPixelProperties.temperatureValue());
    }

    public void testGlintRiskValue() {
        assertEquals(1.0f, vgtPixelProperties.glintRiskValue());
    }

    public void testNdsi() {
        assertEquals(0.333f, vgtPixelProperties.ndsiValue(), 1.E-3);
    }

    public void testNdvi() {
        assertEquals(0.2275f, vgtPixelProperties.ndviValue(), 1.E-3);
    }

    public void testPressure() {
        assertEquals(0.5f, vgtPixelProperties.pressureValue());
    }

    public void testAPrioriLandValue() {
        // land:
        vgtPixelProperties.setRefl(new float[IdepixConstants.VGT_WAVELENGTHS.length]);
        vgtPixelProperties.setSmLand(true);
        assertEquals(1.0f, vgtPixelProperties.aPrioriLandValue());
        // water:
        vgtPixelProperties.setSmLand(false);
        assertEquals(0.0f, vgtPixelProperties.aPrioriLandValue());
    }

    public void testAPrioriWaterValue() {
        // land:
        vgtPixelProperties.setRefl(new float[IdepixConstants.VGT_WAVELENGTHS.length]);
        vgtPixelProperties.setSmLand(true);
        assertEquals(0.0f, vgtPixelProperties.aPrioriWaterValue());
        // water:
        vgtPixelProperties.setSmLand(false);
        assertEquals(1.0f, vgtPixelProperties.aPrioriWaterValue());
    }

    public void testRadiometricLandValue() {
        assertEquals(0.5f, vgtPixelProperties.radiometricLandValue());
    }

    public void testRadiometricWaterValue() {
        assertEquals(0.5f, vgtPixelProperties.radiometricWaterValue());
    }

    private float[] setSpectralFlatnessTestValues() {
        float[] refl = new float[IdepixConstants.VGT_WAVELENGTHS.length];

        // flatness_0 := 1.0:
        refl[0] = 450.0f;
        refl[1] = 645.0f;
        // flatness_1 := 2.0:
        refl[2] = 1025.0f;

        refl[3] = 512.5f;
        return refl;
    }
}
