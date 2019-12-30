package saker.build.ide.eclipse.script.information;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class SakerScriptInformationDesignerExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.script.information.SakerScriptInformationDesigner";
	}
}
