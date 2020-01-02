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
package saker.build.ide.eclipse;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Menu;

public class WindowMenuContribution extends ContributionItem {
	private IMenuListener menulistener = manager -> manager.markDirty();

	public WindowMenuContribution() {
	}

	public WindowMenuContribution(String id) {
		super(id);
	}

	@Override
	public void fill(Menu menu, int index) {
		super.fill(menu, index);
		MenuManager parent = (MenuManager) getParent();
		parent.addMenuListener(menulistener);

		Action action = new Action("Reload plugin environment") {
			@Override
			public void run() {
				EclipseSakerIDEPlugin plugin = ImplActivator.getDefault().getEclipseIDEPlugin();
				if (plugin == null) {
					//doesnt really happen
					return;
				}
				plugin.reloadPluginEnvironment();
			}
		};
		new ActionContributionItem(action).fill(menu, -1);

	}

	@Override
	public boolean isDynamic() {
		//XXX this doesn't really need to be dynamic
		return true;
	}

}
