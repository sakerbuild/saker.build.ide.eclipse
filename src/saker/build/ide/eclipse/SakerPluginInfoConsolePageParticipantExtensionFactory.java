package saker.build.ide.eclipse;

public class SakerPluginInfoConsolePageParticipantExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.SakerPluginInfoConsolePageParticipant";
	}
}
