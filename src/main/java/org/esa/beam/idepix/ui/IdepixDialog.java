package org.esa.beam.idepix.ui;

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductFilter;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.ui.*;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.idepix.util.IdepixUtils;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents the product dialog for the IDEPIX processing.
 *
 * @author olafd
 * @version $Revision: 6824 $ $Date: 2009-11-03 16:02:02 +0100 (Di, 03 Nov 2009) $
 *
 */
class IdepixDialog extends SingleTargetProductDialog {

    private List<SourceProductSelector> sourceProductSelectorList;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;

    private String operatorName;
    private Map<String, Object> parameterMap;
    private IdepixForm form;
    private String targetProductNameSuffix;
    private AppContext appContext;

    public static final int DIALOG_WIDTH = 650;
    public static final int DIALOG_HEIGHT = 560;
    private OperatorSpi operatorSpi;
    private String helpID;

    /*
     * IdepixDialog constructor
     */
    IdepixDialog(String operatorName, AppContext appContext, String title, String helpID, String targetProductNameSuffix) {
        super(appContext, title, helpID);
        this.helpID = helpID;
        this.operatorName = operatorName;
        this.appContext = appContext;
        this.targetProductNameSuffix = targetProductNameSuffix;
        System.setProperty("gpfMode", "GUI");
        initialize(operatorName);
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        final HashMap<String, Product> sourceProducts = createSourceProductsMap();
        return GPF.createProduct(operatorName, parameterMap, sourceProducts);
    }

    @Override
    public int show() {
        initSourceProductSelectors();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        releaseSourceProductSelectors();
        super.hide();
    }

    @Override
    protected boolean verifyUserInput() {
        final HashMap<String, Product> sourceProducts = createSourceProductsMap();
        Product sourceProduct = sourceProducts.get("source");
        if (sourceProduct == null) {
            showErrorDialog("No input product specified!");
            return false;
        } else {
            return IdepixUtils.isInputValid(sourceProduct);
        }
    }

    ///////////// END OF PUBLIC //////////////

    private void initialize(String operatorName) {
        operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName");
        }
        parameterMap = new LinkedHashMap<String, Object>(17);

        form = new IdepixForm(operatorSpi, parameterMap);

        initComponents();
        
        form.initialize();
    }


    private void initComponents() {
        // Fetch source products
        setupSourceProductSelectorList(operatorSpi);
        if (!sourceProductSelectorList.isEmpty()) {
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
        ioParametersPanel.add(getTargetProductSelector().createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());

        final TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();

        SourceProductSelectionListener sourceProductSelectionListener =
                new SourceProductSelectionListener(form, targetProductSelectorModel, targetProductNameSuffix);
        sourceProductSelectorList.get(0).addSelectionChangeListener(sourceProductSelectionListener);

		form.setPreferredSize(new Dimension(IdepixDialog.DIALOG_WIDTH, IdepixDialog.DIALOG_HEIGHT));
        form.add("I/O Parameters", ioParametersPanel);

        final OperatorParameterSupport parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorClass(),
                                                                                       null,
                                                                                       parameterMap,
                                                                                       null);
        OperatorMenu menuSupport = new OperatorMenu(this.getJDialog(), operatorSpi.getOperatorClass(),
                                                    parameterSupport, helpID);
        getJDialog().setJMenuBar(menuSupport.createDefaultMenu());
    }

    private HashMap<String, Product> createSourceProductsMap() {
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

    private void initSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.initProducts();
        }
    }

    private void releaseSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
    }

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

        private AnnotatedSourceProductFilter(SourceProduct annot) {
            this.annot = annot;
        }

        @Override
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
