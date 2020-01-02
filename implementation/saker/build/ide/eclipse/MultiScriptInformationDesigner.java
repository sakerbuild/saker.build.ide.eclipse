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

import java.util.List;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;

public class MultiScriptInformationDesigner implements IScriptInformationDesigner {
	private final List<IScriptInformationDesigner> designers;

	public MultiScriptInformationDesigner(List<IScriptInformationDesigner> designers) {
		this.designers = designers;
	}

	@Override
	public void process(IScriptInformationRoot informationroot) {
		for (IScriptInformationDesigner designer : designers) {
			designer.process(informationroot);
		}
	}

}
