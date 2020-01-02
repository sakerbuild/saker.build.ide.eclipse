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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IEditorAssociationOverride;

public class BuildFileEditorAssociationOverride implements IEditorAssociationOverride {

	@Override
	public IEditorDescriptor[] overrideEditors(IEditorInput editorInput, IContentType contentType,
			IEditorDescriptor[] editorDescriptors) {
		if (!(editorInput instanceof IFileEditorInput)) {
			return editorDescriptors;
		}
		IFileEditorInput ifileinput = (IFileEditorInput) editorInput;
		if (!isScriptConfigurationApplies(ifileinput)) {
			return editorDescriptors;
		}
		IEditorRegistry editorreg = PlatformUI.getWorkbench().getEditorRegistry();
		IEditorDescriptor found = editorreg.findEditor(BuildFileEditor.ID);
		if (found == null) {
			//should happen, but test anyway
			return editorDescriptors;
		}
		IEditorDescriptor[] result = Arrays.copyOf(editorDescriptors, editorDescriptors.length + 1);
		result[editorDescriptors.length] = found;
		return result;
	}

	@Override
	public IEditorDescriptor[] overrideEditors(String fileName, IContentType contentType,
			IEditorDescriptor[] editorDescriptors) {
		return editorDescriptors;
	}

	@Override
	public IEditorDescriptor overrideDefaultEditor(IEditorInput editorInput, IContentType contentType,
			IEditorDescriptor editorDescriptor) {
		if (editorDescriptor != null && !"org.eclipse.ui.DefaultTextEditor".equals(editorDescriptor.getId())) {
			//a default is already set to a non-text file
			//don't override the default editor
			return editorDescriptor;
		}
		if (!(editorInput instanceof IFileEditorInput)) {
			return editorDescriptor;
		}
		IFileEditorInput ifileinput = (IFileEditorInput) editorInput;
		if (!isScriptConfigurationApplies(ifileinput)) {
			return editorDescriptor;
		}
		IEditorRegistry editorreg = PlatformUI.getWorkbench().getEditorRegistry();
		IEditorDescriptor found = editorreg.findEditor(BuildFileEditor.ID);
		if (found == null) {
			//should happen, but test anyway
			return editorDescriptor;
		}
		return found;
	}

	@Override
	public IEditorDescriptor overrideDefaultEditor(String fileName, IContentType contentType,
			IEditorDescriptor editorDescriptor) {
		return editorDescriptor;
	}

	private static boolean isScriptConfigurationApplies(IFileEditorInput input) {
		IFile file = input.getFile();
		IProject project = file.getProject();
		EclipseSakerIDEProject sakerproject = ImplActivator.getDefault().getOrCreateSakerProject(project);
		if (sakerproject == null) {
			return false;
		}
		return sakerproject.isScriptConfigurationAppliesTo(file);

	}

}
