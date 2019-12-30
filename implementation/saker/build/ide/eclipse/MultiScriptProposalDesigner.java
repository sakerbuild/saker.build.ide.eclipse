package saker.build.ide.eclipse;

import java.util.List;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalsRoot;

public class MultiScriptProposalDesigner implements IScriptProposalDesigner {
	private final List<IScriptProposalDesigner> designers;

	public MultiScriptProposalDesigner(List<IScriptProposalDesigner> designers) {
		this.designers = designers;
	}

	@Override
	public void process(IScriptProposalsRoot proposalsroot) {
		for (IScriptProposalDesigner designer : designers) {
			designer.process(proposalsroot);
		}
	}

}
