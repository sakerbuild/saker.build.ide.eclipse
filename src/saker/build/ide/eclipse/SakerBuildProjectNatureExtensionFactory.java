package saker.build.ide.eclipse;

public class SakerBuildProjectNatureExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.SakerBuildProjectNature";
	}
}
