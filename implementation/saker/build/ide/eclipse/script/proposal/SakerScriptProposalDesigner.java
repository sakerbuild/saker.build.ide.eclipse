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
package saker.build.ide.eclipse.script.proposal;

import java.util.Map;

import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.BuildFileEditor;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalEntry;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalsRoot;
import saker.build.ide.eclipse.script.information.SakerScriptInformationDesigner;
import saker.build.ide.eclipse.script.outline.SakerScriptOutlineDesigner;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerScriptProposalDesigner implements IScriptProposalDesigner {
	public static final String PROPOSAL_SCHEMA = "saker.script";
	private static final String PROPOSAL_META_DATA_TYPE = "type";
	private static final String PROPOSAL_META_DATA_TYPE_FILE = "file";
	private static final String PROPOSAL_META_DATA_TYPE_ENUM = "enum";
	private static final String PROPOSAL_META_DATA_TYPE_EXTERNAL_LITERAL = "external_literal";
	private static final String PROPOSAL_META_DATA_TYPE_FIELD = "field";
	private static final String PROPOSAL_META_DATA_TYPE_TASK_PARAMETER = "task_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_TASK = "task";
	private static final String PROPOSAL_META_DATA_TYPE_USER_PARAMETER = "user_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_ENVIRONMENT_PARAMETER = "environment_parameter";
	private static final String PROPOSAL_META_DATA_TYPE_VARIABLE = "variable";
	private static final String PROPOSAL_META_DATA_TYPE_FOREACH_VARIABLE = "foreach_variable";
	private static final String PROPOSAL_META_DATA_TYPE_STATIC_VARIABLE = "static_variable";
	private static final String PROPOSAL_META_DATA_TYPE_GLOBAL_VARIABLE = "global_variable";
	private static final String PROPOSAL_META_DATA_TYPE_TASK_QUALIFIER = "task_qualifier";
	private static final String PROPOSAL_META_DATA_TYPE_LITERAL = "literal";
	private static final String PROPOSAL_META_DATA_TYPE_BUILD_TARGET = "build_target";

	private static final String PROPOSAL_META_DATA_FILE_TYPE = "file_type";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_FILE = "file";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_BUILD_SCRIPT = "build_script";
	private static final String PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY = "dir";

	public static final Image IMG_FOLDER = Activator.getImageFromPlugin("org.eclipse.ui.ide",
			"icons/full/obj16/folder.png");
	public static final Image IMG_FILE = Activator.getImageFromPlugin("org.eclipse.ui",
			"icons/full/obj16/file_obj.png");

	public static final Image IMG_BUILD_SCRIPT = Activator.getImageFromPlugin(Activator.PLUGIN_ID,
			"icons/icon_file.png");

	@Override
	public void process(IScriptProposalsRoot proposalsroot) {
		System.out.println("SakerScriptProposalDesigner.process()");
		boolean darktheme = BuildFileEditor.isCurrentThemeDark();
		for (IScriptProposalEntry proposal : proposalsroot.getProposals()) {
			if (proposal == null) {
				continue;
			}
			if (!PROPOSAL_SCHEMA.equals(proposal.getSchemaIdentifier())) {
				continue;
			}
			processProposal(proposal, darktheme);
		}
	}

	private static void processProposal(IScriptProposalEntry proposal, boolean darktheme) {
		Map<String, String> schemameta = proposal.getSchemaMetaData();
		String type = ObjectUtils.getMapValue(schemameta, PROPOSAL_META_DATA_TYPE);
		if (type == null) {
			return;
		}
		switch (type) {
			case PROPOSAL_META_DATA_TYPE_BUILD_TARGET: {
				proposal.setProposalImage(SakerScriptOutlineDesigner.getImgTarget(darktheme));
				break;
			}
			case PROPOSAL_META_DATA_TYPE_LITERAL: {
				proposal.setProposalImage(SakerScriptOutlineDesigner.getImgStringliteral(darktheme));
				break;
			}
			case PROPOSAL_META_DATA_TYPE_VARIABLE:
			case PROPOSAL_META_DATA_TYPE_STATIC_VARIABLE:
			case PROPOSAL_META_DATA_TYPE_GLOBAL_VARIABLE: {
				proposal.setProposalImage(SakerScriptOutlineDesigner.getImgVar(darktheme));
				break;
			}
			case PROPOSAL_META_DATA_TYPE_TASK: {
				proposal.setProposalImage(SakerScriptOutlineDesigner.getImgTask(darktheme));
				break;
			}
			case PROPOSAL_META_DATA_TYPE_FILE: {
				switch (schemameta.getOrDefault(PROPOSAL_META_DATA_FILE_TYPE, PROPOSAL_META_DATA_FILE_TYPE_FILE)) {
					case PROPOSAL_META_DATA_FILE_TYPE_DIRECTORY: {
						proposal.setProposalImage(IMG_FOLDER);
						break;
					}
					case PROPOSAL_META_DATA_FILE_TYPE_BUILD_SCRIPT: {
						proposal.setProposalImage(IMG_BUILD_SCRIPT);
						break;
					}
					case PROPOSAL_META_DATA_FILE_TYPE_FILE:
					default: {
						proposal.setProposalImage(IMG_FILE);
						break;
					}
				}
				break;
			}
			default: {
				break;
			}
		}
		SakerScriptInformationDesigner.processEntries(proposal.getInformationEntries());
	}

}
