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

import saker.build.ide.eclipse.extension.script.information.IScriptInformationEntry;
import saker.build.ide.support.ui.BaseScriptInformationEntry;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.TextPartition;

public class EclipseScriptInformationEntry extends BaseScriptInformationEntry implements IScriptInformationEntry {
	private String iconSource;

	public EclipseScriptInformationEntry(String title, String subTitle, FormattedTextContent content) {
		super(title, subTitle, content);
	}

	public EclipseScriptInformationEntry(TextPartition partition) {
		super(partition);
	}

	@Override
	public void setIconSource(String sourceurl) {
		if (sourceurl == null) {
			this.iconSource = null;
			return;
		}
		if (sourceurl.indexOf('\"') >= 0) {
			throw new IllegalArgumentException("Cannot contain quotes: " + sourceurl);
		}
		this.iconSource = sourceurl;
	}

	public String getIconSource() {
		return iconSource;
	}
}
