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
package saker.build.ide.eclipse.handlers;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import saker.build.ide.eclipse.SakerBuildProjectNature;

public class UnconfigureCommandHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
		if (currentSelection instanceof IStructuredSelection) {

			Object firstElement = ((IStructuredSelection) currentSelection).getFirstElement();

			// Get an IResource as an adapter from the current selection
			IAdapterManager adapterManager = Platform.getAdapterManager();
			IResource resourceAdapter = adapterManager.getAdapter(firstElement, IResource.class);

			if (resourceAdapter != null) {
				IResource resource = resourceAdapter;
				IProject project = resource.getProject();
				try {
					IProjectDescription description = project.getDescription();
					String[] natures = description.getNatureIds();

					ArrayList<String> list = new ArrayList<>(Arrays.asList(natures));
					list.remove(SakerBuildProjectNature.NATURE_ID);

					String[] newNatures = list.toArray(new String[list.size()]);

					// validate the natures
					IWorkspace workspace = ResourcesPlugin.getWorkspace();
					IStatus status = workspace.validateNatureSet(newNatures);

					// only apply new nature, if the status is ok
					if (status.getCode() == IStatus.OK) {
						description.setNatureIds(newNatures);
						project.setDescription(description, null);
					}

					return status;
				} catch (CoreException e) {
					throw new ExecutionException(e.getMessage(), e);
				}
			}
		}

		return Status.OK_STATUS;
	}

}
