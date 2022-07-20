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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.ide.support.ui.ExecutionDaemonSelector;
import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class DaemonConnectionsProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.daemonConnectionsProjectPropertyPage";

	private EclipseSakerIDEProject ideProject;
	private List<TreePropertyItem<DaemonConnectionIDEProperty>> connectionItems = new ArrayList<>();
	private boolean useClientsAsClusters;

	private TreeViewer connectionsTreeViewer;
//	private List<String> executionDaemonComboNames = new ArrayList<>();
	private Combo executionDaemonCombo;

	private Button useClientsAsClustersButton;

	private ExecutionDaemonSelector daemonSelector;

	private static final AtomicIntegerFieldUpdater<DaemonConnectionsProjectPropertyPage> AIFU_propertyCounter = AtomicIntegerFieldUpdater
			.newUpdater(DaemonConnectionsProjectPropertyPage.class, "propertyCounter");
	@SuppressWarnings("unused")
	private volatile int propertyCounter;

	public DaemonConnectionsProjectPropertyPage() {
		super();
	}

	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);
		ideProject = ImplActivator.getDefault().getOrCreateSakerProject(element.getAdapter(IProject.class));
		IDEProjectProperties ideprops = null;
		if (ideProject != null) {
			ideprops = ideProject.getIDEProjectProperties();
			Set<? extends DaemonConnectionIDEProperty> connectionsprop = ideprops.getConnections();
			if (!ObjectUtils.isNullOrEmpty(connectionsprop)) {
				for (DaemonConnectionIDEProperty prop : connectionsprop) {
					connectionItems.add(new TreePropertyItem<>(AIFU_propertyCounter.getAndIncrement(this), prop));
				}
			}
			useClientsAsClusters = SakerIDESupportUtils.getBooleanValueOrDefault(ideprops.getUseClientsAsClusters(),
					false);
		}
		daemonSelector = new ExecutionDaemonSelector(ideprops);
	}

	@Override
	public void applyData(Object data) {
		if (data instanceof PropertiesValidationErrorResult) {
			PropertiesValidationErrorResult err = (PropertiesValidationErrorResult) data;
			if (err.relatedSubject instanceof DaemonConnectionIDEProperty) {
				for (TreePropertyItem<DaemonConnectionIDEProperty> item : connectionItems) {
					if (item.property.equals(err.relatedSubject)) {
						connectionsTreeViewer.expandToLevel(item, 1);
						TreePath treepath;
						switch (err.errorType) {
							case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_ADDRESS
									+ SakerIDEProject.E_MISSING: {
								treepath = new TreePath(new Object[] { item, new AddressTreeElement(item) });
								break;
							}
							case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME
									+ SakerIDEProject.E_MISSING:
							case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME
									+ SakerIDEProject.E_RESERVED:
							case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME
									+ SakerIDEProject.E_DUPLICATE: {
								treepath = new TreePath(new Object[] { item, new NameTreeElement(item) });
								break;
							}
							default: {
								treepath = new TreePath(new Object[] { item });
								break;
							}
						}
						connectionsTreeViewer.setSelection(new TreeSelection(treepath));
						break;
					}
				}
			}
		}
	}

	private static class AddressTreeElement extends TreePropertyItemChildElement<DaemonConnectionIDEProperty> {
		public AddressTreeElement(TreePropertyItem<DaemonConnectionIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			return ObjectUtils.nullDefault(treeItem.property.getNetAddress(), "");
		}

		@Override
		public String getTitle() {
			return "Address";
		}
	}

	private static class NameTreeElement extends TreePropertyItemChildElement<DaemonConnectionIDEProperty> {
		public NameTreeElement(TreePropertyItem<DaemonConnectionIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			return ObjectUtils.nullDefault(treeItem.property.getConnectionName(), "");
		}

		@Override
		public String getTitle() {
			return "Connection name";
		}
	}

	private static class UseAsClusterTreeElement extends TreePropertyItemChildElement<DaemonConnectionIDEProperty> {
		public UseAsClusterTreeElement(TreePropertyItem<DaemonConnectionIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			return Boolean.toString(treeItem.property.isUseAsCluster());
		}

		@Override
		public String getTitle() {
			return "Use as cluster";
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
		GridLayout layout = new GridLayout(2, false);
		composite.setLayout(layout);
		GridData data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		data.horizontalSpan = 2;
		composite.setLayoutData(data);

		connectionsTreeViewer = new TreeViewer(
				new Tree(composite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.BORDER));
		connectionsTreeViewer.setComparer(new TreePropertyItemIdentifierElementComparer());
		connectionsTreeViewer.getTree().setLayoutData(data);
		connectionsTreeViewer.setContentProvider(new ITreeContentProvider() {
			@Override
			public boolean hasChildren(Object element) {
				if (element instanceof TreePropertyItem) {
					return true;
				}
				return false;
			}

			@Override
			public Object getParent(Object element) {
				if (element instanceof TreePropertyItemChildElement) {
					return ((TreePropertyItemChildElement<?>) element).treeItem;
				}
				return null;
			}

			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement == null) {
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
				Collection<?> repos = (Collection<?>) inputElement;
				return repos.toArray();
			}

			@Override
			public Object[] getChildren(Object parentElement) {
				if (parentElement instanceof TreePropertyItem) {
					@SuppressWarnings("unchecked")
					TreePropertyItem<DaemonConnectionIDEProperty> propertyitem = (TreePropertyItem<DaemonConnectionIDEProperty>) parentElement;
					return new Object[] { new NameTreeElement(propertyitem), new AddressTreeElement(propertyitem),
							new UseAsClusterTreeElement(propertyitem) };
				}
				return null;
			}
		});
		connectionsTreeViewer
				.setLabelProvider(new DelegatingStyledCellLabelProvider(new DaemonConnectionTreeLabelProvider()));
		connectionsTreeViewer.setInput(connectionItems);
		connectionsTreeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@SuppressWarnings("unchecked")
			@Override
			public void doubleClick(DoubleClickEvent event) {
				ISelection selection = event.getSelection();
				if (!(selection instanceof ITreeSelection)) {
					return;
				}
				ITreeSelection treesel = (ITreeSelection) selection;
				Object elem = treesel.getFirstElement();
				if (elem == null) {
					return;
				}
				TreePropertyItem<DaemonConnectionIDEProperty> property;
				if (elem instanceof TreePropertyItem) {
					property = (TreePropertyItem<DaemonConnectionIDEProperty>) elem;
				} else if (elem instanceof TreePropertyItemChildElement) {
					TreePropertyItemChildElement<DaemonConnectionIDEProperty> treeelem = (TreePropertyItemChildElement<DaemonConnectionIDEProperty>) elem;
					property = treeelem.treeItem;
				} else {
					//unknown type, ignore
					return;
				}
				DaemonConnectionDialog dialog = new DaemonConnectionDialog(getShell(),
						property.property.getNetAddress(), property.property.getConnectionName(),
						property.property.isUseAsCluster());
				dialog.create();
				if (dialog.open() == Window.OK) {
					boolean useascluster = dialog.isUseAsCluster();
					property.property = new DaemonConnectionIDEProperty(dialog.getAddress(), dialog.getName(),
							useascluster);
					connectionsTreeViewer.refresh();
					updateExecutionDaemonComboItems();
					validateProperties();
				}
			}
		});
		connectionsTreeViewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.DEL) {
					ISelection selection = connectionsTreeViewer.getSelection();
					if (!(selection instanceof ITreeSelection)) {
						return;
					}
					ITreeSelection treesel = (ITreeSelection) selection;
					Object elem = treesel.getFirstElement();
					if (elem == null) {
						return;
					}
					if (elem instanceof TreePropertyItem) {
						TreePropertyItem.deletePropertyItem(connectionItems, (TreePropertyItem<?>) elem);
						connectionsTreeViewer.refresh();
						updateExecutionDaemonComboItems();
						validateProperties();
						return;
					}
					//no delete support for other items
					return;
				}
			}
		});

		Button adduserparameterbutton = new Button(composite, SWT.PUSH);
		adduserparameterbutton.setText("New connection...");
		adduserparameterbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			DaemonConnectionDialog dialog = new DaemonConnectionDialog(getShell(), "", "", false);
			dialog.create();
			if (dialog.open() == Window.OK) {
				boolean useascluster = dialog.isUseAsCluster();
				connectionItems.add(new TreePropertyItem<>(AIFU_propertyCounter.getAndIncrement(this),
						new DaemonConnectionIDEProperty(dialog.getAddress(), dialog.getName(), useascluster)));
				connectionsTreeViewer.refresh();
				updateExecutionDaemonComboItems();
				validateProperties();
			}
		}));
		adduserparameterbutton.setLayoutData(GridDataFactory.swtDefaults().span(2, 1).create());

		Label executiondaemonlabel = new Label(composite, SWT.NONE);
		executiondaemonlabel.setText("Execution daemon:");
		executionDaemonCombo = new Combo(composite, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		executionDaemonCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
			daemonSelector.setExecutionDaemonIndex(executionDaemonCombo.getSelectionIndex());
			validateProperties();
		}));
		updateExecutionDaemonComboItems();

		useClientsAsClustersButton = new Button(composite, SWT.CHECK);
		useClientsAsClustersButton.setText("Use clients as build clusters");
		useClientsAsClustersButton.setSelection(useClientsAsClusters);
		GridDataFactory.defaultsFor(useClientsAsClustersButton).span(2, 1).applyTo(useClientsAsClustersButton);
		;

		validateProperties();

		return composite;
	}

	private void updateExecutionDaemonComboItems() {
		String execdaemonname = daemonSelector.getSelectedExecutionDaemonName();

		daemonSelector.reset(getDaemonConnectionIDEProperties(), execdaemonname);

		executionDaemonCombo.setItems(daemonSelector.getLabels().toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		executionDaemonCombo.select(daemonSelector.getExecutionDaemonIndex());
	}

	private final class DaemonConnectionTreeLabelProvider extends LabelProvider implements IStyledLabelProvider {
		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof TreePropertyItem) {
				@SuppressWarnings("unchecked")
				TreePropertyItem<DaemonConnectionIDEProperty> item = (TreePropertyItem<DaemonConnectionIDEProperty>) element;
				StyledString result = new StyledString();
				String connname = item.property.getConnectionName();
				if (!ObjectUtils.isNullOrEmpty(connname)) {
					result.append(connname);
				}
				String netaddress = item.property.getNetAddress();
				if (!ObjectUtils.isNullOrEmpty(netaddress)) {
					result.append(" @" + netaddress, StyledString.QUALIFIER_STYLER);
				}
				return result;
			}
			String title = null;
			String content;
			if (element instanceof TreePropertyItemChildElement) {
				TreePropertyItemChildElement<?> treeelem = (TreePropertyItemChildElement<?>) element;
				content = treeelem.getLabel();
				title = treeelem.getTitle();
			} else {
				//should not ever happen, as other kind of elements are not present in the tree
				content = super.getText(element);
			}
			StyledString styledString = new StyledString();
			if (title != null) {
				styledString.append(title + ": ", StyledString.QUALIFIER_STYLER);
			}
			styledString.append(content);
			return styledString;
		}

		@Override
		public Image getImage(Object element) {
			if (element instanceof TreePropertyItemChildElement) {
				TreePropertyItemChildElement<?> treeelem = (TreePropertyItemChildElement<?>) element;
				return treeelem.getImage();
			}
			return super.getImage(element);
		}
	}

	private void validateProperties() {
		Set<String> connectionnames = new TreeSet<>();
		for (TreePropertyItem<DaemonConnectionIDEProperty> item : connectionItems) {
			String cname = item.property.getConnectionName();
			if (ObjectUtils.isNullOrEmpty(cname)) {
				invalidateWithErrorMessage("Missing connection name.");
				return;
			}
			if (!connectionnames.add(cname)) {
				invalidateWithErrorMessage("Duplicate connection name: " + cname);
				return;
			}
			if (DaemonConnectionIDEProperty.isReservedConnectionName(cname)) {
				invalidateWithErrorMessage("Connection name: " + cname + " is reserved.");
				return;
			}
			if (!DaemonConnectionIDEProperty.isValidConnectionNameFormat(cname)) {
				invalidateWithErrorMessage("Invalid connection name format: \"" + cname + "\".");
				return;
			}
		}
		String execdaemonname = daemonSelector.getSelectedExecutionDaemonName();
		if (!ObjectUtils.isNullOrEmpty(execdaemonname)) {
			if (!connectionnames.contains(execdaemonname)) {
				invalidateWithErrorMessage("Execution daemon connection not found.");
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
		connectionItems.clear();
		connectionsTreeViewer.refresh();
		daemonSelector.reset(null);
		useClientsAsClusters = false;
		useClientsAsClustersButton.setSelection(useClientsAsClusters);

		updateExecutionDaemonComboItems();
		validateProperties();
	}

	@Override
	public boolean performOk() {
		Set<DaemonConnectionIDEProperty> connections = getDaemonConnectionIDEProperties();
		String execdaemonname = daemonSelector.getSelectedExecutionDaemonName();
		try {
			ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
					.setConnections(ImmutableUtils.makeImmutableLinkedHashSet((connections)))
					.setUseClientsAsClusters(useClientsAsClustersButton.getSelection())
					.setExecutionDaemonConnectionName(execdaemonname).build());
		} catch (IOException e) {
			ideProject.displayException(SakerLog.SEVERITY_ERROR, "Failed to save project properties.", e);
			return false;
		}
		return true;
	}

	private Set<DaemonConnectionIDEProperty> getDaemonConnectionIDEProperties() {
		Set<DaemonConnectionIDEProperty> connections = new LinkedHashSet<>();
		for (TreePropertyItem<DaemonConnectionIDEProperty> item : connectionItems) {
			connections.add(item.property);
		}
		return connections;
	}

	private static class DaemonConnectionDialog extends TitleAreaDialog {
		private Text addressText;
		private Text nameText;
		private Button useAsClusterButton;

		private String address;
		private String name;
		private boolean useAsCluster;

		public DaemonConnectionDialog(Shell parentShell, String address, String name, boolean useAsCluster) {
			super(parentShell);
			this.address = address;
			this.name = name;
			this.useAsCluster = useAsCluster;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Build daemon connection");
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		public void create() {
			super.create();
			setTitle("Build daemon connection");
			resetMessage();
		}

		private void resetMessage() {
			setMessage("Specify a network connection to a build daemon.", IMessageProvider.NONE);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout layout = new GridLayout(2, false);
			container.setLayout(layout);

			createAddressText(container);
			createNameText(container);

			useAsClusterButton = new Button(container, SWT.CHECK);
			useAsClusterButton.setText("Use as cluster");
			useAsClusterButton.setSelection(this.useAsCluster);
			GridData clusterbuddtonlayoutdata = new GridData();
			clusterbuddtonlayoutdata.horizontalSpan = 2;
			useAsClusterButton.setLayoutData(clusterbuddtonlayoutdata);

			Listener modifylistener = event -> {
				Button okbutton = getButton(IDialogConstants.OK_ID);
				if (okbutton == null) {
					return;
				}
				updateOkButton(okbutton);
			};
			addressText.addListener(SWT.Modify, modifylistener);
			nameText.addListener(SWT.Modify, modifylistener);

			return area;
		}

		private void updateOkButton(Button okbutton) {
			String connname = nameText.getText();
			if (DaemonConnectionIDEProperty.RESERVED_CONNECTION_NAMES.contains(connname)) {
				okbutton.setEnabled(false);
				setMessage("Connection name: " + connname + " is reserved.", IMessageProvider.ERROR);
				return;
			}
			if (!DaemonConnectionIDEProperty.isValidConnectionNameFormat(connname)) {
				okbutton.setEnabled(false);
				setMessage("Invalid connection name format: \"" + connname + "\".", IMessageProvider.ERROR);
				return;
			}
			if (connname.isEmpty() || addressText.getText().isEmpty()) {
				okbutton.setEnabled(false);
				//no message
				resetMessage();
				return;
			}
			okbutton.setEnabled(true);
			resetMessage();
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			Button result = super.createButton(parent, id, label, defaultButton);
			if (id == IDialogConstants.OK_ID) {
				updateOkButton(result);
			}
			return result;
		}

		private void createAddressText(Composite container) {
			Label lbtFirstName = new Label(container, SWT.NONE);
			lbtFirstName.setText("Address");

			GridData dataFirstName = new GridData();
			dataFirstName.grabExcessHorizontalSpace = true;
			dataFirstName.horizontalAlignment = GridData.FILL;

			addressText = new Text(container, SWT.BORDER);
			addressText.setLayoutData(dataFirstName);
			addressText.setText(address);
			addressText.setMessage("Network address");
		}

		private void createNameText(Composite container) {
			Label lbtLastName = new Label(container, SWT.NONE);
			lbtLastName.setText("Connection name");

			GridData dataLastName = new GridData();
			dataLastName.grabExcessHorizontalSpace = true;
			dataLastName.horizontalAlignment = GridData.FILL;
			nameText = new Text(container, SWT.BORDER);
			nameText.setLayoutData(dataLastName);
			nameText.setText(name);
			nameText.setMessage("Name");
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected void okPressed() {
			address = addressText.getText();
			name = nameText.getText();
			useAsCluster = useAsClusterButton.getSelection();
			super.okPressed();
		}

		public String getAddress() {
			return address;
		}

		public String getName() {
			return name;
		}

		public boolean isUseAsCluster() {
			return useAsCluster;
		}
	}

}