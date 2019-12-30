package saker.build.ide.eclipse;

import java.util.List;

import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineRoot;

final class MultiScriptOutlineDesigner implements IScriptOutlineDesigner {
	private final List<IScriptOutlineDesigner> designers;

	MultiScriptOutlineDesigner(List<IScriptOutlineDesigner> designers) {
		this.designers = designers;
	}

	@Override
	public void process(IScriptOutlineRoot outlineroot) {
		for (IScriptOutlineDesigner designer : designers) {
			designer.process(outlineroot);
		}
	}
}