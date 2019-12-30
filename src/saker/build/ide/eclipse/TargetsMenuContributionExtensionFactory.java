package saker.build.ide.eclipse;

public class TargetsMenuContributionExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.TargetsMenuContribution";
	}
}
