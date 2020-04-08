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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.ImplActivator;

public class BuildProjectCommandHandler extends AbstractHandler {

	public static final String COMMAND_ID = "saker.build.ide.eclipse.commands.BuildProjectCommand";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject project = getProject(event);
		if (project == null) {
			return null;
		}
		EclipseSakerIDEProject sakerproject = ImplActivator.getDefault().getOrCreateSakerProject(project);
		if (sakerproject == null) {
			return null;
		}
		sakerproject.buildWithNewJob();
		return null;
	}

	public static IProject getProject(final ExecutionEvent event) {
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			final Object element = ((IStructuredSelection) selection).getFirstElement();

			IProject project = Platform.getAdapterManager().getAdapter(element, IProject.class);
			if (project != null) {
				return project;
			}
		}
		IEditorInput editorinput = HandlerUtil.getActiveEditorInput(event);
		if (editorinput == null) {
			IEditorPart editor = HandlerUtil.getActiveEditor(event);
			if (editor != null) {
				editorinput = editor.getEditorInput();
			}
		}
		if (editorinput != null) {
			IResource ires = editorinput.getAdapter(IResource.class);
			if (ires != null) {
				IProject project = ires.getProject();
				if (project != null) {
					return project;
				}
			}
		}

		return null;
	}
}
