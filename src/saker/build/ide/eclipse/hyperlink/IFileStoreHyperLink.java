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
