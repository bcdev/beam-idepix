package org.esa.beam.idepix.ui;


import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.Map;

/**
 * Idepix input form represented by a customized JTabbedPane
 *
 * @author Olaf Danne
 */
class IdepixForm extends JTabbedPane {

    private OperatorSpi operatorSpi;
    private Map<String, Object> parameterMap;

    public IdepixForm(OperatorSpi operatorSpi, Map<String, Object> parameterMap) {
        this.operatorSpi = operatorSpi;
        this.parameterMap = parameterMap;
    }

    public void initialize() {

        final PropertyContainer propertyContainer =
                PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(),
                                                  new ParameterDescriptorFactory());
        addParameterPane(propertyContainer, "Processing Parameters");
    }

    ///////////// END OF PUBLIC //////////////

    private void addParameterPane(PropertyContainer propertyContainer, String title) {

        BindingContext context = new BindingContext(propertyContainer);

        PropertyPane parametersPane = new PropertyPane(context);
        JPanel parametersPanel = parametersPane.createPanel();
        parametersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        this.add(title, new JScrollPane(parametersPanel));
    }

}
