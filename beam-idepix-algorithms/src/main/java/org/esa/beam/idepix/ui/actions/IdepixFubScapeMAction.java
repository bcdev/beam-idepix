package org.esa.beam.idepix.ui.actions;

import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.idepix.algorithms.scapem.FubScapeMOp;
import org.esa.beam.idepix.algorithms.schiller.SchillerOp;
import org.esa.beam.idepix.ui.IdepixDefaultDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Idepix action for FubScapeM algorithm.
 *
 * @author Olaf Danne, Tonio Fincke
 */
public class IdepixFubScapeMAction extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        final IdepixDefaultDialog dialog =
                new IdepixDefaultDialog(OperatorSpi.getOperatorAlias(FubScapeMOp.class),
                                        getAppContext(),
                                        "IDEPIX Pixel Identification Tool - ScapeM" +
                                                " Algorithm -  " + IdepixConstants.IDEPIX_VERSION,
                                        "idepixChain","");
        dialog.show();
    }
}
