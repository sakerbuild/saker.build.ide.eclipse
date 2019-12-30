package saker.build.ide.eclipse.extension.ideconfig;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public interface IIDEProjectConfigurationRootEntry extends IIDEProjectConfigurationEntry {
	public void apply(IProgressMonitor monitor) throws CoreException;
}
