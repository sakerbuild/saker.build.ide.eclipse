package saker.build.ide.eclipse.extension.params;

import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import saker.build.ide.eclipse.api.ISakerProject;

public interface IExecutionUserParameterContributor {
	public Set<UserParameterModification> contribute(ISakerProject project, Map<String, String> parameters,
			IProgressMonitor monitor) throws CoreException;

	public default void dispose() {
	}
}
