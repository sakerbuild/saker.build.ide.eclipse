package saker.build.ide.eclipse;

public class BuildFileEditorExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.BuildFileEditor";
	}
}
