package saker.build.ide.eclipse.properties;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.ImplActivator;

public class SakerBuildEnvironmentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private EclipseSakerIDEPlugin plugin;

	public SakerBuildEnvironmentPreferencePage() {
		super();
	}

	@Override
	public void init(IWorkbench workbench) {
		plugin = ImplActivator.getDefault().getEclipseIDEPlugin();
	}

	@Override
	protected Control createContents(Composite parent) {
		noDefaultAndApplyButton();

		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		Label label = new Label(composite, SWT.NONE);
		label.setText("Saker.build plugin settings are available on the sub-pages.");

		return composite;
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		return true;
	}

}