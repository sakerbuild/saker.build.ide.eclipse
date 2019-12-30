package saker.build.ide.eclipse.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.file.path.WildcardPath;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.eclipse.properties.RepositoriesProjectPropertyPage.ClassPathLocationToStringVisitor;
import saker.build.ide.eclipse.properties.RepositoriesProjectPropertyPage.ClassPathServiceEnumeratorTitleVisitor;
import saker.build.ide.eclipse.properties.RepositoriesProjectPropertyPage.ClassPathServiceEnumeratorToStringVisitor;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ScriptConfigurationProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.scriptConfigurationProjectPropertyPage";

	private static final AtomicIntegerFieldUpdater<ScriptConfigurationProjectPropertyPage> AIFU_propertyCounter = AtomicIntegerFieldUpdater
			.newUpdater(ScriptConfigurationProjectPropertyPage.class, "propertyCounter");
	@SuppressWarnings("unused")
	private volatile int propertyCounter;

	private EclipseSakerIDEProject ideProject;
	private final List<TreePropertyItem<ScriptConfigurationIDEProperty>> scriptConfigs = new ArrayList<>();
	private final List<String> exclusionWildcards = new ArrayList<>();

	private TabFolder tabFolder;
	private TreeViewer scriptingTreeViewer;
	private org.eclipse.swt.widgets.List exclusionList;

	private TabItem configurationsTabItem;
	private TabItem modellingTabItem;

	public ScriptConfigurationProjectPropertyPage() {
		super();
	}

	@Override
	public void applyData(Object data) {
		if (data instanceof PropertiesValidationErrorResult) {
			PropertiesValidationErrorResult err = (PropertiesValidationErrorResult) data;
			if (err.errorType.startsWith(SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION)) {
				tabFolder.setSelection(modellingTabItem);
				exclusionList.setSelection(new String[] { (String) err.relatedSubject });
			} else if (err.relatedSubject instanceof ScriptConfigurationIDEProperty) {
				for (TreePropertyItem<ScriptConfigurationIDEProperty> item : scriptConfigs) {
					if (err.relatedSubject.equals(item.property)) {
						scriptingTreeViewer.expandToLevel(item, 1);
						TreePath treepath;
						if (err.errorType
								.startsWith(SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD)) {
							treepath = new TreePath(new Object[] { item, new ScriptsWildcardTreeElement(item) });
						} else if (err.errorType
								.startsWith(SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH)) {
							treepath = new TreePath(new Object[] { item, new ClassPathTreeElement(item) });
						} else if (err.errorType
								.startsWith(SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE)) {
							treepath = new TreePath(new Object[] { item, new ServiceEnumeratorTreeElement(item) });
						} else if (err.errorType
								.startsWith(SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_OPTIONS)) {
							treepath = new TreePath(new Object[] { item, new OptionsTreeElement(item) });
						} else {
							treepath = new TreePath(new Object[] { item });
						}
						scriptingTreeViewer.setSelection(new TreeSelection(treepath));
						break;
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
			initializeScriptConfigs(ideprops);
		}
	}

	private void initializeScriptConfigs(IDEProjectProperties ideprops) {
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigsprop = ideprops.getScriptConfigurations();
		if (!ObjectUtils.isNullOrEmpty(scriptconfigsprop)) {
			for (ScriptConfigurationIDEProperty sconfig : scriptconfigsprop) {
				this.scriptConfigs.add(new TreePropertyItem<>(AIFU_propertyCounter.getAndIncrement(this), sconfig));
			}
		}
		Set<String> exclusions = ideprops.getScriptModellingExclusions();
		if (!ObjectUtils.isNullOrEmpty(exclusions)) {
			exclusionWildcards.addAll(exclusions);
		}
	}

	private class ScriptsWildcardTreeElement extends TreePropertyItemChildElement<ScriptConfigurationIDEProperty> {
		public ScriptsWildcardTreeElement(TreePropertyItem<ScriptConfigurationIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			return ObjectUtils.nullDefault(treeItem.property.getScriptsWildcard(), "");
		}

		@Override
		public String getTitle() {
			return "Affected scripts";
		}

		@Override
		public void edit(Shell shell, Consumer<? super ScriptConfigurationIDEProperty> editfinalizer) {
			WizardDialog dialog = new WizardDialog(shell, new ScriptConfigAdditionWizard(ideProject, treeItem.property,
					ScriptConfigAdditionWizard.EDIT_SCRIPTCONFIG) {
				@Override
				protected void finish(ScriptConfigurationIDEProperty property) {
					editfinalizer.accept(property);
				}
			});
			dialog.open();
		}
	}

	private class ClassPathTreeElement extends TreePropertyItemChildElement<ScriptConfigurationIDEProperty> {

		public ClassPathTreeElement(TreePropertyItem<ScriptConfigurationIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			ClassPathLocationIDEProperty cplocation = treeItem.property.getClassPathLocation();
			if (cplocation == null) {
				return "";
			}
			return cplocation.accept(ClassPathLocationToStringVisitor.INSTANCE, null);
		}

		@Override
		public String getTitle() {
			return "Classpath";
		}

		@Override
		public void edit(Shell shell, Consumer<? super ScriptConfigurationIDEProperty> editfinalizer) {
			int editflags = ScriptConfigAdditionWizard.EDIT_CLASSPATH;
			if (ServiceLocationAdditionWizard.isCombinationLockedClassPath(treeItem.property.getClassPathLocation())
					|| ServiceLocationAdditionWizard
							.isCombinationLockedServiceEnumerator(treeItem.property.getServiceEnumerator())) {
				editflags |= ScriptConfigAdditionWizard.EDIT_SERVICE;
			}
			WizardDialog dialog = new WizardDialog(shell,
					new ScriptConfigAdditionWizard(ideProject, treeItem.property, editflags) {
						@Override
						protected void finish(ScriptConfigurationIDEProperty property) {
							editfinalizer.accept(property);
						}
					});
			dialog.open();
		}
	}

	private class ServiceEnumeratorTreeElement extends TreePropertyItemChildElement<ScriptConfigurationIDEProperty> {
		public ServiceEnumeratorTreeElement(TreePropertyItem<ScriptConfigurationIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = treeItem.property.getServiceEnumerator();
			if (serviceenumerator == null) {
				return "";
			}
			return serviceenumerator.accept(ClassPathServiceEnumeratorToStringVisitor.INSTANCE, null);
		}

		@Override
		public String getTitle() {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = treeItem.property.getServiceEnumerator();
			if (serviceenumerator == null) {
				return "Class";
			}
			return serviceenumerator.accept(ClassPathServiceEnumeratorTitleVisitor.INSTANCE, null);
		}

		@Override
		public void edit(Shell shell, Consumer<? super ScriptConfigurationIDEProperty> editfinalizer) {
			int editflags = ScriptConfigAdditionWizard.EDIT_SERVICE;
			if (ServiceLocationAdditionWizard.isCombinationLockedClassPath(treeItem.property.getClassPathLocation())
					|| ServiceLocationAdditionWizard
							.isCombinationLockedServiceEnumerator(treeItem.property.getServiceEnumerator())) {
				editflags |= ScriptConfigAdditionWizard.EDIT_CLASSPATH;
			}
			WizardDialog dialog = new WizardDialog(shell,
					new ScriptConfigAdditionWizard(ideProject, treeItem.property, editflags) {
						@Override
						protected void finish(ScriptConfigurationIDEProperty property) {
							editfinalizer.accept(property);
						}
					});
			dialog.open();
		}
	}

	private class OptionsTreeElement extends TreePropertyItemChildElement<ScriptConfigurationIDEProperty> {
		public OptionsTreeElement(TreePropertyItem<ScriptConfigurationIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			Set<? extends Entry<String, String>> options = treeItem.property.getScriptOptions();
			if (ObjectUtils.isNullOrEmpty(options)) {
				return "No options defined.";
			}
			int s = options.size();
			switch (s) {
				case 0: {
					return "No options defined.";
				}
				case 1: {
					return "1 option defined.";
				}
				default: {
					return s + " options defined.";
				}
			}
		}

		@Override
		public String getTitle() {
			return "Script options";
		}

		@Override
		public void edit(Shell shell, Consumer<? super ScriptConfigurationIDEProperty> editfinalizer) {
			WizardDialog dialog = new WizardDialog(shell, new ScriptConfigAdditionWizard(ideProject, treeItem.property,
					ScriptConfigAdditionWizard.EDIT_SCRIPTCONFIG) {
				@Override
				protected void finish(ScriptConfigurationIDEProperty property) {
					editfinalizer.accept(property);
				}
			});
			dialog.open();
		}
	}

	private final class ScriptingTreeLabelProvider extends LabelProvider implements IStyledLabelProvider {
		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof TreePropertyItem) {
				@SuppressWarnings("unchecked")
				TreePropertyItem<ScriptConfigurationIDEProperty> property = (TreePropertyItem<ScriptConfigurationIDEProperty>) element;

				StyledString styledString = new StyledString();
				styledString.append(ObjectUtils.nullDefault(property.property.getScriptsWildcard(), ""));
				styledString.append(
						" - " + property.property.getClassPathLocation()
								.accept(ClassPathLocationToStringVisitor.INSTANCE, null),
						StyledString.QUALIFIER_STYLER);
				return styledString;
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

	@Override
	protected Control createContents(Composite parent) {
		if (this.ideProject == null) {
			Label resultlabel = new Label(parent, SWT.NONE);
			resultlabel.setText("Project doesn't have saker.build nature associated with it.");
			return resultlabel;
		}

		tabFolder = new TabFolder(parent, SWT.NONE);
		GridData grabfillgriddata = new GridData();
		grabfillgriddata.grabExcessHorizontalSpace = true;
		grabfillgriddata.grabExcessVerticalSpace = true;
		grabfillgriddata.horizontalAlignment = GridData.FILL;
		grabfillgriddata.verticalAlignment = GridData.FILL;
		tabFolder.setLayoutData(grabfillgriddata);

		{
			configurationsTabItem = new TabItem(tabFolder, SWT.NONE);
			configurationsTabItem.setText("Configurations");

			Composite configscomposite = new Composite(tabFolder, SWT.NONE);
			configscomposite.setLayout(new GridLayout());

			scriptingTreeViewer = new TreeViewer(
					new Tree(configscomposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.BORDER));
			scriptingTreeViewer.setComparer(new TreePropertyItemIdentifierElementComparer());
			scriptingTreeViewer.getTree().setLayoutData(grabfillgriddata);
			scriptingTreeViewer.setContentProvider(new ITreeContentProvider() {
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
						TreePropertyItem<ScriptConfigurationIDEProperty> propertyitem = (TreePropertyItem<ScriptConfigurationIDEProperty>) parentElement;
						return new Object[] { new ScriptsWildcardTreeElement(propertyitem),
								new ClassPathTreeElement(propertyitem), new ServiceEnumeratorTreeElement(propertyitem),
								new OptionsTreeElement(propertyitem) };
					}
					return null;
				}
			});
			scriptingTreeViewer
					.setLabelProvider(new DelegatingStyledCellLabelProvider(new ScriptingTreeLabelProvider()));
			scriptingTreeViewer.addDoubleClickListener(ev -> {
				ISelection selection = ev.getSelection();
				if (!(selection instanceof IStructuredSelection)) {
					return;
				}
				IStructuredSelection treesel = (IStructuredSelection) selection;
				Object elem = treesel.getFirstElement();
				if (elem == null) {
					return;
				}
				if (elem instanceof TreePropertyItem) {
					@SuppressWarnings("unchecked")
					TreePropertyItem<ScriptConfigurationIDEProperty> treeitem = (TreePropertyItem<ScriptConfigurationIDEProperty>) elem;
					//double click on a repository configuration
					WizardDialog dialog = new WizardDialog(getShell(), new ScriptConfigAdditionWizard(ideProject,
							treeitem.property, ScriptConfigAdditionWizard.EDIT_ALL) {
						@Override
						protected void finish(ScriptConfigurationIDEProperty property) {
							treeitem.property = property;
							refreshTreeViewer();
							validateProperties();
						}
					});
					dialog.open();
				} else if (elem instanceof TreePropertyItemChildElement) {
					//double click on a configuration property
					@SuppressWarnings("unchecked")
					TreePropertyItemChildElement<ScriptConfigurationIDEProperty> childelem = (TreePropertyItemChildElement<ScriptConfigurationIDEProperty>) elem;
					childelem.edit(getShell(), res -> {
						childelem.treeItem.property = res;
						refreshTreeViewer();
						validateProperties();
					});
				}
			});
			scriptingTreeViewer.getTree().addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					if (e.character == SWT.DEL) {
						ISelection selection = scriptingTreeViewer.getSelection();
						if (!(selection instanceof IStructuredSelection)) {
							return;
						}
						IStructuredSelection treesel = (IStructuredSelection) selection;
						Object elem = treesel.getFirstElement();
						if (elem == null) {
							return;
						}
						if (elem instanceof TreePropertyItem) {
							TreePropertyItem.deletePropertyItem(scriptConfigs, (TreePropertyItem<?>) elem);
							refreshTreeViewer();
							validateProperties();
							return;
						}
						//no delete support for other items
						return;
					}
				}
			});
			scriptingTreeViewer.setInput(scriptConfigs);

			Button addscriptconfigbutton = new Button(configscomposite, SWT.PUSH);
			addscriptconfigbutton.setText("Add script configuration...");
			addscriptconfigbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e1 -> {
				WizardDialog dialog = new WizardDialog(getShell(), new ScriptConfigAdditionWizard(ideProject) {
					@Override
					protected void finish(ScriptConfigurationIDEProperty property) {
						scriptConfigs.add(new TreePropertyItem<ScriptConfigurationIDEProperty>(
								AIFU_propertyCounter.getAndIncrement(ScriptConfigurationProjectPropertyPage.this),
								property));
						refreshTreeViewer();
						validateProperties();
					}
				});
				dialog.open();
			}));

			configurationsTabItem.setControl(configscomposite);
		}

		{
			modellingTabItem = new TabItem(tabFolder, SWT.NONE);
			modellingTabItem.setText("IDE modelling");

			Composite modellingcomposite = new Composite(tabFolder, SWT.NONE);
			modellingcomposite.setLayout(new GridLayout());

			Group exclusionsgroup = new Group(modellingcomposite, SWT.NONE);
			exclusionsgroup.setLayoutData(grabfillgriddata);
			exclusionsgroup.setLayout(new GridLayout());
			exclusionsgroup.setText("Modelling exclusions");

			Label infolabel = new Label(exclusionsgroup, SWT.NONE);
			infolabel.setText(
					"The build scripts matching the specified wildcards won't be part of the script modelling.");

			exclusionList = new org.eclipse.swt.widgets.List(exclusionsgroup, SWT.BORDER | SWT.MULTI);
			exclusionList.setItems(exclusionWildcards.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
			exclusionList.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					int idx = exclusionList.getSelectionIndex();
					if (idx < 0) {
						return;
					}
					ExclusionDialog dialog = new ExclusionDialog(getShell(), exclusionWildcards.get(idx));
					if (dialog.open() == IDialogConstants.OK_ID) {
						exclusionWildcards.set(idx, dialog.getValue());
						exclusionList.setItems(exclusionWildcards.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
						validateProperties();
					}
				}
			});
			exclusionList.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					if (e.character != SWT.DEL) {
						return;
					}
					int[] selectedindices = exclusionList.getSelectionIndices();
					if (ObjectUtils.isNullOrEmpty(selectedindices)) {
						return;
					}
					Arrays.sort(selectedindices);
					//remove backwards, to avoid index shift due to removals
					for (int i = selectedindices.length - 1; i >= 0; i--) {
						exclusionWildcards.remove(selectedindices[i]);
					}
					exclusionList.setItems(exclusionWildcards.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
					validateProperties();
				}
			});
			exclusionList.setLayoutData(grabfillgriddata);

			Button addscriptconfigbutton = new Button(exclusionsgroup, SWT.PUSH);
			addscriptconfigbutton.setText("Add modelling exclusion...");
			addscriptconfigbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e1 -> {
				ExclusionDialog dialog = new ExclusionDialog(getShell(), "");
				if (dialog.open() == IDialogConstants.OK_ID) {
					exclusionWildcards.add(dialog.getValue());
					exclusionList.setItems(exclusionWildcards.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
					validateProperties();
				}
			}));

			modellingTabItem.setControl(modellingcomposite);
		}

		validateProperties();

		return tabFolder;
	}

	private void validateProperties() {
		Set<WildcardPath> wildcards = new TreeSet<>();
		for (TreePropertyItem<ScriptConfigurationIDEProperty> scitem : scriptConfigs) {
			String propwc = scitem.property.getScriptsWildcard();
			if (ObjectUtils.isNullOrEmpty(propwc)) {
				invalidateWithErrorMessage("Missing wildcard specification.");
				return;
			}
			try {
				WildcardPath wc = WildcardPath.valueOf(propwc);
				if (!wildcards.add(wc)) {
					invalidateWithErrorMessage("Duplicate wildcard specification: " + wc);
					return;
				}
			} catch (RuntimeException e) {
				invalidateWithErrorMessage("Invalid wildcard format: " + propwc);
				return;
			}
			Set<? extends Entry<String, String>> options = scitem.property.getScriptOptions();
			if (options != null) {
				Set<String> optkeys = new TreeSet<>();
				for (Entry<String, String> entry : options) {
					String key = entry.getKey();
					if (ObjectUtils.isNullOrEmpty(key)) {
						invalidateWithErrorMessage("Script configuration contains option with empty key: " + propwc);
						return;
					}
					if (!optkeys.add(key)) {
						invalidateWithErrorMessage(
								"Duplicate script configuration option key: " + key + " in " + propwc);
						return;
					}
				}
			}
			String cpvalidation = ValidationUtils.validateClassPathLocation(ideProject.getIDEProjectProperties(),
					scitem.property.getClassPathLocation(), ValidationUtils.VALIDATION_SCRIPTING, propwc);
			if (cpvalidation != null) {
				invalidateWithErrorMessage(cpvalidation);
				return;
			}
			String servicevalidation = ValidationUtils.validateServiceEnumerator(scitem.property.getServiceEnumerator(),
					ValidationUtils.VALIDATION_SCRIPTING, propwc);
			if (servicevalidation != null) {
				invalidateWithErrorMessage(servicevalidation);
				return;
			}
		}
		Set<WildcardPath> exclusions = new TreeSet<>();
		for (String excl : exclusionWildcards) {
			try {
				WildcardPath wc = WildcardPath.valueOf(excl);
				if (!exclusions.add(wc)) {
					invalidateWithErrorMessage("Duplicate exclusion wildcards: " + excl);
					return;
				}
			} catch (IllegalArgumentException e) {
				invalidateWithErrorMessage("Invalid exclusion wildcard format: " + excl);
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
		this.scriptConfigs.clear();
		this.exclusionWildcards.clear();
		initializeScriptConfigs(SimpleIDEProjectProperties.getDefaultsInstance());
		exclusionList.setItems(ObjectUtils.EMPTY_STRING_ARRAY);
		refreshTreeViewer();
		validateProperties();
	}

	private void refreshTreeViewer() {
		scriptingTreeViewer.refresh();
	}

	@Override
	public boolean performOk() {
		Set<ScriptConfigurationIDEProperty> scriptconfigurations = new LinkedHashSet<>();
		for (TreePropertyItem<ScriptConfigurationIDEProperty> scitem : scriptConfigs) {
			scriptconfigurations.add(scitem.property);
		}
		ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
				.setScriptConfigurations(scriptconfigurations)
				.setScriptModellingExclusions(new TreeSet<>(exclusionWildcards)).build());
		return true;
	}

	private class ExclusionDialog extends TitleAreaDialog {
		private Text wildcardText;

		private String value;

		public ExclusionDialog(Shell parentShell, String value) {
			super(parentShell);
			this.value = value;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Script modelling exclusion");
		}

		@Override
		public void create() {
			super.create();
			setTitle("Script modelling exclusion");
			resetMessage();
		}

		private void resetMessage() {
			setMessage("Specify a wildcard pattern to match excluded build scripts.", IMessageProvider.NONE);
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			GridLayout layout = new GridLayout(2, false);
			container.setLayout(layout);

			createWildcardText(container);

			Listener modifylistener = event -> {
				Button okbutton = getButton(IDialogConstants.OK_ID);
				if (okbutton == null) {
					return;
				}
				updateOkButton(okbutton);
			};
			wildcardText.addListener(SWT.Modify, modifylistener);

			return area;
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			Button result = super.createButton(parent, id, label, defaultButton);
			if (id == IDialogConstants.OK_ID) {
				updateOkButton(result);
			}
			return result;
		}

		private void updateOkButton(Button okbutton) {
			String key = wildcardText.getText();
			if (key.isEmpty()) {
				okbutton.setEnabled(false);
				resetMessage();
				return;
			}
			try {
				WildcardPath.valueOf(key);
				okbutton.setEnabled(true);
				resetMessage();
			} catch (IllegalArgumentException e) {
				setMessage("Invalid wildcard format.", IMessageProvider.ERROR);
				okbutton.setEnabled(false);
			}
		}

		private void createWildcardText(Composite container) {
			Label lbtFirstName = new Label(container, SWT.NONE);
			lbtFirstName.setText("Exclusion wildcard:");

			GridData dataFirstName = new GridData();
			dataFirstName.grabExcessHorizontalSpace = true;
			dataFirstName.horizontalAlignment = GridData.FILL;

			wildcardText = new Text(container, SWT.BORDER);
			wildcardText.setLayoutData(dataFirstName);
			String inittext = ObjectUtils.nullDefault(value, "");
			wildcardText.setText(inittext);
			wildcardText.setMessage("Wildcard pattern");
			wildcardText.setSelection(0, inittext.length());
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected void okPressed() {
			value = wildcardText.getText();
			super.okPressed();
		}

		public String getValue() {
			return value;
		}
	}

}