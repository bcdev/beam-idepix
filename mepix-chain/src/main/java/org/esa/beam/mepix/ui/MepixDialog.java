package org.esa.beam.mepix.ui;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyDescriptor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.ui.AppContext;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class represents the product dialog for the MEPIX processing.
 *
 * @author olafd
 * @version $Revision: 6824 $ $Date: 2009-11-03 16:02:02 +0100 (Di, 03 Nov 2009) $
 *
 */
public class MepixDialog extends SingleTargetProductDialog {
	

    private String operatorName;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;
    private Map<String, Object> parameterMap;
    private MepixForm form;
    private String targetProductNameSuffix;

    private boolean isUserDefinedPressureThreshold;
    private double userDefinedPressureThreshold;

    public static final int DIALOG_WIDTH = 500;
    public static final int DIALOG_HEIGHT = 420;
    private OperatorSpi operatorSpi;

    /**
     * MepixSingleTargetProductDialog constructor
     * 
     * @param operatorName
     * @param appContext
     * @param title
     * @param helpID
     */
    public MepixDialog(String operatorName, AppContext appContext, String title, String helpID, String targetProductNameSuffix) {
        super(appContext, title, helpID);
        this.operatorName = operatorName;
        this.targetProductNameSuffix = targetProductNameSuffix;
        initialize(operatorName, appContext);
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        final HashMap<String, Product> sourceProducts = form.createSourceProductsMap();
        return GPF.createProduct(operatorName, parameterMap, sourceProducts);
    }

    @Override
    public int show() {
        form.initSourceProductSelectors();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        form.releaseSourceProductSelectors();
        super.hide();
    }

    ///////////// END OF PUBLIC //////////////

    private void initialize(String operatorName, AppContext appContext) {
            targetProductNameSuffix = "";

            operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
            if (operatorSpi == null) {
                throw new IllegalArgumentException("operatorName");
            }

            form = new MepixForm(appContext, operatorSpi, getTargetProductSelector(),
                    targetProductNameSuffix);

            parameterMap = new LinkedHashMap<String, Object>(17);

            // define new value containers for distribution of the target products to three different tab panes.
            final PropertyContainer propertyContainerIpf = createPanelSpecificValueContainer("ipf");
            final PropertyContainer propertyContainerPressure = createPanelSpecificValueContainer("pressure");

            form.addParameterPane(propertyContainerIpf, "IPF Compatible Products");
            form.addParameterPane(propertyContainerPressure, "Pressure Products");

            if (System.getProperty("mepixMode") != null && System.getProperty("mepixMode").equals("QWG")) {
                final PropertyContainer propertyContainerCloud = createPanelSpecificValueContainer("cloud");
                form.addParameterPane(propertyContainerCloud, "Cloud Products");
            }
        }


    private PropertyContainer createPanelSpecificValueContainer(String panelId) {
        ParameterDescriptorFactory parameterDescriptorFactory = new ParameterDescriptorFactory();
        PropertyContainer pc = PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(), parameterDescriptorFactory);

         try {
            pc.setDefaultValues();
        } catch (ValidationException e) {
            showErrorDialog(e.getMessage());
        }

        for (Property property:pc.getProperties()) {
            PropertyDescriptor propertyDescriptor = property.getDescriptor();
            if (System.getProperty("mepixMode") != null && System.getProperty("mepixMode").equals("QWG")) {
                if (!propertyDescriptor.getName().startsWith(panelId)) {
                    removeProperty(pc, propertyDescriptor);
                }
            } else {
                if (!propertyDescriptor.getName().startsWith(panelId) ||
                     propertyDescriptor.getName().startsWith(panelId + "QWG")  ) {
                    removeProperty(pc, propertyDescriptor);
                }
            }
        }
        return pc;
    }

    private void removeProperty(final PropertyContainer propertyContainer, PropertyDescriptor propertyDescriptor) {
		Property property = propertyContainer.getProperty(propertyDescriptor.getName());
		if (property != null)
			propertyContainer.removeProperty(property);
	}

}
