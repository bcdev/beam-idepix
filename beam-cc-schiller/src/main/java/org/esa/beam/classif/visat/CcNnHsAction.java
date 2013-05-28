package org.esa.beam.classif.visat;


import org.esa.beam.classif.CcNnHsOp;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class CcNnHsAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        final String operatorName = "Meris.CCNNHS";
        final AppContext appContext = getAppContext();
        final String title = "CcNnHs Processor";
        final String helpID = event.getCommand().getHelpId();

        final DefaultSingleTargetProductDialog dialog = new DefaultSingleTargetProductDialog(operatorName, appContext,
                title, helpID);

        dialog.getJDialog().pack();
        dialog.show();
    }
}
