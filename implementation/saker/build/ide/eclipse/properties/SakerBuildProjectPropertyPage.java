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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.file.path.SakerPath;
import saker.build.ide.eclipse.EclipseSakerIDEPlugin;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class SakerBuildProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.sakerBuildProjectPropertyPage";

	private static final int RETURN_CODE_REMOVE = 2;

	private EclipseSakerIDEProject ideProject;

	private boolean requireIdeConfig;
	private MountPathIDEProperty buildTraceOutput;
	private boolean embedBuildTraceArtifacts;

	private Button requireIdeConfigButton;
	private Label buildTraceOutValueLabel;
	private Button embedBuildTraceArtifactsButton;

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
			buildTraceOutput = ideprops.getBuildTraceOutput();
			embedBuildTraceArtifacts = ideprops.isBuildTraceEmbedArtifacts();
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
		composite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		addLabelWithText(composite, "Saker.build project settings are available on the sub-pages.");

		requireIdeConfigButton = new Button(composite, SWT.CHECK);
		requireIdeConfigButton.setText("Require IDE configuration from build tasks.");

		Group buildtracegroup = new Group(composite, SWT.NONE);
		buildtracegroup.setLayout(new GridLayout(1, false));
		buildtracegroup.setLayoutData(
				GridDataFactory.swtDefaults().grab(true, false).align(GridData.FILL, GridData.CENTER).create());
		buildtracegroup.setText("Build trace");

		Composite btcomposite = new Composite(buildtracegroup, SWT.NONE);
		GridLayout buildtracelayout = new GridLayout(3, false);
		btcomposite.setLayout(buildtracelayout);

		btcomposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		addLabelWithText(btcomposite, "Build trace output: ");

		buildTraceOutValueLabel = new Label(btcomposite, SWT.NONE);
		buildTraceOutValueLabel.setLayoutData(GridDataFactory.fillDefaults().hint(200, SWT.DEFAULT)
				.align(SWT.FILL, SWT.CENTER).grab(false, false).create());

		Button modifybtoutbutton = new Button(btcomposite, SWT.PUSH);
		modifybtoutbutton.setText("Modify...");
		modifybtoutbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			BuildTraceOutputDialog dialog = new BuildTraceOutputDialog(getShell(),
					buildTraceOutput == null ? null : buildTraceOutput.getMountClientName(),
					buildTraceOutput == null ? null : buildTraceOutput.getMountPath());
			dialog.create();
			int rc = dialog.open();
			if (rc == Window.OK) {
				buildTraceOutput = MountPathIDEProperty.create(dialog.getConnectionName(), dialog.getMountPath());
				populateControls();
			} else if (rc == RETURN_CODE_REMOVE) {
				this.buildTraceOutput = null;
				populateControls();
			}
		}));

		embedBuildTraceArtifactsButton = new Button(buildtracegroup, SWT.CHECK);
		embedBuildTraceArtifactsButton.setText("Embed output artifacts.");

		populateControls();

		//TODO perform property validation

		return composite;
	}

	private static void addLabelWithText(Composite composite, String text) {
		Label label = new Label(composite, SWT.NONE);
		label.setText(text);
	}

	private void populateControls() {
		requireIdeConfigButton.setSelection(this.requireIdeConfig);
		String btcname;
		String btmountpath;
		if (this.buildTraceOutput == null
				|| ObjectUtils.isNullOrEmpty(btcname = this.buildTraceOutput.getMountClientName())
				|| ObjectUtils.isNullOrEmpty(btmountpath = this.buildTraceOutput.getMountPath())) {
			buildTraceOutValueLabel.setText("No build trace is generated.");
		} else {
			Set<? extends DaemonConnectionIDEProperty> connections = ideProject.getIDEProjectProperties()
					.getConnections();
			String readablecname = PathConfigurationProjectPropertyPage.getReadableClientName(connections, btcname);

			buildTraceOutValueLabel.setText(readablecname + " : " + Objects.toString(btmountpath, "<missing>"));
		}
		embedBuildTraceArtifactsButton.setSelection(this.embedBuildTraceArtifacts);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		this.requireIdeConfig = true;
		this.buildTraceOutput = null;

		populateControls();
	}

	@Override
	public boolean performOk() {
		this.requireIdeConfig = requireIdeConfigButton.getSelection();
		this.embedBuildTraceArtifacts = embedBuildTraceArtifactsButton.getSelection();
		ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
				.setRequireTaskIDEConfiguration(this.requireIdeConfig).setBuildTraceOutput(buildTraceOutput)
				.setBuildTraceEmbedArtifacts(embedBuildTraceArtifacts).build());
		return true;
	}

	private class BuildTraceOutputDialog extends TitleAreaDialog {
		private Combo connectionCombo;
		private Text mountPathText;

		private String connectionName;
		private String mountPath;
		private List<String> itemEndpointNames;

		public BuildTraceOutputDialog(Shell parentShell, String connectionName, String mountPath) {
			super(parentShell);
			this.connectionName = connectionName;
			this.mountPath = mountPath;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Build trace output");
		}

		@Override
		public void create() {
			super.create();
			setTitle("Build trace output");
			resetMessage();
		}

		private void resetMessage() {
			setMessage("Specify the endpoint and output path for the build trace.", IMessageProvider.NONE);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout layout = new GridLayout(2, false);
			container.setLayout(layout);

			createConnectionCombo(container);
			createMountPathText(container);

			Listener modifylistener = event -> {
				updateOkButton(getButton(IDialogConstants.OK_ID));
			};
			mountPathText.addListener(SWT.Modify, modifylistener);
			connectionCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
				modifylistener.handleEvent(null);
			}));

			return area;
		}

		private void updateOkButton(Button okbutton) {
			if (okbutton == null) {
				resetMessage();
				return;
			}
			int idx = connectionCombo.getSelectionIndex();
			if (idx < 0) {
				resetMessage();
				okbutton.setEnabled(false);
				return;
			}
			String selectedconn = itemEndpointNames.get(idx);
			if (!SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(selectedconn)
					&& !SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(selectedconn)
					&& PathConfigurationProjectPropertyPage.getConnectionPropertyWithName(
							ideProject.getIDEProjectProperties().getConnections(), selectedconn) == null) {
				setMessage("Connection endpoint not found: " + selectedconn, IMessageProvider.ERROR);
				okbutton.setEnabled(false);
				return;
			}
			String mountpathstr = mountPathText.getText();
			if (!mountpathstr.isEmpty()) {
				SakerPath mountpath;
				try {
					mountpath = SakerPath.valueOf(mountpathstr);
				} catch (IllegalArgumentException e) {
					setMessage("Invalid output path format: " + mountpathstr, IMessageProvider.ERROR);
					okbutton.setEnabled(false);
					return;
				}
				if (mountpath.isRelative()) {
					if (!SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(selectedconn)) {
						//a relative path was specifed, but not against the project relative connection
						setMessage("Output path must be absolute: " + mountpathstr, IMessageProvider.ERROR);
						okbutton.setEnabled(false);
						return;
					}
				}
				if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(selectedconn)) {
					if (mountpath.isAbsolute() && !SakerPath.ROOT_SLASH.equals(mountpath.getRoot())) {
						//if the path is project relative, only the slash root is allowed
						setMessage("Only the / root is allowed for project relative path: " + mountpathstr,
								IMessageProvider.ERROR);
						okbutton.setEnabled(false);
						return;
					}
				}
			}
			if (mountpathstr.isEmpty()) {
				resetMessage();
				okbutton.setEnabled(false);
				return;
			}
			resetMessage();
			okbutton.setEnabled(true);
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			createButton(parent, IDialogConstants.NO_ID, "Remove", false);
			super.createButtonsForButtonBar(parent);
		}

		@Override
		protected void buttonPressed(int buttonId) {
			if (buttonId == IDialogConstants.NO_ID) {
				setReturnCode(RETURN_CODE_REMOVE);
				close();
				return;
			}
			super.buttonPressed(buttonId);
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			Button result = super.createButton(parent, id, label, defaultButton);
			if (id == IDialogConstants.OK_ID) {
				updateOkButton(result);
			}
			return result;
		}

		private void createMountPathText(Composite container) {
			Label lbtLastName = new Label(container, SWT.NONE);
			lbtLastName.setText("Output path");

			GridData dataLastName = new GridData();
			dataLastName.grabExcessHorizontalSpace = true;
			dataLastName.horizontalAlignment = GridData.FILL;
			mountPathText = new Text(container, SWT.BORDER);
			mountPathText.setLayoutData(dataLastName);
			mountPathText.setText(Objects.toString(mountPath, ""));
			mountPathText.setMessage("Output file path");
		}

		private void createConnectionCombo(Composite container) {
			Label lbtFirstName = new Label(container, SWT.NONE);
			lbtFirstName.setText("File system endpoint");

			GridData dataFirstName = new GridData();
			dataFirstName.grabExcessHorizontalSpace = true;
			dataFirstName.horizontalAlignment = GridData.FILL;

			itemEndpointNames = new ArrayList<>();
			List<String> items = new ArrayList<>();
			items.add(PathConfigurationProjectPropertyPage.LABEL_PROJECT_RELATIVE);
			itemEndpointNames.add(SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE);
			items.add(PathConfigurationProjectPropertyPage.LABEL_LOCAL_FILESYSTEM);
			itemEndpointNames.add(SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM);
			Set<? extends DaemonConnectionIDEProperty> connectionsprop = ideProject.getIDEProjectProperties()
					.getConnections();
			if (!ObjectUtils.isNullOrEmpty(connectionsprop)) {
				ArrayList<? extends DaemonConnectionIDEProperty> copylist = new ArrayList<>(connectionsprop);
				copylist.sort(
						(l, r) -> StringUtils.compareStringsNullFirst(l.getConnectionName(), r.getConnectionName()));
				for (DaemonConnectionIDEProperty connprop : copylist) {
					String connname = connprop.getConnectionName();
					if (ObjectUtils.isNullOrEmpty(connname)) {
						continue;
					}
					items.add(connname + " @" + connprop.getNetAddress());
					itemEndpointNames.add(connname);
				}
			}
			connectionCombo = new Combo(container, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
			int idx = -1;
			if (ObjectUtils.isNullOrEmpty(connectionName)) {
				//select nothing
			} else {
				idx = itemEndpointNames.indexOf(connectionName);
				if (idx < 0) {
					//insert the not found connection name to last
					idx = items.size();
					items.add(connectionName + " @<not-found>");
					itemEndpointNames.add(connectionName);
				}
			}
			connectionCombo.setItems(items.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
			connectionCombo.select(idx);
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected void okPressed() {
			String mountpathstr = mountPathText.getText();
			//convert to path, and assign its string representation in order to normalize the path
			this.mountPath = Objects.toString(EclipseSakerIDEPlugin.tryParsePath(mountpathstr), mountpathstr);
			int connectionidx = connectionCombo.getSelectionIndex();
			switch (connectionidx) {
				case PathConfigurationProjectPropertyPage.MOUNT_DIALOG_PROJECT_CONNECTION_NAME_INDEX: {
					this.connectionName = SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE;
					SakerPath mountsakerpath = SakerPath.valueOf(this.mountPath);
					if (mountsakerpath.isRelative()) {
						this.mountPath = SakerPath.PATH_SLASH.resolve(mountsakerpath).toString();
					}
					break;
				}
				case PathConfigurationProjectPropertyPage.MOUNT_DIALOG_LOCAL_CONNECTION_NAME_INDEX: {
					this.connectionName = SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM;
					break;
				}
				default: {
					this.connectionName = itemEndpointNames.get(connectionidx);
					break;
				}
			}
			super.okPressed();
		}

		public String getConnectionName() {
			return connectionName;
		}

		public String getMountPath() {
			return mountPath;
		}

	}

}