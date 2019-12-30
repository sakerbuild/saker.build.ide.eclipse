package saker.build.ide.eclipse;

public class ProjectBuilderExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.ProjectBuilder";
	}
}
