package org.esa.beam.idepix.algorithms.occci;

/**
 * Enumeration for selection of Landsat8 Schiller NNs
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public enum MerisSeaiceNNSelector {
    FOUR_CLASSES_NORTH("FOUR_CLASSES_NORTH", "8_268.2.net", new double[]{0.55, 1.45, 2.4}),
    FOUR_CLASSES("FOUR_CLASSES", "8_671.3.net", new double[]{0.55, 1.5, 2.45}),
    SIX_CLASSES_NORTH("SIX_CLASSES_NORTH", "7x2_213.9.net", new double[]{0.55, 1.55, 2.45, 3.4, 4.55}),
//    SIX_CLASSES("SIX_CLASSES", "8_593.8.net", new double[]{0.7, 1.65, 2.5, 3.5, 4.6});
    SIX_CLASSES("SIX_CLASSES", "9x6_935.4.net", new double[]{0.45, 1.65, 2.45, 3.35, 4.5});  // delivery HS 20160531

    private final String label;
    private final String nnFileName;
    private final double[] separationValues;

    MerisSeaiceNNSelector(String label, String nnFileName, double[] separationValues) {
        this.label = label;
        this.nnFileName = nnFileName;
        this.separationValues = separationValues;
    }

    public String getLabel() {
        return label;
    }

    public String getNnFileName() {
        return nnFileName;
    }

    public double[] getSeparationValues() {
        return separationValues;
    }
}
