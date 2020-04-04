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
package saker.build.ide.eclipse.script.information;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;
import saker.build.ide.support.ui.BaseScriptInformationRoot;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.TextPartition;

public class EclipseScriptInformationRoot extends BaseScriptInformationRoot<EclipseScriptInformationEntry>
		implements IScriptInformationRoot {

	private EclipseScriptInformationRoot() {
	}

	@Override
	protected EclipseScriptInformationEntry createInformationEntry(TextPartition partition) {
		return new EclipseScriptInformationEntry(partition);
	}

	public static EclipseScriptInformationRoot create(ScriptTokenInformation tokeninfo) {
		EclipseScriptInformationRoot result = new EclipseScriptInformationRoot();
		result.init(tokeninfo);
		return result;
	}

	public static EclipseScriptInformationRoot create(PartitionedTextContent textcontents) {
		EclipseScriptInformationRoot result = new EclipseScriptInformationRoot();
		result.init(textcontents);
		return result;
	}

}
