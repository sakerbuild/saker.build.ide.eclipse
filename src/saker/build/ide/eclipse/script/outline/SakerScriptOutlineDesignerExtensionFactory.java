package saker.build.ide.eclipse.script.outline;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class SakerScriptOutlineDesignerExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.script.outline.SakerScriptOutlineDesigner";
	}
}
