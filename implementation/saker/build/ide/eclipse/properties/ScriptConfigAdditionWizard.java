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

import saker.build.file.path.WildcardPath;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.properties.ClassPathAdditionWizard.ClassPathLocationWizardResult;
import saker.build.ide.eclipse.properties.ServiceLocationAdditionWizard.ServiceLocationWizardResult;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;

public abstract class ScriptConfigAdditionWizard extends SakerWizard {
	public static final int MASK_EDIT = 0x7;

	public static final int EDIT_CLASSPATH = 1 << 0;
	public static final int EDIT_SERVICE = 1 << 1;
	public static final int EDIT_SCRIPTCONFIG = 1 << 2;
	public static final int EDIT_ALL = MASK_EDIT;

	protected final ClassPathAdditionWizard classPathWizard;
	protected final ServiceLocationAdditionWizard serviceWizard;
	protected final ScriptConfigWizardPage configPage;
	protected final ScriptConfigurationIDEProperty editedScriptProperty;
	protected final int editFlags;

	public ScriptConfigAdditionWizard(EclipseSakerIDEProject project) {
		this(project, null, 0);
	}

	public ScriptConfigAdditionWizard(EclipseSakerIDEProject project, ScriptConfigurationIDEProperty editedproperty,
			int editflags) {
		editflags = validateEditFlags(editedproperty, editflags);
		this.editFlags = editflags;
		
		this.editedScriptProperty = editedproperty;
		configPage = new ScriptConfigWizardPage();

		classPathWizard = new ClassPathAdditionWizard(project,
				ClassPathAdditionWizard.FLAG_NO_NEST_REPOSITORY_CLASSPATH,
				editedproperty == null ? null : editedproperty.getClassPathLocation());
		serviceWizard = new ServiceLocationAdditionWizard(
				editedproperty == null ? null : editedproperty.getServiceEnumerator());

		classPathWizard.setClassPathTypeTitleText("Script language");
		classPathWizard.setClassPathTypeText("script language");
		serviceWizard.setClassPathTypeTitleText("Script language");
		serviceWizard.setClassPathTypeText("script language");
		serviceWizard.setServiceDefaultServiceClassName(ScriptAccessProvider.class.getName());

		classPathWizard.setContinuation(property -> {
			return property.accept(new ClassPathLocationIDEProperty.Visitor<IWizardPage, Void>() {
				@Override
				public IWizardPage visit(JarClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_SERVICE) == EDIT_SERVICE)) {
						return serviceWizard.getServiceLocationPage();
					}
					if (((editFlags & EDIT_SCRIPTCONFIG) == EDIT_SCRIPTCONFIG)) {
						return configPage;
					}
					return null;
				}

				@Override
				public IWizardPage visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_SERVICE) == EDIT_SERVICE)) {
						return serviceWizard.getServiceLocationPage();
					}
					if (((editFlags & EDIT_SCRIPTCONFIG) == EDIT_SCRIPTCONFIG)) {
						return configPage;
					}
					return null;
				}

				@Override
				public IWizardPage visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
					if (((editFlags & EDIT_SCRIPTCONFIG) == EDIT_SCRIPTCONFIG)) {
						return configPage;
					}
					return null;
				}

				@Override
				public IWizardPage visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
					throw new AssertionError();
				}
			}, null);
		});
		serviceWizard.setContinuation(property -> {
			if (((editFlags & EDIT_SCRIPTCONFIG) == EDIT_SCRIPTCONFIG)) {
				return configPage;
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

				finish(new ScriptConfigurationIDEProperty(editedproperty.getScriptsWildcard(),
						editedproperty.getScriptOptions(), rescp, ser));
			});
			classPathWizard.setFinisher((page, cp) -> {
				finish(new ScriptConfigurationIDEProperty(editedproperty.getScriptsWildcard(),
						editedproperty.getScriptOptions(), cp, ServiceLocationAdditionWizard
								.inferCombinedServiceEnumerator(cp, editedproperty.getServiceEnumerator())));
			});
			if (((editflags & EDIT_SERVICE) != EDIT_SERVICE)) {
				//not editing service configuration
				configPage.serviceLocationWizardResultSupplier = () -> new ServiceLocationWizardResult() {
					@Override
					public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
						return editedproperty.getServiceEnumerator();
					}
				};
			}
			if (((editflags & EDIT_CLASSPATH) != EDIT_CLASSPATH)) {
				//not editing classpath
				configPage.classPathWizardResultSupplier = () -> new ClassPathLocationWizardResult() {

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
		if (((editFlags & EDIT_SCRIPTCONFIG) == EDIT_SCRIPTCONFIG)) {
			addPage(configPage);
		}
	}

	@Override
	public String getWindowTitle() {
		return "Script configuration";
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

	protected abstract void finish(ScriptConfigurationIDEProperty property);

	private class ScriptConfigWizardPage extends SakerWizardPage implements FinishablePage {
		private Text wildcardText;
		private StringMapTableHandler tableHandler;

		private Supplier<ClassPathLocationWizardResult> classPathWizardResultSupplier = () -> SakerWizard
				.findPreviousPage(this, ClassPathLocationWizardResult.class);
		private Supplier<ServiceLocationWizardResult> serviceLocationWizardResultSupplier = () -> SakerWizard
				.findPreviousPage(this, ServiceLocationWizardResult.class);

		@Override
		public String getTitle() {
			return "Script configuration";
		}

		@Override
		public String getDescription() {
			return "Specify the files and the scripting options for the language.";
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginLeft = 5;
			layout.marginRight = 5;
			composite.setLayout(layout);

			Composite wildcardrow = new Composite(composite, SWT.NONE);
			GridData workingrowgriddata = new GridData();
			workingrowgriddata.grabExcessHorizontalSpace = true;
			workingrowgriddata.horizontalAlignment = GridData.FILL;
			wildcardrow.setLayout(new GridLayout(2, false));
			wildcardrow.setLayoutData(workingrowgriddata);

			WizardPageButtonsUpdatingListener buttonupdatinglistener = new WizardPageButtonsUpdatingListener(this);

			Label wclabel = new Label(wildcardrow, SWT.NONE);
			wclabel.setText("Script files:");
			wildcardText = new Text(wildcardrow, SWT.BORDER);
			GridData wdtextgriddata = new GridData();
			wdtextgriddata.grabExcessHorizontalSpace = true;
			wdtextgriddata.horizontalAlignment = GridData.FILL;
			wildcardText.setLayoutData(wdtextgriddata);
			wildcardText.setMessage("Script wildcard pattern");
			wildcardText.addListener(SWT.Modify, buttonupdatinglistener);

			tableHandler = new StringMapTableHandler();
			tableHandler.setKeyValDialogTitle("Script language option");
			tableHandler.setAddButtonLabel("Add script option...");
			tableHandler.setKeyValDialogBaseMessage("Enter a script option for the language parser (-SO option).");
			tableHandler.addControl(composite);
			tableHandler.addModifyListener(buttonupdatinglistener);
			((GridData) tableHandler.getTable().getLayoutData()).horizontalSpan = 2;

			if (editedScriptProperty != null) {
				String scriptswc = editedScriptProperty.getScriptsWildcard();
				if (!ObjectUtils.isNullOrEmpty(scriptswc)) {
					wildcardText.setText(scriptswc);
				}
				tableHandler.setOptions(editedScriptProperty.getScriptOptions());
			}

			setControl(composite);
		}

		@Override
		public IWizardPage getNextPage() {
			return null;
		}

		@Override
		public boolean canFinishPage() {
			return FinishablePage.super.canFinishPage() && tableHandler.validate() == null && getWildcard() != null;
		}

		@Override
		public boolean canFlipToNextPage() {
			return super.canFlipToNextPage() && tableHandler.validate() == null && getWildcard() != null;
		}

		@Override
		public boolean performFinish() {
			ClassPathLocationWizardResult classlocationpage = classPathWizardResultSupplier.get();
			if (classlocationpage == null) {
				return false;
			}
			ClassPathLocationIDEProperty cplocation = classlocationpage.getClassPathLocation();
			if (cplocation == null) {
				return false;
			}

			ServiceLocationWizardResult servicelocationpage = serviceLocationWizardResultSupplier.get();

			ClassPathServiceEnumeratorIDEProperty serviceenumerator;
			if (servicelocationpage != null) {
				serviceenumerator = servicelocationpage.getServiceEnumerator();
			} else {
				serviceenumerator = classlocationpage.inferServiceProperty();
			}
			if (serviceenumerator == null) {
				return false;
			}
			WildcardPath wc = getWildcard();
			if (wc == null) {
				return false;
			}
			ScriptConfigurationIDEProperty property = new ScriptConfigurationIDEProperty(wc.toString(),
					tableHandler.getOptions(), cplocation, serviceenumerator);
			finish(property);
			return true;
		}

		private WildcardPath getWildcard() {
			String text = wildcardText.getText();
			if (ObjectUtils.isNullOrEmpty(text)) {
				return null;
			}
			try {
				return WildcardPath.valueOf(text);
			} catch (RuntimeException e) {
				//failed to parse
			}
			return null;
		}

	}

}
