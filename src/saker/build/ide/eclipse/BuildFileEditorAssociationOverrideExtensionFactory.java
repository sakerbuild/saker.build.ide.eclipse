package saker.build.ide.eclipse;

public class BuildFileEditorAssociationOverrideExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.BuildFileEditorAssociationOverride";
	}
}
