package saker.build.ide.eclipse;

public class WindowMenuContributionExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.WindowMenuContribution";
	}
}
