package saker.build.ide.eclipse;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorOutlineAdapterFactory implements IAdapterFactory {
	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> required) {
		if (IContentOutlinePage.class.equals(required)) {
			return required.cast(((BuildFileEditor) adaptableObject).new OutlinePage());
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { IContentOutlinePage.class };
	}

}
