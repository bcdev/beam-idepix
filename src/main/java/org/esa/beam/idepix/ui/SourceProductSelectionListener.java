package org.esa.beam.idepix.ui;

import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.idepix.operators.IdepixConstants;
import org.esa.beam.idepix.util.IdepixUtils;

/**
 * Listener for selection of source product
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class SourceProductSelectionListener implements SelectionChangeListener {

    private IdepixForm form;
    private TargetProductSelectorModel targetProductSelectorModel;
    private String targetProductNameSuffix;

    SourceProductSelectionListener(IdepixForm form, TargetProductSelectorModel targetProductSelectorModel,
                                          String targetProductNameSuffix) {
        this.form = form;
        this.targetProductSelectorModel = targetProductSelectorModel;
        this.targetProductNameSuffix = targetProductNameSuffix;
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        final Product selectedProduct = (Product) event.getSelection().getSelectedValue();

        if (selectedProduct != null) {
            // convert to IDEPIX specific product name
            String idepixName = selectedProduct.getName();

            // check for MERIS:
            if (IdepixUtils.isValidMerisProduct(selectedProduct)) {
                idepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(idepixName + targetProductNameSuffix);
                form.setEnabledAt(IdepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.IPF_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.PRESSURE_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.CLOUDS_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.COASTCOLOUR_TAB_INDEX, false);
            } else if (IdepixUtils.isValidAatsrProduct(selectedProduct)) {
                // check for AATSR:
                idepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(idepixName);
                form.setEnabledAt(IdepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.IPF_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.PRESSURE_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.CLOUDS_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.COASTCOLOUR_TAB_INDEX, false);
            } else if (IdepixUtils.isValidVgtProduct(selectedProduct)) {
                // check for VGT:
                idepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(idepixName);
                form.setEnabledAt(IdepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(IdepixConstants.IPF_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.PRESSURE_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.CLOUDS_TAB_INDEX, false);
                form.setEnabledAt(IdepixConstants.COASTCOLOUR_TAB_INDEX, false);
            }
        }
    }

    @Override
    public void selectionContextChanged(SelectionChangeEvent event) {
        // no actions
    }

}
