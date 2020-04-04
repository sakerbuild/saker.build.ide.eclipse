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

import org.eclipse.core.runtime.IConfigurationElement;

public final class ContributedExtensionConfiguration<T> {
	private final T contributor;
	private final IConfigurationElement configurationElement;
	private final boolean enabled;

	public ContributedExtensionConfiguration(T contributor, IConfigurationElement configelem, boolean enabled) {
		this.contributor = contributor;
		this.configurationElement = configelem;
		this.enabled = enabled;
	}

	public T getContributor() {
		return contributor;
	}

	public IConfigurationElement getConfigurationElement() {
		return configurationElement;
	}

	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((configurationElement == null) ? 0 : configurationElement.hashCode());
		result = prime * result + ((contributor == null) ? 0 : contributor.hashCode());
		result = prime * result + (enabled ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContributedExtensionConfiguration<?> other = (ContributedExtensionConfiguration<?>) obj;
		if (configurationElement == null) {
			if (other.configurationElement != null)
				return false;
		} else if (!configurationElement.equals(other.configurationElement))
			return false;
		if (contributor == null) {
			if (other.contributor != null)
				return false;
		} else if (!contributor.equals(other.contributor))
			return false;
		if (enabled != other.enabled)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[contributor=" + contributor + ", configurationElement="
				+ configurationElement + ", enabled=" + enabled + "]";
	}

}
