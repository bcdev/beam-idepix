package org.esa.beam.idepix.ui;


import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.idepix.operators.IdepixConstants;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.util.Map;

/**
 * Idepix input form represented by a customized JTabbedPane
 *
 * @author Olaf Danne
 * @version $Revision: 7464 $ $Date: 2009-12-11 17:18:25 +0100 (Fr, 11 Dez 2009) $
 */
class IdepixForm extends JTabbedPane {

    private OperatorSpi operatorSpi;
    private Map<String, Object> parameterMap;

    public IdepixForm(OperatorSpi operatorSpi, Map<String, Object> parameterMap) {
        this.operatorSpi = operatorSpi;
        this.parameterMap = parameterMap;
    }

    public void initialize() {
        final PropertyContainer propertyContainerCloudscreening =
                createPanelSpecificValueContainer(IdepixConstants.cloudScreeningParameterNames);
        addParameterPane(propertyContainerCloudscreening, "Cloud Screening");

        // define new value containers for distribution of the target products to three different tab panes.
        final PropertyContainer propertyContainerIpf =
                createPanelSpecificValueContainer(IdepixConstants.ipfParameterNames);
        final PropertyContainer propertyContainerPressure =
                createPanelSpecificValueContainer(IdepixConstants.pressureParameterNames);

        addParameterPane(propertyContainerIpf, "IPF Compatible Products");
        addParameterPane(propertyContainerPressure, "Pressure Products");

        final PropertyContainer propertyContainerCloud =
                createPanelSpecificValueContainer(IdepixConstants.cloudProductParameterNames);
        addParameterPane(propertyContainerCloud, "Cloud Products");

        final PropertyContainer propertyContainerGlobalbedo=
                createPanelSpecificValueContainer(IdepixConstants.globalbedoParameterNames);
        addParameterPane(propertyContainerGlobalbedo, "GlobAlbedo");

        final PropertyContainer propertyContainerCoastcolour=
                createPanelSpecificValueContainer(IdepixConstants.coastcolourParameterNames);
        addParameterPane(propertyContainerCoastcolour, "CoastColour");
        setEnabledAt(IdepixConstants.COASTCOLOUR_TAB_INDEX, false); // todo: enable later
    }

     ///////////// END OF PUBLIC //////////////

    private void addParameterPane(PropertyContainer propertyContainer, String title) {

        BindingContext context = new BindingContext(propertyContainer);

        PropertyPane parametersPane = new PropertyPane(context);
        JPanel paremetersPanel = parametersPane.createPanel();
        paremetersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        final Component component1 = this.add(title, new JScrollPane(paremetersPanel));
    }

    private PropertyContainer createPanelSpecificValueContainer(String[] thisPanelIDs) {
        ParameterDescriptorFactory parameterDescriptorFactory = new ParameterDescriptorFactory();
        PropertyContainer pc = PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(),
                                                                 parameterDescriptorFactory);

        try {
            pc.setDefaultValues();
        } catch (ValidationException e) {
            JOptionPane.showOptionDialog(null, e.getMessage(), "IDEPIX - Error Message", JOptionPane.DEFAULT_OPTION,
                                             JOptionPane.ERROR_MESSAGE, null, null, null);
        }

        for (Property property : pc.getProperties()) {
            PropertyDescriptor propertyDescriptor = property.getDescriptor();
                if (!panelContainsProperty(propertyDescriptor.getName(), thisPanelIDs)) {
                    removeProperty(pc, propertyDescriptor);
                }
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
}
