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
package saker.build.ide.eclipse.hyperlink;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public abstract class EditorOpenerHyperLink implements IHyperlink {
	private int lineNumber;
	private String lineStartPosition;
	private String lineEndPosition;

	public EditorOpenerHyperLink(int lineNumber, String lineStartPosition, String lineEndPosition) {
		this.lineNumber = lineNumber;
		this.lineStartPosition = lineStartPosition;
		this.lineEndPosition = lineEndPosition;
	}

	protected abstract IEditorPart openEditorPart(IWorkbenchPage page) throws PartInitException;

	@Override
	public void linkEntered() {
	}

	@Override
	public void linkExited() {
	}

	@Override
	public void linkActivated() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				try {
					IEditorPart editorPart = openEditorPart(page);
					if (lineNumber > 0) {
						ITextEditor texteditor = null;
						if (editorPart instanceof ITextEditor) {
							texteditor = (ITextEditor) editorPart;
						} else if (editorPart != null) {
							texteditor = editorPart.getAdapter(ITextEditor.class);
						}
						if (texteditor != null) {
							IEditorInput input = editorPart.getEditorInput();
							int selectionoffset = -1;
							int selectionlength = -1;
							int linestartpos = (this.lineStartPosition == null || this.lineStartPosition.isEmpty() ? 1
									: Integer.parseInt(this.lineStartPosition)) - 1;
							int lineendpos = this.lineEndPosition == null || this.lineEndPosition.isEmpty() ? -1
									: Integer.parseInt(this.lineEndPosition) - 1;

							IDocumentProvider provider = texteditor.getDocumentProvider();
							try {
								provider.connect(input);
							} catch (CoreException e) {
								// unable to link
								e.printStackTrace();
								return;
							}
							IDocument document = provider.getDocument(input);
							try {
								IRegion region = document.getLineInformation(lineNumber - 1);
								selectionoffset = region.getOffset() + linestartpos;
								//add + 1, as the line end pos is inclusive
								selectionlength = lineendpos < 0 ? region.getLength() - linestartpos
										: (lineendpos - linestartpos) + 1;
							} catch (BadLocationException e) {
								// unable to link
								e.printStackTrace();
							}
							provider.disconnect(input);

							if (selectionoffset >= 0 && selectionlength >= 0) {
								texteditor.selectAndReveal(selectionoffset, selectionlength);
							}
						}
					}
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
