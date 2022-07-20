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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.file.path.SakerPath;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class PathConfigurationProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.pathConfigurationProjectPropertyPage";

	public static final String LABEL_PROJECT_RELATIVE = "Project relative";
	public static final String LABEL_LOCAL_FILESYSTEM = "Local file system";

	private EclipseSakerIDEProject ideProject;
	private List<ProviderMountIDEProperty> mounts = new ArrayList<>();
	private String workingDirectoryProperty;
	private String buildDirectoryProperty;
	private String mirrorDirectoryProperty;

	private Table mountsTable;
	private Text workingDirectoryText;
	private Text buildDirectoryText;
	private Text mirrorDirectoryText;

	public PathConfigurationProjectPropertyPage() {
		super();
	}

	@Override
	public void applyData(Object data) {
		if (data instanceof PropertiesValidationErrorResult) {
			PropertiesValidationErrorResult err = (PropertiesValidationErrorResult) data;
			String type = err.errorType;
			if (type.startsWith(SakerIDEProject.NS_WORKING_DIRECTORY)) {
				workingDirectoryText.setFocus();
				workingDirectoryText.setSelection(0, workingDirectoryText.getText().length());
			} else if (type.startsWith(SakerIDEProject.NS_BUILD_DIRECTORY)) {
				buildDirectoryText.setFocus();
				buildDirectoryText.setSelection(0, buildDirectoryText.getText().length());
			} else if (type.startsWith(SakerIDEProject.NS_MIRROR_DIRECTORY)) {
				mirrorDirectoryText.setFocus();
				mirrorDirectoryText.setSelection(0, mirrorDirectoryText.getText().length());
			} else if (type.startsWith(SakerIDEProject.NS_PROVIDER_MOUNT)) {
				if (err.relatedSubject instanceof ProviderMountIDEProperty) {
					int i = 0;
					for (TableItem item : mountsTable.getItems()) {
						if (err.relatedSubject.equals(item.getData())) {
							mountsTable.select(i);
							break;
						}
						++i;
					}
				}
			}
		}
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		updateApplyButton();
	}

	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);
		ideProject = ImplActivator.getDefault().getOrCreateSakerProject(element.getAdapter(IProject.class));
		if (ideProject != null) {
			IDEProjectProperties ideprops = ideProject.getIDEProjectProperties();
			Set<? extends ProviderMountIDEProperty> mountsprop = ideprops.getMounts();
			if (!ObjectUtils.isNullOrEmpty(mountsprop)) {
				ObjectUtils.addAll(mounts, mountsprop);
			}

			workingDirectoryProperty = ideprops.getWorkingDirectory();
			buildDirectoryProperty = ideprops.getBuildDirectory();
			mirrorDirectoryProperty = ideprops.getMirrorDirectory();
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		if (this.ideProject == null) {
			Label resultlabel = new Label(parent, SWT.NONE);
			resultlabel.setText("Project doesn't have saker.build nature associated with it.");
			return resultlabel;
		}

		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		Group pathconfigsgroup = new Group(composite, SWT.NONE);
		pathconfigsgroup.setLayout(new GridLayout(2, false));
		pathconfigsgroup.setLayoutData(
				GridDataFactory.swtDefaults().grab(true, false).align(GridData.FILL, GridData.CENTER).create());
		pathconfigsgroup.setText("Special paths");

//		Composite workingrow = new Composite(composite, SWT.NONE);
//		GridData workingrowgriddata = new GridData();
//		workingrowgriddata.grabExcessHorizontalSpace = true;
//		workingrowgriddata.horizontalAlignment = GridData.FILL;
//		workingrow.setLayout(new GridLayout(2, false));
//		workingrow.setLayoutData(workingrowgriddata);

		workingDirectoryText = createSpecialPathText(pathconfigsgroup, "Working directory:", "Execution path");
		buildDirectoryText = createSpecialPathText(pathconfigsgroup, "Build directory:", "Execution path");
		mirrorDirectoryText = createSpecialPathText(pathconfigsgroup, "Mirror directory:",
				"Execution daemon local path (empty for default)");

		mountsTable = new Table(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		TableColumn mountrootcol = new TableColumn(mountsTable, SWT.NONE);
		TableColumn connnamecol = new TableColumn(mountsTable, SWT.NONE);
		TableColumn pathcol = new TableColumn(mountsTable, SWT.NONE);
		mountrootcol.setText("Mount root");
		connnamecol.setText("File system endpoint");
		pathcol.setText("Mounted path");
		mountrootcol.setResizable(false);
		connnamecol.setResizable(false);
		pathcol.setResizable(false);

		mountsTable.setLinesVisible(true);
		mountsTable.setHeaderVisible(true);

		GridData tablegd = new GridData();
		tablegd.grabExcessHorizontalSpace = true;
		tablegd.grabExcessVerticalSpace = true;
		tablegd.horizontalAlignment = GridData.FILL;
		tablegd.verticalAlignment = GridData.FILL;
		mountsTable.setLayoutData(tablegd);

		mountsTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.DEL) {
					int selidx = mountsTable.getSelectionIndex();
					if (selidx >= 0) {
						mounts.remove(selidx);
					}
					populateControls();
					validateProperties();
				}
			}
		});
		mountsTable.addControlListener(new TableColumnEvenDistributorControlResizeListener(mountsTable));
		mountsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				int selidx = mountsTable.getSelectionIndex();
				if (selidx < 0) {
					return;
				}
				ProviderMountIDEProperty conn = mounts.get(selidx);
				MountDialog dialog = new MountDialog(getShell(), conn.getRoot(), conn.getMountClientName(),
						conn.getMountPath());
				dialog.create();
				if (dialog.open() == Window.OK) {
					mounts.set(selidx, new ProviderMountIDEProperty(dialog.getMountRoot(),
							MountPathIDEProperty.create(dialog.getConnectionName(), dialog.getMountPath())));
					populateControls();
					validateProperties();
				}
			}
		});
		Button adduserparameterbutton = new Button(composite, SWT.PUSH);
		adduserparameterbutton.setText("Mount new directory...");
		adduserparameterbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			MountDialog dialog = new MountDialog(getShell(), "", "", "");
			dialog.create();
			if (dialog.open() == Window.OK) {
				mounts.add(new ProviderMountIDEProperty(dialog.getMountRoot(),
						MountPathIDEProperty.create(dialog.getConnectionName(), dialog.getMountPath())));
				populateControls();
				validateProperties();
			}
		}));

		workingDirectoryText.setText(ObjectUtils.nullDefault(this.workingDirectoryProperty, ""));
		buildDirectoryText.setText(ObjectUtils.nullDefault(this.buildDirectoryProperty, ""));
		mirrorDirectoryText.setText(ObjectUtils.nullDefault(this.mirrorDirectoryProperty, ""));
		populateControls();
		validateProperties();

		return composite;
	}

	private Text createSpecialPathText(Group pathconfigsgrid, String label, String message) {
		createLabelWithText(pathconfigsgrid, label);
		Text workingdirtext = new Text(pathconfigsgrid, SWT.BORDER);
		GridData wdtextgriddata = new GridData();
		wdtextgriddata.grabExcessHorizontalSpace = true;
		wdtextgriddata.horizontalAlignment = GridData.FILL;
		workingdirtext.setLayoutData(wdtextgriddata);
		workingdirtext.setMessage(message);
		workingdirtext.addModifyListener(ev -> {
			validateProperties();
		});
		return workingdirtext;
	}

	private static void createLabelWithText(Composite workingrow, String labeltext) {
		Label wdlabel = new Label(workingrow, SWT.NONE);
		wdlabel.setText(labeltext);
	}

	public static DaemonConnectionIDEProperty getConnectionPropertyWithName(
			Iterable<? extends DaemonConnectionIDEProperty> connections, String name) {
		return SakerIDESupportUtils.getConnectionPropertyWithName(connections, name);
	}

	private void populateControls() {
		Set<? extends DaemonConnectionIDEProperty> connections = ideProject.getIDEProjectProperties().getConnections();
		Table table = this.mountsTable;
		List<ProviderMountIDEProperty> mounts = this.mounts;
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			table.setItemCount(0);
		} else {
			table.setItemCount(mounts.size());
			int i = 0;
			for (ProviderMountIDEProperty mountprop : mounts) {
				TableItem item = table.getItem(i++);
				item.setData(mountprop);
				item.setText(0, ObjectUtils.nullDefault(mountprop.getRoot(), ""));
				String clientname = mountprop.getMountClientName();
				String clientnamecol;
				if (clientname == null) {
					clientname = "";
				}
				clientnamecol = getReadableClientName(connections, clientname);
				item.setText(1, ObjectUtils.nullDefault(clientnamecol, ""));
				item.setText(2, ObjectUtils.nullDefault(mountprop.getMountPath(), ""));
			}
		}
		table.getParent().requestLayout();
	}

	public static String getReadableClientName(Set<? extends DaemonConnectionIDEProperty> connections,
			String clientname) {
		if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
			return LABEL_PROJECT_RELATIVE;
		}
		if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)) {
			return LABEL_LOCAL_FILESYSTEM;
		}
		String result = clientname;
		DaemonConnectionIDEProperty connectionprop = getConnectionPropertyWithName(connections, clientname);
		if (connectionprop != null) {
			result += " @";
			result += connectionprop.getNetAddress();
		} else {
			result += " @<not-found>";
		}
		return result;
	}

	private boolean isRootMounted(String root) {
		for (ProviderMountIDEProperty mountprop : mounts) {
			if (root.equals(mountprop.getRoot())) {
				return true;
			}
		}
		return false;
	}

	private void validateProperties() {
		String wdpathtext = workingDirectoryText.getText();
		try {
			SakerPath wdpath = SakerPath.valueOf(wdpathtext);
			if (wdpath.isRelative()) {
				invalidateWithErrorMessage("The working directory path must be absolute.");
				return;
			}
			String wdroot = wdpath.getRoot();
			if (!isRootMounted(wdroot)) {
				invalidateWithErrorMessage("Working directory root is not mounted.");
				return;
			}
		} catch (IllegalArgumentException e) {
			invalidateWithErrorMessage("Invalid working directory path format.");
			return;
		}
		String bdtext = buildDirectoryText.getText();
		if (!ObjectUtils.isNullOrEmpty(bdtext)) {
			try {
				SakerPath bdpath = SakerPath.valueOf(bdtext);
				String bdroot = bdpath.getRoot();
				if (bdroot != null && !isRootMounted(bdroot)) {
					invalidateWithErrorMessage("Build directory root is not mounted.");
					return;
				}
			} catch (IllegalArgumentException e) {
				invalidateWithErrorMessage("Invalid build directory path format.");
				return;
			}
		}
		String mirrortext = mirrorDirectoryText.getText();
		if (!ObjectUtils.isNullOrEmpty(mirrortext)) {
			try {
				SakerPath mirrorpath = SakerPath.valueOf(mirrortext);
				if (!mirrorpath.isAbsolute()) {
					invalidateWithErrorMessage("The mirror directory path must be absolute.");
					return;
				}
			} catch (IllegalArgumentException e) {
				invalidateWithErrorMessage("Invalid mirror directory path format.");
				return;
			}
		}
		Set<String> roots = new TreeSet<>();
		for (ProviderMountIDEProperty mountprop : mounts) {
			String rootstr = mountprop.getRoot();
			String clientname = mountprop.getMountClientName();
			String mountpathstr = mountprop.getMountPath();

			if (ObjectUtils.isNullOrEmpty(rootstr)) {
				invalidateWithErrorMessage("Mount root is not specified.");
				return;
			}
			String root;
			try {
				root = SakerPath.normalizeRoot(rootstr);
				if (!roots.add(root)) {
					invalidateWithErrorMessage("Duplicate mount root: " + root);
					return;
				}
			} catch (RuntimeException e) {
				invalidateWithErrorMessage("Invalid mount root format: " + rootstr);
				return;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)
					|| SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
				//these are fine
			} else {
				DaemonConnectionIDEProperty connprop = ValidationUtils.getDaemonConnectionPropertyForConnectionName(
						ideProject.getIDEProjectProperties().getConnections(), clientname);
				if (connprop == null) {
					invalidateWithErrorMessage("Mount endpoint daemon connection not found for: " + root);
					return;
				}
			}
			if (ObjectUtils.isNullOrEmpty(mountpathstr)) {
				invalidateWithErrorMessage("Missing mount path for: " + root);
				return;
			}
			try {
				SakerPath mountpath = SakerPath.valueOf(mountpathstr);
				if (!mountpath.isAbsolute()) {
					invalidateWithErrorMessage("Mount path must be absolute for: " + root + " (" + mountpathstr + ")");
					return;
				}
				if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
					if (!SakerPath.ROOT_SLASH.equals(mountpath.getRoot())) {
						invalidateWithErrorMessage("Mount path root must be / for project relative paths: " + root
								+ " (" + mountpathstr + ")");
						return;
					}
				}
			} catch (RuntimeException e) {
				invalidateWithErrorMessage("Invalid mount path format for: " + root + " (" + mountpathstr + ")");
				return;
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
		mounts.clear();
		mounts.add(SakerIDEProject.DEFAULT_MOUNT_IDE_PROPERTY);
		String defaultroot = SakerIDEProject.DEFAULT_MOUNT_IDE_PROPERTY.getRoot();
		workingDirectoryProperty = defaultroot;
		workingDirectoryText.setText(defaultroot);
		buildDirectoryProperty = SakerIDEProject.DEFAULT_BUILD_DIRECTORY_PATH;
		buildDirectoryText.setText(buildDirectoryProperty);
		mirrorDirectoryProperty = "";
		mirrorDirectoryText.setText(mirrorDirectoryProperty);

		populateControls();
		validateProperties();
	}

	@Override
	public boolean performOk() {
		try {
			ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
					.setWorkingDirectory(workingDirectoryText.getText()).setBuildDirectory(buildDirectoryText.getText())
					.setMirrorDirectory(mirrorDirectoryText.getText()).setMounts(new LinkedHashSet<>(mounts)).build());
		} catch (IOException e) {
			ideProject.displayException(SakerLog.SEVERITY_ERROR, "Failed to save project properties.", e);
			return false;
		}
		return true;
	}

	public static final int MOUNT_DIALOG_PROJECT_CONNECTION_NAME_INDEX = 0;
	public static final int MOUNT_DIALOG_LOCAL_CONNECTION_NAME_INDEX = 1;

	private class MountDialog extends TitleAreaDialog {

		private Combo connectionCombo;
		private Text mountRootText;
		private Text mountPathText;

		private String mountRoot;
		private String mountPath;
		private String connectionName;
		private List<String> itemEndpointNames;

		public MountDialog(Shell parentShell, String mountroot, String connectionName, String mountpath) {
			super(parentShell);
			this.mountRoot = mountroot;
			this.connectionName = connectionName;
			this.mountPath = mountpath;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Mount path");
		}

		@Override
		public void create() {
			super.create();
			setTitle("Mount path");
			resetMessage();
		}

		private void resetMessage() {
			setMessage("Specify a directory to be mounted as an exceution root.", IMessageProvider.NONE);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout layout = new GridLayout(2, false);
			container.setLayout(layout);

			createMountRootText(container);
			createConnectionCombo(container);
			createMountPathText(container);

			Listener modifylistener = event -> {
				updateOkButton(getButton(IDialogConstants.OK_ID));
			};
			mountRootText.addListener(SWT.Modify, modifylistener);
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
					&& getConnectionPropertyWithName(ideProject.getIDEProjectProperties().getConnections(),
							selectedconn) == null) {
				setMessage("Connection endpoint not found: " + selectedconn, IMessageProvider.ERROR);
				okbutton.setEnabled(false);
				return;
			}
			String mountrootstr = mountRootText.getText();
			if (!mountrootstr.isEmpty()) {
				try {
					SakerPath.normalizeRoot(mountrootstr);
				} catch (IllegalArgumentException e) {
					setMessage("Invalid mount root format: " + mountrootstr, IMessageProvider.ERROR);
					okbutton.setEnabled(false);
					return;
				}
			}
			String mountpathstr = mountPathText.getText();
			if (!mountpathstr.isEmpty()) {
				SakerPath mountpath;
				try {
					mountpath = SakerPath.valueOf(mountpathstr);
				} catch (IllegalArgumentException e) {
					setMessage("Invalid mount path format: " + mountpathstr, IMessageProvider.ERROR);
					okbutton.setEnabled(false);
					return;
				}
				if (mountpath.isRelative()) {
					if (!SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(selectedconn)) {
						//a relative path was specifed, but not against the project relative connection
						setMessage("Mount path must be absolute: " + mountpathstr, IMessageProvider.ERROR);
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
			if (mountrootstr.isEmpty() || mountpathstr.isEmpty()) {
				resetMessage();
				okbutton.setEnabled(false);
				return;
			}
			resetMessage();
			okbutton.setEnabled(true);
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			Button result = super.createButton(parent, id, label, defaultButton);
			if (id == IDialogConstants.OK_ID) {
				updateOkButton(result);
			}
			return result;
		}

		private void createMountRootText(Composite container) {
			Label lbtFirstName = new Label(container, SWT.NONE);
			lbtFirstName.setText("Execution root");

			GridData dataFirstName = new GridData();
			dataFirstName.grabExcessHorizontalSpace = true;
			dataFirstName.horizontalAlignment = GridData.FILL;

			mountRootText = new Text(container, SWT.BORDER);
			mountRootText.setLayoutData(dataFirstName);
			mountRootText.setText(mountRoot);
			mountRootText.setMessage("Mount root");
		}

		private void createMountPathText(Composite container) {
			Label lbtLastName = new Label(container, SWT.NONE);
			lbtLastName.setText("Mounted path");

			GridData dataLastName = new GridData();
			dataLastName.grabExcessHorizontalSpace = true;
			dataLastName.horizontalAlignment = GridData.FILL;
			mountPathText = new Text(container, SWT.BORDER);
			mountPathText.setLayoutData(dataLastName);
			mountPathText.setText(mountPath);
			mountPathText.setMessage("Mount directory path");
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
			String mountrootstr = mountRootText.getText();
			this.mountRoot = SakerIDESupportUtils.normalizePathRoot(mountrootstr);
			this.mountPath = SakerIDESupportUtils.normalizePath(mountpathstr);
			int connectionidx = connectionCombo.getSelectionIndex();
			switch (connectionidx) {
				case MOUNT_DIALOG_PROJECT_CONNECTION_NAME_INDEX: {
					this.connectionName = SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE;
					SakerPath mountsakerpath = SakerPath.valueOf(this.mountPath);
					if (mountsakerpath.isRelative()) {
						this.mountPath = SakerPath.PATH_SLASH.resolve(mountsakerpath).toString();
					}
					break;
				}
				case MOUNT_DIALOG_LOCAL_CONNECTION_NAME_INDEX: {
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

		public String getMountRoot() {
			return mountRoot;
		}

		public String getMountPath() {
			return mountPath;
		}

		public String getConnectionName() {
			return connectionName;
		}
	}

}