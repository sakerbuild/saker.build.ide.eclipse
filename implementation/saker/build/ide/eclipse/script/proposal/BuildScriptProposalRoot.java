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
