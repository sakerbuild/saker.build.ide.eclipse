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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.runtime.classpath.HttpUrlJarFileClassPathLocation;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;

public class ClassPathAdditionWizard implements PropertyWizardPart<ClassPathLocationIDEProperty> {
	public static final int FLAG_NO_NEST_REPOSITORY_CLASSPATH = 1 << 0;
	public static final int FLAG_NO_SAKERSCRIPT_CLASSPATH = 1 << 1;

	protected final EclipseSakerIDEProject project;
	protected ClassPathLocationIDEProperty editedClassPathLocationProperty;

	protected final ClassPathTypeChooserWizardPage classPathTypeChooser;

	protected final NestRepositoryVersionChoosingWizardPage classPathNestVersionChooser;
	protected final FileChoosingWizardPage classPathFileArchiveChooser;
	protected final NetworkArchiveChoosingWizardPage classPathNetworkArchiveChooser;

	protected final int flags;

	protected String classPathTypeTitleText = "Configuration";
	protected String classPathTypeText = "configuration";

	private WizardContinuation<? super ClassPathLocationIDEProperty> continuation;
	private BiConsumer<? super IWizardPage, ? super ClassPathLocationIDEProperty> finisher;

	public ClassPathAdditionWizard(EclipseSakerIDEProject project, int flags,
			ClassPathLocationIDEProperty editedProperty) {
		this.project = project;
		this.editedClassPathLocationProperty = editedProperty;
		this.flags = flags;
		this.classPathNestVersionChooser = new NestRepositoryVersionChoosingWizardPage();
		this.classPathTypeChooser = new ClassPathTypeChooserWizardPage();
		this.classPathFileArchiveChooser = new FileChoosingWizardPage();
		this.classPathNetworkArchiveChooser = new NetworkArchiveChoosingWizardPage();
	}

	@Override
	public void setContinuation(WizardContinuation<? super ClassPathLocationIDEProperty> continuation) {
		this.continuation = continuation;
	}

	@Override
	public void setFinisher(BiConsumer<? super IWizardPage, ? super ClassPathLocationIDEProperty> finisher) {
		this.finisher = finisher;
	}

	public void setClassPathTypeText(String classPathTypeText) {
		this.classPathTypeText = classPathTypeText;
	}

	public void setClassPathTypeTitleText(String classPathTypeTitleText) {
		this.classPathTypeTitleText = classPathTypeTitleText;
	}

	private final IWizardPage getClassPathContinuation(ClassPathLocationIDEProperty property) {
		if (continuation == null) {
			return null;
		}
		return continuation.getWizardContinuation(property);
	}

	@Override
	public void addPages(Wizard wizard) {
		wizard.addPage(classPathTypeChooser);
		wizard.addPage(classPathNestVersionChooser);
		wizard.addPage(classPathFileArchiveChooser);
		wizard.addPage(classPathNetworkArchiveChooser);
	}

	public class ClassPathTypeChooserWizardPage extends EclipseSakerWizardPage
			implements ClassPathLocationWizardResult, FinishablePage {
		private static final String TYPE_JAVA_ARCHIVE = "Java Archive";
		private static final String TYPE_NETWORK_ARCHIVE_HTTP = "Network archive (HTTP)";
		private static final String TYPE_NEST_REPOSITORY_CLASS_PATH = "Nest repository class path";
		private static final String TYPE_SAKER_SCRIPT_CLASS_PATH = "SakerScript class path";

		private List list;

		private final Map<String, Supplier<IWizardPage>> selectionPageSuppliers = new LinkedHashMap<>();

		@Override
		public String getTitle() {
			return classPathTypeTitleText + " class path";
		}

		@Override
		public String getDescription() {
			return "Choose the class path type of the " + classPathTypeText + ".";
		}

		@Override
		public void createControl(Composite parent) {
			selectionPageSuppliers.put(TYPE_JAVA_ARCHIVE, Functionals.valSupplier(classPathFileArchiveChooser));
			selectionPageSuppliers.put(TYPE_NETWORK_ARCHIVE_HTTP,
					Functionals.valSupplier(classPathNetworkArchiveChooser));
			if (!((flags & FLAG_NO_NEST_REPOSITORY_CLASSPATH) == FLAG_NO_NEST_REPOSITORY_CLASSPATH)) {
				selectionPageSuppliers.put(TYPE_NEST_REPOSITORY_CLASS_PATH,
						Functionals.valSupplier(classPathNestVersionChooser));
			}
			if (!((flags & FLAG_NO_SAKERSCRIPT_CLASSPATH) == FLAG_NO_SAKERSCRIPT_CLASSPATH)) {
				selectionPageSuppliers.put(TYPE_SAKER_SCRIPT_CLASS_PATH,
						() -> getClassPathContinuation(new BuiltinScriptingLanguageClassPathLocationIDEProperty()));
			}

			list = new List(parent, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
			list.setItems(selectionPageSuppliers.keySet().toArray(ObjectUtils.EMPTY_STRING_ARRAY));
			list.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
				getContainer().updateButtons();
			}));
			list.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					IWizardPage np = getNextPage();
					IWizardContainer container = getContainer();
					if (np != null) {
						container.showPage(np);
					} else if (canFinishPage() && performFinish()) {
						container.getShell().close();
					}
				}
			});

			if (editedClassPathLocationProperty != null) {
				editedClassPathLocationProperty.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
					@Override
					public Void visit(JarClassPathLocationIDEProperty property, Void param) {
						list.setSelection(
								ObjectUtils.indexOfIterable(selectionPageSuppliers.keySet(), TYPE_JAVA_ARCHIVE));
						return null;
					}

					@Override
					public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
						list.setSelection(ObjectUtils.indexOfIterable(selectionPageSuppliers.keySet(),
								TYPE_NETWORK_ARCHIVE_HTTP));
						return null;
					}

					@Override
					public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
						//ignore selection
						list.setSelection(ObjectUtils.indexOfIterable(selectionPageSuppliers.keySet(),
								TYPE_SAKER_SCRIPT_CLASS_PATH));
						return null;
					}

					@Override
					public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
						list.setSelection(ObjectUtils.indexOfIterable(selectionPageSuppliers.keySet(),
								TYPE_NEST_REPOSITORY_CLASS_PATH));
						return null;
					}
				}, null);
			}

			setControl(list);
		}

		@Override
		public boolean canFlipToNextPage() {
			return super.canFlipToNextPage() && list.getSelectionIndex() >= 0;
		}

		private IWizardPage getNextPageFromSelection() {
			return selectionPageSuppliers.getOrDefault(getSelectedElement(), Functionals.nullSupplier()).get();
		}

		@Override
		public IWizardPage getNextPage() {
			return getNextPageFromSelection();
		}

		private String getSelectedElement() {
			int selection = list.getSelectionIndex();
			if (selection < 0) {
				return null;
			}
			return list.getItems()[selection];
		}

		@Override
		public ClassPathLocationIDEProperty getClassPathLocation() {
			String sel = getSelectedElement();
			if (sel == null) {
				return null;
			}
			switch (sel) {
				case TYPE_NEST_REPOSITORY_CLASS_PATH: {
					return new NestRepositoryClassPathLocationIDEProperty();
				}
				case TYPE_SAKER_SCRIPT_CLASS_PATH: {
					return new BuiltinScriptingLanguageClassPathLocationIDEProperty();
				}
				default: {
					break;
				}
			}
			return null;
		}

		@Override
		public ClassPathServiceEnumeratorIDEProperty inferServiceProperty() {
			String sel = getSelectedElement();
			if (sel == null) {
				return null;
			}
			switch (sel) {
				case TYPE_NEST_REPOSITORY_CLASS_PATH: {
					return new NestRepositoryFactoryServiceEnumeratorIDEProperty();
				}
				case TYPE_SAKER_SCRIPT_CLASS_PATH: {
					return new BuiltinScriptingLanguageServiceEnumeratorIDEProperty();
				}
				default: {
					break;
				}
			}
			return null;
		}

		@Override
		public boolean canFinishPage() {
			return finisher != null && getClassPathLocation() != null;
		}

		@Override
		public boolean performFinish() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return false;
			}
			finisher.accept(this, cplocation);
			return true;
		}
	}

	public class NestRepositoryVersionChoosingWizardPage extends EclipseSakerWizardPage
			implements ClassPathLocationWizardResult, FinishablePage {
		private Text versionText;

		public NestRepositoryVersionChoosingWizardPage() {
		}

		@Override
		public String getTitle() {
			return "Nest repository version";
		}

		@Override
		public String getDescription() {
			return "Set the repository version to use.";
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginLeft = 5;
			layout.marginRight = 5;
			composite.setLayout(layout);

			GridData labelgd = new GridData();

			Label archivepathlabel = new Label(composite, SWT.NONE);
			archivepathlabel.setText("Nest repository version:");
			archivepathlabel.setLayoutData(labelgd);

			versionText = new Text(composite, SWT.BORDER);
			versionText.setMessage("Version number");
			GridData textgd = new GridData();
			textgd.grabExcessHorizontalSpace = true;
			textgd.horizontalAlignment = GridData.FILL;
			versionText.setLayoutData(textgd);
			versionText.addListener(SWT.Modify, new WizardPageButtonsUpdatingListener(this));

			Label definfolabel = new Label(composite, SWT.NONE);
			definfolabel.setText("Default version: " + NestRepositoryClassPathLocation.DEFAULT_VERSION
					+ " (leave text box empty to automatically use it)");
			definfolabel.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

			if (editedClassPathLocationProperty != null) {
				editedClassPathLocationProperty.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
					@Override
					public Void visit(JarClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
						String ver = property.getVersion();
						if (ver != null) {
							versionText.setText(ver);
						}
						return null;
					}
				}, null);
			}

			setControl(composite);
		}

		private boolean isPageValid() {
			String verstr = versionText.getText();
			if (ObjectUtils.isNullOrEmpty(verstr)) {
				return true;
			}
			if (!NestRepositoryClassPathLocationIDEProperty.isValidVersionNumber(verstr)) {
				return false;
			}
			return true;
		}

		@Override
		public boolean canFlipToNextPage() {
			return super.canFlipToNextPage() && isPageValid();
		}

		@Override
		public boolean canFinishPage() {
			return finisher != null && isPageValid();
		}

		@Override
		public boolean performFinish() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return false;
			}
			finisher.accept(this, cplocation);
			return true;
		}

		@Override
		public ClassPathLocationIDEProperty getClassPathLocation() {
			String verstr = versionText.getText();
			if (ObjectUtils.isNullOrEmpty(verstr)) {
				return new NestRepositoryClassPathLocationIDEProperty();
			}
			if (NestRepositoryClassPathLocationIDEProperty.isValidVersionNumber(verstr)) {
				return new NestRepositoryClassPathLocationIDEProperty(verstr);
			}
			return null;
		}

		@Override
		public IWizardPage getNextPage() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return null;
			}
			return getClassPathContinuation(cplocation);
		}

		@Override
		public ClassPathServiceEnumeratorIDEProperty inferServiceProperty() {
			return new NestRepositoryFactoryServiceEnumeratorIDEProperty();
		}
	}

	public class FileChoosingWizardPage extends EclipseSakerWizardPage
			implements ClassPathLocationWizardResult, FinishablePage {

		private Text pathText;
		private Combo endpointCombo;
		private Button browseButton;

		private java.util.List<String> itemEndpointNames;

		public FileChoosingWizardPage() {
		}

		@Override
		public String getTitle() {
			return "Java Archive class path";
		}

		@Override
		public String getDescription() {
			return "Choose the JAR file which is used to load the " + classPathTypeText + " from.";
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginLeft = 5;
			layout.marginRight = 5;
			composite.setLayout(layout);

			Label filesystemendpointlabel = new Label(composite, SWT.NONE);
			filesystemendpointlabel.setText("File system endpoint:");
			filesystemendpointlabel.setLayoutData(GridDataFactory.swtDefaults().hint(120, SWT.DEFAULT).create());
			endpointCombo = new Combo(composite, SWT.BORDER | SWT.READ_ONLY | SWT.DROP_DOWN);
			endpointCombo.setLayoutData(GridDataFactory.swtDefaults().span(2, 1).create());
			endpointCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
				updateDialogButtons();
			}));

			itemEndpointNames = new ArrayList<>();
			java.util.List<String> items = new ArrayList<>();
			items.add(PathConfigurationProjectPropertyPage.LABEL_PROJECT_RELATIVE);
			itemEndpointNames.add(SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE);
			items.add(PathConfigurationProjectPropertyPage.LABEL_LOCAL_FILESYSTEM);
			itemEndpointNames.add(SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM);
			Set<? extends DaemonConnectionIDEProperty> connectionsprop = project.getIDEProjectProperties()
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

			GridData labelgd = new GridData();

			Label archivepathlabel = new Label(composite, SWT.NONE);
			archivepathlabel.setText("Archive path:");
			archivepathlabel.setLayoutData(labelgd);

			pathText = new Text(composite, SWT.BORDER);
			pathText.setMessage("JAR location");
			GridData textgd = new GridData();
			textgd.grabExcessHorizontalSpace = true;
			textgd.horizontalAlignment = GridData.FILL;
			pathText.setLayoutData(textgd);
			pathText.addListener(SWT.Modify, new WizardPageButtonsUpdatingListener(this));

			browseButton = new Button(composite, SWT.PUSH);
			browseButton.setText("Browse...");
			browseButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
				String endpointname = getPathEndpointSelectionConnectionName();
				if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(endpointname)) {
					FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
					dialog.setText("Open");
					dialog.setFilterExtensions(new String[] { "*.jar", "*.*" });
					String selected = dialog.open();
					if (selected != null) {
						try {
							SakerPath selectedpath = SakerPath.valueOf(selected);
							pathText.setText(selectedpath.toString());
							endpointCombo
									.select(itemEndpointNames.indexOf(SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM));
						} catch (RuntimeException e) {
							//if any parsing error happens
							project.displayException(SakerLog.SEVERITY_WARNING, "Failed to parse path: " + selected, e);
						}
					}
				} else if (ObjectUtils.isNullOrEmpty(endpointname)
						|| SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(endpointname)) {
					ElementTreeSelectionDialog elemtreedialog = new ElementTreeSelectionDialog(getShell(),
							WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider(),
							new WorkbenchContentProvider() {
								@Override
								public Object[] getElements(Object element) {
									return filterResult(super.getElements(element));
								}

								private Object[] filterResult(Object[] result) {
									if (ObjectUtils.isNullOrEmpty(result)) {
										return result;
									}
									java.util.List<IResource> nresults = new ArrayList<>();
									for (Object resobj : result) {
										if (!(resobj instanceof IResource)) {
											continue;
										}
										IResource res = ((IResource) resobj);
										if (res.getType() == IResource.FILE) {
											if (!FileUtils.hasExtensionIgnoreCase(res.getName(), "jar")) {
												continue;
											}
										}
										nresults.add(res);
									}
									nresults.sort((l, r) -> {
										int ltype = l.getType();
										int rtype = r.getType();
										if (ltype != rtype) {
											if (ltype == IResource.FOLDER) {
												return -1;
											}
											if (rtype == IResource.FOLDER) {
												return 1;
											}
										}
										return l.getName().compareToIgnoreCase(r.getName());
									});
									return nresults.toArray();
								}

								@Override
								public Object[] getChildren(Object element) {
									return filterResult(super.getChildren(element));
								}

							});
					elemtreedialog.setInput(project.getProject());
					elemtreedialog.setValidator(sel -> {
						if (sel.length != 1) {
							return new Status(Status.ERROR, Activator.PLUGIN_ID, "");
						}
						IResource res = (IResource) sel[0];
						if (res.getType() != IResource.FILE) {
							return new Status(Status.ERROR, Activator.PLUGIN_ID, "");
						}
						return new Status(Status.OK, Activator.PLUGIN_ID, "");
					});
					elemtreedialog.setMessage("Select JAR class path in project: " + project.getProject().getName());
					elemtreedialog.setTitle("JAR class path");
					int openres = elemtreedialog.open();
					if (openres == IDialogConstants.OK_ID) {
						Object[] dialogres = elemtreedialog.getResult();
						if (dialogres.length == 1) {
							IResource res = (IResource) dialogres[0];
							pathText.setText(SakerPath.valueOf(res.getProjectRelativePath().toString()).toString());
							//update the combo too to ensure project relative endpoint if the dialog was not opened for it
							endpointCombo
									.select(itemEndpointNames.indexOf(SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE));
						}
					}
				} else {
					//XXX handle. make the endpoints browseable somehow? requires connection though
				}
				updateDialogButtons();
			}));

			String[] endpointselection = { SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE };

			if (editedClassPathLocationProperty != null) {
				editedClassPathLocationProperty.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
					@Override
					public Void visit(JarClassPathLocationIDEProperty property, Void param) {
						String connectionname = property.getConnectionName();
						if (ObjectUtils.isNullOrEmpty(connectionname)) {
							//select nothing
						} else {
							int idx = itemEndpointNames.indexOf(connectionname);
							if (idx < 0) {
								//insert the not found connection name to last
								idx = items.size();
								items.add(connectionname + " @<not-found>");
								itemEndpointNames.add(connectionname);
							}
						}
						endpointselection[0] = connectionname;

						pathText.setText(ObjectUtils.nullDefault(property.getJarPath(), ""));
						return null;
					}

					@Override
					public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
						return null;
					}
				}, null);
			}

			endpointCombo.setItems(items.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
			endpointCombo.select(itemEndpointNames.indexOf(endpointselection[0]));
			updateDialogButtons();

			setControl(composite);
		}

		private void updateDialogButtons() {
			String connname = getPathEndpointSelectionConnectionName();
			if (ObjectUtils.isNullOrEmpty(connname) || SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(connname)
					|| SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(connname)) {
				browseButton.setEnabled(true);
			} else {
				browseButton.setEnabled(false);
			}
			getContainer().updateButtons();
		}

		private boolean isPageValid() {
			String pathstr = pathText.getText();
			if (ObjectUtils.isNullOrEmpty(pathstr)) {
				return false;
			}
			SakerPath path;
			try {
				path = SakerPath.valueOf(pathstr);
			} catch (RuntimeException e) {
				// failed to parse
				return false;
			}
			String connname = getPathEndpointSelectionConnectionName();
			if (ObjectUtils.isNullOrEmpty(connname)) {
				return false;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(connname)) {
				if (!path.isRelative()) {
					return false;
				}
				return Files.isRegularFile(project.getProjectPath().resolve(path.toString()));
			}
			if (!path.isAbsolute()) {
				return false;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(connname)) {
				try {
					return LocalFileProvider.getInstance().getFileAttributes(path).isRegularFile();
				} catch (IOException e) {
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean canFlipToNextPage() {
			return super.canFlipToNextPage() && isPageValid();
		}

//		@Override
//		public boolean canFinishPage() {
//			return isSelectedFileValid();
//		}

		@Override
		public ClassPathLocationIDEProperty getClassPathLocation() {
			String clientname = getPathEndpointSelectionConnectionName();
			SakerPath jarpath = SakerIDESupportUtils.tryParsePath(pathText.getText());
			if (jarpath == null) {
				return null;
			}
			ClassPathLocationIDEProperty cplocation = new JarClassPathLocationIDEProperty(clientname,
					jarpath.toString());
			return cplocation;
		}

		private String getPathEndpointSelectionConnectionName() {
			int endpointidx = endpointCombo.getSelectionIndex();
			if (endpointidx < 0) {
				return null;
			}
			return itemEndpointNames.get(endpointidx);
		}

		@Override
		public IWizardPage getNextPage() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return null;
			}
			return getClassPathContinuation(cplocation);
		}

		@Override
		public boolean canFinishPage() {
			return finisher != null && isPageValid();
		}

		@Override
		public boolean performFinish() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return false;
			}
			finisher.accept(this, cplocation);
			return true;
		}

//		@Override
//		public boolean performFinish() {
//			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
//			if (cplocation == null) {
//				return false;
//			}
//			String repositoryid = null;
//			ClassPathServiceEnumeratorIDEProperty serviceenumerator = new ServiceLoaderClassPathEnumeratorIDEProperty(
//					SakerRepositoryFactory.class.getName());
//			RepositoryIDEProperty property = new RepositoryIDEProperty(cplocation, repositoryid, serviceenumerator);
//			finish(property);
//			return true;
//		}

	}

	public class NetworkArchiveChoosingWizardPage extends EclipseSakerWizardPage
			implements ClassPathLocationWizardResult, FinishablePage {
		private Text urlText;

		public NetworkArchiveChoosingWizardPage() {
		}

		@Override
		public String getTitle() {
			return "HTTP class path";
		}

		@Override
		public String getDescription() {
			return "Specify the web address of the " + classPathTypeText + " where it can be downloaded from.";
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginLeft = 5;
			layout.marginRight = 5;
			composite.setLayout(layout);

			Label l = new Label(composite, SWT.NONE);
			l.setText("Archive HTTP/HTTPS URL:");
			GridData labelgd = new GridData();
			l.setLayoutData(labelgd);

			urlText = new Text(composite, SWT.BORDER);
			urlText.setMessage("https://example.com/classpath.jar");
			GridData textgd = new GridData();
			textgd.grabExcessHorizontalSpace = true;
			textgd.horizontalAlignment = GridData.FILL;
			urlText.setLayoutData(textgd);
			Listener buttonsupdatinglistener = new WizardPageButtonsUpdatingListener(this);
			urlText.addListener(SWT.Modify, buttonsupdatinglistener);

			if (editedClassPathLocationProperty != null) {
				editedClassPathLocationProperty.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
					@Override
					public Void visit(JarClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
						urlText.setText(ObjectUtils.nullDefault(property.getUrl(), ""));
						return null;
					}

					@Override
					public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
						return null;
					}

					@Override
					public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
						return null;
					}
				}, null);
			}

			setControl(composite);
		}

		@Override
		public boolean canFlipToNextPage() {
			if (!super.canFlipToNextPage()) {
				return false;
			}
			try {
				HttpUrlJarFileClassPathLocation.requireHttpProtocol(getURL());
			} catch (MalformedURLException | IllegalArgumentException | NullPointerException e) {
				return false;
			}
			return true;
		}

//		@Override
//		public boolean canFinishPage() {
//			try {
//				HttpUrlJarFileClassPathLocation.requireHttpProtocol(getURL());
//			} catch (MalformedURLException | IllegalArgumentException | NullPointerException e) {
//				return false;
//			}
//			return true;
//		}

		private URL getURL() throws MalformedURLException {
			URL url = new URL(urlText.getText());
			String protocol = url.getProtocol();
			if (!"http".equals(protocol) && !"https".equals(protocol)) {
				throw new MalformedURLException("Expected http or https protocols: " + url);
			}
			return url;
		}

		@Override
		public ClassPathLocationIDEProperty getClassPathLocation() {
			try {
				return new HttpUrlJarClassPathLocationIDEProperty(getURL().toString());
			} catch (MalformedURLException e) {
			}
			return null;
		}

		@Override
		public IWizardPage getNextPage() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return null;
			}
			return getClassPathContinuation(cplocation);
		}

		@Override
		public boolean canFinishPage() {
			return finisher != null && getClassPathLocation() != null;
		}

		@Override
		public boolean performFinish() {
			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
			if (cplocation == null) {
				return false;
			}
			finisher.accept(this, cplocation);
			return true;
		}

//		@Override
//		public boolean performFinish() {
//			ClassPathLocationIDEProperty cplocation = getClassPathLocation();
//			if (cplocation == null) {
//				return false;
//			}
//			String repositoryid = null;
//			ClassPathServiceEnumeratorIDEProperty serviceenumerator = new ServiceLoaderClassPathEnumeratorIDEProperty(
//					SakerRepositoryFactory.class.getName());
//			RepositoryIDEProperty property = new RepositoryIDEProperty(cplocation, repositoryid, serviceenumerator);
//			finish(property);
//			return true;
//		}
	}

	public interface ClassPathLocationWizardResult {
		public ClassPathLocationIDEProperty getClassPathLocation();

		public default ClassPathServiceEnumeratorIDEProperty inferServiceProperty() {
			return null;
		}
	}

}
