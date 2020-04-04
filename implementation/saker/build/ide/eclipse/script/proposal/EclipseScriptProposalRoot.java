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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalsRoot;
import saker.build.ide.support.ui.BaseScriptProposalRoot;
import saker.build.scripting.model.ScriptCompletionProposal;

public class EclipseScriptProposalRoot extends BaseScriptProposalRoot<EclipseScriptProposalEntry>
		implements IScriptProposalsRoot {
	private EclipseScriptProposalRoot() {
	}

	public static EclipseScriptProposalRoot create(List<? extends ScriptCompletionProposal> proposals) {
		EclipseScriptProposalRoot result = new EclipseScriptProposalRoot();
		result.init(proposals);
		return result;
	}

	@Override
	protected EclipseScriptProposalEntry createProposalEntry(ScriptCompletionProposal proposal) {
		return new EclipseScriptProposalEntry(proposal);
	}

	public Set<String> getSchemaIdentifiers() {
		Set<String> result = new TreeSet<>();
		for (EclipseScriptProposalEntry p : getProposals()) {
			String schemaid = p.getSchemaIdentifier();
			if (schemaid == null) {
				continue;
			}
			result.add(schemaid);
		}
		return result;
	}

}
