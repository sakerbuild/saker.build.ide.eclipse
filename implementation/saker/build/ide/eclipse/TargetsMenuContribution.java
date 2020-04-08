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
import java.util.Collection;
import java.util.Collections;
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
import saker.build.ide.support.SakerIDEProject;
import saker.build.scripting.ScriptParsingFailedException;
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
			try {
				if (!project.isNatureEnabled(SakerBuildProjectNature.NATURE_ID)) {
					Action natureaction = new Action("Add saker.build nature") {
						@Override
						public void run() {
							try {
								SakerBuildProjectNature.addNature(project);
							} catch (CoreException e) {
								ImplActivator.getDefault().getEclipseIDEPlugin().displayException(e);
							}
						}
					};
					new ActionContributionItem(natureaction).fill(menu, -1);
					return;
				}
			} catch (CoreException e) {
				ImplActivator.getDefault().getEclipseIDEPlugin().displayException(e);
			}
		} else {
			addTargetsMenu(sakereclipseproject, menu);
			addIDEConfigurationMenu(sakereclipseproject, menu);
		}
	}

	private static void addTargetsMenu(EclipseSakerIDEProject sakereclipseproject, Menu menu) {
		NavigableSet<SakerPath> filepaths = sakereclipseproject.getTrackedScriptPaths();
		if (filepaths.isEmpty()) {
			Action action = new Action("Add new build file") {
				@Override
				public void run() {
					try {
						sakereclipseproject.addNewBuildFile(SakerIDEProject.DEFAULT_BUILD_FILE_NAME);
					} catch (Exception e) {
						sakereclipseproject.displayException(e);
					}
				}
			};
			new ActionContributionItem(action).fill(menu, -1);
		} else {
			SakerPath workingdirpath = sakereclipseproject.getWorkingDirectoryExecutionPath();
			for (SakerPath buildfilepath : filepaths) {
				SakerPath relativepath = buildfilepath;
				if (workingdirpath != null) {
					if (buildfilepath.startsWith(workingdirpath)) {
						relativepath = workingdirpath.relativize(relativepath);
					}
				}
				MenuManager targetmenu = new MenuManager(relativepath.toString(), null);
				targetmenu.setRemoveAllWhenShown(true);
				targetmenu.addMenuListener(new IMenuListener() {
					@Override
					public void menuAboutToShow(IMenuManager manager) {
						appendTargetsToBuildFileMenu(sakereclipseproject, buildfilepath, manager);
						appendOpenInEditorAction(sakereclipseproject, buildfilepath, manager);
					}

					private void appendOpenInEditorAction(EclipseSakerIDEProject sakereclipseproject,
							SakerPath buildfilepath, IMenuManager manager) {
						SakerPath fileprojectpath = sakereclipseproject
								.executionPathToProjectRelativePath(buildfilepath);
						if (fileprojectpath == null) {
							return;
						}
						manager.add(new Separator());
						manager.add(new Action("Open in editor") {
							@Override
							public void run() {
								try {
									IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
											sakereclipseproject.getProject().getFile(
													new Path(fileprojectpath.toString())),
											BuildFileEditor.ID, true);
								} catch (PartInitException e) {
									sakereclipseproject.displayException(e);
								}
							}
						});
					}

					private void appendTargetsToBuildFileMenu(EclipseSakerIDEProject sakereclipseproject,
							SakerPath buildfilepath, IMenuManager manager) {
						Set<String> scripttargets;
						try {
							scripttargets = sakereclipseproject.getScriptTargets(buildfilepath);
						} catch (ScriptParsingFailedException e) {
							SakerPath fileprojectpath = sakereclipseproject
									.executionPathToProjectRelativePath(buildfilepath);
							if (fileprojectpath != null) {
								manager.add(new Action("Failed to parse script file") {
									@Override
									public void run() {
										try {
											IDE.openEditor(
													PlatformUI.getWorkbench().getActiveWorkbenchWindow()
															.getActivePage(),
													sakereclipseproject.getProject()
															.getFile(new Path(fileprojectpath.toString())),
													BuildFileEditor.ID, true);
										} catch (PartInitException e2) {
											e2.addSuppressed(e);
											sakereclipseproject.displayException(e2);
										}
									}
								});
							} else {
								sakereclipseproject.displayException(e);
								BaseAction dummy = new BaseAction("Failed to parse script file");
								dummy.setEnabled(false);
								manager.add(dummy);
							}
							return;
						} catch (IOException e) {
							BaseAction dummy = new BaseAction("Failed to open script file");
							dummy.setEnabled(false);
							manager.add(dummy);
							return;
						}
						if (scripttargets == null) {
							BaseAction dummy = new BaseAction("Script is not part of the configuration");
							dummy.setEnabled(false);
							manager.add(dummy);
							return;
						}
						if (scripttargets.isEmpty()) {
							BaseAction dummy = new BaseAction("No targets found");
							dummy.setEnabled(false);
							manager.add(dummy);
							return;
						}
						for (String target : scripttargets) {
							String acc = getBuildTargetActionAccelerator(sakereclipseproject, buildfilepath, target);
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
					}

					private String getBuildTargetActionAccelerator(EclipseSakerIDEProject sakereclipseproject,
							SakerPath buildfilepath, String target) {
						if (sakereclipseproject.isLatestBuildScriptTargetEquals(buildfilepath, target)) {
							IWorkbench workbench = PlatformUI.getWorkbench();
							IBindingService bindingservice = workbench.getService(IBindingService.class);
							if (bindingservice != null) {
								TriggerSequence[] bindings = bindingservice
										.getActiveBindingsFor(BuildProjectCommandHandler.COMMAND_ID);
								if (bindings != null && bindings.length > 0) {
									return bindingToString(bindings[0]);
								}
							}
						}
						return null;
					}

				});
				targetmenu.fill(menu, -1);
			}
		}
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
