package org.esa.beam.idepix.algorithms.cawa;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.idepix.operators.BasisOp;

/**
 * Idepix operator for pixel identification and classification with CAWA algorithm
 * (merge of GA over land and CC over water).
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "idepix.cawa",
        version = "2.2",
        copyright = "(c) 2014 by Brockmann Consult",
        description = "Pixel identification with CAWA algorithm (merge of GA over land and CC over water).")
public class CawaOp extends BasisOp {
    @Override
    public void initialize() throws OperatorException {
        // todo: for MERIS, we need a 'merge' operator using Globalbedo algorithm over land, and Coastcolour algorithm over water
    }
}
