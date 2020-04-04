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

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineEntry;
import saker.build.ide.support.ui.BaseScriptOutlineEntry;
import saker.build.scripting.model.StructureOutlineEntry;

public class EclipseScriptOutlineEntry extends BaseScriptOutlineEntry<EclipseScriptOutlineEntry>
		implements IScriptOutlineEntry {
	private StyledString widgetLabel;
	private StructureOutlineEntry entry;
	private Image widgetImage;

	EclipseScriptOutlineEntry(EclipseScriptOutlineRoot root, StructureOutlineEntry entry) {
		super(root, entry);

		this.entry = entry;
	}

	public StructureOutlineEntry getEntry() {
		return entry;
	}

	@Override
	public void setWidgetImage(Image image) {
		this.widgetImage = image;
	}

	@Override
	public void setWidgetLabel(StyledString label) {
		this.widgetLabel = label;
	}

	@Override
	public Image getWidgetImage() {
		return widgetImage;
	}

	@Override
	public StyledString getWidgetLabel() {
		return widgetLabel;
	}
}
