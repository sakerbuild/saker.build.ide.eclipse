/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
