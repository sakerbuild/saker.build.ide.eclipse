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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
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
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.dialogs.PropertyPage;

import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class RepositoriesProjectPropertyPage extends PropertyPage {
	public static final String ID = "saker.build.ide.eclipse.properties.repositoriesProjectPropertyPage";

	private static final AtomicIntegerFieldUpdater<RepositoriesProjectPropertyPage> AIFU_propertyCounter = AtomicIntegerFieldUpdater
			.newUpdater(RepositoriesProjectPropertyPage.class, "propertyCounter");
	@SuppressWarnings("unused")
	private volatile int propertyCounter;

	private EclipseSakerIDEProject ideProject;
	private final List<TreePropertyItem<RepositoryIDEProperty>> repositories = new ArrayList<>();

	private TreeViewer repositoryTreeViewer;

	public RepositoriesProjectPropertyPage() {
		super();
	}

	@Override
	public void setElement(IAdaptable element) {
		super.setElement(element);
		ideProject = ImplActivator.getDefault().getOrCreateSakerProject(element.getAdapter(IProject.class));
		if (ideProject != null) {
			IDEProjectProperties ideprops = ideProject.getIDEProjectProperties();
			initializeRepositories(ideprops);
		}
	}

	@Override
	public void applyData(Object data) {
		if (data instanceof PropertiesValidationErrorResult) {
			PropertiesValidationErrorResult err = (PropertiesValidationErrorResult) data;
			if (err.relatedSubject instanceof RepositoryIDEProperty) {
				for (TreePropertyItem<RepositoryIDEProperty> item : repositories) {
					if (err.relatedSubject.equals(item.property)) {
						repositoryTreeViewer.expandToLevel(item, 1);
						TreePath treepath;
						switch (err.errorType) {
							case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_IDENTIFIER
									+ SakerIDEProject.E_DUPLICATE: {
								treepath = new TreePath(
										new Object[] { item, new RepositoryIdentifierTreeElement(item) });
								break;
							}
							default: {
								if (err.errorType.startsWith(
										SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH)) {
									treepath = new TreePath(new Object[] { item, new ClassPathTreeElement(item) });
								} else if (err.errorType.startsWith(
										SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE)) {
									treepath = new TreePath(
											new Object[] { item, new ServiceEnumeratorTreeElement(item) });
								} else {
									treepath = new TreePath(new Object[] { item });
								}
								break;
							}
						}
						repositoryTreeViewer.setSelection(new TreeSelection(treepath));
						break;
					}
				}
			}
		}
	}

	private void initializeRepositories(IDEProjectProperties ideprops) {
		Set<? extends RepositoryIDEProperty> proprepos = ideprops.getRepositories();
		if (!ObjectUtils.isNullOrEmpty(proprepos)) {
			for (RepositoryIDEProperty prop : proprepos) {
				this.repositories.add(new TreePropertyItem<>(AIFU_propertyCounter.getAndIncrement(this), prop));
			}
		}
	}

	private final class RepositoryTreeLabelProvider extends LabelProvider implements IStyledLabelProvider {
		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof TreePropertyItem) {
				@SuppressWarnings("unchecked")
				TreePropertyItem<RepositoryIDEProperty> property = (TreePropertyItem<RepositoryIDEProperty>) element;

				StyledString styledString = new StyledString();
				styledString.append(ObjectUtils.nullDefault(
						SakerIDESupportUtils.classPathLocationToLabel(property.property.getClassPathLocation()), ""));
				String id = property.property.getRepositoryIdentifier();
				if (!ObjectUtils.isNullOrEmpty(id)) {
					styledString.append(" @" + id, StyledString.QUALIFIER_STYLER);
				}
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

	private class ClassPathTreeElement extends TreePropertyItemChildElement<RepositoryIDEProperty> {

		public ClassPathTreeElement(TreePropertyItem<RepositoryIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			ClassPathLocationIDEProperty cploc = treeItem.property.getClassPathLocation();
			if (cploc == null) {
				return "";
			}
			return SakerIDESupportUtils.classPathLocationToLabel(cploc);
		}

		@Override
		public String getTitle() {
			return "Classpath";
		}

		@Override
		public void edit(Shell shell, Consumer<? super RepositoryIDEProperty> editfinalizer) {
			int editflags = RepositoryAdditionWizard.EDIT_CLASSPATH;
			if (ServiceLocationAdditionWizard.isCombinationLockedClassPath(treeItem.property.getClassPathLocation())
					|| ServiceLocationAdditionWizard
							.isCombinationLockedServiceEnumerator(treeItem.property.getServiceEnumerator())) {
				editflags |= RepositoryAdditionWizard.EDIT_SERVICE;
			}
			WizardDialog dialog = new WizardDialog(shell,
					new RepositoryAdditionWizard(ideProject, treeItem.property, editflags) {
						@Override
						protected void finish(RepositoryIDEProperty property) {
							editfinalizer.accept(property);
						}
					});
			dialog.open();
		}
	}

	private class RepositoryIdentifierTreeElement extends TreePropertyItemChildElement<RepositoryIDEProperty> {
		public RepositoryIdentifierTreeElement(TreePropertyItem<RepositoryIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			String identifier = treeItem.property.getRepositoryIdentifier();
			if (identifier == null) {
				return "No identifier/auto-generated";
			}
			return identifier;
		}

		@Override
		public String getTitle() {
			return "Identifier";
		}

		@Override
		public void edit(Shell shell, Consumer<? super RepositoryIDEProperty> editfinalizer) {
			WizardDialog dialog = new WizardDialog(shell, new RepositoryAdditionWizard(ideProject, treeItem.property,
					RepositoryAdditionWizard.EDIT_REPOSITORY_IDENTIFIER) {
				@Override
				protected void finish(RepositoryIDEProperty property) {
					editfinalizer.accept(property);
				}
			});
			dialog.open();
		}
	}

	private class ServiceEnumeratorTreeElement extends TreePropertyItemChildElement<RepositoryIDEProperty> {
		public ServiceEnumeratorTreeElement(TreePropertyItem<RepositoryIDEProperty> property) {
			super(property);
		}

		@Override
		public String getLabel() {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = treeItem.property.getServiceEnumerator();
			if (serviceenumerator == null) {
				return "";
			}
			return SakerIDESupportUtils.serviceEnumeratorToLabel(serviceenumerator);
		}

		@Override
		public String getTitle() {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator = treeItem.property.getServiceEnumerator();
			if (serviceenumerator == null) {
				return "Class";
			}
			return SakerIDESupportUtils.serviceEnumeratorToTitleLabel(serviceenumerator);
		}

		@Override
		public void edit(Shell shell, Consumer<? super RepositoryIDEProperty> editfinalizer) {
			int editflags = RepositoryAdditionWizard.EDIT_SERVICE;
			if (ServiceLocationAdditionWizard.isCombinationLockedClassPath(treeItem.property.getClassPathLocation())
					|| ServiceLocationAdditionWizard
							.isCombinationLockedServiceEnumerator(treeItem.property.getServiceEnumerator())) {
				editflags |= RepositoryAdditionWizard.EDIT_CLASSPATH;
			}
			WizardDialog dialog = new WizardDialog(shell,
					new RepositoryAdditionWizard(ideProject, treeItem.property, editflags) {
						@Override
						protected void finish(RepositoryIDEProperty property) {
							editfinalizer.accept(property);
						}
					});
			dialog.open();
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

		repositoryTreeViewer = new TreeViewer(
				new Tree(composite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.BORDER));
		repositoryTreeViewer.setComparer(new TreePropertyItemIdentifierElementComparer());
		repositoryTreeViewer.getTree().setLayoutData(data);
		repositoryTreeViewer.setContentProvider(new ITreeContentProvider() {
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
					TreePropertyItem<RepositoryIDEProperty> propertyitem = (TreePropertyItem<RepositoryIDEProperty>) parentElement;
					return new Object[] { new ClassPathTreeElement(propertyitem),
							new RepositoryIdentifierTreeElement(propertyitem),
							new ServiceEnumeratorTreeElement(propertyitem) };
				}
				return null;
			}
		});
		repositoryTreeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new RepositoryTreeLabelProvider()));
		repositoryTreeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				ISelection selection = event.getSelection();
				if (!(selection instanceof IStructuredSelection)) {
					return;
				}
				IStructuredSelection treesel = (IStructuredSelection) selection;
				Object elem = treesel.getFirstElement();
				if (elem == null) {
					return;
				}
				if (elem instanceof TreePropertyItem) {
					//double click on a repository configuration
					@SuppressWarnings("unchecked")
					TreePropertyItem<RepositoryIDEProperty> treeitem = (TreePropertyItem<RepositoryIDEProperty>) elem;
					WizardDialog dialog = new WizardDialog(getShell(), new RepositoryAdditionWizard(ideProject,
							treeitem.property, RepositoryAdditionWizard.EDIT_ALL) {
						@Override
						protected void finish(RepositoryIDEProperty property) {
							treeitem.property = property;
							refreshTreeViewer();
							validateProperties();
						}
					});
					dialog.open();
				} else if (elem instanceof TreePropertyItemChildElement) {
					//double click on a configuration property
					@SuppressWarnings("unchecked")
					TreePropertyItemChildElement<RepositoryIDEProperty> childelem = (TreePropertyItemChildElement<RepositoryIDEProperty>) elem;
					childelem.edit(getShell(), res -> {
						childelem.treeItem.property = res;
						refreshTreeViewer();
						validateProperties();
					});
				}
			}

			private void refreshTreeViewer() {
				repositoryTreeViewer.refresh();
			}
		});
		repositoryTreeViewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.DEL) {
					ISelection selection = repositoryTreeViewer.getSelection();
					if (!(selection instanceof IStructuredSelection)) {
						return;
					}
					IStructuredSelection treesel = (IStructuredSelection) selection;
					Object elem = treesel.getFirstElement();
					if (elem == null) {
						return;
					}
					if (elem instanceof TreePropertyItem) {
						TreePropertyItem.deletePropertyItem(repositories, (TreePropertyItem<?>) elem);
						repositoryTreeViewer.refresh();
						validateProperties();
						return;
					}
					//no delete support for other items
					return;
				}
			}
		});
		repositoryTreeViewer.setInput(repositories);

		addAddButton(composite);

		validateProperties();

		return composite;
	}

	private void validateProperties() {
		Set<String> ids = new TreeSet<>();
		for (TreePropertyItem<RepositoryIDEProperty> repoitem : repositories) {
			String repoid = repoitem.property.getRepositoryIdentifier();
			ClassPathLocationIDEProperty classpathlocation = repoitem.property.getClassPathLocation();
			String tagidentifier;
			if (!ObjectUtils.isNullOrEmpty(repoid)) {
				if (!ids.add(repoid)) {
					//already present
					invalidateWithErrorMessage("Duplicate repository identifier: " + repoid);
					return;
				}
				tagidentifier = repoid;
			} else {
				if (classpathlocation != null) {
					tagidentifier = SakerIDESupportUtils.classPathLocationToLabel(classpathlocation);
				} else {
					//XXX can we display something here? service enumerator doesn't really make sense
					tagidentifier = "";
				}
			}
			String cplocvalidation = ValidationUtils.validateClassPathLocation(ideProject.getIDEProjectProperties(),
					classpathlocation, ValidationUtils.VALIDATION_REPOSITORY, tagidentifier);
			if (cplocvalidation != null) {
				invalidateWithErrorMessage(cplocvalidation);
				return;
			}
			String servicevalidation = ValidationUtils.validateServiceEnumerator(
					repoitem.property.getServiceEnumerator(), ValidationUtils.VALIDATION_REPOSITORY, tagidentifier);
			if (servicevalidation != null) {
				invalidateWithErrorMessage(servicevalidation);
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

	private void addAddButton(Composite parent) {
		Button choosejarbutton = new Button(parent, SWT.PUSH);
		choosejarbutton.setText("Add repository...");
		choosejarbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			WizardDialog dialog = new WizardDialog(getShell(), new RepositoryAdditionWizard(ideProject) {
				@Override
				protected void finish(RepositoryIDEProperty property) {
					repositories.add(new TreePropertyItem<>(
							AIFU_propertyCounter.getAndIncrement(RepositoriesProjectPropertyPage.this), property));
					repositoryTreeViewer.refresh();
					validateProperties();
				}
			});
			dialog.open();

		}));
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		this.repositories.clear();
		initializeRepositories(SimpleIDEProjectProperties.getDefaultsInstance());
		repositoryTreeViewer.refresh();
		validateProperties();
	}

	@Override
	public boolean performOk() {
		Set<RepositoryIDEProperty> repos = new LinkedHashSet<>();
		for (TreePropertyItem<RepositoryIDEProperty> repoitem : repositories) {
			repos.add(repoitem.property);
		}
		ideProject.setIDEProjectProperties(SimpleIDEProjectProperties.builder(ideProject.getIDEProjectProperties())
				.setRepositories(repos).build());
		return true;
	}
}