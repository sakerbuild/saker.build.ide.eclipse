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

import saker.build.thirdparty.saker.util.io.IOUtils;

public class ImplActivator implements AutoCloseable, IResourceChangeListener {
	private EclipseSakerIDEPlugin sakerEclipseIDEPlugin;

	public ImplActivator() {
	}

	public EclipseSakerIDEPlugin getEclipseIDEPlugin() {
		return sakerEclipseIDEPlugin;
	}

	public void start(ImplementationStartArguments args) {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		sakerEclipseIDEPlugin = new EclipseSakerIDEPlugin();
		sakerEclipseIDEPlugin.initialize(args.sakerJarPath,
				Paths.get(args.activator.getStateLocation().toFile().getAbsolutePath()));

		new Job("Initializing saker.build plugin") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					sakerEclipseIDEPlugin.start(monitor);
				} catch (Exception e) {
					//display just in case as well
					sakerEclipseIDEPlugin.displayException(e);
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to initialize saker.build plugin", e);
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public EclipseSakerIDEProject getOrCreateSakerProject(IProject project) {
		try {
			if (project.isOpen() && project.isNatureEnabled(SakerBuildProjectNature.NATURE_ID)) {
				return sakerEclipseIDEPlugin.getOrCreateProject(project);
			}
		} catch (CoreException e) {
			sakerEclipseIDEPlugin.displayException(e);
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
			eclipseideplugin.displayException(e);
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
					}
					return true;
				}
			});
		} catch (CoreException e) {
			eclipseideplugin.displayException(e);
		}
	}
}
