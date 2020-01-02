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
import java.util.Set;
import java.util.TreeSet;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalsRoot;
import saker.build.scripting.model.ScriptCompletionProposal;

public class BuildScriptProposalRoot implements IScriptProposalsRoot {

	private List<BuildScriptProposalEntry> proposals;

	public BuildScriptProposalRoot(List<? extends ScriptCompletionProposal> proposals) {
		this.proposals = new ArrayList<>();
		for (ScriptCompletionProposal p : proposals) {
			if (p == null) {
				continue;
			}
			this.proposals.add(new BuildScriptProposalEntry(p));
		}
	}

	@Override
	public List<? extends BuildScriptProposalEntry> getProposals() {
		return proposals;
	}

	public Set<String> getSchemaIdentifiers() {
		Set<String> result = new TreeSet<>();
		for (BuildScriptProposalEntry p : proposals) {
			String schemaid = p.getSchemaIdentifier();
			if (schemaid == null) {
				continue;
			}
			result.add(schemaid);
		}
		return result;
	}

}
