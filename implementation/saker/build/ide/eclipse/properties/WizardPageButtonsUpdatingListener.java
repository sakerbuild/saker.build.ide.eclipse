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