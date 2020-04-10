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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationEntry;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class SakerScriptInformationDesigner implements IScriptInformationDesigner {
	private static final String ICON_SOURCE_TASK = createIconSource("icons/task@2x.png");
	private static final String ICON_SOURCE_VARIABLE = createIconSource("icons/var@2x.png");
	private static final String ICON_SOURCE_TARGET_INPUT_PARAMETER = createIconSource("icons/inparam@2x.png");
	private static final String ICON_SOURCE_TARGET_OUTPUT_PARAMETER = createIconSource("icons/outparam@2x.png");
	private static final String ICON_SOURCE_BUILD_TARGET = createIconSource("icons/target@2x.png");
	private static final String ICON_SOURCE_FILE = createIconSource("icons/icon_file@2x.png");

	public static final String INFORMATION_SCHEMA = "saker.script";
	public static final String INFORMATION_SCHEMA_TASK = INFORMATION_SCHEMA + ".task";
	public static final String INFORMATION_SCHEMA_TASK_PARAMETER = INFORMATION_SCHEMA + ".task_parameter";
	public static final String INFORMATION_SCHEMA_ENUM = INFORMATION_SCHEMA + ".enum";
	public static final String INFORMATION_SCHEMA_VARIABLE = INFORMATION_SCHEMA + ".var";
	public static final String INFORMATION_SCHEMA_FOREACH_VARIABLE = INFORMATION_SCHEMA + ".foreach_var";
	public static final String INFORMATION_SCHEMA_TARGET_INPUT_PARAMETER = INFORMATION_SCHEMA
			+ ".target.input_parameter";
	public static final String INFORMATION_SCHEMA_TARGET_OUTPUT_PARAMETER = INFORMATION_SCHEMA
			+ ".target.output_parameter";
	public static final String INFORMATION_SCHEMA_BUILD_TARGET = INFORMATION_SCHEMA + ".target";
	public static final String INFORMATION_SCHEMA_FILE = INFORMATION_SCHEMA + ".file";
	public static final String INFORMATION_SCHEMA_USER_PARAMETER = INFORMATION_SCHEMA + ".user_parameter";
	public static final String INFORMATION_SCHEMA_ENVIRONMENT_PARAMETER = INFORMATION_SCHEMA + ".environment_parameter";
	public static final String INFORMATION_SCHEMA_EXTERNAL_LITERAL = INFORMATION_SCHEMA + ".external_literal";

	public static final String INFORMATION_META_DATA_FILE_TYPE = "file_type";
	public static final String INFORMATION_META_DATA_FILE_TYPE_FILE = "file";
	public static final String INFORMATION_META_DATA_FILE_TYPE_BUILD_SCRIPT = "build_script";
	public static final String INFORMATION_META_DATA_FILE_TYPE_DIRECTORY = "dir";

	private static final String ICON_FOLDER = createPluginIconSource("org.eclipse.ui.ide",
			"icons/full/obj16/folder.png");
	private static final String ICON_FILE = createPluginIconSource("org.eclipse.ui", "icons/full/obj16/file_obj.png");

	@Override
	public void process(IScriptInformationRoot informationroot) {
		processImpl(informationroot);
	}

	public static void processImpl(IScriptInformationRoot informationroot) {
		System.out.println("SakerScriptInformationDesigner.process()");
		List<? extends IScriptInformationEntry> entries = informationroot.getEntries();
		processEntries(entries);
	}

	public static void processEntries(List<? extends IScriptInformationEntry> entries) {
		if (entries == null) {
			return;
		}
		for (IScriptInformationEntry entry : entries) {
			processEntry(entry);
		}
	}

	public static void processEntry(IScriptInformationEntry entry) {
		if (entry == null) {
			return;
		}
		String entryschema = entry.getSchemaIdentifier();
		if (entryschema == null) {
			return;
		}
		Map<String, String> schemameta = entry.getSchemaMetaData();
		switch (entryschema) {
			case INFORMATION_SCHEMA_TASK: {
				entry.setIconSource(ICON_SOURCE_TASK);
				break;
			}
			case INFORMATION_SCHEMA_VARIABLE: {
				entry.setIconSource(ICON_SOURCE_VARIABLE);
				break;
			}
			case INFORMATION_SCHEMA_TARGET_INPUT_PARAMETER: {
				entry.setIconSource(ICON_SOURCE_TARGET_INPUT_PARAMETER);
				break;
			}
			case INFORMATION_SCHEMA_TARGET_OUTPUT_PARAMETER: {
				entry.setIconSource(ICON_SOURCE_TARGET_OUTPUT_PARAMETER);
				break;
			}
			case INFORMATION_SCHEMA_BUILD_TARGET: {
				entry.setIconSource(ICON_SOURCE_BUILD_TARGET);
				break;
			}
			case INFORMATION_SCHEMA_FILE: {
				switch (schemameta.getOrDefault(INFORMATION_META_DATA_FILE_TYPE,
						INFORMATION_META_DATA_FILE_TYPE_FILE)) {
					case INFORMATION_META_DATA_FILE_TYPE_DIRECTORY: {
						entry.setIconSource(ICON_FOLDER);
						break;
					}
					case INFORMATION_META_DATA_FILE_TYPE_BUILD_SCRIPT: {
						entry.setIconSource(ICON_SOURCE_FILE);
						break;
					}
					case INFORMATION_META_DATA_FILE_TYPE_FILE:
					default: {
						entry.setIconSource(ICON_FILE);
						break;
					}
				}
				break;
			}
			default: {
				break;
			}
		}
	}

	private static String createPluginIconSource(String pluginid, String path) {
		//as seen in AbstractUIPlugin.imageDescriptorFromPlugin
		IPath uriPath = new Path("/plugin").append(pluginid).append(path);
		try {
			URI uri = new URI("platform", null, uriPath.toString(), null);
			URL url = uri.toURL();
			try (InputStream is = url.openStream()) {
				return createInputStreamIconSurce(is);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String createIconSource(String path) {
		URL entry = Activator.getDefault().getBundle().getEntry(path);
		if (entry == null) {
			return null;
		}
		try (InputStream is = entry.openStream()) {
			return createInputStreamIconSurce(is);
		} catch (Exception e) {
			return null;
		}
	}

	private static String createInputStreamIconSurce(InputStream is) throws IOException {
		ByteArrayRegion bytes = StreamUtils.readStreamFully(is);
		return "data:image/png;base64, " + Base64.getEncoder().encodeToString(bytes.copyOptionally());
	}
}
