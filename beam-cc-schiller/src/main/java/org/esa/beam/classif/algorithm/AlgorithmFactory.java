package org.esa.beam.classif.algorithm;


import java.util.HashMap;

public class AlgorithmFactory {

    public static final String ALGORITHM_2013_03_01 = "Algo_2013-03-01";
    public static final String ALGORITHM_2013_05_09 = "Algo_2013-05-09";

    private static final HashMap<String, CCAlgorithm> algorithms = new HashMap<String, CCAlgorithm>();

    public static CCAlgorithm get(String algorithmName) {
        CCAlgorithm algorithm = algorithms.get(algorithmName);
        if (algorithm == null) {
            algorithm = createAlgorithm(algorithmName);
            algorithms.put(algorithmName, algorithm);
        }
        return algorithm;
    }

    private static CCAlgorithm createAlgorithm(String algorithmName) {
        if (ALGORITHM_2013_03_01.equals(algorithmName)) {
            return new CC_2013_03_01();
        } else if (ALGORITHM_2013_05_09.equals(algorithmName)) {
            return new CC_2013_05_09();
        }

        throw new IllegalArgumentException("Invalid algorithm name: " + algorithmName);
    }
}
