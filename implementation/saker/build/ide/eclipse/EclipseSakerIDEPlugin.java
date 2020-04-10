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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IOConsole;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.file.path.SakerPath;
import saker.build.ide.eclipse.api.ISakerPlugin;
import saker.build.ide.eclipse.extension.params.IEnvironmentUserParameterContributor;
import saker.build.ide.eclipse.extension.params.UserParameterModification;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.support.ExceptionDisplayer;
import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SakerIDEPlugin.PluginResourceListener;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SimpleIDEPluginProperties;
import saker.build.ide.support.persist.StructuredArrayObjectInput;
import saker.build.ide.support.persist.StructuredArrayObjectOutput;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;

public final class EclipseSakerIDEPlugin implements Closeable, ExceptionDisplayer, ISakerPlugin {
	private static final String CONFIG_FILE_ROOT_OBJECT_NAME = "saker.build.ide.eclipse.plugin.config";
	private static final String IDE_PLUGIN_PROPERTIES_FILE_NAME = "." + CONFIG_FILE_ROOT_OBJECT_NAME;

	private final SakerIDEPlugin sakerPlugin;
	private volatile boolean closed = false;

	private final Object consoleLock = new Object();
	private SakerPluginInfoConsole console;

	private Path pluginConfigurationFilePath;

	private final Object projectsLock = new Object();
	private final Map<IProject, EclipseSakerIDEProject> projects = new ConcurrentHashMap<>();
	private final Object configurationChangeLock = new Object();
	private List<ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> environmentParameterContributors = Collections
			.emptyList();

	public EclipseSakerIDEPlugin() {
		sakerPlugin = new SakerIDEPlugin();
	}

	public void initialize(Path sakerJarPath, Path plugindirectory) {
		sakerPlugin.addExceptionDisplayer(this);

		this.pluginConfigurationFilePath = plugindirectory.resolve(IDE_PLUGIN_PROPERTIES_FILE_NAME);

		Set<ExtensionDisablement> extensiondisablements = new HashSet<>();

		try (InputStream in = Files.newInputStream(pluginConfigurationFilePath)) {
			XMLStructuredReader reader = new XMLStructuredReader(in);
			try (StructuredObjectInput configurationobj = reader.readObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
				readExtensionDisablements(configurationobj, extensiondisablements);
			}
		} catch (NoSuchFileException e) {
		} catch (IOException e) {
			displayException(e);
		}

		IExtensionRegistry extensionregistry = Platform.getExtensionRegistry();
		IConfigurationElement[] environmentuserparametercontributors = extensionregistry
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_ENVIRONMENT_USER_PARAMETER_CONTRIBUTOR);
		environmentParameterContributors = new ArrayList<>();
		for (IConfigurationElement configelem : environmentuserparametercontributors) {
			try {
				IExtension extension = configelem.getDeclaringExtension();
				String extensionsimpleid = extension.getUniqueIdentifier();
				if (extensionsimpleid == null) {
					displayException(new IllegalArgumentException(
							"Extension " + getExtensionName(extension) + " doesn't declare an unique identifier. ("
									+ Activator.EXTENSION_POINT_ID_ENVIRONMENT_USER_PARAMETER_CONTRIBUTOR + ")"));
					continue;
				}
				boolean enabled = !ExtensionDisablement.isDisabled(extensiondisablements, extension);

				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IEnvironmentUserParameterContributor)) {
					displayException(new ClassCastException("Extension " + getExtensionName(extension)
							+ " doesn't implement " + IEnvironmentUserParameterContributor.class.getName() + ". ("
							+ Activator.EXTENSION_POINT_ID_ENVIRONMENT_USER_PARAMETER_CONTRIBUTOR + ")"));
					continue;
				}
				environmentParameterContributors.add(new ContributedExtensionConfiguration<>(
						(IEnvironmentUserParameterContributor) contributor, configelem, enabled));
			} catch (CoreException e) {
				displayException(e);
			}
		}
		environmentParameterContributors = ImmutableUtils.unmodifiableList(environmentParameterContributors);

		try {
			sakerPlugin.initialize(sakerJarPath, plugindirectory);
			sakerPlugin.start(sakerPlugin.createDaemonLaunchParameters(
					getIDEPluginPropertiesWithEnvironmentParameterContributions(sakerPlugin.getIDEPluginProperties(),
							new NullProgressMonitor())));
		} catch (IOException e) {
			displayException(e);
		}

		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			synchronized (consoleLock) {
				if (closed) {
					return;
				}
				getConsoleImplOnUIThread();
			}
		});
	}

	public void addPluginResourceListener(PluginResourceListener listener) {
		sakerPlugin.addPluginResourceListener(listener);
	}

	public void removePluginResourceListener(PluginResourceListener listener) {
		sakerPlugin.removePluginResourceListener(listener);
	}

	public List<ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> getEnvironmentParameterContributors() {
		return environmentParameterContributors;
	}

	@Override
	public void invalidateEnvironmentUserParameterContributions() {
		new Job("Updating environment parameters") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				sakerPlugin.updateForPluginProperties(
						getIDEPluginPropertiesWithEnvironmentParameterContributions(getIDEPluginProperties(), monitor));
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public final SakerEnvironmentImpl getPluginEnvironment() {
		return sakerPlugin.getPluginEnvironment();
	}

	public final IDEPluginProperties getIDEPluginProperties() {
		return sakerPlugin.getIDEPluginProperties();
	}

	public final void setIDEPluginProperties(IDEPluginProperties properties,
			List<? extends ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> environmentParameterContributors) {
		synchronized (configurationChangeLock) {
			sakerPlugin.setIDEPluginProperties(properties);
			Set<ExtensionDisablement> prevdisablements = getExtensionDisablements(
					this.environmentParameterContributors);
			this.environmentParameterContributors = ImmutableUtils.makeImmutableList(environmentParameterContributors);
			Set<ExtensionDisablement> currentdisablements = getExtensionDisablements(
					this.environmentParameterContributors);
			if (!prevdisablements.equals(currentdisablements)) {
				try {
					writePluginConfigurationFile(currentdisablements);
				} catch (IOException e) {
					displayException(e);
				}
			}
		}

		new Job("Updating environment parameters") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				sakerPlugin.updateForPluginProperties(getIDEPluginPropertiesWithEnvironmentParameterContributions(
						sakerPlugin.getIDEPluginProperties(), monitor));
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public void reloadPluginEnvironment() {
		new Job("Reloading plugin environment") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					synchronized (configurationChangeLock) {
						DaemonLaunchParameters launchparams = sakerPlugin.createDaemonLaunchParameters(
								getIDEPluginPropertiesWithEnvironmentParameterContributions(
										sakerPlugin.getIDEPluginProperties(), monitor));
						if (monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						sakerPlugin.forceReloadPluginDaemon(launchparams);
					}
				} catch (OperationCanceledException e) {
					return Status.CANCEL_STATUS;
				} catch (Exception e) {
					displayException(e);
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public EclipseSakerIDEProject getOrCreateProject(IProject project) {
		if (project == null) {
			return null;
		}
		synchronized (projectsLock) {
			if (closed) {
				throw new IllegalStateException("closed");
			}
			EclipseSakerIDEProject eclipseproject = projects.get(project);
			if (eclipseproject != null) {
				return eclipseproject;
			}
			SakerIDEProject sakerproject = sakerPlugin.getOrCreateProject(project);
			eclipseproject = new EclipseSakerIDEProject(this, sakerproject, project);
			projects.put(project, eclipseproject);
			eclipseproject.initialize();
			return eclipseproject;
		}
	}

	public final Collection<? extends EclipseSakerIDEProject> getLoadedProjects() {
		return ImmutableUtils.unmodifiableCollection(projects.values());
	}

	public void closeProject(IProject project) throws IOException {
		synchronized (projectsLock) {
			EclipseSakerIDEProject eclipseproject = projects.remove(project);
			try {
				if (eclipseproject != null) {
					eclipseproject.close();
				}
			} finally {
				sakerPlugin.closeProject(project);
			}
		}
	}

	public void displayStatus(IStatus status) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			synchronized (consoleLock) {
				if (closed) {
					return;
				}
				SakerPluginInfoConsole console = getConsoleImplOnUIThread();
				console.printlnError("Plugin exception occurred. See Error Log view for more information.");
				console.activate();
			}
		});
		Activator.getDefault().getLog().log(status);
	}

	public void displayError(String message) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			synchronized (consoleLock) {
				if (closed) {
					return;
				}
				SakerPluginInfoConsole console = getConsoleImplOnUIThread();
				console.printlnError(message);
				console.activate();
			}
		});
		System.err.println(message);
	}

	public void printExceptionStackTrace(Throwable e) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			synchronized (consoleLock) {
				if (closed) {
					return;
				}
				SakerPluginInfoConsole console = getConsoleImplOnUIThread();
				console.printException(e);
				console.activate();
			}
		});
	}

	@Override
	public void displayException(Throwable e) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			synchronized (consoleLock) {
				if (closed) {
					return;
				}
				SakerPluginInfoConsole console = getConsoleImplOnUIThread();
				console.printException(e);
				console.activate();
			}
		});
		e.printStackTrace();
		Activator.getDefault().getLog()
				.log(new Status(Status.ERROR, Activator.PLUGIN_ID, "Saker.build plugin exception.", e));
	}

	@Override
	public synchronized void close() throws IOException {
		closed = true;
		IOException exc = null;
		List<ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> envparamcontributors = environmentParameterContributors;
		if (!ObjectUtils.isNullOrEmpty(envparamcontributors)) {
			this.environmentParameterContributors = Collections.emptyList();
			for (ContributedExtensionConfiguration<IEnvironmentUserParameterContributor> contributor : envparamcontributors) {
				try {
					IEnvironmentUserParameterContributor paramcontributor = contributor.getContributor();
					if (paramcontributor != null) {
						paramcontributor.dispose();
					}
				} catch (Exception e) {
					//catch just in case
					exc = IOUtils.addExc(exc, e);
				}
			}
		}
		try {
			closeProjects();
		} catch (IOException e) {
			exc = IOUtils.addExc(exc, e);
		}
		synchronized (consoleLock) {
			IOConsole cons = console;
			if (cons != null) {
				this.console = null;

				Display display = PlatformUI.getWorkbench().getDisplay();
				if (!display.isDisposed()) {
					display.asyncExec(() -> {
						ConsolePlugin plugin = ConsolePlugin.getDefault();
						IConsoleManager conMan = plugin.getConsoleManager();
						conMan.removeConsoles(new IConsole[] { cons });
					});
				}
			}
		}
		exc = IOUtils.closeExc(exc, sakerPlugin);
		IOUtils.throwExc(exc);
	}

	public NavigableMap<String, String> getUserParametersWithContributors(Map<String, String> userparameters,
			List<? extends ContributedExtensionConfiguration<? extends IEnvironmentUserParameterContributor>> contributors,
			IProgressMonitor monitor) {
		NavigableMap<String, String> userparamworkmap = ObjectUtils.newTreeMap(userparameters);
		NavigableMap<String, String> unmodifiableuserparammap = ImmutableUtils
				.unmodifiableNavigableMap(userparamworkmap);
		contributor_loop:
		for (ContributedExtensionConfiguration<? extends IEnvironmentUserParameterContributor> contributor : contributors) {
			if (!contributor.isEnabled()) {
				continue;
			}
			if (monitor != null && monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			try {
				Set<UserParameterModification> modifications = contributor.getContributor().contribute(this,
						unmodifiableuserparammap, monitor);
				if (ObjectUtils.isNullOrEmpty(modifications)) {
					continue;
				}
				Set<String> keys = new TreeSet<>();
				for (UserParameterModification mod : modifications) {
					if (!keys.add(mod.getKey())) {
						displayException(new IllegalArgumentException(
								"Multiple environment user parameter modification for key: " + mod.getKey()));
						continue contributor_loop;
					}
				}
				for (UserParameterModification mod : modifications) {
					mod.apply(userparamworkmap);
				}
			} catch (Exception e) {
				if (monitor != null && monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				displayException(e);
			}
		}
		return userparamworkmap;
	}

	public IScriptProposalDesigner getScriptProposalDesignerForSchemaIdentifiers(Set<String> schemaidentifiers) {
		Objects.requireNonNull(schemaidentifiers, "schema identifiers");
		IConfigurationElement[] ideconfigextensionconfigelements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_SCRIPT_PROPOSAL_DESIGNER);
		List<IScriptProposalDesigner> designers = new ArrayList<>();
		for (IConfigurationElement configelem : ideconfigextensionconfigelements) {
			String configschemaid = configelem.getAttribute("schema_id");
			if (configschemaid != null && !schemaidentifiers.contains(configschemaid)) {
				//can't use
				continue;
			}
			try {
				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IScriptProposalDesigner)) {
					displayException(
							new ClassCastException("Extension " + getExtensionName(configelem.getDeclaringExtension())
									+ " doesn't implement " + IScriptInformationDesigner.class.getName() + ". ("
									+ Activator.EXTENSION_POINT_ID_SCRIPT_PROPOSAL_DESIGNER + ")"));
					continue;
				}
				designers.add((IScriptProposalDesigner) contributor);
			} catch (CoreException e) {
				displayException(e);
				continue;
			}
		}
		if (designers.isEmpty()) {
			return null;
		}
		if (designers.size() == 1) {
			return designers.get(0);
		}
		return new MultiScriptProposalDesigner(designers);
	}

	public IScriptInformationDesigner getScriptInformationDesignerForSchemaIdentifier(String schemaid) {
		IConfigurationElement[] ideconfigextensionconfigelements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_SCRIPT_INFORMATION_DESIGNER);
		List<IScriptInformationDesigner> designers = new ArrayList<>();
		for (IConfigurationElement configelem : ideconfigextensionconfigelements) {
			String configschemaid = configelem.getAttribute("schema_id");
			if (configschemaid != null && !configschemaid.equals(schemaid)) {
				//can't use
				continue;
			}
			try {
				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IScriptInformationDesigner)) {
					displayException(
							new ClassCastException("Extension " + getExtensionName(configelem.getDeclaringExtension())
									+ " doesn't implement " + IScriptInformationDesigner.class.getName() + ". ("
									+ Activator.EXTENSION_POINT_ID_SCRIPT_INFORMATION_DESIGNER + ")"));
					continue;
				}
				designers.add((IScriptInformationDesigner) contributor);
			} catch (CoreException e) {
				displayException(e);
				continue;
			}
		}
		if (designers.isEmpty()) {
			return null;
		}
		if (designers.size() == 1) {
			return designers.get(0);
		}
		return new MultiScriptInformationDesigner(designers);
	}

	public IScriptOutlineDesigner getScriptOutlineDesignerForSchemaIdentifier(String schemaid) {
		IConfigurationElement[] ideconfigextensionconfigelements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_SCRIPT_OUTLINE_DESIGNER);
		List<IScriptOutlineDesigner> designers = new ArrayList<>();
		for (IConfigurationElement configelem : ideconfigextensionconfigelements) {
			String configschemaid = configelem.getAttribute("schema_id");
			if (configschemaid != null && !configschemaid.equals(schemaid)) {
				//can't use
				continue;
			}
			try {
				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IScriptOutlineDesigner)) {
					displayException(
							new ClassCastException("Extension " + getExtensionName(configelem.getDeclaringExtension())
									+ " doesn't implement " + IScriptOutlineDesigner.class.getName() + ". ("
									+ Activator.EXTENSION_POINT_ID_SCRIPT_OUTLINE_DESIGNER + ")"));
					continue;
				}
				designers.add((IScriptOutlineDesigner) contributor);
			} catch (CoreException e) {
				displayException(e);
				continue;
			}
		}
		if (designers.isEmpty()) {
			return null;
		}
		if (designers.size() == 1) {
			return designers.get(0);
		}
		return new MultiScriptOutlineDesigner(designers);
	}

	public static String tryNormalizePathRoot(String root) {
		try {
			return SakerPath.normalizeRoot(root);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static String getExtensionName(IExtension extension) {
		String label = extension.getLabel();
		if (!ObjectUtils.isNullOrEmpty(label)) {
			return label;
		}
		String contributorname = extension.getContributor().getName();
		if (!ObjectUtils.isNullOrEmpty(contributorname)) {
			return contributorname;
		}
		return extension.getNamespaceIdentifier();
	}

	public static void readExtensionDisablements(StructuredObjectInput configurationobj,
			Set<ExtensionDisablement> extensiondisablements) throws IOException {
		try (StructuredArrayObjectInput disablementsarray = configurationobj.readArray("extension_disablements")) {
			if (disablementsarray != null) {
				int len = disablementsarray.length();
				for (int i = 0; i < len; i++) {
					String uniqueid = disablementsarray.readString();
					if (ObjectUtils.isNullOrEmpty(uniqueid)) {
						continue;
					}
					extensiondisablements.add(new ExtensionDisablement(uniqueid));
				}
			}
		}
	}

	public static void writeExtensionDisablements(StructuredObjectOutput configurationobjout,
			Iterable<? extends ExtensionDisablement> disablements) throws IOException {
		if (ObjectUtils.isNullOrEmpty(disablements)) {
			return;
		}
		try (StructuredArrayObjectOutput out = configurationobjout.writeArray("extension_disablements")) {
			for (ExtensionDisablement disablement : disablements) {
				out.write(disablement.getExtensionUniqueIdentifier());
			}
		}
	}

	public static Set<ExtensionDisablement> getExtensionDisablements(
			Iterable<? extends ContributedExtensionConfiguration<?>> contributedextensions) {
		if (ObjectUtils.isNullOrEmpty(contributedextensions)) {
			return Collections.emptySet();
		}
		HashSet<ExtensionDisablement> result = new HashSet<>();
		for (ContributedExtensionConfiguration<?> ext : contributedextensions) {
			if (!ext.isEnabled()) {
				result.add(new ExtensionDisablement(ext.getConfigurationElement().getDeclaringExtension()));
			}
		}
		return result;
	}

	private void writePluginConfigurationFile(Iterable<? extends ExtensionDisablement> disablements)
			throws IOException {
		try (OutputStream os = Files.newOutputStream(pluginConfigurationFilePath)) {
			try (XMLStructuredWriter writer = new XMLStructuredWriter(os)) {
				try (StructuredObjectOutput configurationobj = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
					writeExtensionDisablements(configurationobj, disablements);
				}
			}
		}
	}

	private IDEPluginProperties getIDEPluginPropertiesWithEnvironmentParameterContributions(
			IDEPluginProperties properties, IProgressMonitor monitor) {
		if (environmentParameterContributors.isEmpty()) {
			return properties;
		}
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		SimpleIDEPluginProperties.Builder builder = SimpleIDEPluginProperties.builder(properties);
		Map<String, String> propertiesuserparams = SakerIDEPlugin.entrySetToMap(properties.getUserParameters());
		List<ContributedExtensionConfiguration<IEnvironmentUserParameterContributor>> contributors = environmentParameterContributors;

		NavigableMap<String, String> userparammap = getUserParametersWithContributors(propertiesuserparams,
				contributors, monitor);
		builder.setUserParameters(userparammap.entrySet());
		return builder.build();
	}

	private void closeProjects() throws IOException {
		List<EclipseSakerIDEProject> copiedprojects;
		synchronized (projectsLock) {
			copiedprojects = new ArrayList<>(projects.values());
			projects.clear();
		}
		IOException exc = null;
		for (EclipseSakerIDEProject p : copiedprojects) {
			try {
				p.close();
			} catch (IOException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
	}

	private SakerPluginInfoConsole getConsoleImplOnUIThread() {
		if (console == null) {
			String type = SakerPluginInfoConsole.CONSOLE_TYPE;
			LogHighlightingConsole c = SakerPluginInfoConsole.findExistingConsole(type);
			if (c != null) {
				console = (SakerPluginInfoConsole) c;
			} else {
				ConsolePlugin plugin = ConsolePlugin.getDefault();
				IConsoleManager conMan = plugin.getConsoleManager();

				// no console found, so create a new one
				SakerPluginInfoConsole console = new SakerPluginInfoConsole(this, "Saker.build plugin info", type);
				conMan.addConsoles(new IConsole[] { console });

				console.ensureInit();
				conMan.showConsoleView(console);

				this.console = console;
			}
		}
		return console;
	}

}
