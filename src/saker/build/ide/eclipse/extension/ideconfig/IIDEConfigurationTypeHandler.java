package saker.build.ide.eclipse.extension.ideconfig;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import saker.build.ide.eclipse.api.ISakerProject;

public interface IIDEConfigurationTypeHandler {
	public IIDEProjectConfigurationRootEntry[] parseConfiguration(ISakerProject project, Map<String, ?> configuration,
			IProgressMonitor monitor) throws CoreException;
}
