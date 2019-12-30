package saker.build.ide.eclipse;

import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.part.IPageBookViewPage;

public class SakerPluginInfoConsolePageParticipant implements IConsolePageParticipant {
	public static final String GROUP_DAEMON_CONTROL = "daemonControl";

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}

	@Override
	public void init(IPageBookViewPage page, IConsole console) {
		if (!(console instanceof ISakerPluginInfoConsole)) {
			//not the daemon console.
			return;
		}
		//XXX do we have any useful buttons we can put on the saker build console?
//		ISakerPluginInfoConsole daemonconsole = (ISakerPluginInfoConsole) console;
//		IActionBars bars = page.getSite().getActionBars();
//		IToolBarManager tbm = bars.getToolBarManager();
//		tbm.insertBefore(IConsoleConstants.LAUNCH_GROUP, new Separator(GROUP_DAEMON_CONTROL));
//
//		tbm.appendToGroup(GROUP_DAEMON_CONTROL, new Action("Stop build",
//				PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_STOP)) {
//			@Override
//			public void run() {
//				System.out.println("SakerBuildInfoConsolePageParticipant.init(...).new Action() {...}.run()");
//			}
//		});

//		tbm.appendToGroup(GROUP_DAEMON_CONTROL,
//				new Action("Shutdown daemon", Activator.getImageDescriptor("icons/icon.png")) {
//					@Override
//					public void run() {
//						daemonconsole.shutdownDaemon();
//					}
//				});
//		tbm.appendToGroup(GROUP_DAEMON_CONTROL,
//				new Action("Print launch parameters", Activator.getImageDescriptor("icons/icon.png")) {
//					@Override
//					public void run() {
//						daemonconsole.describeLaunchParameters();
//					}
//				});
//
//		bars.updateActionBars();
	}

	@Override
	public void dispose() {
	}

	@Override
	public void activated() {
	}

	@Override
	public void deactivated() {
	}

}
