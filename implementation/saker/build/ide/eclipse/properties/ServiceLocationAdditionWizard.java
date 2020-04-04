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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ServiceLocationAdditionWizard implements PropertyWizardPart<ClassPathServiceEnumeratorIDEProperty> {
	protected ClassPathServiceEnumeratorIDEProperty editedServiceEnumeratorProperty;

	protected final ServiceLocationWizardPage serviceLocationPage;

	private WizardContinuation<? super ClassPathServiceEnumeratorIDEProperty> continuation;

	protected String classPathTypeTitleText = "Configuration";
	protected String classPathTypeText = "configuration";

	private String serviceDefaultServiceClassName;

	private BiConsumer<? super IWizardPage, ? super ClassPathServiceEnumeratorIDEProperty> finisher;

	public ServiceLocationAdditionWizard(ClassPathServiceEnumeratorIDEProperty editedserviceenumerator) {
		serviceLocationPage = new ServiceLocationWizardPage();
		this.editedServiceEnumeratorProperty = editedserviceenumerator;
	}

	@Override
	public void setContinuation(WizardContinuation<? super ClassPathServiceEnumeratorIDEProperty> continuation) {
		this.continuation = continuation;
	}

	@Override
	public void setFinisher(BiConsumer<? super IWizardPage, ? super ClassPathServiceEnumeratorIDEProperty> finisher) {
		this.finisher = finisher;
	}

	public void setClassPathTypeText(String classPathTypeText) {
		this.classPathTypeText = classPathTypeText;
	}

	public void setClassPathTypeTitleText(String classPathTypeTitleText) {
		this.classPathTypeTitleText = classPathTypeTitleText;
	}

	public void setServiceDefaultServiceClassName(String serviceDefaultServiceClassName) {
		this.serviceDefaultServiceClassName = serviceDefaultServiceClassName;
	}

	public ServiceLocationWizardPage getServiceLocationPage() {
		return serviceLocationPage;
	}

	@Override
	public void addPages(Wizard wizard) {
		wizard.addPage(serviceLocationPage);
	}

	private final IWizardPage getClassPathServiceContinuation(ClassPathServiceEnumeratorIDEProperty property) {
		if (continuation == null) {
			return null;
		}
		return continuation.getWizardContinuation(property);
	}

	public class ServiceLocationWizardPage extends EclipseSakerWizardPage
			implements ServiceLocationWizardResult, FinishablePage {
		private Button serviceLoaderButton;
		private Button classNameButton;
		private Text serviceLoaderText;
		private Text classNameText;

		public ServiceLocationWizardPage() {
			super();
		}

		@Override
		public String getTitle() {
			return classPathTypeTitleText + " class location";
		}

		@Override
		public String getDescription() {
			return "Specify how the " + classPathTypeText + " class should be loaded from the classpath.";
		}

		@Override
		public void createControl(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginLeft = 5;
			layout.marginRight = 5;
			composite.setLayout(layout);

			GridData textgd = new GridData();
			textgd.grabExcessHorizontalSpace = true;
			textgd.horizontalAlignment = GridData.FILL;

			serviceLoaderButton = new Button(composite, SWT.RADIO);
			serviceLoaderButton.setText("Service loader");
			serviceLoaderText = new Text(composite, SWT.BORDER);
			serviceLoaderText.setMessage(classPathTypeTitleText + " service class name");
			serviceLoaderText.setText(serviceDefaultServiceClassName);
			serviceLoaderText.setLayoutData(textgd);

			classNameButton = new Button(composite, SWT.RADIO);
			classNameButton.setText("Class name");
			classNameText = new Text(composite, SWT.BORDER);
			classNameText.setMessage(classPathTypeTitleText + " class name");
			classNameText.setLayoutData(textgd);

			serviceLoaderButton.setSelection(true);
			serviceLoaderText.setEnabled(true);
			classNameText.setEnabled(false);

			Listener buttonsupdatinglistener = new WizardPageButtonsUpdatingListener(this);
			classNameText.addListener(SWT.Modify, buttonsupdatinglistener);
			serviceLoaderText.addListener(SWT.Modify, buttonsupdatinglistener);
			serviceLoaderButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
				serviceLoaderText.setEnabled(serviceLoaderButton.getSelection());
				getContainer().updateButtons();
			}));
			classNameButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
				classNameText.setEnabled(classNameButton.getSelection());
				getContainer().updateButtons();
			}));

			//Bug 80681 - Button.setSelection() does not deselects other radio buttons in group
			//https://bugs.eclipse.org/bugs/show_bug.cgi?id=80681

			if (editedServiceEnumeratorProperty != null) {
				editedServiceEnumeratorProperty.accept(new ClassPathServiceEnumeratorIDEProperty.Visitor<Void, Void>() {
					@Override
					public Void visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
						serviceLoaderButton.setSelection(true);
						serviceLoaderText.setEnabled(true);
						classNameButton.setSelection(false);
						classNameText.setEnabled(false);

						serviceLoaderText.setText(ObjectUtils.nullDefault(property.getServiceClass(), ""));
						return null;
					}

					@Override
					public Void visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
						serviceLoaderButton.setSelection(false);
						serviceLoaderText.setEnabled(false);
						classNameButton.setSelection(true);
						classNameText.setEnabled(true);

						classNameText.setText(ObjectUtils.nullDefault(property.getClassName(), ""));
						return null;
					}

					@Override
					public Void visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
						//ignore, as this shouldn't happen for a valid configuration
						return null;
					}

					@Override
					public Void visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
						//ignore, as this shouldn't happen for a valid configuration
						return null;
					}
				}, null);
			}

			setControl(composite);
		}

		@Override
		public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
			ClassPathServiceEnumeratorIDEProperty serviceenumerator;
			if (serviceLoaderButton.getSelection()) {
				String classname = serviceLoaderText.getText();
				if (ObjectUtils.isNullOrEmpty(classname)) {
					return null;
				}
				serviceenumerator = new ServiceLoaderClassPathEnumeratorIDEProperty(classname);
			} else {
				String classname = classNameText.getText();
				if (ObjectUtils.isNullOrEmpty(classname)) {
					return null;
				}
				serviceenumerator = new NamedClassClassPathServiceEnumeratorIDEProperty(classname);
			}
			return serviceenumerator;
		}

		@Override
		public IWizardPage getNextPage() {
			ClassPathServiceEnumeratorIDEProperty enumerator = getServiceEnumerator();
			if (enumerator == null) {
				return null;
			}
			return getClassPathServiceContinuation(enumerator);
		}

		@Override
		public boolean canFinishPage() {
			return finisher != null && getServiceEnumerator() != null;
		}

		@Override
		public boolean performFinish() {
			ClassPathServiceEnumeratorIDEProperty enumerator = getServiceEnumerator();
			if (enumerator == null) {
				return false;
			}
			finisher.accept(this, enumerator);
			return true;
		}
	}

	public interface ServiceLocationWizardResult {
		public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator();
	}

	private static final Map<ClassPathLocationIDEProperty, ClassPathServiceEnumeratorIDEProperty> CLASSPATH_SERVICE_COMBINATIONS = new HashMap<>();
	static {
		CLASSPATH_SERVICE_COMBINATIONS.put(new NestRepositoryClassPathLocationIDEProperty(),
				new NestRepositoryFactoryServiceEnumeratorIDEProperty());
		CLASSPATH_SERVICE_COMBINATIONS.put(new BuiltinScriptingLanguageClassPathLocationIDEProperty(),
				new BuiltinScriptingLanguageServiceEnumeratorIDEProperty());
	}

	public static ClassPathServiceEnumeratorIDEProperty inferCombinedServiceEnumerator(
			ClassPathLocationIDEProperty property, ClassPathServiceEnumeratorIDEProperty defaultproperty) {
		if (property == null) {
			return defaultproperty;
		}
		ClassPathServiceEnumeratorIDEProperty inferred = CLASSPATH_SERVICE_COMBINATIONS.get(property);
		if (inferred == null) {
			return defaultproperty;
		}
		return inferred;
	}

	public static boolean isCombinationLockedClassPath(ClassPathLocationIDEProperty property) {
		return CLASSPATH_SERVICE_COMBINATIONS.containsKey(property);
	}

	public static boolean isCombinationLockedServiceEnumerator(ClassPathServiceEnumeratorIDEProperty property) {
		//could use a set for the services, but containsValue is fine when there are only 2 types
		return CLASSPATH_SERVICE_COMBINATIONS.containsValue(property);
	}

}
