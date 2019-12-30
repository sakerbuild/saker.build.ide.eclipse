package saker.build.ide.eclipse;

import java.util.List;

import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationRoot;

public class MultiScriptInformationDesigner implements IScriptInformationDesigner {
	private final List<IScriptInformationDesigner> designers;

	public MultiScriptInformationDesigner(List<IScriptInformationDesigner> designers) {
		this.designers = designers;
	}

	@Override
	public void process(IScriptInformationRoot informationroot) {
		for (IScriptInformationDesigner designer : designers) {
			designer.process(informationroot);
		}
	}

}
