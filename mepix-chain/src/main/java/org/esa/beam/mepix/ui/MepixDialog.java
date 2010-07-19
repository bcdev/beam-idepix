package org.esa.beam.mepix.ui;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyDescriptor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.mepix.operators.MepixConstants;
import org.esa.beam.mepix.util.MepixUtils;

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

    public static final int DIALOG_WIDTH = 650;
    public static final int DIALOG_HEIGHT = 420;
    private OperatorSpi operatorSpi;

    /**
     * MepixDialog constructor
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
        System.setProperty("gpfMode", "GUI");
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

    @Override
    protected boolean verifyUserInput() {
        final HashMap<String, Product> sourceProducts = form.createSourceProductsMap();
        Product sourceProduct = sourceProducts.get("source");
        if (sourceProduct == null) {
            showErrorDialog("No input product specified!");
            return false;
        } else {
            return MepixUtils.isInputValid(sourceProduct);
        }
    }

    ///////////// END OF PUBLIC //////////////

    private void initialize(String operatorName, AppContext appContext) {
        targetProductNameSuffix = "";

        operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName");
        }
        parameterMap = new LinkedHashMap<String, Object>(17);

        form = new MepixForm(appContext, operatorSpi, getTargetProductSelector(),
                             targetProductNameSuffix, parameterMap);
    }

}
