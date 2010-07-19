package org.esa.beam.mepix.ui;



import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductFilter;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.mepix.operators.MepixConstants;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
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
    private Map<String, Object> parameterMap;
    private Component vgtParameterPane;

    
    public MepixForm(AppContext appContext, OperatorSpi operatorSpi, TargetProductSelector targetProductSelector,
                     String targetProductNameSuffix, Map<String, Object> parameterMap) {
        this.appContext = appContext;
        this.targetProductSelector = targetProductSelector;
        this.operatorSpi = operatorSpi;
        this.targetProductNameSuffix = targetProductNameSuffix;
        this.parameterMap = parameterMap;

        initComponents();

        final PropertyContainer propertyContainerCloudscreening =
                createPanelSpecificValueContainer(MepixConstants.cloudScreeningParameterNames);
        addParameterPane(propertyContainerCloudscreening, "Cloud Screening");
//        addParameterPane(propertyContainerCloudscreening, "VGT Cloud Screening");

        // define new value containers for distribution of the target products to three different tab panes.
        final PropertyContainer propertyContainerIpf =
                createPanelSpecificValueContainer(MepixConstants.ipfParameterNames);
        final PropertyContainer propertyContainerPressure =
                createPanelSpecificValueContainer(MepixConstants.pressureParameterNames);

        addParameterPane(propertyContainerIpf, "IPF Compatible Products");
//        add("IPF Compatible Products", createParameterPane(propertyContainerIpf));
        addParameterPane(propertyContainerPressure, "Pressure Products");
//        add(createParameterPane(propertyContainerPressure, "Pressure Products"));

//        if (System.getProperty("mepixMode") != null && System.getProperty("mepixMode").equals("QWG")) {
            final PropertyContainer propertyContainerCloud =
                    createPanelSpecificValueContainer(MepixConstants.cloudProductParameterNames);
            addParameterPane(propertyContainerCloud, "Cloud Products");
//            add(createParameterPane(propertyContainerCloud, "Cloud Products"));
//        }

        final PropertyContainer propertyContainerGlobalbedo=
                createPanelSpecificValueContainer(MepixConstants.globalbedoParameterNames);
        addParameterPane(propertyContainerGlobalbedo, "GlobAlbedo");
//        addParameterPane(propertyContainerGlobalbedo, "VGT Cloud Screening");

        final PropertyContainer propertyContainerCoastcolour=
                createPanelSpecificValueContainer(MepixConstants.coastcolourParameterNames);
        addParameterPane(propertyContainerCoastcolour, "CoastColour");
//        addParameterPane(propertyContainerCoastcolour, "VGT Cloud Screening");
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

                if (selectedProduct != null) {
                    // convert to MEPIX specific product name
                    String mepixName = selectedProduct.getName();

                    // check for MERIS:
                    if (selectedProduct.getName().startsWith(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME) ||
                        selectedProduct.getName().startsWith(EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME) ||
                        selectedProduct.getName().startsWith(EnvisatConstants.MERIS_FRS_L1B_PRODUCT_TYPE_NAME)) {
                        String resName = "";
                        if (selectedProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME)) {
                            // MER_RR__1
                            resName = EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME;
                        } else if (selectedProduct.getProductType().equals(
                                EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME)) {
                            // MER_FR__1
                            resName = EnvisatConstants.MERIS_FR_L1B_PRODUCT_TYPE_NAME;
                        }
                        String nameL1bPrefix = resName.substring(0, resName.length() - 2);
                        int nameLength = mepixName.length();
                        mepixName = nameL1bPrefix + "2MEPIX" + mepixName.substring(nameL1bPrefix.length() + 6,
                                                                                   nameLength);
                        if (mepixName.toUpperCase().endsWith(".N1") || mepixName.toUpperCase().endsWith(".E1") ||
                            mepixName.toUpperCase().endsWith(".E2")) {
                            mepixName = mepixName.substring(0, mepixName.length() - 3);
                        }
                        targetProductSelectorModel.setProductName(mepixName + targetProductNameSuffix);
                        if (vgtParameterPane != null) {
                            remove(vgtParameterPane);
                        }
                    } else if (selectedProduct.getProductType().startsWith("ATS_TOA_1")) { // todo: introduce constant
                        // check for AATSR:
                        mepixName = selectedProduct.getName() + "VGT2MEPIX";  // todo: discuss
                        targetProductSelectorModel.setProductName(mepixName);

//                        if (vgtParameterPane != null) {
//                            final PropertyContainer propertyContainerVgt = createPanelSpecificValueContainer("vgt");
//                            vgtParameterPane = createParameterPane(propertyContainerVgt, "VGT Cloud Screening");
//                            add(vgtParameterPane);
//                        }
                    } else if (selectedProduct.getProductType().startsWith("VGT")) { // todo: introduce constant
                        // check for VGT:
                        mepixName = selectedProduct.getName() + "VGT2MEPIX";  // todo: discuss
                        targetProductSelectorModel.setProductName(mepixName);

//                        if (vgtParameterPane != null) {
//                            final PropertyContainer propertyContainerVgt = createPanelSpecificValueContainer("vgt");
//                            vgtParameterPane = createParameterPane(propertyContainerVgt, "VGT Cloud Screening");
//                            add(vgtParameterPane);
//                        }
                    }
//                    else {
//                        throw new OperatorException("Input product must be either MERIS, AATSR or VGT L1b!");
//                    }
                }
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

    public Component createParameterPane(PropertyContainer propertyContainer) {
        BindingContext context = new BindingContext(propertyContainer);

        PropertyPane parametersPane = new PropertyPane(context);
        JPanel paremetersPanel = parametersPane.createPanel();
        paremetersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        final JScrollPane jScrollPane = new JScrollPane(paremetersPanel);
        return jScrollPane;
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

    private PropertyContainer createPanelSpecificValueContainer(String[] thisPanelIDs) {
        ParameterDescriptorFactory parameterDescriptorFactory = new ParameterDescriptorFactory();
        PropertyContainer pc = PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(),
                                                                 parameterDescriptorFactory);

        try {
            pc.setDefaultValues();
        } catch (ValidationException e) {
            JOptionPane.showOptionDialog(null, e.getMessage(), "MEPIX - Error Message", JOptionPane.DEFAULT_OPTION,
                                             JOptionPane.ERROR_MESSAGE, null, null, null);
        }

        for (Property property : pc.getProperties()) {
            PropertyDescriptor propertyDescriptor = property.getDescriptor();
//            if (System.getProperty("mepixMode") != null && System.getProperty("mepixMode").equals("QWG")) {
//                if (!propertyDescriptor.getName().startsWith(panelId)) {
                if (!panelContainsProperty(propertyDescriptor.getName(), thisPanelIDs)) {
                    removeProperty(pc, propertyDescriptor);
                }
//            } else {
//                if (!propertyDescriptor.getName().startsWith(panelId) ||
//                    propertyDescriptor.getName().startsWith(panelId + "QWG")) {
//                    removeProperty(pc, propertyDescriptor);
//                }
//            }
        }
        return pc;
    }

    private boolean panelContainsProperty(String thisPropertyName, String[] propertyNames) {
        for (String name:propertyNames) {
            if (name.equals(thisPropertyName)) {
                return true;
            }
        }
        return false;
    }

    private void removeProperty(final PropertyContainer propertyContainer, PropertyDescriptor propertyDescriptor) {
		Property property = propertyContainer.getProperty(propertyDescriptor.getName());
		if (property != null)
			propertyContainer.removeProperty(property);
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
