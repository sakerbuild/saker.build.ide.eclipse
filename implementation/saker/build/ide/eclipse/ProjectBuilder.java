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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;

import saker.build.file.path.SakerPath;

public class ProjectBuilder extends IncrementalProjectBuilder {

	private static final String CONSOLE_NAME = "Saker.Build Console";
	public static final String BUILDER_ID = Activator.PLUGIN_ID + ".builder";
	public static final String MARKER_TYPE = IMarker.PROBLEM;

	private static final Map<String, Integer> MARKER_SEVERITY_MAP = new HashMap<>();
	private static final String BUILD_CONSOLE_TYPE = Activator.PLUGIN_ID + ".build.console";

	static {
		MARKER_SEVERITY_MAP.put("error", IMarker.SEVERITY_ERROR);
		MARKER_SEVERITY_MAP.put("fatal error", IMarker.SEVERITY_ERROR);
		MARKER_SEVERITY_MAP.put("info", IMarker.SEVERITY_INFO);
		MARKER_SEVERITY_MAP.put("warning", IMarker.SEVERITY_WARNING);
	}

	public ProjectBuilder() {
	}

	private static SakerProjectBuildConsole findExistingConsole(IProject project) {
		String type = getProjectBuildConsoleType(project);
		LogHighlightingConsole con = LogHighlightingConsole.findExistingConsole(type);
		if (con != null) {
			con.activate();
		}
		return (SakerProjectBuildConsole) con;
	}

	private static String getProjectBuildConsoleType(IProject project) {
		return BUILD_CONSOLE_TYPE + "." + project.getName();
	}

	public static SakerProjectBuildConsole findBuildConsole(EclipseSakerIDEProject sakerideproject) {
		IProject project = sakerideproject.getProject();
		SakerProjectBuildConsole found = findExistingConsole(project);
		if (found != null) {
			return found;
		}
		String type = getProjectBuildConsoleType(project);
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();

		// no console found, so create a new one
		SakerProjectBuildConsole console = new SakerProjectBuildConsole(sakerideproject, CONSOLE_NAME, type);
		conMan.addConsoles(new IConsole[] { console });

		console.ensureInit();
		conMan.showConsoleView(console);

		return console;
	}

	public static void buildAsync(EclipseSakerIDEProject project, SakerPath scriptpath, String targetname) {
		SakerPath projectworkingdir = project.getWorkingDirectoryExecutionPath();
		String jobname = createBuildJobName(scriptpath, targetname, projectworkingdir);
		Job j = new Job(jobname) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				project.build(scriptpath, targetname, monitor);

				return Status.OK_STATUS;
			}
		};
		j.setPriority(Job.BUILD);
		j.schedule();
	}

	private static String createBuildJobName(SakerPath scriptpath, String targetname, SakerPath projectworkingdir) {
		if (projectworkingdir == null) {
			return targetname + "@" + scriptpath;
		}
		return targetname + "@"
				+ (scriptpath.startsWith(projectworkingdir) ? projectworkingdir.relativize(scriptpath) : scriptpath);
	}

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
		//don't run the saker.build builder on any Eclipse build events
		//the builder is only registered to support the Clean IDE action, however,
		//running the builds via the builder can be impractical
		// * auto builds can be annoying as saker.build builds perform their own stuff in general
		// * building during a complete workspace build is usually not wished for
		// * the target that is being build is unclear in some builds
		// therefore, the builder can be invoked through the saker.build menu, or via hotkeys
		
//		if (kind == AUTO_BUILD) {
//			return null;
//		}
//		IProject project = getProject();
//		ImplActivator activator = ImplActivator.getDefault();
//		EclipseSakerIDEProject sakerproject = activator.getOrCreateSakerProject(project);
//		if (sakerproject == null) {
//			activator.getEclipseIDEPlugin().displayError(
//					"Project " + project.getName() + " is not a saker.build project. Builder is not invoked on it.");
//			return null;
//		}
//		sakerproject.build(monitor);

		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		try {
			ImplActivator.clean(getProject(), monitor);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.toString()));
		}
	}

	public static void closeProject(IProject project) {
		LogHighlightingConsole console = findExistingConsole(project);
		if (console != null) {
			ConsolePlugin plugin = ConsolePlugin.getDefault();
			IConsoleManager conMan = plugin.getConsoleManager();
			conMan.removeConsoles(new IConsole[] { console });
		}
	}

}
