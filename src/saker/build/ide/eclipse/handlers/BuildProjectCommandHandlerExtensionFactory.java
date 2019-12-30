package saker.build.ide.eclipse.handlers;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class BuildProjectCommandHandlerExtensionFactory extends ImplClassLoadingExtensionFactory {

	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.handlers.BuildProjectCommandHandler";
	}

}
