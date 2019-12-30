package saker.build.ide.eclipse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public abstract class ImplClassLoadingExtensionFactory extends ImplExtensionFactoryBase {
	@Override
	protected final Object createImpl() throws CoreException {
		try {
			Class<?> c = Class.forName(getExtensionClassName(), false, Activator.getDefault().getImplClassLoader());
			return c.getConstructor().newInstance();
		} catch (IllegalArgumentException | SecurityException | ReflectiveOperationException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, null, e));
		}
	}

	protected abstract String getExtensionClassName();

}
