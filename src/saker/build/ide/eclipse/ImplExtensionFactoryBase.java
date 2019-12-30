package saker.build.ide.eclipse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExecutableExtensionFactory;

public abstract class ImplExtensionFactoryBase implements IExecutableExtensionFactory, IExecutableExtension {
	private IConfigurationElement config;
	private String propertyName;
	private Object data;

	@Override
	public final void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		this.config = config;
		this.propertyName = propertyName;
		this.data = data;
	}

	@Override
	public final Object create() throws CoreException {
		Object result = createImpl();
		if (result instanceof IExecutableExtension) {
			((IExecutableExtension) result).setInitializationData(config, propertyName, data);
		}
		return result;
	}

	protected abstract Object createImpl() throws CoreException;

}
