package org.esa.beam.idepix.algorithms.occci;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;

/**
 * MODIS sensor context implementation
 *
 * @author olafd
 */
class ModisSensorContext implements SensorContext {
    private static final int[] SPECTRAL_OUTPUT_INDEXES = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final float[] SPECTRAL_OUTPUT_WAVELENGTHS = new float[]{
            413.f, 443.f, 488.f, 531.f, 551.f, 667.f, 678.f, 748.f, 870.f,
            645.f, 859.f,
            469.f, 555.f, 1240.f, 1640.f, 2130.f
    };
    private static final int[] NN_OUTPUT_INDICES = new int[]{1, 2, 4, 8, 9, 15, 18, 21, 26};

    private final static String MODIS_L1B_REFLECTANCE_1_BAND_NAME = "EV_250_Aggr1km_RefSB.1";           // 645
    private final static String MODIS_L1B_REFLECTANCE_2_BAND_NAME = "EV_250_Aggr1km_RefSB.2";           // 859
    private final static String MODIS_L1B_REFLECTANCE_3_BAND_NAME = "EV_500_Aggr1km_RefSB.3";           // 469
    private final static String MODIS_L1B_REFLECTANCE_4_BAND_NAME = "EV_500_Aggr1km_RefSB.4";           // 555
    private final static String MODIS_L1B_REFLECTANCE_5_BAND_NAME = "EV_500_Aggr1km_RefSB.5";           // 1240
    private final static String MODIS_L1B_REFLECTANCE_6_BAND_NAME = "EV_500_Aggr1km_RefSB.6";           // 1640
    private final static String MODIS_L1B_REFLECTANCE_7_BAND_NAME = "EV_500_Aggr1km_RefSB.7";           // 2130
    private final static String MODIS_L1B_REFLECTANCE_8_BAND_NAME = "EV_1KM_RefSB.8";                    // 412
    private final static String MODIS_L1B_REFLECTANCE_9_BAND_NAME = "EV_1KM_RefSB.9";                    // 443
    private final static String MODIS_L1B_REFLECTANCE_10_BAND_NAME = "EV_1KM_RefSB.10";                   // 488
    private final static String MODIS_L1B_REFLECTANCE_11_BAND_NAME = "EV_1KM_RefSB.11";                   // 531
    private final static String MODIS_L1B_REFLECTANCE_12_BAND_NAME = "EV_1KM_RefSB.12";                   // 547
    private final static String MODIS_L1B_REFLECTANCE_13_BAND_NAME = "EV_1KM_RefSB.13lo";                 // 667
    private final static String MODIS_L1B_REFLECTANCE_14_BAND_NAME = "EV_1KM_RefSB.13hi";                 // 667
    private final static String MODIS_L1B_REFLECTANCE_15_BAND_NAME = "EV_1KM_RefSB.14lo";                 // 678
    private final static String MODIS_L1B_REFLECTANCE_16_BAND_NAME = "EV_1KM_RefSB.14hi";                 // 678
    private final static String MODIS_L1B_REFLECTANCE_17_BAND_NAME = "EV_1KM_RefSB.15";                   // 748
    private final static String MODIS_L1B_REFLECTANCE_18_BAND_NAME = "EV_1KM_RefSB.16";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_19_BAND_NAME = "EV_1KM_RefSB.17";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_20_BAND_NAME = "EV_1KM_RefSB.18";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_21_BAND_NAME = "EV_1KM_RefSB.19";                   // 869
    private final static String MODIS_L1B_REFLECTANCE_22_BAND_NAME = "EV_1KM_RefSB.26";                   // 869

    private final static String[] MODIS_L1B_SPECTRAL_BAND_NAMES = {
            MODIS_L1B_REFLECTANCE_1_BAND_NAME,
            MODIS_L1B_REFLECTANCE_2_BAND_NAME,
            MODIS_L1B_REFLECTANCE_3_BAND_NAME,
            MODIS_L1B_REFLECTANCE_4_BAND_NAME,
            MODIS_L1B_REFLECTANCE_5_BAND_NAME,
            MODIS_L1B_REFLECTANCE_6_BAND_NAME,
            MODIS_L1B_REFLECTANCE_7_BAND_NAME,
            MODIS_L1B_REFLECTANCE_8_BAND_NAME,
            MODIS_L1B_REFLECTANCE_9_BAND_NAME,
            MODIS_L1B_REFLECTANCE_10_BAND_NAME,
            MODIS_L1B_REFLECTANCE_11_BAND_NAME,
            MODIS_L1B_REFLECTANCE_12_BAND_NAME,
            MODIS_L1B_REFLECTANCE_13_BAND_NAME,
            MODIS_L1B_REFLECTANCE_14_BAND_NAME,
            MODIS_L1B_REFLECTANCE_15_BAND_NAME,
            MODIS_L1B_REFLECTANCE_16_BAND_NAME,
            MODIS_L1B_REFLECTANCE_17_BAND_NAME,
            MODIS_L1B_REFLECTANCE_18_BAND_NAME,
            MODIS_L1B_REFLECTANCE_19_BAND_NAME,
            MODIS_L1B_REFLECTANCE_20_BAND_NAME,
            MODIS_L1B_REFLECTANCE_21_BAND_NAME,
            MODIS_L1B_REFLECTANCE_22_BAND_NAME,
    };
    private final static int MODIS_L1B_NUM_SPECTRAL_BANDS = MODIS_L1B_SPECTRAL_BAND_NAMES.length;

    private final static String MODIS_L1B_EMISSIVITY_1_BAND_NAME = "EV_1KM_Emissive.20";                    // 3750
    private final static String MODIS_L1B_EMISSIVITY_2_BAND_NAME = "EV_1KM_Emissive.21";                    // 3959
    private final static String MODIS_L1B_EMISSIVITY_3_BAND_NAME = "EV_1KM_Emissive.22";                    // 3959
    private final static String MODIS_L1B_EMISSIVITY_4_BAND_NAME = "EV_1KM_Emissive.23";                    // 4050
    private final static String MODIS_L1B_EMISSIVITY_5_BAND_NAME = "EV_1KM_Emissive.24";                    // 4465
    private final static String MODIS_L1B_EMISSIVITY_6_BAND_NAME = "EV_1KM_Emissive.25";                    // 4515
    private final static String MODIS_L1B_EMISSIVITY_7_BAND_NAME = "EV_1KM_Emissive.27";                    // 6715
    private final static String MODIS_L1B_EMISSIVITY_8_BAND_NAME = "EV_1KM_Emissive.28";                    // 7325
    private final static String MODIS_L1B_EMISSIVITY_9_BAND_NAME = "EV_1KM_Emissive.29";                    // 8550
    private final static String MODIS_L1B_EMISSIVITY_10_BAND_NAME = "EV_1KM_Emissive.30";                    // 9730
    private final static String MODIS_L1B_EMISSIVITY_11_BAND_NAME = "EV_1KM_Emissive.31";                    // 11030
    private final static String MODIS_L1B_EMISSIVITY_12_BAND_NAME = "EV_1KM_Emissive.32";                    // 12020
    private final static String MODIS_L1B_EMISSIVITY_13_BAND_NAME = "EV_1KM_Emissive.33";                    // 13335
    private final static String MODIS_L1B_EMISSIVITY_14_BAND_NAME = "EV_1KM_Emissive.34";                    // 13635
    private final static String MODIS_L1B_EMISSIVITY_15_BAND_NAME = "EV_1KM_Emissive.35";                    // 13935
    private final static String MODIS_L1B_EMISSIVITY_16_BAND_NAME = "EV_1KM_Emissive.36";                    // 14235

    private final static String[] MODIS_L1B_EMISSIVE_BAND_NAMES = {
            MODIS_L1B_EMISSIVITY_1_BAND_NAME,
            MODIS_L1B_EMISSIVITY_2_BAND_NAME,
            MODIS_L1B_EMISSIVITY_3_BAND_NAME,
            MODIS_L1B_EMISSIVITY_4_BAND_NAME,
            MODIS_L1B_EMISSIVITY_5_BAND_NAME,
            MODIS_L1B_EMISSIVITY_6_BAND_NAME,
            MODIS_L1B_EMISSIVITY_7_BAND_NAME,
            MODIS_L1B_EMISSIVITY_8_BAND_NAME,
            MODIS_L1B_EMISSIVITY_9_BAND_NAME,
            MODIS_L1B_EMISSIVITY_10_BAND_NAME,
            MODIS_L1B_EMISSIVITY_11_BAND_NAME,
            MODIS_L1B_EMISSIVITY_12_BAND_NAME,
            MODIS_L1B_EMISSIVITY_13_BAND_NAME,
            MODIS_L1B_EMISSIVITY_14_BAND_NAME,
            MODIS_L1B_EMISSIVITY_15_BAND_NAME,
            MODIS_L1B_EMISSIVITY_16_BAND_NAME,
    };

    private final static int MODIS_L1B_NUM_EMISSIVE_BANDS = MODIS_L1B_EMISSIVE_BAND_NAMES.length;

    private final static double surfacePressureDefaultValue = 1019.0;
    private final static double ozoneDefaultValue = 330.0;

    // derived from cahalan table from Kerstin tb 2013-11-15
    private final static double[] defaultSolarFluxes = new double[]{
            1740.458085,          // 412
            1844.698571,          // 443
            1949.723913,          // 488
            1875.394737,          // 531
            1882.428333,          // 547
            1545.183846,          // 667
            1507.529167,          // 678
            1277.037,             // 748
            945.3382727,          // 869
            1601.482295,          // 645
            967.137667,           // 859
            2072.03625,           // 469
            1874.005,             // 555
            456.1987143,          // 1240
            229.882,              // 1640
            92.5171833            // 2130
    };

    @Override
    public int getNumSpectralInputBands() {
        return MODIS_L1B_NUM_SPECTRAL_BANDS;
    }

    @Override
    public Sensor getSensor() {
        return Sensor.MODIS;
    }

    @Override
    public void configureSourceSamples(SampleConfigurer sampleConfigurer, Product sourceProduct, String spectralBandPrefix) {
        for (int i = 0; i < MODIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (sourceProduct.containsBand(MODIS_L1B_SPECTRAL_BAND_NAMES[i])) {
                sampleConfigurer.defineSample(i, MODIS_L1B_SPECTRAL_BAND_NAMES[i], sourceProduct);
            } else {
                sampleConfigurer.defineSample(i, MODIS_L1B_SPECTRAL_BAND_NAMES[i].replace(".", "_"), sourceProduct);
            }
        }
        for (int i = 0; i < MODIS_L1B_NUM_EMISSIVE_BANDS; i++) {
            if (sourceProduct.containsBand(MODIS_L1B_EMISSIVE_BAND_NAMES[i])) {
                sampleConfigurer.defineSample(Constants.MODIS_SRC_RAD_OFFSET + i, MODIS_L1B_EMISSIVE_BAND_NAMES[i], sourceProduct);
            } else {
                final String newEmissiveBandName = MODIS_L1B_EMISSIVE_BAND_NAMES[i].replace(".", "_");
                final Band emissiveBand = sourceProduct.getBand(newEmissiveBandName);
                emissiveBand.setScalingFactor(1.0);      // todo: we do this to come back to counts with SeaDAS reader,
                                                         // as the NN was also trained with counts
                emissiveBand.setScalingOffset(0.0);
                sampleConfigurer.defineSample(Constants.MODIS_SRC_RAD_OFFSET + i, newEmissiveBandName, sourceProduct);
            }
        }
    }

    @Override
    public void scaleInputSpectralDataToReflectance(double[] inputs, int offset) {
        // nothing to do - MODIS comes as TOA reflectance
    }

    @Override
    public void init(Product sourceProduct) {
        // nothing to do
    }

    @Override
    public int getSrcRadOffset() {
        return Constants.MODIS_SRC_RAD_OFFSET;
    }
}
