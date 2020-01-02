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

import java.util.List;

import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineEntry;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineRoot;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerScriptOutlineDesigner implements IScriptOutlineDesigner {
	public static final Image IMG_BOOL_FALSE = Activator.getImageDescriptor("icons/bool_false.png").createImage();
	public static final Image IMG_BOOL_TRUE = Activator.getImageDescriptor("icons/bool_true.png").createImage();
	public static final Image IMG_STRING = Activator.getImageDescriptor("icons/string.png").createImage();
	public static final Image IMG_STRINGLITERAL = Activator.getImageDescriptor("icons/stringliteral.png").createImage();
	public static final Image IMG_NUM = Activator.getImageDescriptor("icons/num.png").createImage();
	public static final Image IMG_MAP = Activator.getImageDescriptor("icons/map.png").createImage();
	public static final Image IMG_LIST = Activator.getImageDescriptor("icons/list.png").createImage();
	public static final Image IMG_TASK = Activator.getImageDescriptor("icons/task.png").createImage();
	public static final Image IMG_TARGET = Activator.getImageDescriptor("icons/target.png").createImage();
	public static final Image IMG_INPARAM = Activator.getImageDescriptor("icons/inparam.png").createImage();
	public static final Image IMG_OUTPARAM = Activator.getImageDescriptor("icons/outparam.png").createImage();
	public static final Image IMG_FOR = Activator.getImageDescriptor("icons/foreach.png").createImage();
	public static final Image IMG_VAR = Activator.getImageDescriptor("icons/var.png").createImage();

	@Override
	public void process(IScriptOutlineRoot outlineroot) {
		List<? extends IScriptOutlineEntry> roots = outlineroot.getRootEntries();
		processEntries(roots);
	}

	private void processEntry(IScriptOutlineEntry entry) {
		String entryschema = entry.getSchemaIdentifier();
		if (entryschema != null) {
			switch (entryschema) {
				case "saker.script.literal.null": {
					//TODO null script outline image
					break;
				}
				case "saker.script.literal.boolean": {
					if (Boolean.parseBoolean(entry.getLabel())) {
						entry.setWidgetImage(IMG_BOOL_TRUE);
					} else {
						entry.setWidgetImage(IMG_BOOL_FALSE);
					}
					break;
				}
				case "saker.script.task": {
					entry.setWidgetImage(IMG_TASK);
					break;
				}
				case "saker.script.list": {
					entry.setWidgetImage(IMG_LIST);
					break;
				}
				case "saker.script.map": {
					entry.setWidgetImage(IMG_MAP);
					break;
				}
				case "saker.script.literal.number": {
					entry.setWidgetImage(IMG_NUM);
					break;
				}
				case "saker.script.literal.string": {
					entry.setWidgetImage(IMG_STRINGLITERAL);
					break;
				}
				case "saker.script.literal.compound-string": {
					entry.setWidgetImage(IMG_STRING);
					break;
				}
				case "saker.script.literal.map.entry": {
					//TODO map entry script outline image
					break;
				}
				case "saker.script.target": {
					entry.setWidgetImage(IMG_TARGET);
					break;
				}
				case "saker.script.target.parameter.in": {
					entry.setWidgetImage(IMG_INPARAM);
					break;
				}
				case "saker.script.target.parameter.out": {
					entry.setWidgetImage(IMG_OUTPARAM);
					break;
				}
				case "saker.script.foreach": {
					entry.setWidgetImage(IMG_FOR);
					break;
				}
				case "saker.script.var": {
					entry.setWidgetImage(IMG_VAR);
					break;
				}
				default: {
					String coaltype = ObjectUtils.getMapValue(entry.getSchemaMetaData(), "saker.script.coalesced-type");
					if (coaltype != null) {
						switch (coaltype) {
							case "map": {
								entry.setWidgetImage(IMG_MAP);
								break;
							}
							case "list": {
								entry.setWidgetImage(IMG_LIST);
								break;
							}
							case "string": {
								entry.setWidgetImage(IMG_STRING);
								break;
							}
							case "literal.null": {
								//TODO null script outline image
								break;
							}
							case "literal.boolean.true": {
								entry.setWidgetImage(IMG_BOOL_TRUE);
								break;
							}
							case "literal.boolean.false": {
								entry.setWidgetImage(IMG_BOOL_FALSE);
								break;
							}
							case "literal.number": {
								entry.setWidgetImage(IMG_NUM);
								break;
							}
							case "literal.string": {
								entry.setWidgetImage(IMG_STRINGLITERAL);
								break;
							}
							case "var": {
								entry.setWidgetImage(IMG_VAR);
								break;
							}
							default: {
								break;
							}
						}
					}
					break;
				}
			}
		}
		processEntries(entry.getChildren());
	}

	private void processEntries(List<? extends IScriptOutlineEntry> children) {
		if (ObjectUtils.isNullOrEmpty(children)) {
			return;
		}
		for (IScriptOutlineEntry child : children) {
			processEntry(child);
		}
	}

}
