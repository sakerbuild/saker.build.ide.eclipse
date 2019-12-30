package saker.build.ide.eclipse.extension.params;

import java.util.Map;

public final class RemoveUserParameterModification extends UserParameterModification {

	public RemoveUserParameterModification(String key) throws IllegalArgumentException {
		super(key);
	}

	@Override
	public void apply(Map<String, String> parameters) {
		parameters.remove(key);
	}

}
