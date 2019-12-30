package saker.build.ide.eclipse.properties;

import org.eclipse.jface.wizard.IWizardPage;

public interface WizardContinuation<T> {
	public IWizardPage getWizardContinuation(T result);
}
