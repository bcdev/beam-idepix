package org.esa.beam.idepix.algorithms.occci;

/**
 * Enumeration for selection of Landsat8 Schiller NNs
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public enum MerisSeaiceNNSelector {
    FOUR_CLASSES("FOUR_CLASSES", "8_671.3.net", new double[]{0.55, 1.5, 2.45}),
    FOUR_CLASSES_NORTH("FOUR_CLASSES_NORTH", "8_268.2.net", new double[]{0.55, 1.45, 2.4}),
    SIX_CLASSES("SIX_CLASSES", "8_593.8.net", new double[]{0.7, 1.65, 2.5, 3.5, 4.6});

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
