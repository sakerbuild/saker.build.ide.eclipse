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
package saker.build.ide.eclipse.script.outline;

import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineRoot;
import saker.build.ide.support.ui.BaseScriptOutlineRoot;
import saker.build.scripting.model.ScriptStructureOutline;
import saker.build.scripting.model.StructureOutlineEntry;

public class EclipseScriptOutlineRoot extends BaseScriptOutlineRoot<EclipseScriptOutlineEntry>
		implements IScriptOutlineRoot {

	private EclipseScriptOutlineRoot() {
	}

	public static EclipseScriptOutlineRoot create(ScriptStructureOutline outline) {
		EclipseScriptOutlineRoot result = new EclipseScriptOutlineRoot();
		result.init(outline);
		return result;
	}

	@Override
	protected EclipseScriptOutlineEntry createOutlineEntry(StructureOutlineEntry entry) {
		return new EclipseScriptOutlineEntry(this, entry);
	}

}
