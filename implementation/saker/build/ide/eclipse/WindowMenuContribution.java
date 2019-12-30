package saker.build.ide.eclipse;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Menu;

public class WindowMenuContribution extends ContributionItem {
	private IMenuListener menulistener = manager -> manager.markDirty();

	public WindowMenuContribution() {
	}

	public WindowMenuContribution(String id) {
		super(id);
	}

	@Override
	public void fill(Menu menu, int index) {
		super.fill(menu, index);
		MenuManager parent = (MenuManager) getParent();
		parent.addMenuListener(menulistener);

		Action action = new Action("Reload plugin environment") {
			@Override
			public void run() {
				EclipseSakerIDEPlugin plugin = ImplActivator.getDefault().getEclipseIDEPlugin();
				if (plugin == null) {
					//doesnt really happen
					return;
				}
				plugin.reloadPluginEnvironment();
			}
		};
		new ActionContributionItem(action).fill(menu, -1);

	}

	@Override
	public boolean isDynamic() {
		//XXX this doesn't really need to be dynamic
		return true;
	}

}
