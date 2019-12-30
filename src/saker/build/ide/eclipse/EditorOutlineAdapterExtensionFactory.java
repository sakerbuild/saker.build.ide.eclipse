package saker.build.ide.eclipse;

public class EditorOutlineAdapterExtensionFactory extends ImplClassLoadingExtensionFactory {
	@Override
	protected String getExtensionClassName() {
		return "saker.build.ide.eclipse.EditorOutlineAdapterFactory";
	}
}
