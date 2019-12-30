package saker.build.ide.eclipse;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

public class NaturePropertyTester extends PropertyTester {

	public NaturePropertyTester() {
	}

	private static IResource extractSelection(ISelection sel) {
		if (!(sel instanceof IStructuredSelection))
			return null;
		IStructuredSelection ss = (IStructuredSelection) sel;
		Object element = ss.getFirstElement();
		if (element instanceof IResource)
			return (IResource) element;
		if (!(element instanceof IAdaptable))
			return null;
		IAdaptable adaptable = (IAdaptable) element;
		Object adapter = adaptable.getAdapter(IResource.class);
		return (IResource) adapter;
	}

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof IEditorPart) {
			IEditorPart editorPart = (IEditorPart) receiver;
			IEditorInput input = editorPart.getEditorInput();
			if (input instanceof IFileEditorInput) {
				receiver = ((IFileEditorInput) input).getFile();
			}
		}
		if (receiver instanceof ISelection) {
			receiver = extractSelection((ISelection) receiver);
		}
		if (receiver instanceof IResource) {
			try {
				IProject project = ((IResource) receiver).getProject();
				if (project.isOpen()) {
					return project.isNatureEnabled((String) expectedValue);
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
