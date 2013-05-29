package org.esa.beam.classif;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class AcceptanceTestRunner extends BlockJUnit4ClassRunner {

    private boolean runAcceptanceTests;
    private Class<?> clazz;

    public AcceptanceTestRunner(Class<?> clazz) throws InitializationError {
        super(clazz);

        this.clazz = clazz;

        final String property = System.getProperty("run.acceptance.test");
        runAcceptanceTests = "true".equalsIgnoreCase(property);
    }

    @Override
    public Description getDescription() {
        return Description.createSuiteDescription("Acceptance Test Runner");
    }

    @Override
    public void run(RunNotifier runNotifier) {
        if (runAcceptanceTests) {
            super.run(runNotifier);
        } else {

            final Description description = Description.createTestDescription(clazz, "allMethods. Acceptance tests disabled. Set VM param -Drun.acceptance.test=true to enable.");
            runNotifier.fireTestIgnored(description);
        }
    }
}
