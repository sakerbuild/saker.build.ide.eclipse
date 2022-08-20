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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.net.ssl.KeyManagerFactory;

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.SimpleIDEPluginProperties;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.ide.support.ui.ExceptionFormatSelector;
import saker.build.launching.LaunchConfigUtils;
import saker.build.meta.Versions;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.exc.ExceptionView;

public class SakerBuildEnvironmentPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private static final String AUTH_KEYSTORE_LABEL_TEXT_NONE = "(None)";

	private EclipseSakerIDEPlugin plugin;

	private IDEPluginProperties properties;
	private Combo exceptionFormatCombo;
	private ExceptionFormatSelector exceptionFormatSelector;

	private Button actsAsServerButton;
	private Text portText;
	private Button browseAuthKeyStoreButton;
	private Button clearAuthKeyStoreButton;

	private Label authKeyStorePathLabel;
	private Label authKeyStoreLabel;

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
		dgcomposite.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(dgcomposite);

		SakerBuildProjectPropertyPage.addLabelWithText(dgcomposite, "Port number:");
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
		actsAsServerButton.addSelectionListener(new SelectionListener() {
			private void handleChange() {
				String keystorepath = getAuthKeyStorePathFromUI();

				clearAuthKeyStoreButton.setEnabled(keystorepath != null);
				validateProperties();
			}

			@Override
			public void widgetSelected(SelectionEvent e) {
				handleChange();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				handleChange();
			}
		});
		SakerBuildProjectPropertyPage.addLabelWithText(dgcomposite,
				"The default port is " + DaemonLaunchParameters.DEFAULT_PORT
						+ ", leave as empty to run in private mode.\n" + "Set 0 to use an OS determined port.");

		authKeyStoreLabel = SakerBuildProjectPropertyPage.addLabelWithText(dgcomposite, "Authentication keystore: ");

		Composite keystorecomposite = new Composite(dgcomposite, SWT.NONE);
		keystorecomposite.setLayout(new GridLayout(3, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(keystorecomposite);

		authKeyStorePathLabel = new Label(keystorecomposite, SWT.NONE);
		authKeyStorePathLabel.setLayoutData(GridDataFactory.fillDefaults().hint(SWT.DEFAULT, SWT.DEFAULT)
				.align(SWT.FILL, SWT.CENTER).grab(true, false).create());

		browseAuthKeyStoreButton = new Button(keystorecomposite, SWT.PUSH);
		browseAuthKeyStoreButton.setText("Browse...");
		browseAuthKeyStoreButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(event -> {
			Path selected = performKeyStoreSelection();
			if (selected == null) {
				return;
			}
			authKeyStorePathLabel.setText(selected.toString());
			authKeyStorePathLabel.setEnabled(true);
			clearAuthKeyStoreButton.setEnabled(true);
			validateProperties();
		}));

		clearAuthKeyStoreButton = new Button(keystorecomposite, SWT.PUSH);
		clearAuthKeyStoreButton.setText("Clear");
		clearAuthKeyStoreButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(event -> {
			authKeyStorePathLabel.setText(AUTH_KEYSTORE_LABEL_TEXT_NONE);
			clearAuthKeyStoreButton.setEnabled(false);
			authKeyStorePathLabel.setEnabled(false);
			validateProperties();
		}));

		populateControls();

		validateProperties();

		return composite;
	}

	private Path performKeyStoreSelection() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText("Open keystore");
		dialog.setFilterExtensions(new String[] { "*.pfx;*.p12;*.jks", "*.*" });
		String selected = dialog.open();
		if (selected == null) {
			return null;
		}
		System.out.println("SakerBuildEnvironmentPreferencePage.createContents() " + selected);
		Path selectedpath;
		try {
			selectedpath = Paths.get(selected).toAbsolutePath().normalize();
		} catch (Exception e) {
			//if any parsing error happens
			ErrorDialog.openError(getShell(), "Path selection failed", null, EclipseSakerIDEPlugin
					.createDisplayExceptionStatus(SakerLog.SEVERITY_ERROR, "Failed to parse path: " + selected, e));
			return null;
		}

		//we expect that the key password will be the same as the store password, as per the general recommendation
		//that these two equal
		String storepass = null;
		try {
			storepass = EclipseSakerIDEPlugin.getKeyStorePassword(selectedpath);
		} catch (StorageException e) {
			plugin.displayException(SakerLog.SEVERITY_WARNING,
					"Failed to retrieve keystore password from secure storage for: " + selectedpath, e);
		}
		boolean first = true;
		while (true) {
			char[][] inoutkspass;
			boolean wasfirst = first;
			if (first) {
				//always try to guess as the first attempt
				inoutkspass = new char[][] { storepass == null ? null : storepass.toCharArray(), null };
			} else {
				inoutkspass = new char[][] { storepass == null ? null : storepass.toCharArray() };
			}

			first = false;

			KeyStore keystore;
			try {
				keystore = LaunchConfigUtils.openKeystore(selectedpath, inoutkspass);
			} catch (KeyStoreException e) {
				String errortext;
				if (storepass == null || wasfirst) {
					errortext = null;
				} else {
					StringBuilder sb = new StringBuilder("Incorrect password, exception:\n");
					SakerLog.printFormatException(ExceptionView.create(e), sb, CommonExceptionFormat.NO_TRACE);
					errortext = sb.toString().replace("\r", ""); // avoid \r\n as that displays as double new line
				}
				KeyStorePasswordDialog ksdialog = new KeyStorePasswordDialog(getShell(), "Keystore password",
						"Enter keystore password for: " + selectedpath, "Password:", errortext);
				if (ksdialog.open() == IDialogConstants.OK_ID) {
					storepass = ksdialog.getResult();
					continue;
				}
				return null;
			} catch (Exception e) {
				ErrorDialog.openError(getShell(), "Keystore failure", null,
						EclipseSakerIDEPlugin.createDisplayExceptionStatus(SakerLog.SEVERITY_ERROR,
								"Failed to open keystore: " + selected, e));
				return null;
			}
			KeyManagerFactory kmfactory;
			try {
				kmfactory = LaunchConfigUtils.openKeyManagerFactory(keystore, selectedpath, inoutkspass[0], null,
						KeyStoreException::new);
			} catch (Exception e) {
				ErrorDialog.openError(getShell(), "Keystore failure", null,
						EclipseSakerIDEPlugin.createDisplayExceptionStatus(SakerLog.SEVERITY_ERROR,
								"Failed to create Key manager factory for keystore: " + selected, e));
				return null;
			}
			try {
				LaunchConfigUtils.createSSLContext(selectedpath, keystore, kmfactory,
						EclipseSakerIDEPlugin::createExceptionForKeyStoreOpening);
			} catch (Exception e) {
				ErrorDialog.openError(getShell(), "SSL context failure", null,
						EclipseSakerIDEPlugin.createDisplayExceptionStatus(SakerLog.SEVERITY_ERROR,
								"Failed to create SSL context for keystore: " + selected, e));
				return null;
			}

			if (storepass != null) {
				try {
					EclipseSakerIDEPlugin.writeKeyStorePassword(selectedpath, storepass);
				} catch (StorageException | IOException e) {
					ErrorDialog.openError(getShell(), "Secure storage failure", null,
							EclipseSakerIDEPlugin.createDisplayExceptionStatus(SakerLog.SEVERITY_ERROR,
									"Failed to store password for keystore: " + selected, e));
					return null;
				}
			}
			//return a normalized string representation
			return selectedpath;
		}
	}

	private void populateControls() {
		portText.setText(ObjectUtils.nullDefault(properties.getPort(), ""));
		boolean actsasserver = SakerIDESupportUtils.getBooleanValueOrDefault(properties.getActsAsServer(), false);
		actsAsServerButton.setSelection(actsasserver);

		exceptionFormatSelector.reset(properties);

		exceptionFormatCombo.setItems(exceptionFormatSelector.getLabels().toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		exceptionFormatCombo.select(exceptionFormatSelector.getSelectedIndex());

		String authKeyStorePath = properties.getKeyStorePath();
		if (authKeyStorePath == null) {
			authKeyStorePathLabel.setText(AUTH_KEYSTORE_LABEL_TEXT_NONE);
			authKeyStorePathLabel.setEnabled(false);
		} else {
			authKeyStorePathLabel.setText(authKeyStorePath);
			authKeyStorePathLabel.setEnabled(true);
		}

		clearAuthKeyStoreButton.setEnabled(authKeyStorePath != null);
	}

	private void validateProperties() {
		String portstr = portText.getText().trim();
		boolean hasport = !ObjectUtils.isNullOrEmpty(portstr);
		if (hasport) {
			if (SakerIDESupportUtils.getPortValueOrNull(portstr) == null) {
				invalidateWithErrorMessage("Port number not in range: " + portstr + " for 0 and " + 0xFFFF);
				return;
			}
		}
		boolean actsasserver = actsAsServerButton.getSelection();
		if (actsasserver && !hasport) {
			invalidateWithErrorMessage("Set a port number to to use as server.");
			return;
		}
		String authkeystore = getAuthKeyStorePathFromUI();
		if (!ObjectUtils.isNullOrEmpty(authkeystore)) {
			try {
				Path path = Paths.get(authkeystore).toAbsolutePath().normalize();

				LaunchConfigUtils.createSSLContext(path,
						ImmutableUtils.asUnmodifiableArrayList(null, EclipseSakerIDEPlugin.getKeyStorePassword(path)),
						null, EclipseSakerIDEPlugin::createExceptionForKeyStoreOpening);
			} catch (Exception e) {
				invalidateWithErrorMessage("Failed to open keystore for daemon server: " + authkeystore);
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

	private String getAuthKeyStorePathFromUI() {
		String val = authKeyStorePathLabel.getText();
		if (AUTH_KEYSTORE_LABEL_TEXT_NONE.equals(val)) {
			//a boolean flag for this somewhere would be more clean, but we don't expect 
			//the user to set a keystore with the path of (None)
			//so this is fine
			return null;
		}
		return val;
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();

		properties = SimpleIDEPluginProperties.builder(properties).setActsAsServer((String) null)
				.setExceptionFormat((String) null).setPort((String) null).setKeyStorePath(null).build();

		populateControls();
		validateProperties();
	}

	@Override
	public boolean performOk() {
		SimpleIDEPluginProperties.Builder builder = SimpleIDEPluginProperties.builder(plugin.getIDEPluginProperties())
				.setPort(portText.getText().trim()).setActsAsServer(actsAsServerButton.getSelection())
				.setExceptionFormat(exceptionFormatSelector.getSelectedFormat())
				.setKeyStorePath(getAuthKeyStorePathFromUI());
		try {
			plugin.setIDEPluginProperties(builder.build(), plugin.getEnvironmentParameterContributors());
		} catch (IOException e) {
			plugin.displayException(SakerLog.SEVERITY_ERROR, "Failed to save plugin properties.", e);
			return false;
		}
		return true;
	}

	private static class KeyStorePasswordDialog extends TitleAreaDialog {
		private Text secretText;
		private String title;
		private String message;
		private String prompt;
		private String errorText;

		private String result;

		public KeyStorePasswordDialog(Shell activeShell, String titleinfo, String message, String prompt,
				String errortext) {
			super(activeShell);
			this.title = titleinfo;
			this.message = message;
			this.prompt = prompt;
			this.errorText = errortext;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText(title);
		}

		public String getResult() {
			return result;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite result = (Composite) super.createDialogArea(parent);
			if (title != null) {
				setTitle(title);
			}
			if (message != null) {
				setMessage(message, errorText == null ? IMessageProvider.INFORMATION : IMessageProvider.ERROR);
			}
			Composite composite = new Composite(result, SWT.NONE);
			composite.setLayoutData(
					GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).create());
			GridLayout compositegridlayout = new GridLayout(prompt == null ? 1 : 2, false);
			compositegridlayout.marginWidth = 20;
			composite.setLayout(compositegridlayout);
			if (prompt != null) {
				Label promptlabel = new Label(composite, SWT.NONE);
				promptlabel.setText(prompt);
			}
			secretText = new Text(composite, SWT.BORDER);
			secretText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
			secretText.setEchoChar('*');
			secretText.setMessage("Keystore password");

			Label infolabel = new Label(composite, SWT.NONE);
			infolabel.setText("The password will be stored in the Secure Storage.");
			GridDataFactory.fillDefaults().span(compositegridlayout.numColumns, 1).applyTo(infolabel);

			if (errorText != null) {
				Label errorlabel = new Label(composite, SWT.NONE);
				errorlabel.setText(errorText);
				GridDataFactory.fillDefaults().span(compositegridlayout.numColumns, 1).applyTo(errorlabel);
			}
			return result;
		}

		@Override
		protected void okPressed() {
			result = secretText.getText();
			super.okPressed();
		}
	}

}