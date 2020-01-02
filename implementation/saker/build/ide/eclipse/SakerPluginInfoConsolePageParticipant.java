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
