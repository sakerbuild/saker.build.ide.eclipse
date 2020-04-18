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
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SimpleIDEPluginProperties;
import saker.build.ide.support.ui.ExceptionFormatSelector;
import saker.build.meta.Versions;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerBuildEnvironmentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private EclipseSakerIDEPlugin plugin;

	private Combo exceptionFormatCombo;
	private ExceptionFormatSelector exceptionFormatSelector;

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

		exceptionFormatSelector = new ExceptionFormatSelector(plugin.getIDEPluginProperties());

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
		GridLayout buildtracelayout = new GridLayout(2, false);
		efcomposite.setLayout(buildtracelayout);

		efcomposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		SakerBuildProjectPropertyPage.addLabelWithText(efcomposite, "Build exception display format:");

		exceptionFormatCombo = new Combo(efcomposite, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		exceptionFormatCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
			exceptionFormatSelector.setSelectedIndex(exceptionFormatCombo.getSelectionIndex());
		}));

		updateFormatComboItems();

		return composite;
	}

	private void updateFormatComboItems() {
		exceptionFormatCombo.setItems(exceptionFormatSelector.getLabels().toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		exceptionFormatCombo.select(exceptionFormatSelector.getSelectedIndex());
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();

		exceptionFormatSelector.reset(null);
		updateFormatComboItems();
	}

	@Override
	public boolean performOk() {
		plugin.setIDEPluginProperties(
				SimpleIDEPluginProperties.builder(plugin.getIDEPluginProperties())
						.setExceptionFormat(exceptionFormatSelector.getSelectedFormat()).build(),
				plugin.getEnvironmentParameterContributors());
		return true;
	}

}