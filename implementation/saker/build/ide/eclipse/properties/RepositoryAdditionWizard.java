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

import java.util.function.Supplier;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.properties.ClassPathAdditionWizard.ClassPathLocationWizardResult;
import saker.build.ide.eclipse.properties.ServiceLocationAdditionWizard.ServiceLocationWizardResult;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class RepositoryAdditionWizard extends SakerWizard {
	public static final int MASK_EDIT = 0x7;

	public static final int EDIT_CLASSPATH = 1 << 0;
	public static final int EDIT_SERVICE = 1 << 1;
	public static final int EDIT_REPOSITORY_IDENTIFIER = 1 << 2;
	public static final int EDIT_ALL = MASK_EDIT;

	protected final ClassPathAdditionWizard classPathWizard;
	protected final ServiceLocationAdditionWizard serviceWizard;
	protected final RepositoryIDEProperty editedRepositoryProperty;
	protected final RepositoryIdentifierWizardPage repositoryIdentifierPage = new RepositoryIdentifierWizardPage();

	private final int editFlags;

	public RepositoryAdditionWizard(EclipseSakerIDEProject project) {
		this(project, null, 0);
	}

	public RepositoryAdditionWizard(EclipseSakerIDEProject project, RepositoryIDEProperty editedproperty,
			int editflags) {
		editflags = validateEditFlags(editedproperty, editflags);
		this.editFlags = editflags;

		classPathWizard = new ClassPathAdditionWizard(project, ClassPathAdditionWizard.FLAG_NO_SAKERSCRIPT_CLASSPATH,
				editedproperty == null ? null : editedproperty.getClassPathLocation());
		serviceWizard = new ServiceLocationAdditionWizard(
				editedproperty == null ? null : editedproperty.getServiceEnumerator());

		this.editedRepositoryProperty = editedproperty;

		classPathWizard.setClassPathTypeTitleText("Repository");
		classPathWizard.setClassPathTypeText("repository");
		serviceWizard.setClassPathTypeTitleText("Repository");
		serviceWizard.setClassPathTypeText("repository");
		serviceWizard.setServiceDefaultServiceClassName(SakerRepositoryFactory.class.getName());

		classPathWizard.setContinuation(property -> {
			return property.accept(new ClassPathLocationIDEProperty.Visitor<IWizardPage, Void>() {
				@Override
				public IWizardPage visit(JarClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_SERVICE) == EDIT_SERVICE)) {
						return serviceWizard.getServiceLocationPage();
					}
					if (((editFlags & EDIT_REPOSITORY_IDENTIFIER) == EDIT_REPOSITORY_IDENTIFIER)) {
						return repositoryIdentifierPage;
					}
					return null;
				}

				@Override
				public IWizardPage visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_SERVICE) == EDIT_SERVICE)) {
						return serviceWizard.getServiceLocationPage();
					}
					if (((editFlags & EDIT_REPOSITORY_IDENTIFIER) == EDIT_REPOSITORY_IDENTIFIER)) {
						return repositoryIdentifierPage;
					}
					return null;
				}

				@Override
				public IWizardPage visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
					throw new AssertionError();
				}

				@Override
				public IWizardPage visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_REPOSITORY_IDENTIFIER) == EDIT_REPOSITORY_IDENTIFIER)) {
						return repositoryIdentifierPage;
					}
					return null;
				}
			}, null);
		});
		serviceWizard.setContinuation(property -> {
			if (((editFlags & EDIT_REPOSITORY_IDENTIFIER) == EDIT_REPOSITORY_IDENTIFIER)) {
				return repositoryIdentifierPage;
			}
			return null;
		});

		if (editedproperty != null) {
			serviceWizard.setFinisher((page, ser) -> {
				ClassPathLocationWizardResult cplocparent = findPreviousPage(page, ClassPathLocationWizardResult.class);
				ClassPathLocationIDEProperty rescp;
				if (cplocparent == null) {
					//class path wasn't edited
					rescp = editedproperty.getClassPathLocation();
				} else {
					rescp = cplocparent.getClassPathLocation();
				}

				finish(new RepositoryIDEProperty(rescp, editedproperty.getRepositoryIdentifier(), ser));
			});
			classPathWizard.setFinisher((page, cp) -> {
				finish(new RepositoryIDEProperty(cp, editedproperty.getRepositoryIdentifier(),
						ServiceLocationAdditionWizard.inferCombinedServiceEnumerator(cp,
								editedproperty.getServiceEnumerator())));
			});
			if (((editflags & EDIT_SERVICE) != EDIT_SERVICE)) {
				//not editing service configuration
				repositoryIdentifierPage.serviceLocationWizardResultSupplier = () -> new ServiceLocationWizardResult() {
					@Override
					public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
						return editedproperty.getServiceEnumerator();
					}
				};
			}
			if (((editflags & EDIT_CLASSPATH) != EDIT_CLASSPATH)) {
				//not editing classpath
				repositoryIdentifierPage.classPathWizardResultSupplier = () -> new ClassPathLocationWizardResult() {

					@Override
					public ClassPathLocationIDEProperty getClassPathLocation() {
						return editedproperty.getClassPathLocation();
					}

					@Override
					public ClassPathServiceEnumeratorIDEProperty inferServiceProperty() {
						return editedproperty.getServiceEnumerator();
					}

				};
			}
		}
	}

	private static int validateEditFlags(Object editedproperty, int editflags) {
		if (editedproperty != null) {
			editflags = editflags & MASK_EDIT;
			if (editflags == 0) {
				throw new IllegalArgumentException("No edit flags.");
			}
		} else {
			editflags = EDIT_ALL;
		}
		return editflags;
	}

	@Override
	public void addPages() {
		super.addPages();
		if (((editFlags & EDIT_CLASSPATH) == EDIT_CLASSPATH)) {
			classPathWizard.addPages(this);
		}
		if (((editFlags & EDIT_SERVICE) == EDIT_SERVICE)) {
			serviceWizard.addPages(this);
		}
		if (((editFlags & EDIT_REPOSITORY_IDENTIFIER) == EDIT_REPOSITORY_IDENTIFIER)) {
			addPage(repositoryIdentifierPage);
		}
	}

	@Override
	public String getWindowTitle() {
		return "Task repository";
	}

	private class RepositoryIdentifierWizardPage extends EclipseSakerWizardPage implements FinishablePage {
		private Text identifierText;

		private Supplier<ClassPathLocationWizardResult> classPathWizardResultSupplier = () -> SakerWizard
				.findPreviousPage(this, ClassPathLocationWizardResult.class);
		private Supplier<ServiceLocationWizardResult> serviceLocationWizardResultSupplier = () -> SakerWizard
				.findPreviousPage(this, ServiceLocationWizardResult.class);

		public RepositoryIdentifierWizardPage() {
		}

		@Override
		public String getTitle() {
			return "Repository identifier";
		}

		@Override
		public String getDescription() {
			return "Specify the repository identifier to assign to the configuration.";
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
			l.setText("Repository identifier:");
			GridData labelgd = new GridData();
			l.setLayoutData(labelgd);

			identifierText = new Text(composite, SWT.BORDER);
			identifierText.setMessage("Repository ID");
			GridData textgd = new GridData();
			textgd.grabExcessHorizontalSpace = true;
			textgd.horizontalAlignment = GridData.FILL;
			identifierText.setLayoutData(textgd);

//			Label infolabel = new Label(composite, SWT.WRAP | SWT.BORDER);
//			infolabel.setText(
//					"Repository identifiers are recommended to be user readable sequence of alphabetic characters. "
//							+ "It can be used to reference the repository from build scripts or configurations.");
//			GridData infogd = new GridData();
//			infogd.horizontalSpan = layout.numColumns;
//			infogd.horizontalAlignment = SWT.FILL;
////			infogd.heightHint = 50;
////			infogd.widthHint = 200;
////			infogd.grabExcessHorizontalSpace = true;
//			infolabel.setLayoutData(infogd);

			if (editedRepositoryProperty != null) {
				String editedrepoid = editedRepositoryProperty.getRepositoryIdentifier();
				if (!ObjectUtils.isNullOrEmpty(editedrepoid)) {
					identifierText.setText(editedrepoid);
				}
			}

			setControl(composite);
		}

		@Override
		public IWizardPage getNextPage() {
			return null;
		}

		@Override
		public boolean performFinish() {
			RepositoryIDEProperty property = createRepositoryProperty();
			if (property == null) {
				return false;
			}
			finish(property);
			return true;
		}

		@Override
		public boolean canFinishPage() {
			return true;
		}

		private RepositoryIDEProperty createRepositoryProperty() {
			ClassPathLocationWizardResult classlocationpage = classPathWizardResultSupplier.get();
			if (classlocationpage == null) {
				return null;
			}
			ClassPathLocationIDEProperty cplocation = classlocationpage.getClassPathLocation();
			if (cplocation == null) {
				return null;
			}

			ServiceLocationWizardResult servicelocationpage = serviceLocationWizardResultSupplier.get();

			ClassPathServiceEnumeratorIDEProperty serviceenumerator;
			if (servicelocationpage != null) {
				serviceenumerator = servicelocationpage.getServiceEnumerator();
			} else {
				serviceenumerator = classlocationpage.inferServiceProperty();
			}
			if (serviceenumerator == null) {
				return null;
			}
			String repositoryid = getRepositoryIdentifier();
			RepositoryIDEProperty property = new RepositoryIDEProperty(cplocation, repositoryid, serviceenumerator);
			return property;
		}

		private String getRepositoryIdentifier() {
			String id = identifierText.getText();
			if (id.isEmpty()) {
				return null;
			}
			return id;
		}
	}

	@Override
	public boolean canFinish() {
		IWizardPage page = getContainer().getCurrentPage();
		return page instanceof FinishablePage && ((FinishablePage) page).canFinishPage() && page.getNextPage() == null;
	}

	@Override
	public boolean performFinish() {
		IWizardPage page = getContainer().getCurrentPage();
		if (page instanceof FinishablePage) {
			FinishablePage fpage = (FinishablePage) page;
			return fpage.performFinish();
		}
		return false;
	}

	protected abstract void finish(RepositoryIDEProperty property);
}
