package org.esa.beam.idepix.operators;

import junit.framework.TestCase;
import org.esa.beam.dataio.envisat.EnvisatConstants;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class MerisPixelPropertiesTest extends TestCase {

    private MerisPixelProperties merisPixelProperties;

    public void setUp() {
        merisPixelProperties = new MerisPixelProperties();
    }

    public void testBrightValue() {
        merisPixelProperties.setBrr442(-1.0f);
        merisPixelProperties.setBrr442Thresh(0.1f);
        assertEquals((float) IdepixConstants.NO_DATA_VALUE, merisPixelProperties.brightValue());

        merisPixelProperties.setBrr442(0.0f);
        assertEquals((float) IdepixConstants.NO_DATA_VALUE, merisPixelProperties.brightValue());

        merisPixelProperties.setBrr442(0.5f);
        assertEquals(5.0f, merisPixelProperties.brightValue());

        merisPixelProperties.setBrr442(1.1f);
        assertEquals(11.0f, merisPixelProperties.brightValue());
    }

    public void testSpectralFlatnessValue() {
        float[] refl = setSpectralFlatnessTestValues();
        merisPixelProperties.setRefl(refl);
        assertEquals(-1.0f, merisPixelProperties.spectralFlatnessValue(), 1.E-3);
    }

    public void testWhiteValue() {
        float[] refl = setSpectralFlatnessTestValues();
        merisPixelProperties.setRefl(refl);

        // bright value: no data
        merisPixelProperties.setBrr442(-1.0f);
        merisPixelProperties.setBrr442Thresh(0.1f);
        assertEquals(0.0f, merisPixelProperties.whiteValue());

        // bright value > BRIGHT_FOR_WHITE_THRESH
        merisPixelProperties.setBrr442(0.5f);
        merisPixelProperties.setBrr442Thresh(0.1f);
        assertEquals(-3.0f, merisPixelProperties.whiteValue(), 1.0E-3);
    }

    public void testTemperature() {
        assertEquals(0.5f, merisPixelProperties.temperatureValue());
    }

    public void testNdsi() {
        float[] brr = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];

        brr[11] = 1.0f;
        brr[12] = 0.2f;
        merisPixelProperties.setBrr(brr);
        assertEquals(0.666f, merisPixelProperties.ndsiValue(), 1.E-3);
    }

    public void testNdvi() {
        float[] brr = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];

        brr[4] = 0.5f;
        brr[9] = 1.0f;
        merisPixelProperties.setBrr(brr);
        assertEquals(0.333f, merisPixelProperties.ndviValue(), 1.E-3);
    }

    public void testPressure() {
        merisPixelProperties.setRefl(new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS]);
        // land:
        merisPixelProperties.setL1FlagLand(true);
        merisPixelProperties.setP1(300.0f);
        assertEquals(0.7f, merisPixelProperties.pressureValue());
        // water:
        merisPixelProperties.setL1FlagLand(false);
        merisPixelProperties.setPscatt(400.0f);
        assertEquals(0.6f, merisPixelProperties.pressureValue());
    }

    public void testAPrioriLandValue() {
        // land:
        merisPixelProperties.setRefl(new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS]);
        merisPixelProperties.setL1FlagLand(true);
        assertEquals(1.0f, merisPixelProperties.aPrioriLandValue());
        // water:
        merisPixelProperties.setL1FlagLand(false);
        assertEquals(0.0f, merisPixelProperties.aPrioriLandValue());
    }

    public void testAPrioriWaterValue() {
        // water:
        merisPixelProperties.setRefl(new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS]);
        merisPixelProperties.setL1FlagLand(false);
        assertEquals(1.0f, merisPixelProperties.aPrioriWaterValue());
        // land:
        merisPixelProperties.setL1FlagLand(true);
        assertEquals(0.0f, merisPixelProperties.aPrioriWaterValue());
    }

    public void testRadiometricLandValue() {
        assertEquals(0.5f, merisPixelProperties.radiometricLandValue());
    }

    public void testRadiometricWaterValue() {
        assertEquals(0.5f, merisPixelProperties.radiometricWaterValue());
    }


    private float[] setSpectralFlatnessTestValues() {
        float[] refl = new float[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];

        // flatness_0 := 1.0:
        refl[0] = 412.7f;
        refl[2] = 489.9f;
        // flatness_2 := 2.0:
        refl[4] = 559.7f;
        refl[5] = 679.5f;
        // flatness_3 := 3.0:
        refl[6] = 664.6f;
        refl[9] = 930.7f;
        return refl;
    }

}
