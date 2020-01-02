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

import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationEntry;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class SakerScriptInformationDesigner implements IScriptInformationDesigner {
	private static final String ICON_SOURCE_TASK = createIconSource("icons/task@2.png");
	private static final String ICON_SOURCE_VARIABLE = createIconSource("icons/var@2.png");
	private static final String ICON_SOURCE_TARGET_INPUT_PARAMETER = createIconSource("icons/inparam@2.png");
	private static final String ICON_SOURCE_TARGET_OUTPUT_PARAMETER = createIconSource("icons/outparam@2.png");
	private static final String ICON_SOURCE_BUILD_TARGET = createIconSource("icons/target@2.png");

	public static final String INFORMATION_SCHEMA_IDENTIFIER = "saker.script";
	private static final String INFORMATION_SCHEMA_TASK = INFORMATION_SCHEMA_IDENTIFIER + ".task";
	private static final String INFORMATION_SCHEMA_TASK_PARAMETER = INFORMATION_SCHEMA_IDENTIFIER + ".task_parameter";
	private static final String INFORMATION_SCHEMA_ENUM = INFORMATION_SCHEMA_IDENTIFIER + ".enum";
	private static final String INFORMATION_SCHEMA_VARIABLE = INFORMATION_SCHEMA_IDENTIFIER + ".var";
	private static final String INFORMATION_SCHEMA_TARGET_INPUT_PARAMETER = INFORMATION_SCHEMA_IDENTIFIER
			+ ".target.input_parameter";
	private static final String INFORMATION_SCHEMA_TARGET_OUTPUT_PARAMETER = INFORMATION_SCHEMA_IDENTIFIER
			+ ".target.output_parameter";
	private static final String INFORMATION_SCHEMA_BUILD_TARGET = INFORMATION_SCHEMA_IDENTIFIER + ".target";

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
			default: {
				break;
			}
		}
	}

	private static String createIconSource(String path) {
		URL entry = Activator.getDefault().getBundle().getEntry(path);
		if (entry == null) {
			return null;
		}
		try (InputStream is = entry.openStream()) {
			ByteArrayRegion bytes = StreamUtils.readStreamFully(is);
			return "data:image/png;base64, " + Base64.getEncoder().encodeToString(bytes.copyOptionally());
		} catch (Exception e) {
			return null;
		}
	}
}
