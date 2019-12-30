package saker.build.ide.eclipse.properties;

import java.util.function.BiConsumer;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;

public interface PropertyWizardPart<T> {
	public void setContinuation(WizardContinuation<? super T> continuation);

	public void setFinisher(BiConsumer<? super IWizardPage, ? super T> finisher);

	public void addPages(Wizard wizard);
}
