package saker.build.ide.eclipse.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import saker.build.file.path.SakerPath;
import saker.build.ide.eclipse.EclipseSakerIDEProject;
import saker.build.ide.eclipse.PluginUtils;
import saker.build.ide.eclipse.properties.StringMapTableHandler;
import saker.build.ide.support.properties.ParameterizedBuildTargetIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.model.info.BuildTargetInformation;
import saker.build.scripting.model.info.BuildTargetParameterInformation;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class ParameterizedTargetsEditorDialog extends TitleAreaDialog {
	private EclipseSakerIDEProject sakerEclipseProject;
	private SakerPath buildFilePath;

	private String titleText;

	private Collection<? extends BuildTargetInformation> targets;

	private Table table;
	private List<ParameterizedBuildTargetIDEProperty> properties = new ArrayList<>();
	private TableViewer tableViewer;

	/**
	 * @param parentShell
	 * @param sakerEclipseProject
	 * @param buildFilePath
	 *            Project relative path or execution absolute path.
	 * @param targets
	 */
	public ParameterizedTargetsEditorDialog(Shell parentShell, EclipseSakerIDEProject sakerEclipseProject,
			SakerPath buildFilePath, Collection<? extends BuildTargetInformation> targets) {
		super(parentShell);
		this.sakerEclipseProject = sakerEclipseProject;
		this.buildFilePath = buildFilePath;
		this.targets = targets;
		setHelpAvailable(false);

		for (ParameterizedBuildTargetIDEProperty targetproperty : sakerEclipseProject.getIDEProjectProperties()
				.getParameterizedBuildTargets()) {
			SakerPath paramedexecpath = sakerEclipseProject
					.getParameterizedBuildTargetScriptExecutionPath(targetproperty);
			if (paramedexecpath == null) {
				continue;
			}

			if (buildFilePath.isAbsolute()) {
				if (!paramedexecpath.equals(buildFilePath)) {
					continue;
				}
			} else {
				if (!paramedexecpath.equals(sakerEclipseProject.projectPathToExecutionPath(buildFilePath))) {
					continue;
				}
			}
			properties.add(targetproperty);
		}
	}

	public void setTitleText(String titleText) {
		this.titleText = titleText;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(titleText);
	}

	@Override
	public void create() {
		super.create();
		setTitle(titleText);
	}

	@Override
	protected Control createDialogArea(Composite container) {
		Composite parent = (Composite) super.createDialogArea(container);

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(GridLayoutFactory.swtDefaults().create());
		composite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		PluginUtils.createLabelWithText(composite, "Edit parameterized build targets:");

		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		tableViewer.setLabelProvider(new ParameterizedTargetStyledLabelProvider());
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());
		tableViewer.setInput(properties);

		table = tableViewer.getTable();
		table.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		table.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.DEL) {
					TableItem[] sel = table.getSelection();
					if (sel.length == 0) {
						return;
					}
					for (TableItem selti : sel) {
						properties.remove(selti.getData());
					}
					tableViewer.refresh();
				}
			}
		});
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				TableItem[] sel = table.getSelection();
				if (sel.length != 1) {
					return;
				}
				ParameterizedBuildTargetIDEProperty editprop = (ParameterizedBuildTargetIDEProperty) sel[0].getData();

				ParameterizedTargetPropertyEditDialog dialog = new ParameterizedTargetPropertyEditDialog(getShell(),
						targets, editprop);
				int res = dialog.open();
				switch (res) {
					case ParameterizedTargetPropertyEditDialog.OK: {
						ParameterizedBuildTargetIDEProperty changedprop = dialog.getProperty();
						int idx = properties.indexOf(editprop);
						if (idx >= 0) {
							//update it
							properties.set(idx, changedprop);
							tableViewer.refresh();
						}
						break;
					}
					case ParameterizedTargetPropertyEditDialog.RETURN_CODE_DELETE: {
						properties.remove(editprop);
						tableViewer.refresh();
						break;
					}
				}
			}
		});

		Button addscriptconfigbutton = new Button(composite, SWT.PUSH);
		addscriptconfigbutton.setLayoutData(GridDataFactory.swtDefaults().create());
		addscriptconfigbutton.setText("Add parameterized target...");
		addscriptconfigbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e1 -> {
			ParameterizedTargetPropertyEditDialog dialog = new ParameterizedTargetPropertyEditDialog(getShell(),
					targets, buildFilePath);
			int res = dialog.open();
			if (res == ParameterizedTargetPropertyEditDialog.OK) {
				ParameterizedBuildTargetIDEProperty property = dialog.getProperty();
				//add it
				properties.add(property);
				tableViewer.refresh();
			}
		}));
		return parent;
	}

	@Override
	protected void okPressed() {
		try {
			sakerEclipseProject.setIDEProjectProperties(
					SimpleIDEProjectProperties.builder(sakerEclipseProject.getIDEProjectProperties())
							.setParameterizedBuildTargets(new LinkedHashSet<>(properties)).build());
		} catch (IOException e) {
			sakerEclipseProject.displayException(SakerLog.SEVERITY_ERROR, "Failed to save project properties.", e);
		}
		super.okPressed();
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected Point getInitialSize() {
		Point result = super.getInitialSize();
		return new Point(result.x, Math.max(Math.min(result.y, 800), result.x));
	}

	private class ParameterizedTargetPropertyEditDialog extends TitleAreaDialog {

		private static final int BUTTON_ID_DELETE = IDialogConstants.CLIENT_ID + 1;
		public static final int RETURN_CODE_DELETE = 2;

		private String uuid;
		private String scriptPath;
		private String targetName;

		private String displayName;

		private StringMapTableHandler parametersHandler = new StringMapTableHandler() {
			@Override
			protected IContentProposalProvider getKeyContentProposalProvider() {
				String targetname = targetNameText.getText();
				if (targetname == null) {
					return null;
				}

				ArrayList<BuildTargetParameterInformation> inparams = new ArrayList<>();
				for (BuildTargetInformation bti : targets) {
					if (!targetname.equals(bti.getTargetName())) {
						continue;
					}
					Collection<? extends BuildTargetParameterInformation> params = bti.getParameters();
					if (ObjectUtils.isNullOrEmpty(params)) {
						continue;
					}
					for (BuildTargetParameterInformation paraminfo : params) {
						String paramtype = paraminfo.getType();
						if (paramtype != null && !BuildTargetParameterInformation.TYPE_INPUT.equals(paramtype)) {
							continue;
						}
						//null param type => not known, add it to proposals
						// or input param type
						String paramname = paraminfo.getParameterName();
						if (paramname == null) {
							continue;
						}
						inparams.add(paraminfo);
					}
				}
				if (inparams.isEmpty()) {
					return null;
				}
				return new IContentProposalProvider() {
					@Override
					public IContentProposal[] getProposals(String contents, int position) {
						String prefix = contents.substring(0, position);
						ArrayList<ContentProposal> list = new ArrayList<>();
						for (BuildTargetParameterInformation paraminfo : inparams) {
							String paramname = paraminfo.getParameterName();
							if (StringUtils.startsWithIgnoreCase(paramname, prefix)) {
								list.add(new InformationHolderContentProposal(paramname, paraminfo));
							}
						}
						return list.toArray(new IContentProposal[list.size()]);
					}
				};
			}
		};

		private Text targetNameText;
		private Text displayNameText;
		private Collection<? extends BuildTargetInformation> targets;

		private ParameterizedTargetPropertyEditDialog(Shell parentShell,
				Collection<? extends BuildTargetInformation> targets) {
			super(parentShell);
			this.targets = targets;
			setHelpAvailable(false);

			this.parametersHandler.setAddButtonLabel("Add target parameter...");
			this.parametersHandler.setKeyValDialogTitle("Target parameter");
			this.parametersHandler.setKeyValDialogBaseMessage("Enter the value of an input build target parameter.");
			this.parametersHandler.setKeyColumnTitle("Parameter name");
			this.parametersHandler.setValueColumnTitle("Value");
		}

		public ParameterizedTargetPropertyEditDialog(Shell parentShell,
				Collection<? extends BuildTargetInformation> targets, ParameterizedBuildTargetIDEProperty property) {
			this(parentShell, targets);
			this.uuid = property.getUuid();
			if (this.uuid == null) {
				//the property is missing an UUID for some reason, probably because of configuration inconsistency
				//generate one for it
				this.uuid = UUID.randomUUID().toString();
			}
			this.scriptPath = property.getScriptPath();
			this.targetName = ObjectUtils.nullDefault(property.getTargetName(), "");
			this.displayName = ObjectUtils.nullDefault(property.getDisplayName(), "");
			this.parametersHandler.setEntries(property.getBuildTargetParameters());
		}

		public ParameterizedTargetPropertyEditDialog(Shell parentShell,
				Collection<? extends BuildTargetInformation> targets, SakerPath scriptpath) {
			this(parentShell, targets);
			this.scriptPath = Objects.toString(scriptpath, null);
			this.targetName = "";
			this.displayName = "";
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText("Edit parameterized target");
			newShell.setMinimumSize(500, 800);
		}

		@Override
		public void create() {
			super.create();
			setTitle("Edit parameterized target");
			setMessage("Configure the parameters for the desired build target.");
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			super.createButtonsForButtonBar(parent);
			if (uuid != null) {
				//editing one, add delete button
				createButton(parent, BUTTON_ID_DELETE, "Delete", false);
			}
		}

		@Override
		protected void buttonPressed(int buttonId) {
			if (buttonId == BUTTON_ID_DELETE) {
				setReturnCode(RETURN_CODE_DELETE);
				close();
				return;
			}
			super.buttonPressed(buttonId);
		}

		@Override
		protected void okPressed() {
			targetName = targetNameText.getText();
			displayName = displayNameText.getText();
			super.okPressed();
		}

		@Override
		protected Control createDialogArea(Composite container) {
			Composite parent = (Composite) super.createDialogArea(container);

			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(GridLayoutFactory.swtDefaults().create());
			composite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

			Composite textscomposite = new Composite(composite, SWT.NONE);
			textscomposite.setLayout(GridLayoutFactory.swtDefaults().numColumns(2).create());
			textscomposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

			PluginUtils.createLabelWithText(textscomposite, "Build target:");
			targetNameText = new Text(textscomposite, SWT.BORDER);
			targetNameText.setLayoutData(
					GridDataFactory.swtDefaults().align(GridData.FILL, GridData.CENTER).grab(true, false).create());
			targetNameText.setMessage("Build target name");
			targetNameText.setText(targetName);

			PluginUtils.createLabelWithText(textscomposite, "Display name:");
			displayNameText = new Text(textscomposite, SWT.BORDER);
			displayNameText.setLayoutData(
					GridDataFactory.swtDefaults().align(GridData.FILL, GridData.CENTER).grab(true, false).create());
			displayNameText.setMessage("Config display name");
			displayNameText.setText(displayName);

			if (!ObjectUtils.isNullOrEmpty(targets)) {
				ContentProposalAdapter adapter = new ContentProposalAdapter(targetNameText, new TextContentAdapter(),
						new IContentProposalProvider() {
							@Override
							public IContentProposal[] getProposals(String contents, int position) {
								String prefix = contents.substring(0, position);
								ArrayList<ContentProposal> list = new ArrayList<>();
								for (BuildTargetInformation targetinfo : targets) {
									String targetname = targetinfo.getTargetName();
									if (StringUtils.startsWithIgnoreCase(targetname, prefix)) {
										list.add(new InformationHolderContentProposal(targetname, targetinfo));
									}
								}
								return list.toArray(new IContentProposal[list.size()]);
							}
						}, StringMapTableHandler.KEY_STROKE_CODE_COMPLETION, null);
				adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
				Label assistInfoLabel = new Label(textscomposite, SWT.NONE);
				assistInfoLabel.setText("Content assist is available for target name. ("
						+ StringMapTableHandler.KEY_STROKE_CODE_COMPLETION.format() + ")");
				assistInfoLabel.setLayoutData(
						GridDataFactory.swtDefaults().span(2, 1).align(SWT.BEGINNING, SWT.CENTER).create());
			}

			parametersHandler.addControl(composite);
			((GridData) parametersHandler.getTable().getLayoutData()).horizontalSpan = 2;
			return parent;
		}

		public ParameterizedBuildTargetIDEProperty getProperty() {
			ParameterizedBuildTargetIDEProperty property = new ParameterizedBuildTargetIDEProperty(
					uuid == null ? UUID.randomUUID().toString() : uuid, scriptPath, targetName, displayName,
					parametersHandler.getEntriesMap());
			return property;
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

	}

	private static final class ParameterizedTargetStyledLabelProvider extends LabelProvider
			implements IStyledLabelProvider {
		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		@Override
		public StyledString getStyledText(Object element) {
			ParameterizedBuildTargetIDEProperty prop = (ParameterizedBuildTargetIDEProperty) element;
			String dispname = prop.getDisplayName();
			String targetname = prop.getTargetName();
			StyledString styledString = new StyledString();
			if (!ObjectUtils.isNullOrEmpty(dispname)) {
				styledString.append(dispname);
				styledString.append(" - @" + targetname, StyledString.QUALIFIER_STYLER);
			} else {
				styledString.append(targetname);
			}
			NavigableMap<String, String> params = prop.getBuildTargetParameters();
			if (params.isEmpty()) {
				styledString.append(" (no parameters)");
			} else if (params.size() == 1) {
				Entry<String, String> firstentry = params.firstEntry();
				styledString.append(" (" + firstentry.getKey() + " = " + firstentry.getValue() + ")");
			} else {
				styledString.append(" (" + StringUtils.toStringJoin(", ", params.keySet()) + ")");
			}
			return styledString;
		}
	}
}