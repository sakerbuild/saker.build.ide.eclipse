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
import saker.build.ide.eclipse.BuildFileEditor;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineEntry;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineRoot;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerScriptOutlineDesigner implements IScriptOutlineDesigner {
	private static final Image IMG_BOOL_FALSE = Activator.getImageDescriptor("icons/bool_false.png").createImage();
	private static final Image IMG_BOOL_TRUE = Activator.getImageDescriptor("icons/bool_true.png").createImage();
	private static final Image IMG_STRING = Activator.getImageDescriptor("icons/string.png").createImage();
	private static final Image IMG_STRINGLITERAL = Activator.getImageDescriptor("icons/stringliteral.png")
			.createImage();
	private static final Image IMG_NUM = Activator.getImageDescriptor("icons/num.png").createImage();
	private static final Image IMG_MAP = Activator.getImageDescriptor("icons/map.png").createImage();
	private static final Image IMG_LIST = Activator.getImageDescriptor("icons/list.png").createImage();
	private static final Image IMG_TASK = Activator.getImageDescriptor("icons/task.png").createImage();
	private static final Image IMG_TARGET = Activator.getImageDescriptor("icons/target.png").createImage();
	private static final Image IMG_INPARAM = Activator.getImageDescriptor("icons/inparam.png").createImage();
	private static final Image IMG_OUTPARAM = Activator.getImageDescriptor("icons/outparam.png").createImage();
	private static final Image IMG_FOR = Activator.getImageDescriptor("icons/foreach.png").createImage();
	private static final Image IMG_VAR = Activator.getImageDescriptor("icons/var.png").createImage();

	private static final Image DARK_IMG_BOOL_FALSE = Activator.getImageDescriptor("icons/bool_false_dark.png")
			.createImage();
	private static final Image DARK_IMG_BOOL_TRUE = Activator.getImageDescriptor("icons/bool_true_dark.png")
			.createImage();
	private static final Image DARK_IMG_STRING = Activator.getImageDescriptor("icons/string_dark.png").createImage();
	private static final Image DARK_IMG_STRINGLITERAL = Activator.getImageDescriptor("icons/stringliteral_dark.png")
			.createImage();
	private static final Image DARK_IMG_NUM = Activator.getImageDescriptor("icons/num_dark.png").createImage();
	private static final Image DARK_IMG_MAP = Activator.getImageDescriptor("icons/map_dark.png").createImage();
	private static final Image DARK_IMG_LIST = Activator.getImageDescriptor("icons/list_dark.png").createImage();
	private static final Image DARK_IMG_TASK = Activator.getImageDescriptor("icons/task_dark.png").createImage();
	private static final Image DARK_IMG_TARGET = Activator.getImageDescriptor("icons/target_dark.png").createImage();
	private static final Image DARK_IMG_INPARAM = Activator.getImageDescriptor("icons/inparam_dark.png").createImage();
	private static final Image DARK_IMG_OUTPARAM = Activator.getImageDescriptor("icons/outparam_dark.png")
			.createImage();
	private static final Image DARK_IMG_FOR = Activator.getImageDescriptor("icons/foreach_dark.png").createImage();
	private static final Image DARK_IMG_VAR = Activator.getImageDescriptor("icons/var_dark.png").createImage();

	public static Image getImgBoolFalse(boolean darktheme) {
		return darktheme ? DARK_IMG_BOOL_FALSE : IMG_BOOL_FALSE;
	}

	public static Image getImgBoolTrue(boolean darktheme) {
		return darktheme ? DARK_IMG_BOOL_TRUE : IMG_BOOL_TRUE;
	}

	public static Image getImgString(boolean darktheme) {
		return darktheme ? DARK_IMG_STRING : IMG_STRING;
	}

	public static Image getImgStringliteral(boolean darktheme) {
		return darktheme ? DARK_IMG_STRINGLITERAL : IMG_STRINGLITERAL;
	}

	public static Image getImgNum(boolean darktheme) {
		return darktheme ? DARK_IMG_NUM : IMG_NUM;
	}

	public static Image getImgMap(boolean darktheme) {
		return darktheme ? DARK_IMG_MAP : IMG_MAP;
	}

	public static Image getImgList(boolean darktheme) {
		return darktheme ? DARK_IMG_LIST : IMG_LIST;
	}

	public static Image getImgTask(boolean darktheme) {
		return darktheme ? DARK_IMG_TASK : IMG_TASK;
	}

	public static Image getImgTarget(boolean darktheme) {
		return darktheme ? DARK_IMG_TARGET : IMG_TARGET;
	}

	public static Image getImgInparam(boolean darktheme) {
		return darktheme ? DARK_IMG_INPARAM : IMG_INPARAM;
	}

	public static Image getImgOutparam(boolean darktheme) {
		return darktheme ? DARK_IMG_OUTPARAM : IMG_OUTPARAM;
	}

	public static Image getImgFor(boolean darktheme) {
		return darktheme ? DARK_IMG_FOR : IMG_FOR;
	}

	public static Image getImgVar(boolean darktheme) {
		return darktheme ? DARK_IMG_VAR : IMG_VAR;
	}

	@Override
	public void process(IScriptOutlineRoot outlineroot) {
		List<? extends IScriptOutlineEntry> roots = outlineroot.getRootEntries();
		boolean darktheme = BuildFileEditor.isCurrentThemeDark();
		processEntries(roots, darktheme);
	}

	private void processEntry(IScriptOutlineEntry entry, boolean darktheme) {
		String entryschema = entry.getSchemaIdentifier();
		if (entryschema != null) {
			switch (entryschema) {
				case "saker.script.literal.null": {
					//TODO null script outline image
					break;
				}
				case "saker.script.literal.boolean": {
					if (Boolean.parseBoolean(entry.getLabel())) {
						entry.setWidgetImage(getImgBoolTrue(darktheme));
					} else {
						entry.setWidgetImage(getImgBoolFalse(darktheme));
					}
					break;
				}
				case "saker.script.task": {
					entry.setWidgetImage(getImgTask(darktheme));
					break;
				}
				case "saker.script.list": {
					entry.setWidgetImage(getImgList(darktheme));
					break;
				}
				case "saker.script.map": {
					entry.setWidgetImage(getImgMap(darktheme));
					break;
				}
				case "saker.script.literal.number": {
					entry.setWidgetImage(getImgNum(darktheme));
					break;
				}
				case "saker.script.literal.string": {
					entry.setWidgetImage(getImgStringliteral(darktheme));
					break;
				}
				case "saker.script.literal.compound-string": {
					entry.setWidgetImage(getImgString(darktheme));
					break;
				}
				case "saker.script.literal.map.entry": {
					//TODO map entry script outline image
					break;
				}
				case "saker.script.target": {
					entry.setWidgetImage(getImgTarget(darktheme));
					break;
				}
				case "saker.script.target.parameter.in": {
					entry.setWidgetImage(getImgInparam(darktheme));
					break;
				}
				case "saker.script.target.parameter.out": {
					entry.setWidgetImage(getImgOutparam(darktheme));
					break;
				}
				case "saker.script.foreach": {
					entry.setWidgetImage(getImgFor(darktheme));
					break;
				}
				case "saker.script.var": {
					entry.setWidgetImage(getImgVar(darktheme));
					break;
				}
				default: {
					String coaltype = ObjectUtils.getMapValue(entry.getSchemaMetaData(), "saker.script.coalesced-type");
					if (coaltype != null) {
						switch (coaltype) {
							case "map": {
								entry.setWidgetImage(getImgMap(darktheme));
								break;
							}
							case "list": {
								entry.setWidgetImage(getImgList(darktheme));
								break;
							}
							case "string": {
								entry.setWidgetImage(getImgString(darktheme));
								break;
							}
							case "literal.null": {
								//TODO null script outline image
								break;
							}
							case "literal.boolean.true": {
								entry.setWidgetImage(getImgBoolTrue(darktheme));
								break;
							}
							case "literal.boolean.false": {
								entry.setWidgetImage(getImgBoolFalse(darktheme));
								break;
							}
							case "literal.number": {
								entry.setWidgetImage(getImgNum(darktheme));
								break;
							}
							case "literal.string": {
								entry.setWidgetImage(getImgStringliteral(darktheme));
								break;
							}
							case "var": {
								entry.setWidgetImage(getImgVar(darktheme));
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
		processEntries(entry.getChildren(), darktheme);
	}

	private void processEntries(List<? extends IScriptOutlineEntry> children, boolean darktheme) {
		if (ObjectUtils.isNullOrEmpty(children)) {
			return;
		}
		for (IScriptOutlineEntry child : children) {
			processEntry(child, darktheme);
		}
	}

}
