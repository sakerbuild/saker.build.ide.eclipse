package saker.build.ide.eclipse.properties;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;

public abstract class SakerWizard extends Wizard {
	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		return page.getNextPage();
	}

	@Override
	public IWizardPage getPreviousPage(IWizardPage page) {
		return page.getPreviousPage();
	}

	public static <T> T findPreviousPage(IWizardPage page, Class<T> type) {
		while (page != null) {
			IWizardPage prev = page.getPreviousPage();
			if (type.isInstance(prev)) {
				return type.cast(prev);
			}
			page = prev;
		}
		return null;
	}
}
