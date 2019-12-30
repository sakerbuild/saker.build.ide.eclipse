package saker.build.ide.eclipse.properties;

import java.util.LinkedHashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;

public class SakerBuildProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.sakerBuildProjectPropertyPage";

	private EclipseSakerIDEProject ideProject;

	private boolean requireIdeConfig;

	private Button requireIdeConfigButton;

	public SakerBuildProjectPropertyPage() {
		super();
	}

	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);
		ideProject = ImplActivator.getDefault().getOrCreateSakerProject(element.getAdapter(IProject.class));
		if (ideProject != null) {
			IDEProjectProperties ideprops = ideProject.getIDEProjectProperties();
			requireIdeConfig = ideprops.isRequireTaskIDEConfiguration();
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		if (this.ideProject == null) {
			noDefaultAndApplyButton();

			Label resultlabel = new Label(parent, SWT.NONE);
			resultlabel.setText("Project doesn't have saker.build nature associated with it.");
			//XXX create an add nature button?
			return resultlabel;
		}
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL);
		data.grabExcessHorizontalSpace = true;
		composite.setLayoutData(data);

		Label label = new Label(composite, SWT.NONE);
		label.setText("Saker.build project settings are available on the sub-pages.");

		requireIdeConfigButton = new Button(composite, SWT.CHECK);
		requireIdeConfigButton.setText("Require IDE configuration from build tasks.");

		populateControls();

		return composite;
	}

	private void populateControls() {
		requireIdeConfigButton.setSelection(this.requireIdeConfig);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		this.requireIdeConfig = true;

		populateControls();
	}

	@Override
	public boolean performOk() {
		this.requireIdeConfig = requireIdeConfigButton.getSelection();
		ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
				.setRequireTaskIDEConfiguration(this.requireIdeConfig).build());
		return true;
	}

}