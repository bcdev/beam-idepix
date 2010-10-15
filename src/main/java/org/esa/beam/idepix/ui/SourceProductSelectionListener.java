package org.esa.beam.idepix.ui;

import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.idepix.operators.MepixConstants;
import org.esa.beam.idepix.util.MepixUtils;

/**
 * Listener for selection of source product
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class SourceProductSelectionListener implements SelectionChangeListener {

    private MepixForm form;
    private TargetProductSelectorModel targetProductSelectorModel;
    private String targetProductNameSuffix;

    SourceProductSelectionListener(MepixForm form, TargetProductSelectorModel targetProductSelectorModel,
                                          String targetProductNameSuffix) {
        this.form = form;
        this.targetProductSelectorModel = targetProductSelectorModel;
        this.targetProductNameSuffix = targetProductNameSuffix;
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        final Product selectedProduct = (Product) event.getSelection().getSelectedValue();

        if (selectedProduct != null) {
            // convert to MEPIX specific product name
            String mepixName = selectedProduct.getName();

            // check for MERIS:
            if (MepixUtils.isValidMerisProduct(selectedProduct)) {
                mepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(mepixName + targetProductNameSuffix);
                form.setEnabledAt(MepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.IPF_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.PRESSURE_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.CLOUDS_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.COASTCOLOUR_TAB_INDEX, false);
            } else if (MepixUtils.isValidAatsrProduct(selectedProduct)) {
                // check for AATSR:
                mepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(mepixName);
                form.setEnabledAt(MepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.IPF_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.PRESSURE_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.CLOUDS_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.COASTCOLOUR_TAB_INDEX, false);
            } else if (MepixUtils.isValidVgtProduct(selectedProduct)) {
                // check for VGT:
                mepixName = selectedProduct.getName() + "_IDEPIX";  // todo: discuss
                targetProductSelectorModel.setProductName(mepixName);
                form.setEnabledAt(MepixConstants.GLOBALBEDO_TAB_INDEX, true);
                form.setEnabledAt(MepixConstants.IPF_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.PRESSURE_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.CLOUDS_TAB_INDEX, false);
                form.setEnabledAt(MepixConstants.COASTCOLOUR_TAB_INDEX, false);
            }
        }
    }

    @Override
    public void selectionContextChanged(SelectionChangeEvent event) {
        // no actions
    }

}
