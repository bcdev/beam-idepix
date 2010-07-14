package org.esa.beam.mepix.operators;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public enum CloudScreeningSelector {
    QWG(0),
    GlobAlbedo(1),
    CoastColour(2);

    private final int value;

    private CloudScreeningSelector(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
