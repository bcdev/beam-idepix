package org.esa.beam.idepix.operators;

import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.util.math.MathUtils;

/**
 * This class represents pixel properties as derived from MERIS L1b data
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class AnasMerisPixelProperties extends MerisPixelProperties {

    @Override
    public boolean isCloud() {
        return refl[6] > 0.5;
    }
}
