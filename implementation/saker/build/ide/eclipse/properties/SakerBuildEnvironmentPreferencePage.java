/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.ide.eclipse.properties;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.SimpleIDEPluginProperties;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.ide.support.ui.ExceptionFormatSelector;
import saker.build.meta.Versions;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerBuildEnvironmentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private EclipseSakerIDEPlugin plugin;

	private IDEPluginProperties properties;
	private Combo exceptionFormatCombo;
	private ExceptionFormatSelector exceptionFormatSelector;

	private Button actsAsServerButton;
	private Text portText;

	public SakerBuildEnvironmentPreferencePage() {
		super();
	}

	@Override
	public void init(IWorkbench workbench) {
		plugin = ImplActivator.getDefault().getEclipseIDEPlugin();
		properties = plugin.getIDEPluginProperties();
	}

	@Override
	protected Control createContents(Composite parent) {
		noDefaultAndApplyButton();

		exceptionFormatSelector = new ExceptionFormatSelector(properties);

		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		new Label(composite, SWT.NONE).setText("General settings for the saker.build plugin.");
		new Label(composite, SWT.NONE).setText("Saker.build system version: " + Versions.VERSION_STRING_FULL);

		Group exeptionformatgroup = new Group(composite, SWT.NONE);
		exeptionformatgroup.setLayout(new GridLayout(1, false));
		exeptionformatgroup.setLayoutData(
				GridDataFactory.swtDefaults().grab(true, false).align(GridData.FILL, GridData.CENTER).create());
		exeptionformatgroup.setText("Exception format");

		Composite efcomposite = new Composite(exeptionformatgroup, SWT.NONE);
		GridLayout eflayout = new GridLayout(2, false);
		efcomposite.setLayout(eflayout);

		efcomposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		SakerBuildProjectPropertyPage.addLabelWithText(efcomposite, "Build exception display format:");

		exceptionFormatCombo = new Combo(efcomposite, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		exceptionFormatCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
			exceptionFormatSelector.setSelectedIndex(exceptionFormatCombo.getSelectionIndex());
		}));

		Group daemongroup = new Group(composite, SWT.NONE);
		daemongroup.setLayout(new GridLayout(1, false));
		daemongroup.setLayoutData(
				GridDataFactory.swtDefaults().grab(true, false).align(GridData.FILL, GridData.CENTER).create());
		daemongroup.setText("Build daemon");

		Composite dgcomposite = new Composite(daemongroup, SWT.NONE);
		GridLayout dglayout = new GridLayout(2, false);
		dgcomposite.setLayout(dglayout);

		dgcomposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		SakerBuildProjectPropertyPage.addLabelWithText(dgcomposite, "Port number (default 3500):");
		portText = new Text(dgcomposite, SWT.BORDER);
		portText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				validateProperties();
			}
		});
		portText.setMessage("Port number 0-65535");
		GridDataFactory.defaultsFor(portText).hint(100, SWT.DEFAULT).grab(false, false).applyTo(portText);
		actsAsServerButton = new Button(dgcomposite, SWT.CHECK);
		actsAsServerButton.setText("Acts as server");

		populateControls();

		validateProperties();

		return composite;
	}

	private void populateControls() {
		portText.setText(ObjectUtils.nullDefault(properties.getPort(), ""));
		actsAsServerButton
				.setSelection(SakerIDESupportUtils.getBooleanValueOrDefault(properties.getActsAsServer(), false));

		exceptionFormatSelector.reset(properties);

		exceptionFormatCombo.setItems(exceptionFormatSelector.getLabels().toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		exceptionFormatCombo.select(exceptionFormatSelector.getSelectedIndex());
	}

	private void validateProperties() {
		String portstr = portText.getText().trim();
		if (!ObjectUtils.isNullOrEmpty(portstr)) {
			if (SakerIDESupportUtils.getPortValueOrNull(portstr) == null) {
				invalidateWithErrorMessage("Port number not in range: " + portstr + " for 0 and " + 0xFFFF);
				return;
			}
		}
		setValidProperties();
	}

	private void invalidateWithErrorMessage(String message) {
		setErrorMessage(message);
		setValid(false);
	}

	private void setValidProperties() {
		setValid(true);
		setErrorMessage(null);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();

		properties = SimpleIDEPluginProperties.builder(properties).setActsAsServer((String) null)
				.setExceptionFormat((String) null).setPort((String) null).build();

		populateControls();
		validateProperties();
	}

	@Override
	public boolean performOk() {
		plugin.setIDEPluginProperties(
				SimpleIDEPluginProperties.builder(plugin.getIDEPluginProperties()).setPort(portText.getText().trim())
						.setActsAsServer(actsAsServerButton.getSelection())
						.setExceptionFormat(exceptionFormatSelector.getSelectedFormat()).build(),
				plugin.getEnvironmentParameterContributors());
		return true;
	}

}