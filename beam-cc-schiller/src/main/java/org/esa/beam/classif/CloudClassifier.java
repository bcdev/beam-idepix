package org.esa.beam.classif;

import org.esa.beam.classif.algorithm.Constants;

public class CloudClassifier {

    private static final double[] ALL_VAR_1_THRESH = new double[]{1.65, 2.4, 3.2};
    private static final double[] ALL_VAR_2_THRESH = new double[]{1.7, 2.35, 3.3};

    private static final double[] TER_VAR_1_THRESH = new double[]{1.75, 2.45, 3.4};
    private static final double[] TER_VAR_2_THRESH = new double[]{1.75, 2.5, 3.45};

    private static final double[] WAT_VAR_1_THRESH = new double[]{1.65, 2.4, 3.45};
    private static final double[] WAT_VAR_2_THRESH = new double[]{1.65, 2.35, 3.45};

    private static final double[] WAT_SIMPLE_VAR_1_THRESH = new double[]{1.55, 2.5};
    private static final double[] WAT_SIMPLE_VAR_2_THRESH = new double[]{1.45, 2.5};

    public static int toFlag_all_var1(double nnVal) {
        return toFlag(nnVal, ALL_VAR_1_THRESH);
    }

    public static int toFlag_all_var2(double nnVal) {
        return toFlag(nnVal, ALL_VAR_2_THRESH);
    }

    public static int toFlag_ter_var1(double nnVal) {
        return toFlag(nnVal, TER_VAR_1_THRESH);
    }

    public static int toFlag_ter_var2(double nnVal) {
        return toFlag(nnVal, TER_VAR_2_THRESH);
    }

    public static int toFlag_wat_var1(double nnVal) {
        return toFlag(nnVal, WAT_VAR_1_THRESH);
    }

    public static int toFlag_wat_var2(double nnVal) {
        return toFlag(nnVal, WAT_VAR_2_THRESH);
    }

    public static int toFlag_wat_simple_var1(double nnVal) {
        return toSimpleFlag(nnVal, WAT_SIMPLE_VAR_1_THRESH);
    }

    public static int toFlag_wat_simple_var2(double nnVal) {
        return toSimpleFlag(nnVal, WAT_SIMPLE_VAR_2_THRESH);
    }

    private static int toFlag(double nnVal, double[] thresholds) {
        if (nnVal < 0.0) {
            return Constants.UNPROCESSD_MASK;
        }

        if (nnVal >= thresholds[2]) {
            return Constants.CLOUD_MASK;
        } else if (nnVal >= thresholds[1]) {
            return Constants.NONCL_MASK;
        } else if (nnVal >= thresholds[0]) {
            return Constants.SPAMX_MASK;
        }
        return Constants.CLEAR_MASK;
    }

    private static int toSimpleFlag(double nnVal, double[] thresholds) {
        if (nnVal < 0.0) {
            return Constants.UNPROCESSD_MASK;
        }

        if (nnVal >= thresholds[1]) {
            return Constants.CLOUD_MASK;
        } else if (nnVal >= thresholds[0]) {
            return Constants.SPAMX_OR_NONCL_MASK;
        }
        return Constants.CLEAR_MASK;
    }
}
