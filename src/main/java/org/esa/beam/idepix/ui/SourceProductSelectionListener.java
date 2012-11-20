package org.esa.beam.idepix.ui;

import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;

/**
 * Listener for selection of source product
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
class SourceProductSelectionListener implements SelectionChangeListener {

    private TargetProductSelectorModel targetProductSelectorModel;
    private String targetProductNameSuffix;

    SourceProductSelectionListener(TargetProductSelectorModel targetProductSelectorModel,
                                          String targetProductNameSuffix) {
        this.targetProductSelectorModel = targetProductSelectorModel;
        this.targetProductNameSuffix = targetProductNameSuffix;
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        final Product selectedProduct = (Product) event.getSelection().getSelectedValue();

        if (selectedProduct != null) {
            // convert to IDEPIX specific product name
            String idepixName = selectedProduct.getName() + "_IDEPIX";
            targetProductSelectorModel.setProductName(idepixName + targetProductNameSuffix);
        }
    }

    @Override
    public void selectionContextChanged(SelectionChangeEvent event) {
        // no actions
    }

}
