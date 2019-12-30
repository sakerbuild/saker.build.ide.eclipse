package saker.build.ide.eclipse;

public class SakerBuildInfoConsolePageParticipantExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.SakerBuildInfoConsolePageParticipant";
	}
}
