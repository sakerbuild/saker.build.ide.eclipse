package saker.build.ide.eclipse.hyperlink;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

public class IFileHyperLink extends EditorOpenerHyperLink {
	private IFile file;

	public IFileHyperLink(IFile file, int line, String linestart, String lineend) {
		super(line, linestart, lineend);
		this.file = file;
	}

	@Override
	protected IEditorPart openEditorPart(IWorkbenchPage page) throws PartInitException {
		return IDE.openEditor(page, file);
	}

}