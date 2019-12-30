package saker.build.ide.eclipse.api;

import org.eclipse.core.resources.IProject;

public interface ISakerProject {
	public IProject getProject();

	public ISakerPlugin getPlugin();

	public String executionPathToProjectRelativePath(String executionpath);

	//argument may be absolute or relative too
	public String projectPathToExecutionPath(String path);
}
