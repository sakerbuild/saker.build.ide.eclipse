package saker.build.ide.eclipse.script.proposal;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class SakerScriptProposalDesignerExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.script.proposal.SakerScriptProposalDesigner";
	}
}
