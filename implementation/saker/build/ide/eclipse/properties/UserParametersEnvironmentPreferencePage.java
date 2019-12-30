package saker.build.ide.eclipse.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.ContributedExtensionConfiguration;
import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.eclipse.extension.params.IEnvironmentUserParameterContributor;
import saker.build.ide.eclipse.properties.StringMapTableHandler.ExtensionProvidedEntry;
import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SimpleIDEPluginProperties;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class UserParametersEnvironmentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private EclipseSakerIDEPlugin plugin;
	private Set<? extends Entry<String, String>> userParameters = null;

	private StringMapTableHandler tableHandler;
	private ExtensionPointsHandler<IEnvironmentUserParameterContributor> extensionHandler;
	private TabFolder tabFolder;
	private TabItem parametersTabItem;
	private TabItem extensionsTabItem;

	private List<ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> extensionContributors = Collections
			.emptyList();

	public UserParametersEnvironmentPreferencePage() {
		super();
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		updateApplyButton();
	}

	@Override
	public void init(IWorkbench workbench) {
		plugin = ImplActivator.getDefault().getEclipseIDEPlugin();
		IDEPluginProperties props = plugin.getIDEPluginProperties();
		if (props != null) {
			this.userParameters = props.getUserParameters();
		}
		extensionContributors = ObjectUtils.newArrayList(plugin.getEnvironmentParameterContributors());
	}

	@Override
	protected Control createContents(Composite parent) {
		tabFolder = new TabFolder(parent, SWT.NONE);
		GridData grabfillgriddata = new GridData();
		grabfillgriddata.grabExcessHorizontalSpace = true;
		grabfillgriddata.grabExcessVerticalSpace = true;
		grabfillgriddata.horizontalAlignment = GridData.FILL;
		grabfillgriddata.verticalAlignment = GridData.FILL;
		tabFolder.setLayoutData(grabfillgriddata);

		{
			parametersTabItem = new TabItem(tabFolder, SWT.NONE);
			parametersTabItem.setText("Parameters");

			Composite parameterscomposite = new Composite(tabFolder, SWT.NONE);
			parameterscomposite.setLayout(new GridLayout());

			GridData data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = GridData.FILL;
			data.verticalAlignment = GridData.FILL;
			parameterscomposite.setLayoutData(data);

			tableHandler = new StringMapTableHandler();
			tableHandler.setAddButtonLabel("Add user parameter...");
			tableHandler.setKeyValDialogTitle("Environment user parameter");
			tableHandler.setKeyValDialogBaseMessage("Enter an environment user parameter for your build (-E option).");
			tableHandler.addControl(parameterscomposite);
			tableHandler.setOptions(userParameters);
			tableHandler.addModifyListener(ev -> {
				tableHandler.setExtensionEntries(getCurrentExtensionEntries());
				validateProperties();
			});

			parametersTabItem.setControl(parameterscomposite);
		}
		{
			extensionsTabItem = new TabItem(tabFolder, SWT.NONE);
			extensionsTabItem.setText("Extensions");
			extensionsTabItem.setImage(Activator.IMAGE_EXT_POINT);

			Composite extensionscomposite = new Composite(tabFolder, SWT.NONE);
			extensionscomposite.setLayout(new GridLayout());
			extensionHandler = new ExtensionPointsHandler<>();
			extensionHandler.setNoExtensionsText("No environment user parameter extensions installed.");
			extensionHandler.setExtensionContributors(extensionContributors);
			extensionHandler.addControl(extensionscomposite);
			extensionHandler.addModifyListener(ev -> {
				tableHandler.setExtensionEntries(getCurrentExtensionEntries());
				validateProperties();
			});

			extensionsTabItem.setControl(extensionscomposite);
		}

		tableHandler.setExtensionEntries(getCurrentExtensionEntries());

		validateProperties();
		return tabFolder;
	}

	private List<ExtensionProvidedEntry> getCurrentExtensionEntries() {
		Map<String, String> userentries = SakerIDEPlugin.entrySetToMap(tableHandler.getOptions());
		NavigableMap<String, String> currententries = plugin.getUserParametersWithContributors(userentries,
				extensionHandler.getExtensionContributors(), null);
		List<ExtensionProvidedEntry> result = new ArrayList<>();
		for (Entry<String, String> entry : currententries.entrySet()) {
			if (Objects.equals(userentries.get(entry.getKey()), entry.getValue())) {
				//the contributors haven't changed the given entry.
				continue;
			}
			//TODO set contributors
			result.add(new ExtensionProvidedEntry(entry.getKey(), entry.getValue(), Collections.emptyList()));
		}
		return result;
	}

	private void validateProperties() {
		PropertiesValidationErrorResult validation = tableHandler.validate();
		if (validation != null) {
			switch (validation.errorType) {
				case StringMapTableHandler.E_DUPLICATE_KEY: {
					invalidateWithErrorMessage("Duplicate user parameter: " + validation.relatedSubject);
					return;
				}
				case StringMapTableHandler.E_EMPTY_KEY: {
					invalidateWithErrorMessage("Emty user parameter name.");
					return;
				}
				default: {
					break;
				}
			}
		}
		setValidProperties();
	}

	private void setValidProperties() {
		setValid(true);
		setErrorMessage(null);
	}

	private void invalidateWithErrorMessage(String message) {
		setErrorMessage(message);
		setValid(false);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		this.userParameters = null;
		tableHandler.setOptions(userParameters);

		extensionHandler.enableAll();
	}

	@Override
	public boolean performOk() {
		plugin.setIDEPluginProperties(
				SimpleIDEPluginProperties.builder(plugin.getIDEPluginProperties())
						.setUserParameters(tableHandler.getOptions()).build(),
				extensionHandler.getExtensionContributors());
		return true;
	}

}