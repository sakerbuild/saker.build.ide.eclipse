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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
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
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.configuration.ProjectIDEConfigurationCollection;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.ParameterizedBuildTargetIDEProperty;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.PropertiesValidationException;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.runtime.environment.BuildTaskExecutionResult;
import saker.build.runtime.environment.BuildTaskExecutionResult.ResultKind;
import saker.build.runtime.environment.BuildTaskExecutionResultImpl;
import saker.build.runtime.execution.BuildUserPromptHandler;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.execution.ExecutionProgressMonitor;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.runtime.execution.SakerLog.ExceptionFormat;
import saker.build.runtime.execution.SecretInputReader;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.info.BuildTargetInformation;
import saker.build.task.TaskProgressMonitor;
import saker.build.task.TaskResultCollection;
import saker.build.task.utils.TaskUtils;
import saker.build.thirdparty.saker.util.DateUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.TriConsumer;
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

	/**
	 * Qualified name of the an absolute execution path a build script of the last invoked build target.
	 */
	private static final QualifiedName LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME = new QualifiedName(Activator.PLUGIN_ID,
			"last-build-script-path");
	/**
	 * Qualified name for the last invoked build target.
	 */
	private static final QualifiedName LAST_BUILD_TARGET_NAME_QUALIFIED_NAME = new QualifiedName(Activator.PLUGIN_ID,
			"last-build-target-name");
	/**
	 * Qualified name of the unique id of the parameterized build target that was invoked in the last build.
	 */
	private static final QualifiedName LAST_PARAMETERIZED_BUILD_TARGET_UUID_QUALIFIED_NAME = new QualifiedName(
			Activator.PLUGIN_ID, "last-param-build-target-uuid");

	private static final String CONSOLE_NAME = "Saker.build Console";

	private final EclipseSakerIDEPlugin eclipseSakerPlugin;
	private final SakerIDEProject sakerProject;
	private final IProject ideProject;
	private final Lock executionLock = new ReentrantLock();
	private final Object configurationChangeLock = new Object();

	private final Set<ProjectPropertiesChangeListener> propertiesChangeListeners = Collections
			.newSetFromMap(new WeakHashMap<>());

	private List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> executionParameterContributors = Collections
			.emptyList();
	private Path projectConfigurationFilePath;

	public EclipseSakerIDEProject(EclipseSakerIDEPlugin eclipseSakerIDEPlugin, SakerIDEProject sakerproject,
			IProject project) {
		//check nulls just in case
		Objects.requireNonNull(eclipseSakerIDEPlugin, "eclipse saker IDE plugin");
		Objects.requireNonNull(sakerproject, "saker project");
		Objects.requireNonNull(project, "project");
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
			displayException(SakerLog.SEVERITY_ERROR,
					"Failed to read saker project configuration for project: " + ideProject.getName(), e);
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
					throw new IllegalArgumentException("Extension " + EclipseSakerIDEPlugin.getExtensionName(extension)
							+ " doesn't declare an unique identifier. ("
							+ Activator.EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR + ")");
				}
				boolean enabled = !ExtensionDisablement.isDisabled(extensiondisablements, extension);

				Object contributor = configelem.createExecutableExtension("class");
				if (!(contributor instanceof IExecutionUserParameterContributor)) {
					throw new ClassCastException("Extension " + EclipseSakerIDEPlugin.getExtensionName(extension)
							+ " doesn't implement " + IExecutionUserParameterContributor.class.getName() + ". ("
							+ Activator.EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR + ")");
				}
				executionParameterContributors.add(new ContributedExtensionConfiguration<>(
						(IExecutionUserParameterContributor) contributor, configelem, enabled));
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR,
						"Failed to initialize extension on project: " + ideProject.getName(), e);
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
		return Objects.toString(executionPathToProjectRelativePath(SakerIDESupportUtils.tryParsePath(executionpath)),
				null);
	}

	public SakerPath executionPathToProjectRelativePath(SakerPath executionsakerpath) {
		if (executionsakerpath == null) {
			return null;
		}
		return SakerIDESupportUtils.executionPathToProjectRelativePath(getIDEProjectProperties(),
				SakerPath.valueOf(getProjectPath()), executionsakerpath);
	}

	@Override
	public String projectPathToExecutionPath(String path) {
		return Objects.toString(projectPathToExecutionPath(SakerIDESupportUtils.tryParsePath(path)), null);
	}

	public SakerPath projectPathToExecutionPath(SakerPath path) {
		if (path == null) {
			return null;
		}
		return SakerIDESupportUtils.projectPathToExecutionPath(getIDEProjectProperties(),
				SakerPath.valueOf(getProjectPath()), path);
	}

	public SakerPath getParameterizedBuildTargetScriptExecutionPath(ParameterizedBuildTargetIDEProperty prop) {
		if (prop == null) {
			return null;
		}
		SakerPath parsedpath = SakerIDESupportUtils.tryParsePath(prop.getScriptPath());
		if (parsedpath == null) {
			return null;
		}
		if (parsedpath.isAbsolute()) {
			return parsedpath;
		}
		return projectPathToExecutionPath(parsedpath);
	}

	public boolean isScriptModellingConfigurationAppliesTo(IFile file) {
		SakerPath projectrelativepath = SakerPath.valueOf(file.getProjectRelativePath().toString());
		SakerPath execpath = projectPathToExecutionPath(projectrelativepath);

		return isScriptModellingConfigurationAppliesTo(execpath);
	}

	public void handleFileMove(IFile file, IPath movedto) {
		//move the parameterized targets with the file
		SakerPath oldrelativepath = SakerPath.valueOf(file.getProjectRelativePath().toString());
		SakerPath oldexecpath = projectPathToExecutionPath(oldrelativepath);

		IPath projectpath = file.getProject().getFullPath();
		if (!projectpath.isPrefixOf(movedto)) {
			//moved to a different project
			return;
		}
		SakerPath newrelativepath = SakerPath.valueOf(movedto.makeRelativeTo(projectpath).toString());
		SakerPath newexecpath = projectPathToExecutionPath(newrelativepath);

		if (oldexecpath != null && newexecpath != null) {
			//update the last build script property to the new path
			try {
				String lastscriptpath = ideProject.getPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME);
				if (lastscriptpath != null && oldexecpath.equals(SakerIDESupportUtils.tryParsePath(lastscriptpath))) {
					ideProject.setPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME,
							Objects.toString(newexecpath, null));
				}
			} catch (CoreException e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to update last build script path propery.", e);
			}
		}

		//perform the moving
		IDEProjectProperties properties = getIDEProjectProperties();
		if (properties == null) {
			return;
		}
		Set<? extends ParameterizedBuildTargetIDEProperty> paramtargets = properties.getParameterizedBuildTargets();
		if (ObjectUtils.isNullOrEmpty(paramtargets)) {
			return;
		}
		boolean changed = false;
		Set<ParameterizedBuildTargetIDEProperty> ntargets = new LinkedHashSet<>();
		for (ParameterizedBuildTargetIDEProperty paramtarget : paramtargets) {
			SakerPath parsedpath = SakerIDESupportUtils.tryParsePath(paramtarget.getScriptPath());
			if (oldrelativepath.equals(parsedpath) || (oldexecpath != null && oldexecpath.equals(parsedpath))) {
				//change the path of this property, and add that one
				ntargets.add(new ParameterizedBuildTargetIDEProperty(paramtarget.getUuid(), newrelativepath.toString(),
						paramtarget.getTargetName(), paramtarget.getDisplayName(),
						paramtarget.getBuildTargetParameters()));
				changed = true;
				continue;
			}
			ntargets.add(paramtarget);
			continue;
		}

		if (changed) {
			try {
				setIDEProjectProperties(
						SimpleIDEProjectProperties.builder(properties).setParameterizedBuildTargets(ntargets).build());
			} catch (IOException e) {
				displayException(SakerLog.SEVERITY_ERROR, "Failed to save project properties.", e);
			}
		}

	}

	private boolean isScriptModellingConfigurationAppliesTo(SakerPath execpath) {
		return SakerIDESupportUtils.isScriptModellingConfigurationAppliesTo(execpath, getIDEProjectProperties());
	}

	public IFile getIFileAtExecutionPath(SakerPath path) {
		SakerPath relpath = executionPathToProjectRelativePath(path);
		if (relpath == null) {
			return null;
		}
		return ideProject.getFile(new org.eclipse.core.runtime.Path(relpath.toString()));
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
				IExecutionUserParameterContributor contributor = extension.getContributor();
				Set<UserParameterModification> modifications = contributor.contribute(this, unmodifiableuserparammap,
						monitor);
				if (ObjectUtils.isNullOrEmpty(modifications)) {
					continue;
				}
				Set<String> keys = new TreeSet<>();
				for (UserParameterModification mod : modifications) {
					if (!keys.add(mod.getKey())) {
						displayException(SakerLog.SEVERITY_WARNING,
								"Multiple execution user parameter modification for key: " + mod.getKey()
										+ " by extension class: " + ObjectUtils.classNameOf(contributors)
										+ " for project: " + ideProject.getName());
						continue contributor_loop;
					}
				}
				for (UserParameterModification mod : modifications) {
					mod.apply(userparamworkmap);
				}
			} catch (Exception e) {
				//catch other kind of exceptions too
				displayException(SakerLog.SEVERITY_WARNING, "Failed to apply user parameter contributor extension "
						+ extension.getConfigurationElement().getName() + " for project: " + ideProject.getName(), e);
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

	public SakerIDEProject getSakerProject() {
		return sakerProject;
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
		NavigableMap<String, Object> configfieldmap = new TreeMap<>();
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
		configfieldmap = ImmutableUtils.unmodifiableNavigableMap(configfieldmap);

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
					//throw so its properly logged
					throw new ClassCastException("Extension " + configelem.getName() + " doesn't implement "
							+ IIDEConfigurationTypeHandler.class.getName() + " with class: "
							+ ObjectUtils.classNameOf(parser));
				}
				parsers.add((IIDEConfigurationTypeHandler) parser);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_ERROR,
						"Failed to intialize IDE configuration parser extension: " + configelem.getName(), e);
				continue;
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
				displayException(SakerLog.SEVERITY_WARNING,
						"Failed to invoke IDE configuration parser extension: " + parser.getClass(), e);
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
					displayException(SakerLog.SEVERITY_WARNING,
							"Failed to apply IDE configuration: " + ideconfig.getIdentifier(), e);
					return;
				}
			}
		}
	}

	public void setIDEProjectProperties(IDEProjectProperties properties,
			List<ContributedExtensionConfiguration<IExecutionUserParameterContributor>> executionParameterContributors)
			throws IOException {
		synchronized (configurationChangeLock) {
			projectPropertiesChanging();
			try {
				boolean propertieschanged = sakerProject.setIDEProjectProperties(properties);
				Set<ExtensionDisablement> prevdisablements = EclipseSakerIDEPlugin
						.getExtensionDisablements(this.executionParameterContributors);
				Set<ExtensionDisablement> currentdisablements = EclipseSakerIDEPlugin
						.getExtensionDisablements(executionParameterContributors);
				if (!prevdisablements.equals(currentdisablements)) {
					propertieschanged = true;
					try {
						writeProjectConfigurationFile(currentdisablements);
						this.executionParameterContributors = executionParameterContributors;
					} catch (IOException e) {
						//this failed, but continue nonetheless, as setting the main properties is successful
						displayException(SakerLog.SEVERITY_ERROR,
								"Failed to save configuration file for project: " + ideProject.getName(), e);
					}
				}
				if (propertieschanged) {
					sakerProject.updateForProjectProperties(
							getIDEProjectPropertiesWithExecutionParameterContributions(properties, null));
				}
			} finally {
				projectPropertiesChanged();
			}
		}
	}

	public void setIDEProjectProperties(IDEProjectProperties properties) throws IOException {
		synchronized (configurationChangeLock) {
			projectPropertiesChanging();
			try {
				boolean propertieschanged = sakerProject.setIDEProjectProperties(properties);
				if (propertieschanged) {
					sakerProject.updateForProjectProperties(
							getIDEProjectPropertiesWithExecutionParameterContributions(properties, null));
				}
			} finally {
				projectPropertiesChanged();
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

	public final ScriptModellingEnvironment getScriptingEnvironment() throws IOException {
		return sakerProject.getScriptingEnvironment();
	}

	public final NavigableSet<SakerPath> getTrackedScriptPaths() {
		return sakerProject.getTrackedScriptPaths();
	}

	@Deprecated
	public final Set<String> getScriptTargets(SakerPath scriptpath) throws ScriptParsingFailedException, IOException {
		return sakerProject.getScriptTargets(scriptpath);
	}

	public final Collection<? extends BuildTargetInformation> getScriptBuildTargetInfos(SakerPath scriptpath)
			throws ScriptParsingFailedException, IOException {
		return sakerProject.getScriptBuildTargetInfos(scriptpath);
	}

	public SakerPath getProjectDirectoryExecutionPath() {
		return projectPathToExecutionPath(SakerPath.EMPTY);
	}

	public void clean(IProgressMonitor monitor) {
		try {
			clearLastBuildTarget();
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR,
					"Failed to save last build information properties for project: " + ideProject.getName(), e);
		}
		try {
			ideProject.deleteMarkers(ProjectBuilder.MARKER_TYPE, true, IProject.DEPTH_INFINITE);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to delete markets on project: " + ideProject.getName(),
					e);
		}
		try {
			sakerProject.clean();
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to clean project: " + ideProject.getName(), e);
		}
		try {
			ideProject.refreshLocal(IProject.DEPTH_INFINITE, monitor);
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR, "Failed to refresh project: " + ideProject.getName(), e);
		}
	}

	public void build(IProgressMonitor monitor) {
		withLatestOrChosenBuildTarget((scriptpath, target, parameterizedtarget) -> {
			if (parameterizedtarget == null) {
				build(scriptpath, target, monitor);
			} else {
				build(parameterizedtarget, monitor);
			}
		});
	}

	public void buildWithNewJob() {
		withLatestOrChosenBuildTarget((scriptpath, target, parameterizedtarget) -> {
			if (parameterizedtarget == null) {
				ProjectBuilder.buildAsync(this, scriptpath, target);
			} else {
				ProjectBuilder.buildAsync(this, parameterizedtarget);
			}
		});
	}

	public boolean isLatestBuildScriptTargetEquals(SakerPath scriptpath, String target, String parameterizeduuid) {
		try {
			String lastparameduuid = ideProject
					.getPersistentProperty(LAST_PARAMETERIZED_BUILD_TARGET_UUID_QUALIFIED_NAME);
			if (parameterizeduuid != null && parameterizeduuid.equals(lastparameduuid)) {
				//no need to check the script path and target path, as they might've been moved meanwhile
				return true;
			}
			String lastscriptpath = ideProject.getPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME);
			if (lastscriptpath == null) {
				return false;
			}
			if (!SakerPath.valueOf(lastscriptpath).equals(scriptpath)) {
				return false;
			}
			String lasttargetname = ideProject.getPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME);
			if (lasttargetname == null) {
				return false;
			}
			if (!lasttargetname.equals(target)) {
				return false;
			}
			if (!Objects.equals(lastparameduuid, parameterizeduuid)) {
				return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private ParameterizedBuildTargetIDEProperty getParameterizedBuildTarget(String uuid) {
		if (uuid == null) {
			return null;
		}
		IDEProjectProperties props = getIDEProjectProperties();
		if (props == null) {
			return null;
		}
		Set<? extends ParameterizedBuildTargetIDEProperty> targets = props.getParameterizedBuildTargets();
		if (targets == null) {
			return null;
		}
		for (ParameterizedBuildTargetIDEProperty prop : targets) {
			if (!uuid.equals(prop.getUuid())) {
				continue;
			}
			return prop;
		}
		return null;
	}

	private void withLatestOrChosenBuildTarget(
			TriConsumer<SakerPath, String, ParameterizedBuildTargetIDEProperty> consumer) {
		try {
			String lastparameduuid = ideProject
					.getPersistentProperty(LAST_PARAMETERIZED_BUILD_TARGET_UUID_QUALIFIED_NAME);
			if (lastparameduuid != null) {
				ParameterizedBuildTargetIDEProperty paramedtarget = getParameterizedBuildTarget(lastparameduuid);
				if (getParameterizedBuildTargetScriptExecutionPath(paramedtarget) != null) {
					//if the script file is mapped to a valid execution path
					consumer.accept(null, null, paramedtarget);
					return;
				}
			} else {
				String lastscriptpath = ideProject.getPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME);
				if (lastscriptpath != null) {
					String lasttargetname = ideProject.getPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME);
					SakerPath lastbuildpath = SakerPath.valueOf(lastscriptpath);

					consumer.accept(lastbuildpath, lasttargetname, null);
					return;
				}
			}
		} catch (Exception e) {
			displayException(SakerLog.SEVERITY_ERROR,
					"Failed to select build target for project: " + ideProject.getName(), e);
		}
		AskListItem chosen = askBuildTarget();
		if (chosen != null) {
			consumer.accept(chosen.scriptPath, chosen.target, chosen.parameterizedTarget);
			return;
		}
	}

	private void setLastBuildTarget(SakerPath scriptfile, String targetname, String parameterizedtargetuuid)
			throws CoreException {
		//nullify string representation for safety
		ideProject.setPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME, Objects.toString(scriptfile, null));
		ideProject.setPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME, targetname);
		ideProject.setPersistentProperty(LAST_PARAMETERIZED_BUILD_TARGET_UUID_QUALIFIED_NAME, parameterizedtargetuuid);
	}

	private void clearLastBuildTarget() throws CoreException {
		ideProject.setPersistentProperty(LAST_BUILD_SCRIPT_PATH_QUALIFIED_NAME, null);
		ideProject.setPersistentProperty(LAST_BUILD_TARGET_NAME_QUALIFIED_NAME, null);
		ideProject.setPersistentProperty(LAST_PARAMETERIZED_BUILD_TARGET_UUID_QUALIFIED_NAME, null);
	}

	public void addDefaultNewBuildFile() throws CoreException {
//		SakerPath execpath = projectPathToExecutionPath(SakerPath.valueOf(SakerIDEProject.DEFAULT_BUILD_FILE_NAME));
//		if (!isScriptModellingConfigurationAppliesTo(execpath)) {
//			//TODO if the name is not included in the script configuration, ask for rename
//		}
		addNewBuildFile(SakerIDEProject.DEFAULT_BUILD_FILE_NAME);
	}

	public void addNewBuildFile(String name) throws CoreException {
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
	@Deprecated
	public void displayException(Throwable e) {
		this.displayException(SakerLog.SEVERITY_ERROR, "Saker.build project exception: " + ideProject.getName(), e);
	}

	public void displayException(int severity, String message) {
		displayException(severity, message, null);
	}

	@Override
	public void displayException(int severity, String message, Throwable exc) {
		if (message == null) {
			message = "Saker.build project exception: " + ideProject.getName();
		}
		eclipseSakerPlugin.displayException(severity, message, exc);
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

	protected void build(SakerPath scriptfile, String targetname, IProgressMonitor monitor) {
		build(scriptfile, targetname, null, null, targetname, monitor);
	}

	protected void build(ParameterizedBuildTargetIDEProperty parambuildtarget, IProgressMonitor monitor) {
		SakerPath buildfilepath = getParameterizedBuildTargetScriptExecutionPath(parambuildtarget);
		build(buildfilepath, parambuildtarget.getTargetName(), parambuildtarget.getBuildTargetParameters(),
				parambuildtarget.getUuid(),
				SakerIDESupportUtils.getParameterizedBuildTargetDisplayString(parambuildtarget), monitor);
	}

	/**
	 * Runs a build.
	 * 
	 * @param scriptfile
	 *            The absolute execution path of the build script.
	 * @param targetname
	 *            The target name to run.
	 * @param buildtargetparameters
	 *            The build target parameters to pass to the invoked target.
	 * @param parameterizedtargetuuid
	 *            The unique identifier of the parameterized target being invoked. May be <code>null</code>.
	 * @param displaytargetname
	 *            The target name to display in the Job title.
	 * @param monitor
	 *            The progress monitor if any.
	 */
	private void build(SakerPath scriptfile, String targetname, NavigableMap<String, String> buildtargetparameters,
			String parameterizedtargetuuid, String displaytargetname, IProgressMonitor monitor) {
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
			boolean locked = false;
			try {
				executionLock.lockInterruptibly();
				locked = true;
				console.clearConsole();
				if (monitorwrapper.isCancelled()) {
					out.write("Build cancelled.\n");
					return;
				}
				ExecutionParametersImpl params;
				IDEProjectProperties projectproperties;
				try {
					projectproperties = getIDEProjectPropertiesWithExecutionParameterContributions(
							getIDEProjectProperties(), monitor);
				} catch (OperationCanceledException e) {
					out.write("Build cancelled.\n");
					return;
				}
				try {
					params = sakerProject.createExecutionParameters(projectproperties);
					//there were no validation errors
				} catch (PropertiesValidationException e) {
					err.write("Invalid build configuration:\n".getBytes());
					for (PropertiesValidationErrorResult error : e.getErrors()) {
						err.write(SakerIDESupportUtils.createValidationErrorMessage(error).getBytes());
						err.write('\n');
					}

					IDEProjectProperties initialprojectproperties = projectproperties;
					display.asyncExec(() -> {
						PropertiesValidationBuildErrorDialog errordialog = new PropertiesValidationBuildErrorDialog(
								display.getActiveShell(), initialprojectproperties, e.getErrors());
						errordialog.open();
					});
					return;
				}
				DaemonEnvironment daemonenv = sakerProject.getExecutionDaemonEnvironment(projectproperties);
				if (daemonenv == null) {
					throw new IllegalStateException("Build daemon environment is not running.");
				}
				ExecutionPathConfiguration pathconfiguration = params.getPathConfiguration();
				executionworkingdir = pathconfiguration.getWorkingDirectory();
				params.setRequiresIDEConfiguration(SakerIDESupportUtils
						.getBooleanValueOrDefault(projectproperties.getRequireTaskIDEConfiguration(), true));

				try {
					setLastBuildTarget(scriptfile, targetname, parameterizedtargetuuid);
				} catch (CoreException e) {
					displayException(SakerLog.SEVERITY_ERROR,
							"Failed to save last build information properties for project: " + ideProject.getName(), e);
				}
				try {
					ideProject.deleteMarkers(ProjectBuilder.MARKER_TYPE, true, IProject.DEPTH_INFINITE);
				} catch (CoreException e) {
					displayException(SakerLog.SEVERITY_ERROR,
							"Failed to delete markets on project: " + ideProject.getName(), e);
				}

				SakerPath relativescriptpath = executionPathToProjectRelativePath(scriptfile);
				String jobname = displaytargetname + "@" + relativescriptpath;

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
									displayException(SakerLog.SEVERITY_ERROR,
											"Failed to promp user during build for project: " + ideProject.getName(),
											e);
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
								displayException(SakerLog.SEVERITY_ERROR,
										"Failed to get secret value during build for project: " + ideProject.getName(),
										e);
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
				result = sakerProject.build(scriptfile, targetname, daemonenv, params, buildtargetparameters);
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
						displayException(SakerLog.SEVERITY_WARNING, "Failed to refresh build directory " + builddir
								+ " in project: " + ideProject.getName(), e);
					}
				}
				if (ObjectUtils.isNullOrEmpty(projectproperties.getExecutionDaemonConnectionName())) {
					//set derived to the mirror directory only if the build is running in-process
					SakerPath mirrordir = params.getMirrorDirectory();
					if (mirrordir != null) {
						try {
							Path localmirrorpath = LocalFileProvider.toRealPath(mirrordir);
							if (localmirrorpath.startsWith(projectpath)) {
								IFolder mirrorfolder = ideProject
										.getFolder(projectpath.relativize(localmirrorpath).toString());
								if (mirrorfolder != null) {
									//intentionally don't refresh. There's no particular need for it
									mirrorfolder.setDerived(true, monitor);
								}
							}
						} catch (InvalidPathException e) {
							//the mirror path is not a valid path on the local file system
							//ignoreable
						} catch (OperationCanceledException e) {
						} catch (Exception e) {
							displayException(SakerLog.SEVERITY_WARNING, "Failed to refresh build mirror directory "
									+ mirrordir + " in project: " + ideProject.getName(), e);
						}
					}
				}
				TaskResultCollection resultcollection = result.getTaskResultCollection();
				if (resultcollection != null) {
					try {
						Collection<? extends IDEConfiguration> ideconfigs = resultcollection.getIDEConfigurations();
						addIDEConfigurations(ideconfigs);
					} catch (Exception e) {
						//do not throw IDE configuration related exception as this doesn't caues the build to fail
						displayException(SakerLog.SEVERITY_WARNING, "Failed to set IDE configurations.", e);
					}
				}
				return;
			} catch (Throwable e) {
				if (result == null) {
					result = BuildTaskExecutionResultImpl.createInitializationFailed(e);
					try {
						out.write("Failed to initialize execution.\n".getBytes());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				return;
			} finally {
				if (result != null) {
					ScriptPositionedExceptionView posexcview = result.getPositionedExceptionView();
					if (posexcview != null) {
						ExceptionFormat exceptionformat = CommonExceptionFormat.DEFAULT_FORMAT;
						IDEPluginProperties pluginprops = eclipseSakerPlugin.getIDEPluginProperties();
						if (pluginprops != null) {
							String propexcformat = pluginprops.getExceptionFormat();
							if (propexcformat != null) {
								//convert to upper case to attempt to handle possible case differences
								try {
									exceptionformat = CommonExceptionFormat
											.valueOf(propexcformat.toUpperCase(Locale.ENGLISH));
								} catch (IllegalArgumentException ignored) {
								}
							}
						}

						consoleaccessor.stackTrace = posexcview;
						TaskUtils.printTaskExceptionsOmitTransitive(posexcview, new PrintStream(err),
								executionworkingdir, exceptionformat);
					}
				}
				IOException streamscloseexc = IOUtils.closeExc(out, err);
				if (streamscloseexc != null) {
					displayException(SakerLog.SEVERITY_WARNING,
							"Failed to close build console streams for project: " + ideProject.getName(),
							streamscloseexc);
				}
				if (locked) {
					executionLock.unlock();
				}
			}
		} finally {
			console.endBuild(consoleaccessor);
			if (wasinterrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void writeProjectConfigurationFile(Iterable<? extends ExtensionDisablement> disablements)
			throws IOException {
		Path propfilepath = projectConfigurationFilePath;
		Path tempfilepath = propfilepath.resolveSibling(propfilepath.getFileName() + "." + UUID.randomUUID() + ".temp");
		try (OutputStream os = Files.newOutputStream(tempfilepath)) {
			try (XMLStructuredWriter writer = new XMLStructuredWriter(os)) {
				try (StructuredObjectOutput configurationobj = writer.writeObject(CONFIG_FILE_ROOT_OBJECT_NAME)) {
					EclipseSakerIDEPlugin.writeExtensionDisablements(configurationobj, disablements);
				}
			}
		}
		Files.move(tempfilepath, propfilepath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void projectPropertiesChanging() {
		List<ProjectPropertiesChangeListener> listenercopy;
		synchronized (propertiesChangeListeners) {
			listenercopy = ImmutableUtils.makeImmutableList(propertiesChangeListeners);
		}
		for (ProjectPropertiesChangeListener l : listenercopy) {
			try {
				l.projectPropertiesChanging();
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_WARNING,
						"Failed to call projectPropertiesChanging() listener on class: " + ObjectUtils.classNameOf(l)
								+ " for project: " + ideProject.getName(),
						e);
			}
		}
	}

	private void projectPropertiesChanged() {
		List<ProjectPropertiesChangeListener> listenercopy;
		synchronized (propertiesChangeListeners) {
			listenercopy = ImmutableUtils.makeImmutableList(propertiesChangeListeners);
		}
		for (ProjectPropertiesChangeListener l : listenercopy) {
			try {
				l.projectPropertiesChanged();
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_WARNING,
						"Failed to call projectPropertiesChanged() listener on class: " + ObjectUtils.classNameOf(l)
								+ " for project: " + ideProject.getName(),
						e);
			}
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

	private void addIDEConfigurations(Collection<? extends IDEConfiguration> ideconfigs) throws IOException {
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
		AskListItem[] diagres = { null };
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			diagres[0] = openAskBuildTargetDialog();
		});
		if (diagres[0] == null) {
			return null;
		}
		return diagres[0];
	}

	private AskListItem openAskBuildTargetDialog() {
		Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		Set<? extends SakerPath> buildfiles = getTrackedScriptPaths();
		Iterator<? extends SakerPath> it = buildfiles.iterator();
		if (!it.hasNext()) {
			int newbuildfiledialogres = new NoBuildFileErrorDialog(activeShell).open();
			if (newbuildfiledialogres == IDialogConstants.OK_ID) {
				try {
					addDefaultNewBuildFile();
				} catch (Exception e) {
					displayException(SakerLog.SEVERITY_ERROR,
							"Failed to create new build script file for project: " + ideProject.getName(), e);
				}
			}
			return null;
		}

		List<AskListItem> input = new ArrayList<>();
		do {
			SakerPath buildfile = it.next();
			SakerPath displaybuildfile = ObjectUtils.nullDefault(executionPathToProjectRelativePath(buildfile),
					buildfile);

			Set<String> targets;
			try {
				targets = getScriptTargets(buildfile);
			} catch (Exception e) {
				displayException(SakerLog.SEVERITY_WARNING,
						"Failed to determine built targets of script file: " + buildfile, e);
				continue;
			}
			if (!ObjectUtils.isNullOrEmpty(targets)) {
				for (String target : targets) {
					input.add(new AskListItem(buildfile, target, displaybuildfile));
				}
			}
		} while (it.hasNext());

		IDEProjectProperties ideprops = getIDEProjectProperties();
		if (ideprops != null) {
			Set<? extends ParameterizedBuildTargetIDEProperty> paramedtargets = ideprops.getParameterizedBuildTargets();
			if (paramedtargets != null) {
				for (ParameterizedBuildTargetIDEProperty paramedtarget : paramedtargets) {
					if (ObjectUtils.isNullOrEmpty(paramedtarget.getTargetName())) {
						continue;
					}
					if (!paramedtarget.isParameterized()) {
						//no additional parameters, makes no sense to invoke this instead of the actual target in the build script
						continue;
					}
					SakerPath buildfile = getParameterizedBuildTargetScriptExecutionPath(paramedtarget);
					if (buildfile == null) {
						//the script path doesn't correspond to an actual execution path
						continue;
					}
					if (!buildfiles.contains(buildfile)) {
						//the script file is not tracked
						continue;
					}
					SakerPath displaybuildfile = ObjectUtils.nullDefault(executionPathToProjectRelativePath(buildfile),
							buildfile);
					input.add(new AskListItem(displaybuildfile, paramedtarget));
				}
			}
		}
		ListDialog listdialog = new ListDialog(activeShell);
		listdialog.setTitle("Build target");
		listdialog.setMessage("Choose a build target to run.");

		listdialog.setInput(input);
		listdialog.setContentProvider(ArrayContentProvider.getInstance());
		listdialog.setLabelProvider(new LabelProvider());

		listdialog.setHelpAvailable(false);
		listdialog.open();
		Object[] dialogresult = listdialog.getResult();
		AskListItem chosenitem = ObjectUtils.isNullOrEmpty(dialogresult) ? null : (AskListItem) dialogresult[0];
		return chosenitem;
	}

	private static class AskListItem {
		private SakerPath scriptPath;
		private String target;

		private ParameterizedBuildTargetIDEProperty parameterizedTarget;

		private transient SakerPath displayPath;

		public AskListItem(SakerPath scriptPath, String target, SakerPath displayPath) {
			this.scriptPath = scriptPath;
			this.target = target;
			this.displayPath = displayPath;
		}

		public AskListItem(SakerPath displayPath, ParameterizedBuildTargetIDEProperty parameterizedTarget) {
			this.displayPath = displayPath;
			this.parameterizedTarget = parameterizedTarget;
		}

		@Override
		public String toString() {
			return (target == null ? SakerIDESupportUtils.getParameterizedBuildTargetDisplayString(parameterizedTarget)
					: target) + "@" + displayPath;
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
				items[i] = SakerIDESupportUtils.createValidationErrorMessage(errorresult);
			}
			errorlist.setItems(items);
			errorlist.getParent().requestLayout();
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
			} else if (type.startsWith(SakerIDEProject.NS_BUILD_TRACE_OUT)) {
				pageid = SakerBuildProjectPropertyPage.ID;
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
