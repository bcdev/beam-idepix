package org.esa.beam.classif;


import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CcNnHsOpTest {

    private CcNnHsOp ccNnHsOp;

    @Before
    public void setUp() {
        ccNnHsOp = new CcNnHsOp();
    }

    @Test
    public void testOperatorMetadata() {
        final OperatorMetadata operatorMetadata = CcNnHsOp.class.getAnnotation(OperatorMetadata.class);
        assertNotNull(operatorMetadata);
        assertEquals("Meris.CCNNHS", operatorMetadata.alias());
        assertEquals("1.0", operatorMetadata.version());
        assertEquals("Tom Block", operatorMetadata.authors());
        assertEquals("(c) 2013 by Brockmann Consult", operatorMetadata.copyright());
        assertEquals("Computing cloud masks using neural networks by H.Schiller", operatorMetadata.description());
    }

    @Test
    public void testSourceProductAnnotation() throws NoSuchFieldException {
        final Field productField = CcNnHsOp.class.getDeclaredField("sourceProduct");
        assertNotNull(productField);

        final SourceProduct productFieldAnnotation = productField.getAnnotation(SourceProduct.class);
        assertNotNull(productFieldAnnotation);
    }

    @Test
    public void testValidPixelExpressionAnnotation() throws NoSuchFieldException {
        final Field validPixelField = CcNnHsOp.class.getDeclaredField("validPixelExpression");

        final Parameter annotation = validPixelField.getAnnotation(Parameter.class);
        assertNotNull(annotation);
        assertEquals("NOT l1_flags.INVALID", annotation.defaultValue());
        assertEquals("A flag expression that defines pixels to be processed.", annotation.description());
    }

    @Test
    public void testAlgorithmNameAnnotation() throws NoSuchFieldException {
        final Field algorithmNameFiled = CcNnHsOp.class.getDeclaredField("algorithmName");

        final Parameter annotation = algorithmNameFiled.getAnnotation(Parameter.class);
        assertNotNull(annotation);
        assertEquals("Algo_2013-03-01", annotation.defaultValue());
        assertEquals("Select algorithm for processing.", annotation.description());

        final String[] valueSet = annotation.valueSet();
        assertEquals(2, valueSet.length);
        assertEquals("Algo_2013-03-01", valueSet[0]);
        assertEquals("Algo_2013-05-09", valueSet[1]);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testInheritance() {
        assertTrue(ccNnHsOp instanceof PixelOperator);
    }


    @Test
    public void testSpi() {
        final CcNnHsOp.Spi spi = new CcNnHsOp.Spi();
        final Class<? extends Operator> operatorClass = spi.getOperatorClass();
        assertTrue(operatorClass.isAssignableFrom(CcNnHsOp.class));
    }
}
