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

import java.util.Arrays;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

public class SakerBuildProjectNature implements IProjectNature {
	public static final String NATURE_ID = Activator.PLUGIN_ID + ".project.nature";
	private IProject project;

	@Override
	public void configure() throws CoreException {
		addBuilder();
	}

	@Override
	public void deconfigure() throws CoreException {
		removeBuilder();

		ImplActivator.getDefault().closeProject(project);
	}

	public static void removeBuilder(IProject project, String builderid) throws CoreException {
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (builderid.equals(commands[i].getBuilderName())) {
				ICommand[] nc = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, nc, 0, i);
				System.arraycopy(commands, i + 1, nc, i, commands.length - i - 1);

				desc.setBuildSpec(nc);
				project.setDescription(desc, null);

				return;
			}
		}
	}

	private void addBuilder() throws CoreException {
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (ProjectBuilder.BUILDER_ID.equals(commands[i].getBuilderName())) {
				return;
			}
		}

		// set builder to project
		ICommand command = desc.newCommand();
		command.setBuilderName(ProjectBuilder.BUILDER_ID);
		ICommand[] nc = Arrays.copyOf(commands, commands.length + 1);
		nc[commands.length] = command;
		desc.setBuildSpec(nc);
		project.setDescription(desc, null);
	}

	private void removeBuilder() throws CoreException {
		removeBuilder(project, ProjectBuilder.BUILDER_ID);
	}

	@Override
	public IProject getProject() {
		return project;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}

	public static void addNature(IProject project) throws CoreException {
		if (project.isNatureEnabled(NATURE_ID)) {
			return;
		}
		IProjectDescription description = project.getDescription();
		String[] natureids = description.getNatureIds();

		String[] naturesarray = Arrays.copyOf(natureids, natureids.length + 1);
		naturesarray[natureids.length] = NATURE_ID;

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IStatus status = workspace.validateNatureSet(naturesarray);

		// only apply new nature, if the status is ok
		if (status.getCode() == IStatus.OK) {
			description.setNatureIds(naturesarray);
			project.setDescription(description, null);
		} else {
			throw new CoreException(status);
		}
	}
}
