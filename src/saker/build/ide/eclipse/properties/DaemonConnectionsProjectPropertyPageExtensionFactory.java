package saker.build.ide.eclipse.properties;

import saker.build.ide.eclipse.ImplClassLoadingExtensionFactory;

public class DaemonConnectionsProjectPropertyPageExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.properties.DaemonConnectionsProjectPropertyPage";
	}
}
