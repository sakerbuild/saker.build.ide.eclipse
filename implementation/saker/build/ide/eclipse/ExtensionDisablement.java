package saker.build.ide.eclipse;

import java.util.Objects;

import org.eclipse.core.runtime.IExtension;

public class ExtensionDisablement {
	private String extensionUniqueIdentifier;

	public ExtensionDisablement(IExtension extension) {
		extensionUniqueIdentifier = Objects.requireNonNull(extension.getUniqueIdentifier(),
				"extension unique identifier");
	}

	public ExtensionDisablement(String extensionUniqueIdentifier) {
		Objects.requireNonNull(extensionUniqueIdentifier, "extension unique identifier");
		this.extensionUniqueIdentifier = extensionUniqueIdentifier;
	}

	public String getExtensionUniqueIdentifier() {
		return extensionUniqueIdentifier;
	}

	public boolean isDisabled(IExtension extension) {
		String uniqueid = extension.getUniqueIdentifier();
		if (uniqueid == null) {
			//generally disable all extensions which doesn't have an unique identifier
			return true;
		}
		return uniqueid.equals(extensionUniqueIdentifier);
	}

	public static boolean isDisabled(Iterable<? extends ExtensionDisablement> disablements, IExtension extension) {
		for (ExtensionDisablement disablement : disablements) {
			if (disablement.isDisabled(extension)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((extensionUniqueIdentifier == null) ? 0 : extensionUniqueIdentifier.hashCode());
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
		ExtensionDisablement other = (ExtensionDisablement) obj;
		if (extensionUniqueIdentifier == null) {
			if (other.extensionUniqueIdentifier != null)
				return false;
		} else if (!extensionUniqueIdentifier.equals(other.extensionUniqueIdentifier))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + extensionUniqueIdentifier + "]";
	}

}
