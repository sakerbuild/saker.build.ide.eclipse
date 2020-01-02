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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineEntry;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class BuildScriptOutlineEntry implements IScriptOutlineEntry {
	private StructureOutlineEntry entry;
	private Image widgetImage;
	private StyledString widgetLabel;
	private LazySupplier<List<? extends BuildScriptOutlineEntry>> childrenSupplier;

	public BuildScriptOutlineEntry(StructureOutlineEntry entry) {
		this.entry = entry;
		this.childrenSupplier = LazySupplier.of(() -> {
			List<? extends StructureOutlineEntry> children = entry.getChildren();
			if (ObjectUtils.isNullOrEmpty(children)) {
				return Collections.emptyList();
			}
			BuildScriptOutlineEntry[] res = new BuildScriptOutlineEntry[children.size()];
			int i = 0;
			for (StructureOutlineEntry childentry : children) {
				res[i++] = new BuildScriptOutlineEntry(childentry);
			}
			return ImmutableUtils.unmodifiableArrayList(res);
		});
	}

	public StructureOutlineEntry getEntry() {
		return entry;
	}

	@Override
	public List<? extends BuildScriptOutlineEntry> getChildren() {
		return childrenSupplier.get();
	}

	@Override
	public String getSchemaIdentifier() {
		return entry.getSchemaIdentifier();
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return entry.getSchemaMetaData();
	}

	@Override
	public String getLabel() {
		return entry.getLabel();
	}

	@Override
	public String getType() {
		return entry.getType();
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
