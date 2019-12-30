package saker.build.ide.eclipse.extension.params;

import java.util.Map;

public abstract class UserParameterModification {
	public static UserParameterModification set(String key, String value) {
		return new SetUserParameterModification(key, value);
	}

	public static UserParameterModification remove(String key) {
		return new RemoveUserParameterModification(key);
	}

	protected final String key;

	/*default*/ UserParameterModification(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("Empty or null key.");
		}
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public abstract void apply(Map<String, String> parameters);

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
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
		UserParameterModification other = (UserParameterModification) obj;
		if (!key.equals(other.key))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[key=" + key + "]";
	}
}
