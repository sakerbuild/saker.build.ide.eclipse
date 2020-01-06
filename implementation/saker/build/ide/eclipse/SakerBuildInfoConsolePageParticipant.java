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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.part.IPageBookViewPage;

import saker.build.ide.eclipse.ISakerBuildInfoConsole.BuildInterfaceAccessor;
import saker.build.ide.eclipse.ISakerBuildInfoConsole.BuildInterfaceAccessor.StackTraceAccessor;
import saker.build.ide.eclipse.ISakerBuildInfoConsole.BuildStateObserver;

public class SakerBuildInfoConsolePageParticipant implements IConsolePageParticipant, BuildStateObserver {
	private static final String TEXT_STOP_BUILD = "Stop build";
	private static final String TEXT_INTERRUPT_BUILD = "Interrupt build";
	private static final ImageDescriptor IMAGE_DESCRIPTOR_CONSOLE_STACKTRACE = Activator
			.getImageDescriptor("icons/console_stacktrace.png");
	private static final ImageDescriptor IMAGE_DESCRIPTOR_INTERRUPT_DISABLED = Activator
			.getImageDescriptor("icons/stop_interrupt_disabled.png");
	private static final ImageDescriptor IMAGE_DESCRIPTOR_INTERRUPT = Activator
			.getImageDescriptor("icons/stop_interrupt.png");
	private static final ImageDescriptor IMAGE_DESCRIPTOR_STOP = PlatformUI.getWorkbench().getSharedImages()
			.getImageDescriptor(ISharedImages.IMG_ELCL_STOP);
	private static final ImageDescriptor IMAGE_DESCRIPTOR_STOP_DISABLED = PlatformUI.getWorkbench().getSharedImages()
			.getImageDescriptor(ISharedImages.IMG_ELCL_STOP_DISABLED);

	private IPageBookViewPage page;

	private SakerProjectBuildConsole console;
	private StopAction stopBuildAction;

	private ActionContributionItem consolePrintContribution;

	private Set<BuildInterfaceAccessor> buildAccessors = new HashSet<>();

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}

	@Override
	public synchronized void init(IPageBookViewPage page, IConsole console) {
		this.page = page;
		if (this.console != null) {
			return;
		}
		if (!(console instanceof ISakerBuildInfoConsole)) {
			//not the daemon console.
			return;
		}
		SakerProjectBuildConsole buildconsole = (SakerProjectBuildConsole) console;
		this.console = buildconsole;
		buildconsole.addBuildStateObserver(this);
		IActionBars bars = page.getSite().getActionBars();
		IToolBarManager tbm = bars.getToolBarManager();

		stopBuildAction = new StopAction(TEXT_STOP_BUILD, IMAGE_DESCRIPTOR_STOP);
		tbm.appendToGroup(IConsoleConstants.LAUNCH_GROUP, stopBuildAction);
	}

	@Override
	public synchronized void dispose() {
		ISakerBuildInfoConsole console = this.console;
		if (console != null) {
			console.removeBuildStateObserver(this);
		}
		stopBuildAction = null;
		this.console = null;
	}

	@Override
	public void activated() {
	}

	@Override
	public void deactivated() {
	}

	@Override
	public synchronized void buildStarted(BuildInterfaceAccessor accessor) {
		if (buildAccessors.add(accessor)) {
			if (stopBuildAction != null) {
				stopBuildAction.resetEnable();
			}
			if (consolePrintContribution != null) {
				IToolBarManager tbm = page.getSite().getActionBars().getToolBarManager();
				tbm.remove(consolePrintContribution);
				Display.getDefault().syncExec(() -> tbm.update(false));
				consolePrintContribution = null;
			}
		}
	}

	@Override
	public synchronized void buildEnded(BuildInterfaceAccessor accessor) {
		if (buildAccessors.remove(accessor)) {
			boolean enabled = !buildAccessors.isEmpty();
			if (!enabled && stopBuildAction != null) {
				stopBuildAction.disable();
			}
			StackTraceAccessor st = accessor.getStackTraceAccessor();
			if (consolePrintContribution != null) {
				IToolBarManager tbm = page.getSite().getActionBars().getToolBarManager();
				tbm.remove(consolePrintContribution);
				Display.getDefault().syncExec(() -> tbm.update(false));
			}
			if (st != null) {
				consolePrintContribution = new ActionContributionItem(new PrintCompleteStacktraceAction(st));
				IToolBarManager tbm = page.getSite().getActionBars().getToolBarManager();
				tbm.prependToGroup(IConsoleConstants.LAUNCH_GROUP, consolePrintContribution);
				Display.getDefault().asyncExec(() -> tbm.update(false));
			}
		}
	}

	private final class PrintCompleteStacktraceAction extends Action {
		private StackTraceAccessor stacktrace;

		public PrintCompleteStacktraceAction(StackTraceAccessor stacktrace) {
			super("Print complete stacktrace", IMAGE_DESCRIPTOR_CONSOLE_STACKTRACE);
			this.stacktrace = stacktrace;
		}

		@Override
		public void run() {
			synchronized (SakerBuildInfoConsolePageParticipant.this) {
				StackTraceAccessor e = this.stacktrace;
				if (e == null) {
					return;
				}
				e.printToConsole(console);
				this.stacktrace = null;
				if (consolePrintContribution != null && consolePrintContribution.getAction() == this) {
					IToolBarManager tbm = page.getSite().getActionBars().getToolBarManager();
					tbm.remove(consolePrintContribution);
					consolePrintContribution = null;
					Display.getDefault().asyncExec(() -> tbm.update(false));
				}
			}
		}
	}

	private final class StopAction extends Action {
		{
			setDisabledImageDescriptor(IMAGE_DESCRIPTOR_STOP_DISABLED);
		}

		private boolean interrupting = false;

		StopAction(String text, ImageDescriptor image) {
			super(text, image);
		}

		public void resetEnable() {
			resetImage();
			this.setEnabled(true);
		}

		private void resetImage() {
			setText(TEXT_STOP_BUILD);
			this.setImageDescriptor(IMAGE_DESCRIPTOR_STOP);
			this.setDisabledImageDescriptor(IMAGE_DESCRIPTOR_STOP_DISABLED);
			interrupting = false;
		}

		public void disable() {
			resetImage();
			this.setEnabled(false);
		}

		@Override
		public void run() {
			synchronized (SakerBuildInfoConsolePageParticipant.this) {
				if (interrupting) {
					for (BuildInterfaceAccessor accessor : buildAccessors) {
						accessor.interruptAndStop();
					}
				} else {
					for (BuildInterfaceAccessor accessor : buildAccessors) {
						accessor.stop();
					}
				}
				setImageDescriptor(IMAGE_DESCRIPTOR_INTERRUPT);
				setDisabledImageDescriptor(IMAGE_DESCRIPTOR_INTERRUPT_DISABLED);
				setText(TEXT_INTERRUPT_BUILD);
				interrupting = true;
			}
		}
	}

}
