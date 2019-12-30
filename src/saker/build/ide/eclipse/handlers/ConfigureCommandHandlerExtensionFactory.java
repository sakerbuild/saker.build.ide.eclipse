package saker.build.ide.eclipse.handlers;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class ConfigureCommandHandlerExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.handlers.ConfigureCommandHandler";
	}
}
