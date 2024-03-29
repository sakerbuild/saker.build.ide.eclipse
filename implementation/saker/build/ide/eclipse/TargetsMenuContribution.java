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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.bindings.Trigger;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.keys.IBindingService;

import saker.build.file.path.SakerPath;
import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.eclipse.handlers.BuildProjectCommandHandler;
import saker.build.ide.eclipse.ui.ParameterizedTargetsEditorDialog;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.ParameterizedBuildTargetIDEProperty;
import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.model.info.BuildTargetInformation;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;

public class TargetsMenuContribution extends ContributionItem {
	private IMenuListener menulistener = manager -> manager.markDirty();

	public TargetsMenuContribution() {
	}

	public TargetsMenuContribution(String id) {
		super(id);
	}

	@Override
	public void fill(Menu menu, int index) {
		IProject project = findProject();
		super.fill(menu, index);
		MenuManager parent = (MenuManager) getParent();
		parent.addMenuListener(menulistener);

		String header = "Project: " + project.getName();
		boolean projectopen = project.isOpen();
		if (!projectopen) {
			header += " (Closed)";
		}
		BaseAction projectdummy = new BaseAction(header);
		projectdummy.setEnabled(false);
		new ActionContributionItem(projectdummy).fill(menu, -1);

		if (!projectopen) {
			return;
		}
		new Separator().fill(menu, -1);

		EclipseSakerIDEProject sakereclipseproject = ImplActivator.getDefault().getOrCreateSakerProject(project);
		if (sakereclipseproject == null) {
			//not a saker project
			boolean natureenabled = false;
			try {
				natureenabled = project.isNatureEnabled(SakerBuildProjectNature.NATURE_ID);
			} catch (Exception e) {
				ImplActivator.getDefault().displayException(SakerLog.SEVERITY_ERROR,
						"Failed to check project for saker.build nature: " + project.getName(), e);
			}
			if (!natureenabled) {
				Action natureaction = new Action("Add saker.build nature") {
					@Override
					public void run() {
						try {
							SakerBuildProjectNature.addNature(project);
						} catch (CoreException e) {
							ImplActivator.getDefault().displayException(SakerLog.SEVERITY_ERROR,
									"Failed to configure project with saker.build nature: " + project.getName(), e);
						}
					}
				};
				new ActionContributionItem(natureaction).fill(menu, -1);
				return;
			}
		} else {
			addTargetsMenu(sakereclipseproject, menu);
			addIDEConfigurationMenu(sakereclipseproject, menu);
			new Separator().fill(menu, -1);
			new ActionContributionItem(new Action("Clean project") {
				@Override
				public void run() {
					Job j = new Job("Clean saker.build project") {
						@Override
						protected IStatus run(IProgressMonitor monitor) {
							try {
								sakereclipseproject.clean(monitor);

								return Status.OK_STATUS;
							} catch (Exception e) {
								return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
										"Failed to clean saker.build project", e);
							}
						}
					};
					j.setPriority(Job.BUILD);
					j.schedule();
				}
			}).fill(menu, -1);
		}
	}

	private static void addTargetsMenu(EclipseSakerIDEProject sakereclipseproject, Menu menu) {
		NavigableSet<SakerPath> filepaths = sakereclipseproject.getTrackedScriptPaths();
		if (filepaths.isEmpty()) {
			Action action = new Action("Add new build file") {
				@Override
				public void run() {
					try {
						sakereclipseproject.addDefaultNewBuildFile();
					} catch (Exception e) {
						sakereclipseproject.displayException(SakerLog.SEVERITY_ERROR,
								"Failed to create new build script file for project: "
										+ sakereclipseproject.getProject().getName(),
								e);
					}
				}
			};
			new ActionContributionItem(action).fill(menu, -1);
		} else {
			for (SakerPath buildfilepath : filepaths) {
				//if the script path is not under the project, it might be mounted or something
				//fall back to the full execution path
				SakerPath relativepath = ObjectUtils.nullDefault(
						sakereclipseproject.executionPathToProjectRelativePath(buildfilepath), buildfilepath);

				MenuManager targetmenu = new MenuManager(relativepath.toString(), null);
				targetmenu.setRemoveAllWhenShown(true);
				targetmenu.addMenuListener(new IMenuListener() {
					@Override
					public void menuAboutToShow(IMenuManager manager) {
						Collection<? extends BuildTargetInformation> targets = appendTargetsToBuildFileMenu(manager,
								sakereclipseproject, buildfilepath);
						appendParameterizedTargetsToBuildFileMenu(manager, sakereclipseproject, buildfilepath, targets);
						appendOpenInEditorAction(manager, sakereclipseproject, buildfilepath);
					}

				});
				targetmenu.fill(menu, -1);
			}
		}
	}

	private static void appendOpenInEditorAction(IMenuManager manager, EclipseSakerIDEProject sakereclipseproject,
			SakerPath buildfilepath) {
		SakerPath fileprojectpath = sakereclipseproject.executionPathToProjectRelativePath(buildfilepath);
		if (fileprojectpath == null) {
			return;
		}
		manager.add(new Separator());
		manager.add(new Action("Open in editor") {
			@Override
			public void run() {
				try {
					IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
							sakereclipseproject.getProject().getFile(new Path(fileprojectpath.toString())),
							BuildFileEditor.ID, true);
				} catch (PartInitException e) {
					sakereclipseproject.displayException(SakerLog.SEVERITY_ERROR,
							"Failed to open script editor for: " + fileprojectpath, e);
				}
			}
		});
	}

	private static Collection<? extends BuildTargetInformation> appendTargetsToBuildFileMenu(IMenuManager manager,
			EclipseSakerIDEProject sakereclipseproject, SakerPath buildfilepath) {
		Collection<? extends BuildTargetInformation> scripttargets;
		try {
			scripttargets = sakereclipseproject.getScriptBuildTargetInfos(buildfilepath);
		} catch (ScriptParsingFailedException e) {
			SakerPath fileprojectpath = sakereclipseproject.executionPathToProjectRelativePath(buildfilepath);
			if (fileprojectpath != null) {
				manager.add(new Action("Failed to parse script file") {
					@Override
					public void run() {
						try {
							IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
									sakereclipseproject.getProject().getFile(new Path(fileprojectpath.toString())),
									BuildFileEditor.ID, true);
						} catch (PartInitException e2) {
							e2.addSuppressed(e);
							sakereclipseproject.displayException(SakerLog.SEVERITY_ERROR,
									"Failed to open script editor for file: " + fileprojectpath, e2);
						}
					}
				});
			} else {
				//don't display the exception here, as this might happen often, and we don't want to overwhelm the logs
				BaseAction dummy = new BaseAction("Failed to parse script file (" + e + ")");
				dummy.setEnabled(false);
				manager.add(dummy);
			}
			return null;
		} catch (IOException e) {
			BaseAction dummy = new BaseAction("Failed to open script file");
			dummy.setEnabled(false);
			manager.add(dummy);
			return null;
		}
		if (scripttargets == null) {
			BaseAction dummy = new BaseAction("Script is not part of the configuration");
			dummy.setEnabled(false);
			manager.add(dummy);
			return null;
		}
		if (scripttargets.isEmpty()) {
			BaseAction dummy = new BaseAction("No targets found");
			dummy.setEnabled(false);
			manager.add(dummy);
			return scripttargets;
		}
		//make a modifiable collection
		scripttargets = new ArrayList<>(scripttargets);
		for (Iterator<? extends BuildTargetInformation> it = scripttargets.iterator(); it.hasNext();) {
			BuildTargetInformation targetinfo = it.next();
			String target = targetinfo.getTargetName();
			if (target == null) {
				//ignore this target, as no target name info is available
				it.remove();
				continue;
			}
			String acc = getBuildTargetActionAccelerator(sakereclipseproject, buildfilepath, target, null);
			String actionlabel = target;
			if (acc != null) {
				actionlabel += "\t" + acc;
			}
			Action runbuildaction = new Action(actionlabel) {
				@Override
				public void run() {
					ProjectBuilder.buildAsync(sakereclipseproject, buildfilepath, target);
				}
			};
			manager.add(runbuildaction);
		}
		return scripttargets;
	}

	private static void appendParameterizedTargetsToBuildFileMenu(IMenuManager manager,
			EclipseSakerIDEProject sakereclipseproject, SakerPath buildfilepath,
			Collection<? extends BuildTargetInformation> targets) {

		//XXX we should only list (and allow editing) targets that actually have input parameters
		manager.add(new Separator());

		IDEProjectProperties props = sakereclipseproject.getIDEProjectProperties();
		if (props != null) {
			Set<? extends ParameterizedBuildTargetIDEProperty> paramtargets = props.getParameterizedBuildTargets();
			if (paramtargets != null) {
				for (ParameterizedBuildTargetIDEProperty parambuildtarget : paramtargets) {
					SakerPath scriptpath = sakereclipseproject
							.getParameterizedBuildTargetScriptExecutionPath(parambuildtarget);
					if (!buildfilepath.equals(scriptpath)) {
						//different script path, this parameterized target is associated with a different script
						continue;
					}
					String targetname = parambuildtarget.getTargetName();
					if (ObjectUtils.isNullOrEmpty(targetname)) {
						//no target name, can't do much with it
						continue;
					}

					String acc = getBuildTargetActionAccelerator(sakereclipseproject, buildfilepath, targetname,
							parambuildtarget.getUuid());
					if (acc == null && !parambuildtarget.isParameterized()) {
						//no additional parameters, makes no sense to invoke this instead of the actual target in the build script
						//if acc is not null, (that is, it was the last build target), then display the target even
						//	if it has no additional parameters
						continue;
					}

					String displayname = SakerIDESupportUtils
							.getParameterizedBuildTargetDisplayString(parambuildtarget);
					String actionlabel = displayname;
					if (acc != null) {
						actionlabel += "\t" + acc;
					}
					manager.add(new Action(actionlabel) {
						@Override
						public void run() {
							ProjectBuilder.buildAsync(sakereclipseproject, parambuildtarget);
						}
					});
				}
			}
		}
		manager.add(new Action("Edit parameterized targets...") {
			@Override
			public void run() {
				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

						SakerPath projrelativepath = sakereclipseproject
								.executionPathToProjectRelativePath(buildfilepath);
						SakerPath propertypath = ObjectUtils.nullDefault(projrelativepath, buildfilepath);

						ParameterizedTargetsEditorDialog dialog = new ParameterizedTargetsEditorDialog(activeShell,
								sakereclipseproject, propertypath, targets);
						dialog.setBlockOnOpen(false);
						dialog.setTitleText(
								"Parameterize targets: " + ObjectUtils.nullDefault(projrelativepath, buildfilepath));

						dialog.create();
						dialog.setMessage("Configure parameterized targets for the build script.");
						dialog.open();
					}
				});
			}
		});
	}

	private static String getBuildTargetActionAccelerator(EclipseSakerIDEProject sakereclipseproject,
			SakerPath buildfilepath, String target, String parameterizeduuid) {
		if (sakereclipseproject.isLatestBuildScriptTargetEquals(buildfilepath, target, parameterizeduuid)) {
			IWorkbench workbench = PlatformUI.getWorkbench();
			IBindingService bindingservice = workbench.getService(IBindingService.class);
			if (bindingservice != null) {
				TriggerSequence[] bindings = bindingservice.getActiveBindingsFor(BuildProjectCommandHandler.COMMAND_ID);
				if (bindings != null && bindings.length > 0) {
					return bindingToString(bindings[0]);
				}
			}
		}
		return null;
	}

	private static String bindingToString(TriggerSequence triggerSequence) {
		StringBuilder sb = new StringBuilder();
		for (Trigger t : triggerSequence.getTriggers()) {
			if (t instanceof KeyStroke) {
				KeyStroke ks = (KeyStroke) t;
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(ks.format());
			}
		}
		return sb.toString();
	}

	private static void addIDEConfigurationMenu(EclipseSakerIDEProject sakereclipseproject, Menu menu) {
		new Separator().fill(menu, -1);
		MenuManager ideViewMenu = new MenuManager("IDE Configuration");

		Collection<? extends IDEConfiguration> configurations = sakereclipseproject
				.getProjectIDEConfigurationCollection().getConfigurations();

		if (!configurations.isEmpty()) {
			IConfigurationElement[] ideconfigextensionconfigelements = Platform.getExtensionRegistry()
					.getConfigurationElementsFor(Activator.EXTENSION_POINT_ID_IDE_CONFIGURATION_PARSER);
			Map<String, Set<String>> idetypetypenames = new TreeMap<>();
			for (IConfigurationElement configelem : ideconfigextensionconfigelements) {
				String typename = configelem.getAttribute("type_name");
				if (ObjectUtils.isNullOrEmpty(typename)) {
					continue;
				}
				String type = configelem.getAttribute("type");
				if (ObjectUtils.isNullOrEmpty(type)) {
					continue;
				}
				idetypetypenames.computeIfAbsent(type, Functionals.treeSetComputer()).add(typename);
			}

			Map<String, MenuManager> idetypemenumanagers = new TreeMap<>();
			for (IDEConfiguration ideconfig : configurations) {
				String configtype = ideconfig.getType();
				if (ObjectUtils.isNullOrEmpty(configtype)) {
					continue;
				}
				String id = ideconfig.getIdentifier();
				if (ObjectUtils.isNullOrEmpty(id)) {
					continue;
				}
				Set<String> configtypenames = idetypetypenames.get(configtype);
				if (configtypenames == null) {
					configtypenames = Collections.singleton("<" + configtype + ">");
				}
				for (String typename : configtypenames) {
					MenuManager ideconfigmenumanager = idetypemenumanagers.get(typename);
					if (ideconfigmenumanager == null) {
						ideconfigmenumanager = new MenuManager(typename);
						idetypemenumanagers.put(typename, ideconfigmenumanager);
					}
					// @ is handled specially
					ideconfigmenumanager.add(new Action(id.contains("@") ? id + "\t" : id) {
						@Override
						public void run() {
							Shell menushell = menu.getShell();
							new Job("Applying IDE configuration - " + ideconfig.getIdentifier()) {
								@Override
								protected IStatus run(IProgressMonitor monitor) {
									sakereclipseproject.applyIDEConfiguration(menushell, ideconfig, monitor);
									return Status.OK_STATUS;
								}
							}.schedule();
						}
					});
				}
			}
			for (MenuManager mm : idetypemenumanagers.values()) {
				ideViewMenu.add(mm);
			}
		}

		if (ideViewMenu.isEmpty()) {
			//TODO different message when IDE configurations are turned off.
			Action dummy = new Action("Run a build to generate an IDE configuration") {
				@Override
				public void run() {
					sakereclipseproject.buildWithNewJob();
				}
			};
			ideViewMenu.add(dummy);
		}

		ideViewMenu.fill(menu, -1);
	}

	@Override
	public boolean isDynamic() {
		return true;
	}

	public static IProject findProject() {
		IWorkbench iworkbench = PlatformUI.getWorkbench();
		if (iworkbench == null)
			return null;
		IWorkbenchWindow iworkbenchwindow = iworkbench.getActiveWorkbenchWindow();
		if (iworkbenchwindow == null)
			return null;
		IWorkbenchPage iworkbenchpage = iworkbenchwindow.getActivePage();
		if (iworkbenchpage == null)
			return null;
		IWorkbenchPart activepart = iworkbenchpage.getActivePart();
		if (activepart instanceof IConsoleView) {
			IConsole console = ((IConsoleView) activepart).getConsole();
			if (console instanceof SakerProjectBuildConsole) {
				return ((SakerProjectBuildConsole) console).getProject().getProject();
			}
		}
		ISelection selection = iworkbenchpage.getSelection();
		IProject selectionres = getProjectFromSelection(selection);
		if (selectionres != null) {
			return selectionres;
		}
		IProject editorres = getProjectFromEditor(iworkbenchpage.getActiveEditor());
		if (editorres != null) {
			return editorres;
		}
		return null;
	}

	private static IProject getProjectFromEditor(IEditorPart editorpart) {
		if (editorpart == null) {
			return null;
		}
		IEditorInput input = editorpart.getEditorInput();
		if (input instanceof IFileEditorInput) {
			return ((IFileEditorInput) input).getFile().getProject();
		}
		return null;
	}

	private static IProject getProjectFromSelection(ISelection selection) {
		if (!(selection instanceof IStructuredSelection))
			return null;
		IStructuredSelection ss = (IStructuredSelection) selection;
		Object element = ss.getFirstElement();
		if (element instanceof IResource)
			return ((IResource) element).getProject();
		if (!(element instanceof IAdaptable))
			return null;
		IAdaptable adaptable = (IAdaptable) element;
		IResource adapter = adaptable.getAdapter(IResource.class);
		return adapter == null ? null : adapter.getProject();
	}
}
