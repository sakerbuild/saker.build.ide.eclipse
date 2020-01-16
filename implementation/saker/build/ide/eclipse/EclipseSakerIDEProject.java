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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IconAndMessageDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IOConsoleInputStream;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;

import saker.build.daemon.DaemonEnvironment;
import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.eclipse.ISakerBuildInfoConsole.BuildInterfaceAccessor;
import saker.build.ide.eclipse.api.ISakerProject;
import saker.build.ide.eclipse.extension.ideconfig.IIDEConfigurationTypeHandler;
import saker.build.ide.eclipse.extension.ideconfig.IIDEProjectConfigurationEntry;
import saker.build.ide.eclipse.extension.ideconfig.IIDEProjectConfigurationRootEntry;
import saker.build.ide.eclipse.extension.params.IExecutionUserParameterContributor;
import saker.build.ide.eclipse.extension.params.UserParameterModification;
import saker.build.ide.eclipse.properties.DaemonConnectionsProjectPropertyPage;
import saker.build.ide.eclipse.properties.IdentityElementComparer;
import saker.build.ide.eclipse.properties.PathConfigurationProjectPropertyPage;
import saker.build.ide.eclipse.properties.RepositoriesProjectPropertyPage;
import saker.build.ide.eclipse.properties.SakerBuildProjectPropertyPage;
import saker.build.ide.eclipse.properties.ScriptConfigurationProjectPropertyPage;
import saker.build.ide.eclipse.properties.UserParametersProjectPropertyPage;
import saker.build.ide.support.ExceptionDisplayer;
import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.SakerIDEProject.ProjectResourceListener;
import saker.build.ide.support.configuration.ProjectIDEConfigurationCollection;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.PropertiesValidationException;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.BuildTaskExecutionResult.ResultKind;
import saker.build.runtime.environment.BuildTaskExecutionResultImpl;
import saker.build.runtime.execution.BuildUserPromptHandler;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ExecutionProgressMonitor;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.task.TaskProgressMonitor;
import saker.build.task.TaskResultCollection;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

public final class EclipseSakerIDEProject implements ExceptionDisplayer, ISakerProject {
	private static final String CONFIG_FILE_ROOT_OBJECT_NAME = "saker.build.ide.eclipse.project.config";
	private static final String PROPERTIES_FILE_NAME = "." + CONFIG_FILE_ROOT_OBJECT_NAME;

	public interface ProjectPropertiesChangeListener {
		public default void projectPropertiesChanging() {
		}

		public default void projectPropertiesChanged() {
		}
	}

	private static final QualifiedName LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME = new QualifiedName(Activator.PLUGIN_ID,
			"last-build-script-path");
	private static final QualifiedName LAST_BUILD_TARGET_NAME_QUALIFIED_NAME = new QualifiedName(Activator.PLUGIN_ID,
			"last-build-target-name");

	private static final String CONSOLE_NAME = "Saker.build Console";

	private EclipseSakerIDEPlugin eclipseSakerPlugin;
	private SakerIDEProject sakerProject;
	private IProject ideProject;
	private final Lock executionLock = new ReentrantLock();
	private final Object configurationChangeLock = new Object();

	private Set<ProjectPropertiesChangeListener> propertiesChangeListeners = Collections
			.newSetFromMap(new WeakHashMap<>());

	private List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> executionParameterContributors = Collections
			.emptyList();
	private Path projectConfigurationFilePath;

	public EclipseSakerIDEProject(EclipseSakerIDEPlugin eclipseSakerIDEPlugin, SakerIDEProject sakerproject,
			IProject project) {
		this.eclipseSakerPlugin = eclipseSakerIDEPlugin;
		this.sakerProject = sakerproject;
		this.ideProject = project;
	}

	public void initialize() {
		sakerProject.addExceptionDisplayer(this);
		Path projectpath = ideProject.getLocation().toFile().toPath();

		this.projectConfigurationFilePath = projectpath.resolve(PROPERTIES_FILE_NAME);

		Set<ExtensionDisablement> extensiondisablements = new HashSet<>();

		try (InputStream in = Files.newInputStream(projectConfigurationFilePath)) {
			XMLStructuredReader reader = new XMLStructuredReader(in);
			try (StructuredObjectInput configurationobj = reader.readObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
				EclipseSakerIDEPlugin.readExtensionDisablements(configurationobj, extensiondisablements);
			}
		} catch (NoSuchFileException e) {
		} catch (IOException e) {
			displayException(e);
		}

		IExtensionRegistry extensionregistry = Platform.getExtensionRegistry();
		IConfigurationElement[] excecutionuserparametercontributors = extensionregistry
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR);
		executionParameterContributors = new ArrayList<>();
		for (IConfigurationElement configelem : excecutionuserparametercontributors) {
			try {
				IExtension extension = configelem.getDeclaringExtension();
				String extensionsimpleid = extension.getUniqueIdentifier();
				if (extensionsimpleid == null) {
					displayException(new IllegalArgumentException(
							"Extension " + EclipseSakerIDEPlugin.getExtensionName(extension)
									+ " doesn't declare an unique identifier. ("
									+ Activator.EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR + ")"));
					continue;
				}
				boolean enabled = !ExtensionDisablement.isDisabled(extensiondisablements, extension);

				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IExecutionUserParameterContributor)) {
					displayException(
							new ClassCastException("Extension " + EclipseSakerIDEPlugin.getExtensionName(extension)
									+ " doesn't implement " + IExecutionUserParameterContributor.class.getName() + ". ("
									+ Activator.EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR + ")"));
					continue;
				}
				executionParameterContributors.add(new ContributedExtensionConfiguration<>(
						(IExecutionUserParameterContributor) contributor, configelem, enabled));
			} catch (CoreException e) {
				displayException(e);
			}
		}
		executionParameterContributors = ImmutableUtils.unmodifiableList(executionParameterContributors);

		sakerProject.initialize(projectpath);
	}

	protected void close() throws IOException {
		IOException exc = null;
		List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> envparamcontributors = executionParameterContributors;
		if (!ObjectUtils.isNullOrEmpty(envparamcontributors)) {
			this.executionParameterContributors = Collections.emptyList();
			for (ContributedExtensionConfiguration<IExecutionUserParameterContributor> contributor : envparamcontributors) {
				try {
					IExecutionUserParameterContributor paramcontributor = contributor.getContributor();
					if (paramcontributor != null) {
						paramcontributor.dispose();
					}
				} catch (Exception e) {
					//catch just in case
					exc = IOUtils.addExc(exc, e);
				}
			}
		}
		IOUtils.throwExc(exc);
	}

	public List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> getExecutionParameterContributors() {
		return executionParameterContributors;
	}

	@Override
	public IProject getProject() {
		return ideProject;
	}

	@Override
	public EclipseSakerIDEPlugin getPlugin() {
		return eclipseSakerPlugin;
	}

	@Override
	public String executionPathToProjectRelativePath(String executionpath) {
		if (executionpath == null) {
			return null;
		}
		SakerPath executionsakerpath;
		try {
			executionsakerpath = SakerPath.valueOf(executionpath);
		} catch (IllegalArgumentException e) {
			return null;
		}
		return Objects.toString(executionPathToProjectRelativePath(executionsakerpath), null);
	}

	public SakerPath executionPathToProjectRelativePath(SakerPath executionsakerpath) {
		IDEProjectProperties properties = getIDEProjectProperties();
		if (executionsakerpath == null) {
			return null;
		}
		if (executionsakerpath.isRelative()) {
			SakerPath propworkdir = EclipseSakerIDEPlugin.tryParsePath(properties.getWorkingDirectory());
			if (propworkdir == null || propworkdir.isRelative()) {
				return null;
			}
			executionsakerpath = propworkdir.resolve(executionsakerpath);
		}
		//the path to resolve is an absolute execution path

		SakerPath projectsakerpath = SakerPath.valueOf(getProjectPath());
		ProviderMountIDEProperty mountprop = getMountPropertyForPath(executionsakerpath, properties);
		if (mountprop == null) {
			return null;
		}
		String mountclientname = mountprop.getMountClientName();
		//if mountclientname == null then we fail with null
		if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(mountclientname)) {
			//the mounting is project relative
			SakerPath mountedpath = EclipseSakerIDEPlugin.tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			SakerPath mountedfullpath = projectsakerpath.resolve(mountedpath.replaceRoot(null));
			executionsakerpath = mountedfullpath.resolve(executionsakerpath.replaceRoot(null));
			if (executionsakerpath.startsWith(projectsakerpath)) {
				return projectsakerpath.relativize(executionsakerpath);
			}
			return null;
		}
		if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(mountclientname)) {
			//the mount is on the local filesystem which is where the project resides
			SakerPath mountedpath = EclipseSakerIDEPlugin.tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			executionsakerpath = mountedpath.resolve(executionsakerpath.replaceRoot(null));
			if (executionsakerpath.startsWith(projectsakerpath)) {
				return projectsakerpath.relativize(executionsakerpath);
			}
			return null;
		}
		//the mount is made through a daemon connection, cannot determine the file system association
		return null;
	}

	@Override
	public String projectPathToExecutionPath(String path) {
		if (path == null) {
			return null;
		}
		SakerPath sakerpath;
		try {
			sakerpath = SakerPath.valueOf(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
		return Objects.toString(projectPathToExecutionPath(sakerpath), null);
	}

	public SakerPath projectPathToExecutionPath(SakerPath path) {
		IDEProjectProperties ideprops = getIDEProjectProperties();
		SakerPath projectsakerpath = SakerPath.valueOf(getProjectPath());
		if (path.isRelative()) {
			try {
				path = projectsakerpath.resolve(path);
			} catch (IllegalArgumentException e) {
				//if somewhy we fail to resolve the path. E.g. the path contains too many ".." at start
				return null;
			}
		}
		Set<? extends ProviderMountIDEProperty> mounts = ideprops.getMounts();
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		for (ProviderMountIDEProperty mountprop : mounts) {
			String rootstr = mountprop.getRoot();
			String mountpathstr = mountprop.getMountPath();
			String clientname = mountprop.getMountClientName();
			if (ObjectUtils.isNullOrEmpty(rootstr) || ObjectUtils.isNullOrEmpty(mountpathstr)
					|| ObjectUtils.isNullOrEmpty(clientname)) {
				continue;
			}
			String root;
			SakerPath mountpath;
			try {
				root = SakerPath.normalizeRoot(rootstr);
				mountpath = SakerPath.valueOf(mountpathstr);
			} catch (IllegalArgumentException e) {
				//invalid configuration, failed to parse
				continue;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
				//the mount path is resolved against the project directory
				clientname = SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM;
				mountpath = projectsakerpath.resolve(mountpath.replaceRoot(null));
				//continue with testing local 
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)) {
				int commonnamecount = path.getCommonNameCount(mountpath);
				if (commonnamecount >= 0) {
					return path.subPath(commonnamecount).replaceRoot(root);
				}
			}
		}
		return null;
	}

	public IFile getIFileAtExecutionPath(SakerPath path) {
		SakerPath relpath = executionPathToProjectRelativePath(path);
		if (relpath == null) {
			return null;
		}
		return ideProject.getFile(new org.eclipse.core.runtime.Path(relpath.toString()));
	}

	public boolean isScriptConfigurationAppliesTo(IFile file) {
		IDEProjectProperties properties = getIDEProjectProperties();
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = properties.getScriptConfigurations();
		if (ObjectUtils.isNullOrEmpty(scriptconfigs)) {
			return false;
		}

		SakerPath projectrelativepath = SakerPath.valueOf(file.getProjectRelativePath().toString());
		SakerPath execpath = projectPathToExecutionPath(projectrelativepath);
		if (execpath == null) {
			return false;
		}
		for (ScriptConfigurationIDEProperty scprop : scriptconfigs) {
			String wcstr = scprop.getScriptsWildcard();
			if (ObjectUtils.isNullOrEmpty(wcstr)) {
				continue;
			}
			WildcardPath scriptwc;
			try {
				scriptwc = WildcardPath.valueOf(wcstr);
			} catch (IllegalArgumentException e) {
				return false;
			}
			if (scriptwc.includes(execpath)) {
				Set<String> exclusions = properties.getScriptModellingExclusions();
				if (!ObjectUtils.isNullOrEmpty(exclusions)) {
					for (String excl : exclusions) {
						WildcardPath exclwc;
						try {
							exclwc = WildcardPath.valueOf(excl);
						} catch (IllegalArgumentException e) {
							continue;
						}
						if (exclwc.includes(execpath)) {
							return false;
						}
					}
				}
				return true;
			}
		}
		return false;
	}

	public NavigableMap<String, String> getUserParametersWithContributors(Map<String, String> userparameters,
			List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> contributors,
			IProgressMonitor monitor) {
		NavigableMap<String, String> userparamworkmap = ObjectUtils.newTreeMap(userparameters);
		NavigableMap<String, String> unmodifiableuserparammap = ImmutableUtils
				.unmodifiableNavigableMap(userparamworkmap);
		contributor_loop:
		for (ContributedExtensionConfiguration<IExecutionUserParameterContributor> extension : contributors) {
			if (!extension.isEnabled()) {
				continue;
			}
			if (monitor != null && monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			try {
				Set<UserParameterModification> modifications = extension.getContributor().contribute(this,
						unmodifiableuserparammap, monitor);
				if (ObjectUtils.isNullOrEmpty(modifications)) {
					continue;
				}
				Set<String> keys = new TreeSet<>();
				for (UserParameterModification mod : modifications) {
					if (!keys.add(mod.getKey())) {
						displayException(new IllegalArgumentException(
								"Multiple execution user parameter modification for key: " + mod.getKey()));
						continue contributor_loop;
					}
				}
				for (UserParameterModification mod : modifications) {
					mod.apply(userparamworkmap);
				}
			} catch (Exception e) {
				//catch other kind of exceptions too
				displayException(e);
			}
		}
		return userparamworkmap;
	}

	public void addProjectResourceListener(ProjectResourceListener listener) {
		sakerProject.addProjectResourceListener(listener);
	}

	public void removeProjectResourceListener(ProjectResourceListener listener) {
		sakerProject.removeProjectResourceListener(listener);
	}

	public void addProjectPropertiesChangeListener(ProjectPropertiesChangeListener listener) {
		if (listener == null) {
			return;
		}
		synchronized (propertiesChangeListeners) {
			propertiesChangeListeners.add(listener);
		}
	}

	public void removeProjectPropertiesChangeListener(ProjectPropertiesChangeListener listener) {
		if (listener == null) {
			return;
		}
		synchronized (propertiesChangeListeners) {
			propertiesChangeListeners.remove(listener);
		}
	}

	public void applyIDEConfiguration(Shell shell, IDEConfiguration ideconfig, IProgressMonitor monitor) {
		System.out.println("EclipseSakerIDEProject.applyIDEConfiguration() " + ideconfig);
		if (ideconfig == null) {
			//shouldn't really happen
			return;
		}
		String ideconfigtype = ideconfig.getType();
		Display display = PlatformUI.getWorkbench().getDisplay();
		if (ideconfigtype == null) {
			display.asyncExec(() -> {
				MessageDialog.openError(shell, "Apply IDE configuration", "IDE configuration contains no type field.");
			});
			return;
		}
		Map<String, Object> configfieldmap = new TreeMap<>();
		for (String fn : ideconfig.getFieldNames()) {
			if (fn == null) {
				continue;
			}
			Object fval = ideconfig.getField(fn);
			if (fval == null) {
				continue;
			}
			configfieldmap.put(fn, fval);
		}

		List<IIDEConfigurationTypeHandler> parsers = new ArrayList<>();
		IConfigurationElement[] extensionconfigelements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_IDE_CONFIGURATION_PARSER);
		if (ObjectUtils.isNullOrEmpty(extensionconfigelements)) {
			display.asyncExec(() -> {
				MessageDialog.openInformation(shell, "Apply IDE configuration",
						"No plugin extensions found to handle IDE configuration type: " + ideconfigtype);
			});
			return;
		}
		for (IConfigurationElement configelem : extensionconfigelements) {
			String type = configelem.getAttribute("type");
			if (!ideconfigtype.equals(type)) {
				continue;
			}
			try {
				Object parser = configelem.createExecutableExtension("class");
				if (!(parser instanceof IIDEConfigurationTypeHandler)) {
					displayException(new ClassCastException("Extension " + configelem.getName() + " doesn't implement "
							+ IIDEConfigurationTypeHandler.class.getName()));
					continue;
				}
				parsers.add((IIDEConfigurationTypeHandler) parser);
			} catch (CoreException e) {
				displayException(e);
			}
		}
		if (parsers.isEmpty()) {
			display.asyncExec(() -> {
				MessageDialog.openInformation(shell, "Apply IDE configuration",
						"No plugin extensions found to handle IDE configuration type: " + ideconfigtype);
			});
			return;
		}
		List<IIDEProjectConfigurationRootEntry> rootentries = new ArrayList<>();

		boolean hadsuccess = false;

		for (IIDEConfigurationTypeHandler parser : parsers) {
			IIDEProjectConfigurationRootEntry[] entries;
			try {
				entries = parser.parseConfiguration(this, configfieldmap, monitor);
			} catch (CoreException e) {
				eclipseSakerPlugin.displayException(e);
				continue;
			}
			if (ObjectUtils.isNullOrEmpty(entries)) {
				continue;
			}
			for (IIDEProjectConfigurationRootEntry roote : entries) {
				if (roote == null) {
					continue;
				}
				rootentries.add(roote);
			}
			hadsuccess = true;
		}
		if (!hadsuccess) {
			display.asyncExec(() -> {
				MessageDialog.openError(shell, "Apply IDE configuration", "Failed to parse IDE configuration object.");
			});
			return;
		}
		IDEConfigurationSelectorDialog[] dialog = { null };
		display.syncExec(() -> {
			dialog[0] = new IDEConfigurationSelectorDialog(shell, rootentries, ideProject, ideconfig.getIdentifier());
			dialog[0].open();
		});
		if (dialog[0].getReturnCode() == Dialog.OK) {
			for (IIDEProjectConfigurationRootEntry rootentry : rootentries) {
				if (!rootentry.isSelected()) {
					continue;
				}
				try {
					rootentry.apply(monitor);
				} catch (CoreException e) {
					displayException(e);
					display.asyncExec(() -> {
						MessageDialog.openError(shell, "Apply IDE configuration",
								"Failed to fully apply IDE configuration: " + e.toString());
					});
					return;
				}
			}
		}
	}

	public void setProjectIDEConfigurationCollection(ProjectIDEConfigurationCollection configurationCollection) {
		sakerProject.setProjectIDEConfigurationCollection(configurationCollection);
	}

	public void setIDEProjectProperties(IDEProjectProperties properties,
			List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> executionParameterContributors) {
		synchronized (configurationChangeLock) {
			try {
				projectPropertiesChanging();
			} catch (Exception e) {
				displayException(e);
			}
			try {
				sakerProject.setIDEProjectProperties(properties);
				Set<ExtensionDisablement> prevdisablements = EclipseSakerIDEPlugin
						.getExtensionDisablements(this.executionParameterContributors);
				this.executionParameterContributors = executionParameterContributors;
				Set<ExtensionDisablement> currentdisablements = EclipseSakerIDEPlugin
						.getExtensionDisablements(this.executionParameterContributors);
				if (!prevdisablements.equals(currentdisablements)) {
					try {
						writeProjectConfigurationFile(currentdisablements);
					} catch (IOException e) {
						displayException(e);
					}
				}
				sakerProject.updateForProjectProperties(
						getIDEProjectPropertiesWithExecutionParameterContributions(properties, null));
			} finally {
				try {
					projectPropertiesChanged();
				} catch (Exception e) {
					//don't propagate exceptions
					displayException(e);
				}
			}
		}
	}

	public void setIDEProjectProperties(IDEProjectProperties properties) {
		synchronized (configurationChangeLock) {
			try {
				projectPropertiesChanging();
			} catch (Exception e) {
				displayException(e);
			}
			try {
				sakerProject.setIDEProjectProperties(properties);
				sakerProject.updateForProjectProperties(
						getIDEProjectPropertiesWithExecutionParameterContributions(properties, null));
			} finally {
				try {
					projectPropertiesChanged();
				} catch (Exception e) {
					//don't propagate exceptions
					displayException(e);
				}
			}
		}
	}

	public IDEProjectProperties getIDEProjectProperties() {
		return sakerProject.getIDEProjectProperties();
	}

	public final ProjectIDEConfigurationCollection getProjectIDEConfigurationCollection() {
		return sakerProject.getProjectIDEConfigurationCollection();
	}

	public Path getProjectPath() {
		return sakerProject.getProjectPath();
	}

	public final ScriptModellingEnvironment getScriptingEnvironment() {
		return sakerProject.getScriptingEnvironment();
	}

	public final NavigableSet<SakerPath> getTrackedScriptPaths() {
		return sakerProject.getTrackedScriptPaths();
	}

	public final Set<String> getScriptTargets(SakerPath scriptpath) throws ScriptParsingFailedException, IOException {
		return sakerProject.getScriptTargets(scriptpath);
	}

	public SakerPath getWorkingDirectoryExecutionPath() {
		return projectPathToExecutionPath(SakerPath.EMPTY);
	}

	public void clean(IProgressMonitor monitor) {
		try {
			ideProject.setPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME, null);
			ideProject.setPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME, null);
			ideProject.deleteMarkers(ProjectBuilder.MARKER_TYPE, true, IProject.DEPTH_INFINITE);

			sakerProject.clean();
			ideProject.refreshLocal(IProject.DEPTH_INFINITE, monitor);
		} catch (CoreException | IOException | InterruptedException e) {
			displayException(e);
		}
	}

	public void build(IProgressMonitor monitor) {
		withLatestOrChosenBuildTarget((scriptpath, target) -> {
			build(scriptpath, target, monitor);
		});
	}

	public void buildWithNewJob() {
		withLatestOrChosenBuildTarget((scriptpath, target) -> {
			ProjectBuilder.buildAsync(this, scriptpath, target);
		});
	}

	private void withLatestOrChosenBuildTarget(BiConsumer<SakerPath, String> consumer) {
		try {
			String lastscriptpath = ideProject.getPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME);
			if (lastscriptpath != null) {
				String lasttargetname = ideProject.getPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME);
				SakerPath lastbuildpath = SakerPath.valueOf(lastscriptpath);
				consumer.accept(lastbuildpath, lasttargetname);
				return;
			}
		} catch (CoreException e) {
			displayException(e);
		}
		AskListItem chosen = askBuildTarget();
		if (chosen != null) {
			consumer.accept(chosen.scriptPath, chosen.target);
			return;
		}
	}

	public void addNewBuildFile(String name) throws CoreException {
		//TODO if the name is not included in the script configuration, ask for rename
		IFile file = ideProject.getFile(name);
		if (file.exists()) {
			//accepts null input stream to signal no contents, empty file
			file.setContents((InputStream) null, IResource.FORCE, null);
		} else {
			file.create(StreamUtils.nullInputStream(), IResource.FORCE, null);
		}
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IDE.openEditor(page, file, BuildFileEditor.ID);
	}

	@Override
	public void displayException(Throwable e) {
		//XXX project specific exception display
		eclipseSakerPlugin.printExceptionStackTrace(e);
		Activator.getDefault().getLog()
				.log(new Status(Status.ERROR, Activator.PLUGIN_ID, "Error on project: " + ideProject.getName(), e));
	}

	private static final class ProjectBuildConsoleInterfaceAccessor implements BuildInterfaceAccessor {
		private static final class ProjectBuildStackTraceAccessor implements StackTraceAccessor {
			private ScriptPositionedExceptionView stacktrace;

			private ProjectBuildStackTraceAccessor(ScriptPositionedExceptionView stacktrace) {
				this.stacktrace = stacktrace;
			}

			@Override
			public synchronized void printToConsole(ISakerBuildInfoConsole console) {
				ScriptPositionedExceptionView st = stacktrace;
				if (st == null) {
					return;
				}
				this.stacktrace = null;
				((SakerProjectBuildConsole) console).printCompleteStackTrace(st);
			}
		}

		private final ProgressMonitorWrapper wrapper;
		private Thread buildThread = Thread.currentThread();
		private ScriptPositionedExceptionView stackTrace;

		public ProjectBuildConsoleInterfaceAccessor(ProgressMonitorWrapper wrapper) {
			this.wrapper = wrapper;
		}

		@Override
		public void stop() {
			wrapper.cancelled = true;
			wrapper.progressMonitor.setCanceled(true);
		}

		@Override
		public void interruptAndStop() {
			synchronized (this) {
				ThreadUtils.interruptThread(buildThread);
			}
			stop();
		}

		public void clearBuildThread() {
			synchronized (this) {
				this.buildThread = null;
			}
		}

		@Override
		public StackTraceAccessor getStackTraceAccessor() {
			ScriptPositionedExceptionView stacktrace = stackTrace;
			if (stacktrace == null) {
				return null;
			}
			return new ProjectBuildStackTraceAccessor(stacktrace);
		}
	}

	private static class ProgressMonitorWrapper implements ExecutionProgressMonitor, TaskProgressMonitor {
		protected IProgressMonitor progressMonitor;
		private volatile boolean cancelled;

		public ProgressMonitorWrapper(IProgressMonitor progressMonitor) {
			this.progressMonitor = progressMonitor;
		}

		@Override
		public boolean isCancelled() {
			if (cancelled) {
				return true;
			}
			if (progressMonitor.isCanceled()) {
				cancelled = true;
				return true;
			}
			return false;
		}

		@Override
		public TaskProgressMonitor startTaskProgress() {
			return this;
		}

	}

	protected BuildTaskExecutionResult build(SakerPath scriptfile, String targetname, IProgressMonitor monitor) {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		boolean wasinterrupted = false;

		ProgressMonitorWrapper monitorwrapper = new ProgressMonitorWrapper(monitor);

		ProjectBuildConsoleInterfaceAccessor consoleaccessor = new ProjectBuildConsoleInterfaceAccessor(monitorwrapper);

		SakerProjectBuildConsole console = ProjectBuilder.findBuildConsole(this);
		console.startBuild(consoleaccessor);
		try {
			//streams are closed in finally without throwing
			IOConsoleOutputStream out = console.newOutputStream();
			IOConsoleOutputStream err = console.newOutputStream();

			BuildTaskExecutionResult result = null;
			SakerPath executionworkingdir = null;
			Display display = PlatformUI.getWorkbench().getDisplay();
			try {
				executionLock.lockInterruptibly();
				console.clearConsole();
				if (monitorwrapper.isCancelled()) {
					out.write("Build cancelled.\n");
					return BuildTaskExecutionResultImpl
							.createInitializationFailed(new OperationCanceledException("Build cancelled."));
				}
				ExecutionParametersImpl params;
				IDEProjectProperties projectproperties;
				try {
					projectproperties = getIDEProjectPropertiesWithExecutionParameterContributions(
							getIDEProjectProperties(), monitor);
				} catch (OperationCanceledException e) {
					out.write("Build cancelled.\n");
					return BuildTaskExecutionResultImpl.createInitializationFailed(e);
				}
				try {
					params = sakerProject.createExecutionParameters(projectproperties);
					//there were no validation errors
				} catch (PropertiesValidationException e) {
					Object[] diagresult = { null, null };

					IDEProjectProperties initialprojectproperties = projectproperties;
					display.syncExec(() -> {
						PropertiesValidationBuildErrorDialog errordialog = new PropertiesValidationBuildErrorDialog(
								display.getActiveShell(), initialprojectproperties, e.getErrors());
						int dialogres = errordialog.open();
						diagresult[0] = dialogres;
						diagresult[1] = errordialog.getSuccessProperties();
					});
					if ((int) diagresult[0] == IDialogConstants.OK_ID) {
						projectproperties = (IDEProjectProperties) diagresult[1];
						params = sakerProject.createExecutionParameters(projectproperties);
						//continue the build
					} else {
						//the user decided not to continue the build
						out.write("Build aborted due to invalid project configuration.\n");
						return BuildTaskExecutionResultImpl.createInitializationFailed(e);
					}
				}
				DaemonEnvironment daemonenv = sakerProject.getExecutionDaemonEnvironment(projectproperties);
				ExecutionPathConfiguration pathconfiguration = params.getPathConfiguration();
				executionworkingdir = pathconfiguration.getWorkingDirectory();
				params.setRequiresIDEConfiguration(projectproperties.isRequireTaskIDEConfiguration());

				try {
					ideProject.setPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME, scriptfile.toString());
					ideProject.setPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME, targetname);
					ideProject.deleteMarkers(ProjectBuilder.MARKER_TYPE, true, IProject.DEPTH_INFINITE);
				} catch (CoreException e) {
					displayException(e);
				}

				SakerPath thisworking = pathconfiguration.getWorkingDirectory();
				SakerPath relativescriptpath = scriptfile;
				if (thisworking != null && scriptfile.startsWith(thisworking)) {
					relativescriptpath = thisworking.relativize(scriptfile);
				}
				String jobname = targetname + "@" + relativescriptpath;

				display.syncExec(new Runnable() {
					@Override
					public void run() {
						// \t inserted because @ is handled specially
						console.setName(CONSOLE_NAME + " (" + ideProject.getName() + "): " + jobname + "\t");
						err.setColor(display.getSystemColor(SWT.COLOR_RED));
					}
				});
				IOConsoleInputStream consolein = console.getInputStream();
				//read any data input remaining from the previous build
				try {
					//available() call can throw an IOException for no aparrent reason
					//https://bugs.eclipse.org/bugs/show_bug.cgi?id=307309
					int consoleavailable = consolein.available();
					if (consoleavailable > 0) {
						byte[] buf = new byte[consoleavailable];
						consolein.read(buf);
					}
				} catch (IOException e) {
				}
				params.setProgressMonitor(monitorwrapper);
				params.setStandardOutput(ByteSink.valueOf(out));
				params.setErrorOutput(ByteSink.valueOf(err));
				params.setStandardInput(ByteSource.valueOf(consolein));
				BuildUserPromptHandler userprompthandler = new BuildUserPromptHandler() {
					@Override
					public int prompt(String title, String message, List<String> options) {
						Objects.requireNonNull(options, "options");
						if (options.isEmpty()) {
							return -1;
						}
						int[] res = { -1 };
						synchronized (this) {
							display.syncExec(() -> {
								try {
									UserPromptBuildDialog dialog = new UserPromptBuildDialog(display.getActiveShell(),
											title, message, options);
									if (dialog.open() == IDialogConstants.OK_ID) {
										res[0] = dialog.getSelectedButton();
									}
								} catch (Exception e) {
									displayException(e);
								}
							});
						}
						return res[0];
					}
				};
				params.setUserPrompHandler(userprompthandler);
				SecretInputReader secretinputreader = new SecretInputReader() {
					@Override
					public synchronized String readSecret(String titleinfo, String message, String prompt,
							String secretidentifier) {
						String[] res = { null };
						display.syncExec(() -> {
							try {
								SecretReaderBuildDialog dialog = new SecretReaderBuildDialog(display.getActiveShell(),
										titleinfo, message, prompt, secretidentifier);
								if (dialog.open() == IDialogConstants.OK_ID) {
									res[0] = dialog.getResult();
								}
							} catch (Exception e) {
								displayException(e);
							}
						});
						return res[0];
					}
				};
				params.setSecretInputReader(secretinputreader);
				try {
					out.write(("Build started. (" + jobname + ")\n").getBytes());
				} catch (IOException e) {
					//shouldnt happen, we don't display this exception to the user
					e.printStackTrace();
				}
				long starttime = System.nanoTime();
				result = sakerProject.build(scriptfile, targetname, daemonenv, params);
				long finishtime = System.nanoTime();

				//so we cannot be interrupted any more
				consoleaccessor.clearBuildThread();
				//clear the interrupt flag if we were interrupted so the finalizing succeeds.
				wasinterrupted = Thread.interrupted();

				try {
					if (result.getResultKind() == ResultKind.INITIALIZATION_ERROR) {
						out.write("Failed to initialize execution.\n".getBytes());
					} else {
						out.write(("Build finished. " + new Date(System.currentTimeMillis()) + " ("
								+ DateUtils.durationToString((finishtime - starttime) / 1_000_000) + ")\n").getBytes());
					}
				} catch (IOException e) {
					//shouldnt happen, we don't display this exception to the user
					e.printStackTrace();
				}
				SakerPath builddir = params.getBuildDirectory();
				Path projectpath = getProjectPath();
				if (builddir != null) {
					try {
						if (builddir.isRelative()) {
							builddir = pathconfiguration.getWorkingDirectory().resolve(builddir);
						}
						Path builddirlocalpath = pathconfiguration.toLocalPath(builddir);
						if (builddirlocalpath != null && builddirlocalpath.startsWith(projectpath)) {
							IFolder buildfolder = ideProject
									.getFolder(projectpath.relativize(builddirlocalpath).toString());
							if (buildfolder != null) {
								buildfolder.refreshLocal(IFolder.DEPTH_INFINITE, monitor);
								if (buildfolder.exists()) {
									buildfolder.setDerived(true, monitor);
								}
							}
						}
					} catch (OperationCanceledException e) {
					} catch (Exception e) {
						//CoreException, or if we fail some path parsing, converting, or others
						displayException(e);
					}
				}
				if (ObjectUtils.isNullOrEmpty(projectproperties.getExecutionDaemonConnectionName())) {
					//set derived to the mirror directory only if the build is running in-process
					try {
						SakerPath mirrordir = params.getMirrorDirectory();
						if (mirrordir != null) {
							Path localmirrorpath = LocalFileProvider.toRealPath(mirrordir);
							if (localmirrorpath.startsWith(projectpath)) {
								IFolder mirrorfolder = ideProject
										.getFolder(projectpath.relativize(localmirrorpath).toString());
								if (mirrorfolder != null) {
									//intentionally don't refresh. There's no particular need for it
									mirrorfolder.setDerived(true, monitor);
								}
							}
						}
					} catch (InvalidPathException e) {
						//the mirror path is not a valid path on the local file system
						//ignoreable
					} catch (OperationCanceledException e) {
					} catch (Exception e) {
						//CoreException, or if we fail some path parsing, converting, or others
						displayException(e);
					}
				}
				TaskResultCollection resultcollection = result.getTaskResultCollection();
				if (resultcollection != null) {
					Collection<? extends IDEConfiguration> ideconfigs = resultcollection.getIDEConfigurations();
					addIDEConfigurations(ideconfigs);
				}
				return result;
			} catch (Throwable e) {
				if (result == null) {
					result = BuildTaskExecutionResultImpl.createInitializationFailed(e);
					try {
						out.write("Failed to initialize execution.\n".getBytes());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				return result;
			} finally {
				if (result != null) {
					ScriptPositionedExceptionView posexcview = result.getPositionedExceptionView();
					if (posexcview != null) {
						consoleaccessor.stackTrace = posexcview;
						//TODO make exception format configureable
						TaskUtils.printTaskExceptionsOmitTransitive(posexcview, new PrintStream(err),
								executionworkingdir, CommonExceptionFormat.DEFAULT_FORMAT);
					}
				}
				IOException streamscloseexc = IOUtils.closeExc(out, err);
				if (streamscloseexc != null) {
					displayException(streamscloseexc);
				}
				executionLock.unlock();
			}
		} finally {
			console.endBuild(consoleaccessor);
			if (wasinterrupted) {
				Thread.interrupted();
			}
		}
	}

	private void writeProjectConfigurationFile(Iterable<? extends ExtensionDisablement> disablements)
			throws IOException {
		try (OutputStream os = Files.newOutputStream(projectConfigurationFilePath)) {
			try (XMLStructuredWriter writer = new XMLStructuredWriter(os)) {
				try (StructuredObjectOutput configurationobj = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
					EclipseSakerIDEPlugin.writeExtensionDisablements(configurationobj, disablements);
				}
			}
		}
	}

	private static ProviderMountIDEProperty getMountPropertyForPath(SakerPath path, IDEProjectProperties properties) {
		Set<? extends ProviderMountIDEProperty> mounts = properties.getMounts();
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		String pathroot = path.getRoot();
		if (pathroot == null) {
			return null;
		}
		for (ProviderMountIDEProperty prop : mounts) {
			if (pathroot.equals(prop.getRoot())) {
				return prop;
			}
		}
		return null;
	}

	private void projectPropertiesChanging() {
		List<ProjectPropertiesChangeListener> listenercopy;
		synchronized (propertiesChangeListeners) {
			listenercopy = ImmutableUtils.makeImmutableList(propertiesChangeListeners);
		}
		for (ProjectPropertiesChangeListener l : listenercopy) {
			l.projectPropertiesChanging();
		}
	}

	private void projectPropertiesChanged() {
		List<ProjectPropertiesChangeListener> listenercopy;
		synchronized (propertiesChangeListeners) {
			listenercopy = ImmutableUtils.makeImmutableList(propertiesChangeListeners);
		}
		for (ProjectPropertiesChangeListener l : listenercopy) {
			l.projectPropertiesChanged();
		}
	}

	private IDEProjectProperties getIDEProjectPropertiesWithExecutionParameterContributions(
			IDEProjectProperties properties, IProgressMonitor monitor) {
		if (executionParameterContributors.isEmpty()) {
			return properties;
		}
		SimpleIDEProjectProperties.Builder builder = SimpleIDEProjectProperties.builder(properties);
		Map<String, String> userparameters = SakerIDEPlugin.entrySetToMap(properties.getUserParameters());
		List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> contributors = executionParameterContributors;

		NavigableMap<String, String> userparamworkmap = getUserParametersWithContributors(userparameters, contributors,
				monitor);
		builder.setUserParameters(userparamworkmap.entrySet());
		return builder.build();
	}

	private void addIDEConfigurations(Collection<? extends IDEConfiguration> ideconfigs) {
		if (ObjectUtils.isNullOrEmpty(ideconfigs)) {
			return;
		}
		Set<Entry<String, String>> typeidentifierkinds = new HashSet<>();
		for (IDEConfiguration ideconfig : ideconfigs) {
			typeidentifierkinds
					.add(ImmutableUtils.makeImmutableMapEntry(ideconfig.getType(), ideconfig.getIdentifier()));
		}
		ProjectIDEConfigurationCollection ideconfigcoll = getProjectIDEConfigurationCollection();
		List<IDEConfiguration> nconfigs = new ArrayList<>(ideconfigcoll.getConfigurations());

		//remove all configurations from the previous collection which have a type-identifier kind
		//    that is being overwritten
		for (Iterator<IDEConfiguration> it = nconfigs.iterator(); it.hasNext();) {
			IDEConfiguration ideconfig = it.next();
			Entry<String, String> entry = ImmutableUtils.makeImmutableMapEntry(ideconfig.getType(),
					ideconfig.getIdentifier());
			if (typeidentifierkinds.contains(entry)) {
				it.remove();
			}
		}
		nconfigs.addAll(ideconfigs);
		ProjectIDEConfigurationCollection nideconfiguration = new ProjectIDEConfigurationCollection(nconfigs);
		//equality is checked by SakerIDEProject
		sakerProject.setProjectIDEConfigurationCollection(nideconfiguration);
	}

	private AskListItem askBuildTarget() {
		Object[][] diagres = { null };
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				Set<? extends SakerPath> buildfiles = getTrackedScriptPaths();
				Iterator<? extends SakerPath> it = buildfiles.iterator();
				if (!it.hasNext()) {
					int newbuildfiledialogres = new NoBuildFileErrorDialog(activeShell).open();
					if (newbuildfiledialogres == IDialogConstants.OK_ID) {
						try {
							addNewBuildFile(SakerIDEProject.DEFAULT_BUILD_FILE_NAME);
						} catch (CoreException e) {
							displayException(e);
						}
					}
					return;
				}
				SakerPath workingdirpath = getWorkingDirectoryExecutionPath();

				List<AskListItem> input = new ArrayList<>();
				do {
					SakerPath displaybuildfile;
					SakerPath buildfile = it.next();
					if (workingdirpath != null && buildfile.startsWith(workingdirpath)) {
						displaybuildfile = workingdirpath.relativize(buildfile);
					} else {
						displaybuildfile = buildfile;
					}

					try {
						Set<String> targets = getScriptTargets(buildfile);
						if (!ObjectUtils.isNullOrEmpty(targets)) {
							for (String target : targets) {
								input.add(new AskListItem(buildfile, target, displaybuildfile));
							}
						}
					} catch (ScriptParsingFailedException | IOException e) {
						displayException(e);
						//XXX open dialog with errors?
					}
				} while (it.hasNext());
				ListDialog listdialog = new ListDialog(activeShell);
				listdialog.setTitle("Build target");
				listdialog.setMessage("Choose a build target to run.");

				listdialog.setInput(input);
				listdialog.setContentProvider(ArrayContentProvider.getInstance());
				listdialog.setLabelProvider(new LabelProvider());

				listdialog.setHelpAvailable(false);
				listdialog.open();
				diagres[0] = listdialog.getResult();
			}
		});
		if (diagres[0] == null || diagres[0].length == 0) {
			return null;
		}
		return (AskListItem) diagres[0][0];
	}

	private static class AskListItem {
		private SakerPath scriptPath;
		private String target;
		private SakerPath displayPath;

		public AskListItem(SakerPath scriptPath, String target, SakerPath displayPath) {
			this.scriptPath = scriptPath;
			this.target = target;
			this.displayPath = displayPath;
		}

		@Override
		public String toString() {
			return target + "@" + displayPath;
		}
	}

	private static class SecretReaderBuildDialog extends TitleAreaDialog {
		private Text secretText;
		private String title;
		private String message;
		private String prompt;
		private String secretIdentifier;

		private String result;

		public SecretReaderBuildDialog(Shell activeShell, String titleinfo, String message, String prompt,
				String secretidentifier) {
			super(activeShell);
			this.title = titleinfo;
			this.message = message;
			this.prompt = prompt;
			this.secretIdentifier = secretidentifier;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Build secret prompt");
		}

		public String getResult() {
			return result;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite result = (Composite) super.createDialogArea(parent);
			if (title != null) {
				setTitle(title);
			}
			if (message != null) {
				setMessage(message);
			}
			Composite composite = new Composite(result, SWT.NONE);
			composite.setLayoutData(
					GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).create());
			GridLayout compositegridlayout = new GridLayout(prompt == null ? 1 : 2, false);
			compositegridlayout.marginWidth = 20;
			composite.setLayout(compositegridlayout);
			if (prompt != null) {
				Label promptlabel = new Label(composite, SWT.NONE);
				promptlabel.setText(prompt);
			}
			secretText = new Text(composite, SWT.BORDER);
			secretText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
			secretText.setEchoChar('*');
			secretText.setMessage("Secret");
			if (secretIdentifier != null) {
				//empty cell
				new Label(composite, SWT.NONE).setLayoutData(new GridData());

				Label secretidlabel = new Label(composite, SWT.NONE);
				secretidlabel.setText("Secret identifier: " + secretIdentifier);
			}
			return result;
		}

		@Override
		protected void okPressed() {
			result = secretText.getText();
			super.okPressed();
		}
	}

	private static class UserPromptBuildDialog extends TitleAreaDialog {

		private String title;
		private String message;
		private List<String> options;
		private int selectedButton = -1;

		public UserPromptBuildDialog(Shell parentShell, String title, String message, List<String> options) {
			super(parentShell);
			this.title = title;
			this.message = message;
			this.options = options;
		}

		public int getSelectedButton() {
			return selectedButton;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Build prompt");
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected Point getInitialSize() {
			Point result = super.getInitialSize();
			return new Point(500, Math.max(Math.min(result.y, 800), 300));
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite result = (Composite) super.createDialogArea(parent);
			if (title != null) {
				setTitle(title);
			}
			if (message != null) {
				setMessage(message);
			}
			ScrolledComposite scrolled = new ScrolledComposite(result, SWT.V_SCROLL);
			scrolled.setLayoutData(GridDataFactory.swtDefaults().hint(SWT.DEFAULT, SWT.DEFAULT)
					.align(SWT.FILL, SWT.FILL).grab(false, true).create());
			Composite composite = new Composite(scrolled, SWT.NONE);
			scrolled.setContent(composite);
			composite.setLayout(new GridLayout());
			int i = 0;
			GridData buttongriddata = GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).create();
			buttongriddata.widthHint = 450;
			for (String opt : options) {
				if (!ObjectUtils.isNullOrEmpty(opt)) {
					Button button = new Button(composite, SWT.PUSH | SWT.WRAP);
					button.setText(opt);
					button.setLayoutData(buttongriddata);
					button.setAlignment(SWT.LEFT);
					int finalidx = i;
					button.addSelectionListener(SelectionListener.widgetSelectedAdapter(ev -> {
						setReturnCode(IDialogConstants.OK_ID);
						selectedButton = finalidx;
						close();
					}));
				}
				++i;
			}
			composite.setSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			return result;
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			//no explicit buttons
			createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		}

	}

	private class PropertiesValidationBuildErrorDialog extends IconAndMessageDialog {
		private IDEProjectProperties successProperties;

		private IDEProjectProperties projectProperties;
		private List<PropertiesValidationErrorResult> errors;

		public PropertiesValidationBuildErrorDialog(Shell parentShell, IDEProjectProperties projectproperties,
				Set<PropertiesValidationErrorResult> errors) {
			super(parentShell);
			this.projectProperties = projectproperties;
			this.errors = ImmutableUtils.makeImmutableList(errors);
			message = "The project configuration contains invalid values. "
					+ "Fix them in order to continue the build. "
					+ "Double click an error to open the related property pages.";
		}

		@Override
		protected Point getInitialSize() {
			Point result = super.getInitialSize();
			return new Point(Math.min(result.x, 600), Math.max(Math.min(result.y, 800), 600));
		}

		public IDEProjectProperties getSuccessProperties() {
			return successProperties;
		}

		@Override
		protected Image getImage() {
			return getShell().getDisplay().getSystemImage(SWT.ICON_ERROR);
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Project configuration error");
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			//copied from ErrorDialog.createDialogArea source
			Composite composite = new Composite(parent, SWT.NONE);
			createMessageArea(composite);
			GridLayout layout = new GridLayout();
			layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
			layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
			layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
			layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
			layout.numColumns = 2;
			composite.setLayout(layout);
			GridData childData = new GridData(GridData.FILL_BOTH);
			childData.horizontalSpan = 2;
			childData.grabExcessVerticalSpace = false;
			composite.setLayoutData(childData);
			composite.setFont(parent.getFont());

			org.eclipse.swt.widgets.List errorlist = new org.eclipse.swt.widgets.List(parent,
					SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE);
			populateErrorList(errorlist);
			errorlist.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).span(2, 1).create());
			errorlist.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent ev) {
					int selectionidx = errorlist.getSelectionIndex();
					if (selectionidx < 0) {
						return;
					}
					PropertiesValidationErrorResult err = errors.get(selectionidx);
					openPropertyPage(err);
					IDEProjectProperties currentproperties = getIDEProjectProperties();
					if (PropertiesValidationBuildErrorDialog.this.projectProperties.equals(currentproperties)) {
						//the properties didn't change.
					} else {
						//the properties have changed.
						Set<PropertiesValidationErrorResult> errors = SakerIDEProject
								.validateProjectProperties(currentproperties);
						PropertiesValidationBuildErrorDialog.this.errors = ImmutableUtils.makeImmutableList(errors);
						if (!errors.isEmpty()) {
							populateErrorList(errorlist);
						} else {
							PropertiesValidationBuildErrorDialog.this.successProperties = currentproperties;
							setReturnCode(IDialogConstants.OK_ID);
							close();
						}
					}
				}
			});

			return composite;
		}

		private void populateErrorList(org.eclipse.swt.widgets.List errorlist) {
			String[] items = new String[errors.size()];
			for (int i = 0; i < items.length; i++) {
				PropertiesValidationErrorResult errorresult = errors.get(i);
				items[i] = createErrorLabel(errorresult);
			}
			errorlist.setItems(items);
			errorlist.getParent().requestLayout();
		}

		private String createErrorLabel(PropertiesValidationErrorResult err) {
			String type = err.errorType;
			switch (type) {
				case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_ADDRESS + SakerIDEProject.E_MISSING: {
					DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
					String cname = property.getConnectionName();
					return "Daemon connection address is missing." + (cname == null ? "" : " (" + cname + ")");
				}
				case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_MISSING: {
					DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
					String address = property.getNetAddress();
					return "Daemon connection name is missing." + (address == null ? "" : " (" + address + ")");
				}
				case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_RESERVED: {
					DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
					return "Daemon connection name is reserved: " + property.getConnectionName();
				}
				case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_DUPLICATE: {
					DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
					return "Duplicate daemon connection name: " + property.getConnectionName();
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_MISSING: {
					return "Missing mount root.";
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_DUPLICATE: {
					ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
					return "Duplicate mounted root: " + property.getRoot();
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_FORMAT: {
					ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
					return "Invalid mount root format: " + property.getRoot();
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_CLIENT + SakerIDEProject.E_MISSING: {
					return "Missing mount file system endpoint.";
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
					return "Missing mounted path.";
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_RELATIVE: {
					ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
					return "Mounted path must be absolute: " + property.getMountPath();
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_INVALID_ROOT: {
					ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
					return "Mounted path root is invalid: " + property.getMountPath();
				}
				case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
					ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
					return "Invalid mounted path format: " + property.getMountPath();
				}
				case SakerIDEProject.NS_EXECUTION_DAEMON_NAME + SakerIDEProject.E_MISSING_DAEMON: {
					return "Daemon connection not found for execution daemon name: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_USER_PARAMETERS + SakerIDEProject.E_INVALID_KEY: {
					return "Invalid user parameter key: "
							+ (err.relatedSubject == null ? "null" : "\"" + err.relatedSubject + "\"");
				}
				case SakerIDEProject.NS_USER_PARAMETERS + SakerIDEProject.E_DUPLICATE_KEY: {
					return "Duplicate user parameter key: " + "\"" + err.relatedSubject + "\"";
				}

				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD + SakerIDEProject.E_MISSING: {
					return "Missing wildcard for script configuration.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD
						+ SakerIDEProject.E_DUPLICATE: {
					ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
					return "Duplicate wildcard for script configuration: " + property.getScriptsWildcard();
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD + SakerIDEProject.E_FORMAT: {
					ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
					return "Invalid wildcard format for script configuration: " + property.getScriptsWildcard();
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_OPTIONS
						+ SakerIDEProject.E_INVALID_KEY: {
					String key = (String) ((Object[]) err.relatedSubject)[1];
					return "Invalid script configuration option key: " + (key == null ? "null" : "\"" + key + "\"");
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_OPTIONS
						+ SakerIDEProject.E_DUPLICATE_KEY: {
					String key = (String) ((Object[]) err.relatedSubject)[1];
					return "Duplicate script configuration option key: " + (key == null ? "null" : "\"" + key + "\"");
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.E_MISSING: {
					return "Missing script configuration class path.";
				}

				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_CONNECTION + SakerIDEProject.E_MISSING_DAEMON: {
					return "Script configuration JAR class path daemon connection is missing.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
					return "Script configuration JAR file path is missing.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
					ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
					return "Invalid script configuration JAR file path format: "
							+ ((JarClassPathLocationIDEProperty) property.getClassPathLocation()).getJarPath();
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_MISSING: {
					return "Script configuration class path  HTTP/HTTPS URL is missing.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_PROTOCOL: {
					ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
					return "Invalid script configuration class path HTTP/HTTPS URL protocol: "
							+ ((HttpUrlJarClassPathLocationIDEProperty) property.getClassPathLocation()).getUrl();
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_FORMAT: {
					ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
					return "Invalid script configuration class path  HTTP/HTTPS URL format: "
							+ ((HttpUrlJarClassPathLocationIDEProperty) property.getClassPathLocation()).getUrl();
				}
				//the following can't happen
//				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
//						+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
//					break;
//				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
					return "Invalid class path for script configuration.";
				}

				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.E_MISSING: {
					return "Script language implementation loader is missing.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_SERVICE_LOADER + SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
					return "Script language service loader class is missing.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_NAMED_CLASS
						+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
					return "Script language implementation class name is missing.";
				}
				//the following can't happen
//				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE
//						+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
//					break;
//				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
					return "Invalid service configuration for script configuration.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_VERSION_FORMAT: {
					return "Invalid version number format for saker.nest repository classpath.";
				}
				case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.E_INVALID_COMBINATION: {
					return "Invalid class path and service combination for script configuration.";
				}

				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_IDENTIFIER
						+ SakerIDEProject.E_DUPLICATE: {
					RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
					return "Duplicate repository identifier: " + property.getRepositoryIdentifier();
				}

				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_CONNECTION + SakerIDEProject.E_MISSING_DAEMON: {
					return "Repository configuration JAR class path daemon connection is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
					return "Repository configuration JAR file path is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
						+ SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
					RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
					return "Invalid repository configuration JAR file path format: "
							+ ((JarClassPathLocationIDEProperty) property.getClassPathLocation()).getJarPath();
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_MISSING: {
					return "Repository configuration class path HTTP/HTTPS URL is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_PROTOCOL: {
					RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
					return "Invalid repository configuration class path HTTP/HTTPS URL protocol: "
							+ ((HttpUrlJarClassPathLocationIDEProperty) property.getClassPathLocation()).getUrl();
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_FORMAT: {
					RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
					return "Invalid repository configuration class path  HTTP/HTTPS URL format: "
							+ ((HttpUrlJarClassPathLocationIDEProperty) property.getClassPathLocation()).getUrl();
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
					return "Invalid class path for repository configuration.";
				}
				//the following can't happen
//				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
//						+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
//					break;
//				}

				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.E_MISSING: {
					return "Repository implementation loader is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_SERVICE_LOADER + SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
					return "Repository service loader class is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_NAMED_CLASS + SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
					return "Repository implementation class name is missing.";
				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
						+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
					return "Invalid service configuration for repository configuration.";
				}
				//the following can't happen
//				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
//						+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
//					break;
//				}
				case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
						+ SakerIDEProject.C_SERVICE + SakerIDEProject.E_INVALID_COMBINATION: {
					return "Invalid class path and service combination for repository configuration.";
				}

				case SakerIDEProject.NS_MIRROR_DIRECTORY + SakerIDEProject.E_FORMAT: {
					return "Invalid mirror directory format: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_MIRROR_DIRECTORY + SakerIDEProject.E_RELATIVE: {
					return "Mirror directory must be absolute: " + err.relatedSubject;
				}

				case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_MISSING: {
					return "Missing working directory configuration.";
				}
				case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_FORMAT: {
					return "Invalid working directory format: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_RELATIVE: {
					return "Working directory must be absolute: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_ROOT_NOT_FOUND: {
					return "Working directory root not found: " + err.relatedSubject;
				}

				case SakerIDEProject.NS_BUILD_DIRECTORY + SakerIDEProject.E_FORMAT: {
					return "Invalid build directory format: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_BUILD_DIRECTORY + SakerIDEProject.E_ROOT_NOT_FOUND: {
					return "Build directory root not found: " + err.relatedSubject;
				}

				case SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION + SakerIDEProject.E_FORMAT: {
					return "Invalid script modelling exclusion format: " + err.relatedSubject;
				}
				case SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION + SakerIDEProject.E_DUPLICATE: {
					return "Duplicate script modelling exclusion wildcard: " + err.relatedSubject;
				}

				default: {
					return "Unrecognized error: " + type + " : " + err.relatedSubject;
				}
			}
		}

		private int openPropertyPage(PropertiesValidationErrorResult err) {
			String pageid;
			String type = err.errorType;
			if (type.startsWith(SakerIDEProject.NS_BUILD_DIRECTORY)) {
				pageid = PathConfigurationProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_DAEMON_CONNECTION)) {
				pageid = DaemonConnectionsProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_EXECUTION_DAEMON_NAME)) {
				pageid = DaemonConnectionsProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_MIRROR_DIRECTORY)) {
				pageid = PathConfigurationProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_PROVIDER_MOUNT)) {
				pageid = PathConfigurationProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_REPOSITORY_CONFIGURATION)) {
				pageid = RepositoriesProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_SCRIPT_CONFIGURATION)) {
				pageid = ScriptConfigurationProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_USER_PARAMETERS)) {
				pageid = UserParametersProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_WORKING_DIRECTORY)) {
				pageid = PathConfigurationProjectPropertyPage.ID;
			} else if (type.startsWith(SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION)) {
				pageid = ScriptConfigurationProjectPropertyPage.ID;
			} else {
				pageid = SakerBuildProjectPropertyPage.ID;
			}
			return PreferencesUtil.createPropertyDialogOn(getShell(), ideProject, pageid, null, err).open();
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			createButton(parent, IDialogConstants.CANCEL_ID, "Abort build", true);
		}
	}

	private static class NoBuildFileErrorDialog extends IconAndMessageDialog {
		public NoBuildFileErrorDialog(Shell parentShell) {
			super(parentShell);
			message = "No build file found in project. Create a new one to build the project.";
		}

		@Override
		protected Image getImage() {
			return getShell().getDisplay().getSystemImage(SWT.ICON_ERROR);
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Missing build file");
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			//copied from ErrorDialog.createDialogArea source
			Composite composite = new Composite(parent, SWT.NONE);
			createMessageArea(composite);
			GridLayout layout = new GridLayout();
			layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
			layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
			layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
			layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
			layout.numColumns = 2;
			composite.setLayout(layout);
			GridData childData = new GridData(GridData.FILL_BOTH);
			childData.horizontalSpan = 2;
			childData.grabExcessVerticalSpace = false;
			composite.setLayoutData(childData);
			composite.setFont(parent.getFont());

			return composite;
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			if (id == IDialogConstants.OK_ID) {
				label = "Create new build file";
			}
			return super.createButton(parent, id, label, defaultButton);
		}
	}

	private static class IDEConfigurationSelectorDialog extends TitleAreaDialog {

		private static class ConfigurationItem {
			private static final ConfigurationItem[] EMPTY_CONFIGURATIONITEM_ARRAY = new ConfigurationItem[0];

			final ConfigurationItem parent;
			final ConfigurationItem[] children;
			final IIDEProjectConfigurationEntry entry;

			public ConfigurationItem(ConfigurationItem parent, IIDEProjectConfigurationEntry entry) {
				this.parent = parent;
				this.entry = entry;
				IIDEProjectConfigurationEntry[] subentries = entry.getSubEntries();
				if (ObjectUtils.isNullOrEmpty(subentries)) {
					children = EMPTY_CONFIGURATIONITEM_ARRAY;
				} else {
					children = new ConfigurationItem[subentries.length];
					for (int i = 0; i < subentries.length; i++) {
						children[i] = new ConfigurationItem(this, subentries[i]);
					}
				}
			}

			boolean isAllChildrenSelected() {
				for (ConfigurationItem c : children) {
					if (!c.entry.isSelected()) {
						return false;
					}
					if (!c.isAllChildrenSelected()) {
						return false;
					}
				}
				return true;
			}

			boolean isAnyChildSelected() {
				for (ConfigurationItem c : children) {
					if (c.entry.isSelected()) {
						return true;
					}
					if (c.isAnyChildSelected()) {
						return true;
					}
				}
				return false;
			}

			boolean hasChildren() {
				return children.length > 0;
			}

//			boolean isAnyChildSelected() {
//				if (children.length == 0) {
//					return false;
//				}
//				for (ConfigurationItem c : children) {
//					if (c.entry.isSelected()) {
//						return true;
//					}
//					if (!c.isAnyChildSelected()) {
//						return true;
//					}
//				}
//				return false;
//			}

			ConfigurationItem getRootParent() {
				ConfigurationItem it = this;
				while (it.parent != null) {
					it = it.parent;
				}
				return it;
			}
		}

		private static final class ConfigurationItemLabelProvider extends LabelProvider
				implements IStyledLabelProvider {
			@Override
			public String getText(Object element) {
				return getStyledText(element).getString();
			}

			@Override
			public StyledString getStyledText(Object element) {
				ConfigurationItem confitem = (ConfigurationItem) element;
				StyledString result = confitem.entry.getStyledLabel();
				return result;
			}

			@Override
			public Image getImage(Object element) {
				ConfigurationItem confitem = (ConfigurationItem) element;
				return confitem.entry.getImage();
			}
		}

		private CheckboxTreeViewer treeViewer;
		private List<ConfigurationItem> rootEntries = new ArrayList<>();
		private IProject ideProject;
		private String configurationName;

		public IDEConfigurationSelectorDialog(Shell parentShell, List<IIDEProjectConfigurationRootEntry> rootentries,
				IProject ideProject, String configurationName) {
			super(parentShell);
			this.ideProject = ideProject;
			this.configurationName = configurationName;
			for (IIDEProjectConfigurationRootEntry re : rootentries) {
				rootEntries.add(new ConfigurationItem(null, re));
			}
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Apply IDE configuration" + (configurationName == null ? "" : " - " + configurationName));
		}

		@Override
		protected Point getInitialSize() {
			Point result = super.getInitialSize();
			return new Point(500, Math.max(Math.min(result.y, 800), 600));
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected Control createContents(Composite parent) {
			Control result = super.createContents(parent);
			setMessage("Select the project configuration modifications to apply to project: " + ideProject.getName());
			return result;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			composite.setLayout(layout);
			GridData data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = GridData.FILL;
			data.verticalAlignment = GridData.FILL;
			composite.setLayoutData(data);

			treeViewer = new CheckboxTreeViewer(
					new Tree(composite, SWT.CHECK | SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.BORDER));
			treeViewer.setComparer(new IdentityElementComparer());
			if (rootEntries.size() == 1) {
				treeViewer.setAutoExpandLevel(2);
			}
			treeViewer.getTree().setLayoutData(data);
			treeViewer.setContentProvider(new ITreeContentProvider() {
				@Override
				public boolean hasChildren(Object element) {
					return !ObjectUtils.isNullOrEmpty(((ConfigurationItem) element).children);
				}

				@Override
				public Object getParent(Object element) {
					return ((ConfigurationItem) element).parent;
				}

				@Override
				public Object[] getElements(Object inputElement) {
					return ((Collection<?>) inputElement).toArray(ObjectUtils.EMPTY_OBJECT_ARRAY);
				}

				@Override
				public Object[] getChildren(Object parentElement) {
					return ((ConfigurationItem) parentElement).children;
				}
			});
			treeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new ConfigurationItemLabelProvider()));
			treeViewer.setInput(rootEntries);
			treeViewer.addCheckStateListener(new ICheckStateListener() {
				@Override
				public void checkStateChanged(CheckStateChangedEvent event) {
					ConfigurationItem checkelement = (ConfigurationItem) event.getElement();
					boolean checked = event.getChecked();
					if (checked) {
						checkelement.entry.setSelected(checked);
						selectAllParents(checkelement);
						selectAllChildren(checkelement);
					} else {
						//the element has been checked out
						if (checkelement.isAnyChildSelected()) {
							//tri-state checking
							//unselect all children
							//while the element stays gray
							checkelement.entry.setSelected(true);
							clearAllChildren(checkelement);
						} else {
							//no child selected
							//unselect the element as well
							checkelement.entry.setSelected(false);
						}
					}
					initializeCheckedState(checkelement.getRootParent());
				}
			});
			for (ConfigurationItem citem : rootEntries) {
				initializeCheckedState(citem);
			}

			return composite;
		}

		static void selectAllParents(ConfigurationItem it) {
			it = it.parent;
			while (it != null) {
				it.entry.setSelected(true);
				it = it.parent;
			}
		}

		void selectAllChildren(ConfigurationItem item) {
			for (ConfigurationItem c : item.children) {
				c.entry.setSelected(true);
				selectAllChildren(c);
			}
		}

		void clearAllChildren(ConfigurationItem item) {
			for (ConfigurationItem c : item.children) {
				c.entry.setSelected(false);
				clearAllChildren(c);
			}
		}

		void initializeCheckedState(ConfigurationItem item) {
			if (item.entry.isSelected()) {
				if (item.hasChildren()) {
					if (item.isAllChildrenSelected()) {
						treeViewer.setChecked(item, true);
						treeViewer.setGrayed(item, false);
					} else {
						treeViewer.setGrayChecked(item, true);
					}
				} else {
					treeViewer.setChecked(item, true);
				}
			} else {
				treeViewer.setGrayChecked(item, false);
				if (item.hasChildren()) {
					//the child cannot be selected if the parent is not
					for (ConfigurationItem childitem : item.children) {
						childitem.entry.setSelected(false);
					}
				}
			}
			if (item.hasChildren()) {
				for (ConfigurationItem childitem : item.children) {
					initializeCheckedState(childitem);
				}
			}
		}
	}

}
