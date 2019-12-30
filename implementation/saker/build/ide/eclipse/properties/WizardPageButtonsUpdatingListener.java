package saker.build.ide.eclipse.properties;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public final class WizardPageButtonsUpdatingListener implements Listener {
	private final IWizardPage page;

	public WizardPageButtonsUpdatingListener(IWizardPage page) {
		this.page = page;
	}

	@Override
	public void handleEvent(Event event) {
		page.getWizard().getContainer().updateButtons();
	}
}