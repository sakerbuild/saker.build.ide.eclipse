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
