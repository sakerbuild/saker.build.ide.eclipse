package saker.build.ide.eclipse.hyperlink;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

public class IFileStoreHyperLink extends EditorOpenerHyperLink {
	private IFileStore fileStore;

	public IFileStoreHyperLink(IFileStore fileStore, int lineNumber, String lineStartPosition, String lineEndPosition) {
		super(lineNumber, lineStartPosition, lineEndPosition);
		this.fileStore = fileStore;
	}

	@Override
	protected IEditorPart openEditorPart(IWorkbenchPage page) throws PartInitException {
		return IDE.openEditorOnFileStore(page, fileStore);
	}

}
