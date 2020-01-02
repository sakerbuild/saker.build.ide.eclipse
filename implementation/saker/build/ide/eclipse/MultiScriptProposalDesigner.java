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

import java.util.List;

import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalsRoot;

public class MultiScriptProposalDesigner implements IScriptProposalDesigner {
	private final List<IScriptProposalDesigner> designers;

	public MultiScriptProposalDesigner(List<IScriptProposalDesigner> designers) {
		this.designers = designers;
	}

	@Override
	public void process(IScriptProposalsRoot proposalsroot) {
		for (IScriptProposalDesigner designer : designers) {
			designer.process(proposalsroot);
		}
	}

}
