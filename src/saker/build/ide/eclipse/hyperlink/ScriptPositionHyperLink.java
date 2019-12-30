package saker.build.ide.eclipse.hyperlink;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

import saker.build.ide.eclipse.Activator;

public class ScriptPositionHyperLink implements IHyperlink {
	private String fEditorId;
	private IFile file;
	private int fileOffset;
	private int length;

	public ScriptPositionHyperLink(IFile file, int fileOffset, int length) {
		this.file = file;
		this.fileOffset = fileOffset;
		this.length = length;
	}

	@Override
	public void linkEntered() {
	}

	@Override
	public void linkExited() {
	}

	@Override
	public void linkActivated() {
		IWorkbenchWindow window = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				try {
					IEditorPart editorPart = page.openEditor(new FileEditorInput(file), getEditorId());
					ITextEditor texteditor = null;
					if (editorPart instanceof ITextEditor) {
						texteditor = (ITextEditor) editorPart;
					} else if (editorPart != null) {
						texteditor = editorPart.getAdapter(ITextEditor.class);
					}
					if (texteditor != null) {
						texteditor.selectAndReveal(fileOffset, length);
					}
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private String getEditorId() {
		if (fEditorId == null) {
			IWorkbench workbench = Activator.getDefault().getWorkbench();
			IEditorDescriptor desc = workbench.getEditorRegistry().getDefaultEditor(file.getName(),
					getFileContentType());
			if (desc == null) {
				desc = workbench.getEditorRegistry().getDefaultEditor("default.txt");// .getDefaultEditor(file.getName());//.findEditor(IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID);
			}
			fEditorId = desc.getId();
		}
		return fEditorId;
	}

	private IContentType getFileContentType() {
		try {
			IContentDescription desc = file.getContentDescription();
			if (desc != null) {
				return desc.getContentType();
			}
		} catch (CoreException e) {
		}
		return null;
	}
}
