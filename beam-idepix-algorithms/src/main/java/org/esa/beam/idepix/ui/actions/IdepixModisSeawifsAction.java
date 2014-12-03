package org.esa.beam.idepix.ui.actions;

import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.idepix.algorithms.occci.OccciOp;
import org.esa.beam.idepix.ui.IdepixDefaultDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Idepix action for  MODIS/SeaWiFS algorithm.
 *
 * @author Olaf Danne
 */
public class IdepixModisSeawifsAction extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        final IdepixDefaultDialog dialog =
                new IdepixDefaultDialog(OperatorSpi.getOperatorAlias(OccciOp.class),
                                        getAppContext(),
                                        "IDEPIX Pixel Identification Tool - MODIS/SeaWiFS" +
                                                " Algorithm",
                                        "idepixChain","");
        dialog.show();
    }
}
