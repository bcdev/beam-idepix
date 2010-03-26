package org.esa.beam.mepix.ui;



import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductFilter;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.processor.ui.ParameterPage;
import org.esa.beam.framework.param.ParamGroup;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 7464 $ $Date: 2009-12-11 17:18:25 +0100 (Fr, 11 Dez 2009) $
 */
public class MepixForm extends JTabbedPane {

    private List<SourceProductSelector> sourceProductSelectorList;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;

    private TargetProductSelector targetProductSelector;
    private OperatorSpi operatorSpi;
    private String targetProductNameSuffix;
    private AppContext appContext;

    public MepixForm(AppContext appContext, OperatorSpi operatorSpi, TargetProductSelector targetProductSelector,
                     String targetProductNameSuffix) {
        this.appContext = appContext;
        this.targetProductSelector = targetProductSelector;
        this.operatorSpi = operatorSpi;
        this.targetProductNameSuffix = targetProductNameSuffix;

        initComponents();
    }

    public void initComponents() {
        // Fetch source products
        setupSourceProductSelectorList(operatorSpi);
        if (sourceProductSelectorList.size() > 0) {
//            setSourceProductSelectorLabels();
           setSourceProductSelectorToolTipTexts();
        }

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(3, 3);

        JPanel ioParametersPanel = new JPanel(tableLayout);
        for (SourceProductSelector selector : sourceProductSelectorList) {
            ioParametersPanel.add(selector.createDefaultPanel());
        }
        ioParametersPanel.add(targetProductSelector.createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());
        sourceProductSelectorList.get(0).addSelectionChangeListener(new SelectionChangeListener() {
            public void selectionChanged(SelectionChangeEvent event) {
//                final Product selectedProduct = (Product) event.getSelection().getFirstElement();
                final Product selectedProduct = (Product) event.getSelection().getSelectedValue();
                final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();

                // convert to MEPIX specific product name
                String mepixName = selectedProduct.getName();
                if (selectedProduct.getName().startsWith(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME) ||
                		selectedProduct.getName().startsWith(EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME)) {
        	        String resName = "";
        	        if (selectedProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
        	        	// MER_RR__1
        	        	resName = EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME;
        	        } else if (selectedProduct.getProductType().equals(EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME)) {
        	        	// MER_FR__1
        	        	resName = EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME;
        	        }
        	        String nameL1bPrefix = resName.substring(0, resName.length()-2);
        	        int nameLength = mepixName.length();
        	        mepixName = nameL1bPrefix + "2MEPIX" + mepixName.substring(nameL1bPrefix.length()+6, nameLength);
        	        if (mepixName.toUpperCase().endsWith(".N1") || mepixName.toUpperCase().endsWith(".E1") ||
        	        		mepixName.toUpperCase().endsWith(".E2"))
        	        	mepixName = mepixName.substring(0, mepixName.length()-3);
                }

                targetProductSelectorModel.setProductName(mepixName + targetProductNameSuffix);
            }
            public void selectionContextChanged(SelectionChangeEvent event) {
            }
        });

		this.setPreferredSize(new Dimension(MepixDialog.DIALOG_WIDTH, MepixDialog.DIALOG_HEIGHT));
        this.add("I/O Parameters", ioParametersPanel);
    }

    public void addParameterPane(PropertyContainer propertyContainer, String title) {
        BindingContext context = new BindingContext(propertyContainer);

        PropertyPane parametersPane = new PropertyPane(context);
        JPanel paremetersPanel = parametersPane.createPanel();
        paremetersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        this.add(title, new JScrollPane(paremetersPanel));
    }

    public HashMap<String, Product> createSourceProductsMap() {
        final HashMap<String, Product> sourceProducts = new HashMap<String, Product>(8);
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            String key = field.getName();
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (!annot.alias().isEmpty()) {
                key = annot.alias();
            }
            sourceProducts.put(key, selector.getSelectedProduct());
        }
        return sourceProducts;
    }

    public void initSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.initProducts();
        }
    }

    public void releaseSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
    }

    ///////////// END OF PUBLIC //////////////

    private void setupSourceProductSelectorList(OperatorSpi operatorSpi) {
        sourceProductSelectorList = new ArrayList<SourceProductSelector>(3);
        sourceProductSelectorMap = new HashMap<Field, SourceProductSelector>(3);
        final Field[] fields = operatorSpi.getOperatorClass().getDeclaredFields();
        for (Field field : fields) {
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (annot != null) {
                final ProductFilter productFilter = new AnnotatedSourceProductFilter(annot);
                SourceProductSelector sourceProductSelector = new SourceProductSelector(appContext);
                sourceProductSelector.setProductFilter(productFilter);
                sourceProductSelectorList.add(sourceProductSelector);
                sourceProductSelectorMap.put(field, sourceProductSelector);
            }
        }
    }

    private void setSourceProductSelectorToolTipTexts() {
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);

            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            final String description = annot.description();
            if (!description.isEmpty()) {
                selector.getProductNameComboBox().setToolTipText(description);
            }
        }
    }


    private static class AnnotatedSourceProductFilter implements ProductFilter {

        private final SourceProduct annot;

        public AnnotatedSourceProductFilter(SourceProduct annot) {
            this.annot = annot;
        }

        public boolean accept(Product product) {

            if (!annot.type().isEmpty() && !product.getProductType().matches(annot.type())) {
                return false;
            }

            for (String bandName : annot.bands()) {
                if (!product.containsBand(bandName)) {
                    return false;
                }
            }

            return true;
        }
    }
}
