package saker.build.ide.eclipse.properties;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class RepositoriesProjectPropertyPageExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.properties.RepositoriesProjectPropertyPage";
	}
}
