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
package saker.build.ide.eclipse.script.proposal;

import org.eclipse.swt.graphics.Image;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalEntry;
import saker.build.ide.eclipse.script.information.EclipseScriptInformationEntry;
import saker.build.ide.support.ui.BaseScriptProposalEntry;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.TextPartition;

public class EclipseScriptProposalEntry extends BaseScriptProposalEntry<EclipseScriptInformationEntry>
		implements IScriptProposalEntry {
	private Image proposalImage;

	public EclipseScriptProposalEntry(ScriptCompletionProposal proposal) {
		super(proposal);
	}

	@Override
	protected EclipseScriptInformationEntry createInformationEntry(TextPartition partition) {
		return new EclipseScriptInformationEntry(partition);
	}

	@Override
	public void setProposalImage(Image image) {
		this.proposalImage = image;
	}

	public Image getProposalImage() {
		return proposalImage;
	}

}
