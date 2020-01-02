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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalEntry;
import saker.build.ide.eclipse.script.information.BuildScriptScriptInformationEntry;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.TextPartition;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class BuildScriptProposalEntry implements IScriptProposalEntry {
	private final String schemaIdentifier;
	private final Map<String, String> schemaMetaData;

	private String displayString;
	private String displayType;
	private String displayRelation;
	private Image proposalImage;
	private LazySupplier<List<BuildScriptScriptInformationEntry>> informationEntries;
	private ScriptCompletionProposal proposal;

	public BuildScriptProposalEntry(ScriptCompletionProposal proposal) {
		this.proposal = proposal;
		this.schemaIdentifier = proposal.getSchemaIdentifier();
		this.schemaMetaData = ImmutableUtils.makeImmutableNavigableMap(proposal.getSchemaMetaData());
		this.displayString = proposal.getDisplayString();
		this.displayRelation = proposal.getDisplayRelation();
		this.displayType = proposal.getDisplayType();
		this.informationEntries = LazySupplier.of(() -> {
			ArrayList<BuildScriptScriptInformationEntry> entries = new ArrayList<>();
			PartitionedTextContent info = proposal.getInformation();
			if (info != null) {
				Iterable<? extends TextPartition> partitions = info.getPartitions();
				if (partitions != null) {
					for (TextPartition partition : partitions) {
						if (partition == null) {
							continue;
						}
						entries.add(new BuildScriptScriptInformationEntry(partition));
					}
				}
			}
			return entries;
		});
	}

	public ScriptCompletionProposal getProposal() {
		return proposal;
	}

	@Override
	public String getDisplayString() {
		return displayString;
	}

	@Override
	public String getDisplayType() {
		return displayType;
	}

	@Override
	public String getDisplayRelation() {
		return displayRelation;
	}

	@Override
	public void setDisplayString(String display) {
		this.displayString = display;
	}

	@Override
	public void setDisplayType(String type) {
		this.displayType = type;
	}

	@Override
	public void setDisplayRelation(String relation) {
		this.displayRelation = relation;
	}

	@Override
	public void setProposalImage(Image image) {
		this.proposalImage = image;
	}

	@Override
	public List<? extends BuildScriptScriptInformationEntry> getInformationEntries() {
		return informationEntries.get();
	}

	@Override
	public String getSchemaIdentifier() {
		return schemaIdentifier;
	}

	@Override
	public Map<String, String> getSchemaMetaData() {
		return schemaMetaData;
	}

	public Image getProposalImage() {
		return proposalImage;
	}

	@Override
	public String toString() {
		return "BuildScriptProposalEntry[" + displayString + " - " + displayType + " : " + displayRelation + "]";
	}
}
