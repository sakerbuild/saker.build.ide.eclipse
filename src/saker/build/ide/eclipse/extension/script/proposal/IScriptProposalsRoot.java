package saker.build.ide.eclipse.extension.script.proposal;

import java.util.List;

public interface IScriptProposalsRoot {
	public List<? extends IScriptProposalEntry> getProposals();
}
