package saker.build.ide.eclipse.extension.params;

import java.util.Map;

final class SetUserParameterModification extends UserParameterModification {
	private String value;

	public SetUserParameterModification(String key, String value) throws IllegalArgumentException {
		super(key);
		this.value = value;
	}

	@Override
	public void apply(Map<String, String> parameters) {
		parameters.put(key, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		SetUserParameterModification other = (SetUserParameterModification) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[key=" + key + ", value=" + value + "]";
	}

}
