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
import java.nio.file.Paths;
import java.util.ServiceConfigurationError;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import saker.build.runtime.execution.SakerLog;
import saker.build.thirdparty.saker.util.io.IOUtils;

public class ImplActivator implements AutoCloseable, IResourceChangeListener {
	private EclipseSakerIDEPlugin sakerEclipseIDEPlugin;

	public ImplActivator() {
	}

	public EclipseSakerIDEPlugin getEclipseIDEPlugin() {
		return sakerEclipseIDEPlugin;
	}

	public void displayException(int severity, String message, Throwable exc) {
		EclipseSakerIDEPlugin.displayException(sakerEclipseIDEPlugin, severity, message, exc);
	}

	public void displayException(int severity, String message) {
		EclipseSakerIDEPlugin.displayException(sakerEclipseIDEPlugin, severity, message);
	}

	public void start(ImplementationStartArguments args) {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		EclipseSakerIDEPlugin plugininstance = new EclipseSakerIDEPlugin();
		plugininstance.initialize(args.sakerJarPath,
				Paths.get(args.activator.getStateLocation().toFile().getAbsolutePath()));

		sakerEclipseIDEPlugin = plugininstance;

		new Job("Initializing saker.build plugin") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					plugininstance.start(monitor);
				} catch (Exception | LinkageError | StackOverflowError | OutOfMemoryError | AssertionError
						| ServiceConfigurationError e) {
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to initialize saker.build plugin", e);
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public EclipseSakerIDEProject getOrCreateSakerProject(IProject project) {
		if (project == null) {
			return null;
		}
		try {
			if (project.isOpen() && project.isNatureEnabled(SakerBuildProjectNature.NATURE_ID)) {
				return sakerEclipseIDEPlugin.getOrCreateProject(project);
			}
		} catch (CoreException e) {
			EclipseSakerIDEPlugin.displayException(sakerEclipseIDEPlugin, SakerLog.SEVERITY_ERROR,
					"Failed to open project for saker.build.", e);
		}
		return null;
	}

	@Override
	public void close() throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		IOException exc = null;
		synchronized (this) {
			exc = IOUtils.closeExc(exc, sakerEclipseIDEPlugin);
		}
		IOUtils.throwExc(exc);
	}

	public static ImplActivator getDefault() {
		Activator activator = Activator.getDefault();
		if (activator == null) {
			return null;
		}
		return (ImplActivator) activator.getImplActivator();
	}

	public static void clean(IProject project, IProgressMonitor monitor) throws IOException {
		EclipseSakerIDEProject ideproject = getDefault().getOrCreateSakerProject(project);
		clean(ideproject, monitor);
	}

	public static void clean(EclipseSakerIDEProject ideproject, IProgressMonitor monitor) {
		if (ideproject == null) {
			return;
		}
		ideproject.clean(monitor);
	}

	public void closeProject(IProject project) {
		EclipseSakerIDEPlugin eclipseideplugin = sakerEclipseIDEPlugin;
		if (eclipseideplugin == null) {
			return;
		}
		try {
			eclipseideplugin.closeProject(project);
		} catch (IOException e) {
			eclipseideplugin.displayException(SakerLog.SEVERITY_ERROR, "Failed to close project: " + project.getName(),
					e);
		}
		ProjectBuilder.closeProject(project);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta eventdelta = event.getDelta();
		if (eventdelta == null) {
			return;
		}
		EclipseSakerIDEPlugin eclipseideplugin = sakerEclipseIDEPlugin;
		if (eclipseideplugin == null) {
			return;
		}
		try {
			eventdelta.accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResource resource = delta.getResource();
					IProject project = resource.getProject();
					if (project == null) {
						return true;
					}
					if (resource instanceof IProject) {
						if (((delta.getKind() & IResourceDelta.CHANGED) == IResourceDelta.CHANGED)) {
							if (((delta.getFlags() & IResourceDelta.OPEN) == IResourceDelta.OPEN)) {
								IProject p = (IProject) resource;
								if (!p.isOpen()) {
									closeProject(p);
								}
							}
						}
					} else if (resource instanceof IFile) {
						if (((delta.getFlags() & (IResourceDelta.MOVED_TO)) == (IResourceDelta.MOVED_TO))) {
							EclipseSakerIDEProject eclipsesakerproject = getOrCreateSakerProject(project);
							if (eclipsesakerproject != null) {
								IFile file = (IFile) resource;
								eclipsesakerproject.handleFileMove(file, delta.getMovedToPath());
							}
						}
					}
					return true;
				}
			});
		} catch (Exception e) {
			eclipseideplugin.displayException(SakerLog.SEVERITY_WARNING, "Failed to handle resource change event.", e);
		}
	}
}
