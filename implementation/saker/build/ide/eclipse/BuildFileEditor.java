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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.IInformationProviderExtension;
import org.eclipse.jface.text.information.IInformationProviderExtension2;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.eclipse.script.information.EclipseScriptInformationEntry;
import saker.build.ide.eclipse.script.information.EclipseScriptInformationRoot;
import saker.build.ide.eclipse.script.outline.EclipseScriptOutlineEntry;
import saker.build.ide.eclipse.script.outline.EclipseScriptOutlineRoot;
import saker.build.ide.eclipse.script.proposal.EclipseScriptProposalEntry;
import saker.build.ide.eclipse.script.proposal.EclipseScriptProposalRoot;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.ui.BaseScriptInformationRoot;
import saker.build.ide.support.ui.ScriptEditorModel;
import saker.build.ide.support.ui.ScriptEditorModel.ModelUpdateListener;
import saker.build.ide.support.ui.ScriptEditorModel.TokenState;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.scripting.ScriptParsingFailedException;
import saker.build.scripting.ScriptParsingOptions;
import saker.build.scripting.SimpleScriptParsingOptions;
import saker.build.scripting.model.CompletionProposalEdit;
import saker.build.scripting.model.CompletionProposalEditKind;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.InsertCompletionProposalEdit;
import saker.build.scripting.model.PartitionedTextContent;
import saker.build.scripting.model.ScriptCompletionProposal;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.scripting.model.ScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.ScriptStructureOutline;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.SimplePartitionedTextContent;
import saker.build.scripting.model.SimpleScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.SimpleTextPartition;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.scripting.model.TextRegionChange;
import saker.build.scripting.model.TokenStyle;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

public class BuildFileEditor extends AbstractDecoratedTextEditor implements ModelUpdateListener {
	public static final String ID = Activator.PLUGIN_ID + ".script.editor";

	//IWorkbenchThemeConstants.ACTIVE_TAB_BG_END
	private static final String BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME = "org.eclipse.ui.workbench.ACTIVE_TAB_BG_END";

	private static final int OUTLINE_JOB_INPUT_DELAY = 200;

	private static class OutlineElement {
		protected OutlineElement parent;
		protected EclipseScriptOutlineEntry element;

		public OutlineElement(OutlineElement parent, EclipseScriptOutlineEntry element) {
			this.parent = parent;
			this.element = element;
		}
	}

	public class OutlinePage extends ContentOutlinePage implements ITreeContentProvider {
		private int ignoreSelectionListener = 0;

		private class OutlineLabelProvider extends LabelProvider implements IStyledLabelProvider {

			@Override
			public StyledString getStyledText(Object element) {
				OutlineElement elem = (OutlineElement) element;
				EclipseScriptOutlineEntry entry = elem.element;
				StyledString widgetlabel = entry.getWidgetLabel();
				if (widgetlabel != null) {
					return widgetlabel;
				}

				StyledString result = new StyledString();
				String labelstr = entry.getLabel();
				if (labelstr != null) {
					result.append(labelstr);
				}
				String typestr = entry.getType();
				if (!ObjectUtils.isNullOrEmpty(typestr)) {
					result.append((ObjectUtils.isNullOrEmpty(labelstr) ? "" : " : ") + typestr,
							StyledString.DECORATIONS_STYLER);
				}
				return result;
			}

			@Override
			public String getText(Object element) {
				OutlineElement elem = (OutlineElement) element;
				EclipseScriptOutlineEntry entry = elem.element;
				StyledString widgetlabel = entry.getWidgetLabel();
				if (widgetlabel != null) {
					return widgetlabel.toString();
				}

				String label = entry.getLabel();
				String type = entry.getType();
				if (label != null) {
					if (type != null) {
						StringBuilder sb = new StringBuilder();
						sb.append(label);
						sb.append(" : ");
						sb.append(type);
						return sb.toString();
					}
					return label;
				}
				return type;
			}

			@Override
			public Image getImage(Object element) {
				OutlineElement elem = (OutlineElement) element;
				return elem.element.getWidgetImage();
			}
		}

		@Override
		public void createControl(Composite parent) {
			super.createControl(parent);
			TreeViewer viewer = getTreeViewer();
			viewer.setContentProvider(this);
			viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new OutlineLabelProvider()));
			viewer.getTree().addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (ignoreSelectionListener != 0) {
						return;
					}
					if (e.item instanceof TreeItem) {
						TreeItem treeitem = (TreeItem) e.item;
						OutlineElement source = (OutlineElement) treeitem.getData();
						if (source != null) {
							ignoreSelectionListener++;
							try {
								selectAndReveal(source.element.getEntry().getSelectionOffset(),
										source.element.getEntry().getSelectionLength());
							} finally {
								--ignoreSelectionListener;
							}
						}
					}
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
			viewer.setUseHashlookup(true);
			viewer.setComparer(new IElementComparer() {
				@Override
				public int hashCode(Object element) {
					OutlineElement elem = (OutlineElement) element;
					return elem.element.getEntry().hashCode();
				}

				@Override
				public boolean equals(Object a, Object b) {
					if (a == b) {
						return true;
					}
					if (a instanceof OutlineElement) {
						if (!(b instanceof OutlineElement)) {
							return false;
						}
					} else {
						if (b instanceof OutlineElement) {
							return false;
						}
						return Objects.equals(a, b);
					}
					OutlineElement ae = (OutlineElement) a;
					OutlineElement be = (OutlineElement) b;
					StructureOutlineEntry aentry = ae.element.getEntry();
					StructureOutlineEntry bentry = be.element.getEntry();
					if (aentry == bentry) {
						return true;
					}
					if (aentry.getOffset() != bentry.getOffset()) {
						return false;
					}
					if (aentry.getLength() != bentry.getLength()) {
						return false;
					}
					if (aentry.getSelectionOffset() != bentry.getSelectionOffset()) {
						return false;
					}
					if (aentry.getSelectionLength() != bentry.getSelectionLength()) {
						return false;
					}
					if (!Objects.equals(aentry.getLabel(), bentry.getLabel())) {
						return false;
					}
					if (!Objects.equals(aentry.getType(), bentry.getType())) {
						return false;
					}
					return true;
				}
			});
			viewer.setInput("dummy");
			outline = this;
			rescheduleOutlineUpdate();
		}

		@Override
		public void dispose() {
			outline = null;
			super.dispose();
		}

		@Override
		public Object[] getElements(Object inputElement) {
			EclipseScriptOutlineRoot root;
			try {
				ScriptSyntaxModel m = editorModel.getModelMaybeOutOfDate();
				if (m == null) {
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
				ScriptStructureOutline structureoutline = m.getStructureOutline();
				if (structureoutline == null) {
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
				root = EclipseScriptOutlineRoot.create(structureoutline);
			} catch (Exception e) {
				ImplActivator.getDefault().displayException(SakerLog.SEVERITY_WARNING,
						"Failed to process script outline.", e);
				return ObjectUtils.EMPTY_OBJECT_ARRAY;
			}
			IScriptOutlineDesigner designer = getScriptOutlineDesigner(root);
			if (designer != null) {
				designer.process(root);
			}
			List<? extends EclipseScriptOutlineEntry> outlineroots = root.getRootEntries();
			OutlineElement[] result = new OutlineElement[outlineroots.size()];
			int i = 0;
			for (EclipseScriptOutlineEntry tree : outlineroots) {
				result[i++] = new OutlineElement(null, tree);
			}
			return result;
		}

		private IScriptOutlineDesigner getScriptOutlineDesigner(EclipseScriptOutlineRoot outlineroot) {
			if (outlineroot == null) {
				return null;
			}
			String schemaid = outlineroot.getSchemaIdentifier();
			if (ObjectUtils.isNullOrEmpty(schemaid)) {
				return null;
			}
			return ImplActivator.getDefault().getEclipseIDEPlugin()
					.getScriptOutlineDesignerForSchemaIdentifier(schemaid);
		}

		@Override
		public Object[] getChildren(Object parentElement) {
			OutlineElement elem = (OutlineElement) parentElement;

			EclipseScriptOutlineEntry tree = elem.element;
			List<? extends EclipseScriptOutlineEntry> children = tree.getChildren();
			OutlineElement[] result = new OutlineElement[children.size()];
			int i = 0;
			for (EclipseScriptOutlineEntry child : children) {
				result[i++] = new OutlineElement(elem, child);
			}
			return result;
		}

		@Override
		public Object getParent(Object element) {
			OutlineElement elem = (OutlineElement) element;
			return elem.parent;
		}

		@Override
		public boolean hasChildren(Object element) {
			OutlineElement elem = (OutlineElement) element;
			EclipseScriptOutlineEntry tree = elem.element;
			return !tree.getChildren().isEmpty();
		}

		protected void updateContent() {
			try {
				ignoreSelectionListener++;

				TreeViewer treeviewer = getTreeViewer();
				treeviewer.refresh();
				updateSelectionImpl(treeviewer);
			} finally {
				ignoreSelectionListener--;
			}
		}

		private void updateSelectionImpl(TreeViewer treeviewer) {
			TreeItem[] items = treeviewer.getTree().getItems();
			OutlineElement[] elems = new OutlineElement[items.length];
			for (int i = 0; i < elems.length; i++) {
				elems[i] = (OutlineElement) items[i].getData();
			}

			ISelection sel = BuildFileEditor.this.doGetSelection();
			if (sel instanceof ITextSelection) {
				ITextSelection textselection = (ITextSelection) sel;
				int offset = textselection.getOffset();
				int length = textselection.getLength();
				OutlineElement found = findSelectionOutlineElement(elems, offset, length);
				if (found != null) {
					treeviewer.setSelection(new StructuredSelection(found));
					treeviewer.reveal(found);
				}
			}
		}

		protected void updateSelection() {
			try {
				ignoreSelectionListener++;

				TreeViewer treeviewer = getTreeViewer();
				updateSelectionImpl(treeviewer);
			} finally {
				ignoreSelectionListener--;
			}
		}

		private OutlineElement findSelectionOutlineElement(OutlineElement[] roots, int offset, int length) {
			for (OutlineElement elem : roots) {
				EclipseScriptOutlineEntry t = elem.element;
				StructureOutlineEntry e = t.getEntry();
				if (offset >= e.getOffset() && offset < e.getOffset() + e.getLength()) {
					//has overlapping region
					OutlineElement thiselem = new OutlineElement(elem, t);
					OutlineElement sub = findSelectionOutlineElement(t.getChildren(), offset, length, thiselem);
					if (sub != null) {
						return sub;
					}
					return thiselem;
				}
			}
			return null;
		}

		private OutlineElement findSelectionOutlineElement(List<? extends EclipseScriptOutlineEntry> roots, int offset,
				int length, OutlineElement parent) {
			for (EclipseScriptOutlineEntry t : roots) {
				StructureOutlineEntry e = t.getEntry();
				if (offset >= e.getOffset() && offset < e.getOffset() + e.getLength()) {
					//has overlapping region
					OutlineElement thiselem = new OutlineElement(parent, t);
					OutlineElement sub = findSelectionOutlineElement(t.getChildren(), offset, length, thiselem);
					if (sub != null) {
						return sub;
					}
					return thiselem;
				}
			}
			return null;
		}
	}

	private boolean findSelectionTreeItem(List<? extends StructureOutlineEntry> roots, int offset, int length,
			List<StructureOutlineEntry> result) {
		boolean added = false;
		for (StructureOutlineEntry t : roots) {
			if (offset >= t.getOffset() && offset < t.getOffset() + t.getLength()) {
				//has overlapping region
				result.add(t);
				findSelectionTreeItem(t.getChildren(), offset, length, result);
				added = true;
			}
		}
		return added;
	}

	private List<StructureOutlineEntry> findSelectionTreeItem(List<? extends StructureOutlineEntry> roots, int offset,
			int length) {
		List<StructureOutlineEntry> result = new ArrayList<>();
		findSelectionTreeItem(roots, offset, length, result);
		return result;
	}

	private static final class HoverInformationControl extends AbstractInformationControl
			implements IInformationControlExtension2 {

		private Browser browser;

		private final Object browserTextSetLock = new Object();
		private Object loadingInput;

		private HoverInformationControl(Shell parentShell) {
			super(parentShell, EditorsUI.getTooltipAffordanceString());
			create();
		}

		private HoverInformationControl(Shell parentShell, ToolBarManager toolbarmanager) {
			super(parentShell, toolbarmanager);
			create();
		}

		@Override
		public IInformationControlCreator getInformationPresenterControlCreator() {
			return new RichInformationControlCreator();
		}

		@Override
		public boolean hasContents() {
			//if we return a dynamic value based on if we have contents or not
			//then if we don't have contents, the hover dialog will be sized enormously the next time it is presented
			//this has something to do with size computations and stuff. whatever.
			//if we don't have documentational entries, show an information about no doc available
			return true;
		}

		@Override
		protected void createContent(Composite parent) {
			Display display = getShell().getDisplay();
			Color bgcol = display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
			Color foregroundcolor = display.getSystemColor(SWT.COLOR_INFO_FOREGROUND);

			browser = new Browser(parent, SWT.NONE);
			browser.setJavascriptEnabled(false);
			browser.addLocationListener(new LocationListener() {
				@Override
				public void changing(LocationEvent event) {
//					System.out.println("BuildFileEditor.HoverInformationControl.createContent(...).new LocationListener() {...}.changing() " + event);
					String loc = event.location;

					if ("about:blank".equals(loc)) { //$NON-NLS-1$
						/*
						 * Using the Browser.setText API triggers a location change to "about:blank".
						 * bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=130314
						 */
						//input set with setText
						return;
					}
					event.doit = false;
				}

				@Override
				public void changed(LocationEvent event) {
//					System.out.println("BuildFileEditor.HoverInformationControl.createContent(...).new LocationListener() {...}.changed() " + event);
				}
			});

			browser.setForeground(foregroundcolor);

			browser.setBackground(bgcol);
			browser.setFont(display.getSystemFont());
		}

		@Override
		public void setInput(Object input) {
			Display display = browser.getDisplay();
			Color bgcol = display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
			String loadinghtml = getLoadingHtml(bgcol);

			synchronized (browserTextSetLock) {
				loadingInput = input;
				Thread loadert = ThreadUtils.startDaemonThread(() -> {
					String htmlcontent = generateInformationHtml(input, bgcol);
					display.asyncExec(() -> {
						synchronized (browserTextSetLock) {
							if (loadingInput == input) {
								if (browser.isDisposed()) {
									return;
								}
								browser.setText(htmlcontent);
							}
						}
					});
				});
				//if we can finish the loading in a reasonable time window then don't set the loading html
				try {
					loadert.join(20);
					if (!loadert.isAlive()) {
						return;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				browser.setText(loadinghtml);
			}

		}

		private static String generateInformationHtml(Object input, Color bgcol) {
			List<EclipseScriptInformationEntry> entries;
			if (input instanceof PartitionedTextContent) {
				input = EclipseScriptInformationRoot.create((PartitionedTextContent) input);
			} else if (input instanceof ScriptTokenInformation) {
				EclipseScriptInformationRoot inforoot = EclipseScriptInformationRoot
						.create((ScriptTokenInformation) input);
				IScriptInformationDesigner designer = ImplActivator.getDefault().getEclipseIDEPlugin()
						.getScriptInformationDesignerForSchemaIdentifier(inforoot.getSchemaIdentifier());
				if (designer != null) {
					designer.process(inforoot);
				}

				input = inforoot;
			}
			if (input instanceof EclipseScriptInformationRoot) {
				entries = ((EclipseScriptInformationRoot) input).getEntries();
			} else if (input instanceof EclipseScriptProposalEntry) {
				entries = new ArrayList<>();
				List<? extends EclipseScriptInformationEntry> infoentries = EclipseScriptInformationRoot
						.getFilteredBuildScriptScriptInformationEntries(
								((EclipseScriptProposalEntry) input).getInformationEntries());
				if (infoentries != null) {
					for (EclipseScriptInformationEntry entry : infoentries) {
						entries.add(entry);
					}
				}
			} else {
				System.err.println("BuildFileEditor.HoverInformationControl.setInput() NOT valid input" + input + " - "
						+ ObjectUtils.classOf(input));
				StringBuilder sb = new StringBuilder();
				appendHtmlHeader(sb, bgcol);
				sb.append(
						"<em>No information available. Unrecognized input: (" + ObjectUtils.classOf(input) + ")</em>");
				appendHtmlFooter(sb);
				return sb.toString();
			}

			return generateInformationHtml(entries, bgcol);
		}

		private static String getLoadingHtml(Color bgcol) {
			StringBuilder sb = new StringBuilder();
			appendHtmlHeader(sb, bgcol);
			sb.append("<em>Loading...</em>");
			appendHtmlFooter(sb);
			return sb.toString();
		}

		private static String generateInformationHtml(List<? extends EclipseScriptInformationEntry> entries,
				Color bgcol) {
			Set<String> sections = new LinkedHashSet<>();

			StringBuilder sectionsb = new StringBuilder();

			entries = BaseScriptInformationRoot.getFilteredBuildScriptScriptInformationEntries(entries);

			for (EclipseScriptInformationEntry entry : entries) {
				sectionsb.setLength(0);
				String title = entry.getTitle();
				String subtitle = entry.getSubTitle();
				String iconsrc = entry.getIconSource();

				appendSectionHeader(sectionsb, SakerIDESupportUtils.resolveInformationTitle(title, subtitle),
						SakerIDESupportUtils.resolveInformationSubTitle(title, subtitle), iconsrc);

				FormattedTextContent formattedinput = entry.getContent();
				appendFormatted(sectionsb, formattedinput);
				if (sectionsb.length() > 0) {
					sections.add(sectionsb.toString());
				}
			}
			StringBuilder contentsb = new StringBuilder();
			appendHtmlHeader(contentsb, bgcol);
			if (sections.isEmpty()) {
				contentsb.append("<em>No information available.</em>");
			} else {
				for (Iterator<String> it = sections.iterator(); it.hasNext();) {
					String section = it.next();
					contentsb.append(section);
					if (it.hasNext()) {
						contentsb.append("<hr>");
					}
				}
			}
			appendHtmlFooter(contentsb);
			return contentsb.toString();
		}

		private static void appendSectionHeader(StringBuilder sectionsb, String title, String subtitle,
				String iconimgsrc) {
			if (ObjectUtils.isNullOrEmpty(title)) {
				return;
			}
			sectionsb.append("<div class=\"ptitle\">");
			if (!ObjectUtils.isNullOrEmpty(iconimgsrc)) {
				sectionsb.append("<img src=\"");
				sectionsb.append(iconimgsrc);
				sectionsb.append("\">");
			}
			sectionsb.append(escapeHtml(title));
			sectionsb.append("</div>");
			if (!ObjectUtils.isNullOrEmpty(subtitle)) {
				sectionsb.append("<div class=\"psubtitle\">");
				sectionsb.append(escapeHtml(subtitle));
				sectionsb.append("</div>");
			}
		}

		private static void appendFormatted(StringBuilder sectionsb, FormattedTextContent formattedinput) {
			if (formattedinput == null) {
				return;
			}
			//white-space: pre-line doesn't work in intellij, so we replace \n with <br> in cas of plaintext formats
			Set<String> formats = formattedinput.getAvailableFormats();
			if (formats.contains(FormattedTextContent.FORMAT_HTML)) {
				String formattedtext = formattedinput.getFormattedText(FormattedTextContent.FORMAT_HTML);
				if (!ObjectUtils.isNullOrEmpty(formattedtext)) {
					//should not be null, but just in case of client error
					sectionsb.append("<div class=\"pcontent\">");
					sectionsb.append(formattedtext);
					sectionsb.append("</div>");
					return;
				}
			}
			if (formats.contains(FormattedTextContent.FORMAT_PLAINTEXT)) {
				String formattedtext = formattedinput.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
				if (formattedtext != null) {
					//should not be null, but just in case of client error
					sectionsb.append("<div class=\"pcontent\">");
					sectionsb.append(escapeHtml(formattedtext).replace("\n", "<br>"));
					sectionsb.append("</div>");
					return;
				}
			}
			for (String f : formats) {
				String formattedtext = formattedinput.getFormattedText(f);
				if (ObjectUtils.isNullOrEmpty(formattedtext)) {
					continue;
				}
				//should not be null, but just in case of client error
				sectionsb.append("<div class=\"pcontent\">");
				sectionsb.append(escapeHtml(formattedtext).replace("\n", "<br>"));
				sectionsb.append("</div>");
				return;
			}
		}

		private static void appendHtmlHeader(StringBuilder contentsb, Color bgcol) {
			contentsb.append("<!DOCTYPE html><html><head><style>\r\n");
			contentsb.append(
					"html { font-family: 'Segoe UI',sans-serif; font-size: 9pt; font-style: normal; font-weight: normal; }\r\n");
			contentsb.append(
					"body, h1, h2, h3, h4, h5, h6, p, table, td, caption, th, ul, ol, dl, li, dd, dt { font-size: 1em; }\r\n");
			contentsb.append(".ptitle { font-weight: bold; }\r\n");
			contentsb.append(
					".ptitle>img { display: inline-block; height: 1.3em; width: auto; margin-right: 0.2em; vertical-align: text-bottom; }\r\n");
			contentsb.append(".psubtitle { font-weight: normal; font-style: italic; margin-left: 6px; }\r\n");
			contentsb.append(".pcontent { margin-top: 5px; margin-left: 6px; }\r\n");
			contentsb.append("hr { opacity: 0.5; }\r\n");
			contentsb.append("pre { font-family: monospace; }</style></head><body");
			if (bgcol != null) {
				contentsb.append(" style=\"background-color: #");
				appendColorRGB(contentsb, bgcol);
				contentsb.append(";\"");
			}
			contentsb.append(">");
		}

		private static void appendColorRGB(StringBuilder contentsb, Color bgcol) {
			contentsb.append(String.format("%02x%02x%02x", bgcol.getRed(), bgcol.getGreen(), bgcol.getBlue()));
		}

		private static void appendHtmlFooter(StringBuilder contentsb) {
			contentsb.append("</body></html>");
		}

		private static String escapeHtml(String text) {
			return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}

		@Override
		public void setFocus() {
			// TODO Auto-generated method stub
			super.setFocus();
		}

		@Override
		public boolean restoresSize() {
			//TODO handle this somehow?
			return true;
		}
	}

	private static final class HoverInformationControlCreator implements IInformationControlCreator {
		@Override
		public IInformationControl createInformationControl(Shell shell) {
			return new HoverInformationControl(shell);
		}
	}

	private static final class RichInformationControlCreator implements IInformationControlCreator {
		@Override
		public IInformationControl createInformationControl(Shell shell) {
			return new HoverInformationControl(shell, new ToolBarManager());
		}
	}

	private class BuildFileTextHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {
		@Override
		public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
			ScriptSyntaxModel m = getUpdatedModel();
			if (m == null) {
				return null;
			}
			int hoveroffset = hoverRegion.getOffset();
			int hoverlen = hoverRegion.getLength();
			ScriptTokenInformation tokeninfo = editorModel.getTokenInformationAtPosition(hoveroffset, hoverlen);
			if (tokeninfo != null) {
				return tokeninfo;
			}
			//empty partition to display no information
			return new SimplePartitionedTextContent(new SimpleTextPartition(null, null, null));
		}

		@Override
		public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
			return new Region(offset, 0);
//			if (model == null) {
//				return null;
//			}
//			ScriptToken token = getTokenForOffset(offset);
//			if (token == null) {
//				return null;
//			}
//			return new Region(token.getOffset(), token.getLength());
		}

		@Override
		@Deprecated
		public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
			return null;
		}

		@Override
		public IInformationControlCreator getHoverControlCreator() {
			return new HoverInformationControlCreator();
		}
	}

	private static final class BuildFileReconcilingStrategy implements IReconcilingStrategy {
		private IDocument document;

		@Override
		public void setDocument(IDocument document) {
			this.document = document;
		}

		@Override
		public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		}

		@Override
		public void reconcile(IRegion partition) {
		}
	}

	private static final class BuildFileReconciler implements IReconciler {
		private ITextViewer textViewer;

		@Override
		public void uninstall() {
			this.textViewer = null;
		}

		@Override
		public void install(ITextViewer textViewer) {
			this.textViewer = textViewer;
		}

		@Override
		public IReconcilingStrategy getReconcilingStrategy(String contentType) {
			return new BuildFileReconcilingStrategy();
		}
	}

	public class Configuration extends SourceViewerConfiguration {

		private class BuildFilePresentationReconciler implements IPresentationReconciler, ITextListener {
			private class BuildFilePresentationRepairer implements IPresentationRepairer {
				private IDocument document;

				@Override
				public void createPresentation(TextPresentation presentation, ITypedRegion damage) {
					Configuration.this.createPresentation(presentation, damage);
				}

				@Override
				public void setDocument(IDocument document) {
					this.document = document;
				}
			}

			private class BuildFilePresentationDamager implements IPresentationDamager {
				private IDocument document;

				@Override
				public IRegion getDamageRegion(ITypedRegion partition, DocumentEvent event,
						boolean documentPartitioningChanged) {
					return new Region(0, event.getDocument().getLength());
				}

				@Override
				public void setDocument(IDocument document) {
					this.document = document;
				}
			}

			private ITextViewer textViewer;
			private BuildFilePresentationRepairer presentationRepairer = new BuildFilePresentationRepairer();
			private BuildFilePresentationDamager presentationDamager = new BuildFilePresentationDamager();

			public BuildFilePresentationReconciler() {
			}

			@Override
			public void install(ITextViewer viewer) {
				this.textViewer = viewer;
				viewer.invalidateTextPresentation();
				this.textViewer.addTextListener(this);
			}

			@Override
			public void uninstall() {
				this.textViewer.removeTextListener(this);
				this.textViewer = null;
			}

			@Override
			public IPresentationDamager getDamager(String contentType) {
				return presentationDamager;
			}

			@Override
			public IPresentationRepairer getRepairer(String contentType) {
				return presentationRepairer;
			}

			@Override
			public void textChanged(TextEvent event) {
				TextRegionChange dataevent = new TextRegionChange(event.getOffset(), event.getLength(),
						event.getText());
				editorModel.textChange(dataevent);

				System.out.println("BuildFileEditor.textChanged() " + dataevent.getOffset() + " ("
						+ dataevent.getLength() + ") -> " + StringUtils.length(dataevent.getText())
//								+ " " + dataevent.replacedText + " -> " + dataevent.text
				);
			}
		}

		private class BuildFileInformationProvider
				implements IInformationProvider, IInformationProviderExtension, IInformationProviderExtension2 {
			@Override
			public IRegion getSubject(ITextViewer textViewer, int offset) {
				return textHover.getHoverRegion(textViewer, offset);
			}

			@Override
			@Deprecated
			public String getInformation(ITextViewer textViewer, IRegion subject) {
				throw new UnsupportedOperationException();
			}

			@Override
			public IInformationControlCreator getInformationPresenterControlCreator() {
				return new RichInformationControlCreator();
			}

			@Override
			public Object getInformation2(ITextViewer textViewer, IRegion region) {
				return textHover.getHoverInfo2(textViewer, region);
			}
		}

		private InformationPresenter informationPresenter;

		private BuildFileContentAssistant contentAssistant;
		private BuildFileTextHover textHover = new BuildFileTextHover();

//		private final ChangeEventUpdaterJob changeEventProcessorJob = new ChangeEventUpdaterJob(this);

		public Configuration() {
		}

		@Override
		public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
			return new RichInformationControlCreator();
		}

		public void createPresentation(TextPresentation presentation, ITypedRegion damage) {
			List<TokenState> tokenstates = editorModel.getCurrentTokenState();
			if (ObjectUtils.isNullOrEmpty(tokenstates)) {
				return;
			}
			for (TokenState token : tokenstates) {
				Color fg = null;
				Color bg = null;

				TokenStyle style = token.getStyle();
				int bgc = TokenStyle.COLOR_UNSPECIFIED;
				int fgc = TokenStyle.COLOR_UNSPECIFIED;
				int s = 0;
				if (style != null) {
					fgc = style.getForegroundColor();
					bgc = style.getBackgroundColor();
					s = style.getStyle();
					if (fgc != TokenStyle.COLOR_UNSPECIFIED) {
						fg = getColor(fgc);
					}
				}
				StyleRange range = new StyleRange(token.getOffset(), token.getLength(), fg, bg);
				if (bgc != TokenStyle.COLOR_UNSPECIFIED) {
					bg = getColor(bgc);
				}
				if (((s & TokenStyle.STYLE_ITALIC) == TokenStyle.STYLE_ITALIC)) {
					range.fontStyle |= SWT.ITALIC;
				}
				if (((s & TokenStyle.STYLE_BOLD) == TokenStyle.STYLE_BOLD)) {
					range.fontStyle |= SWT.BOLD;
				}
				if (((s & TokenStyle.STYLE_UNDERLINE) == TokenStyle.STYLE_UNDERLINE)) {
					range.underline = true;
				}
				if (((s & TokenStyle.STYLE_STRIKETHROUGH) == TokenStyle.STYLE_STRIKETHROUGH)) {
					range.strikeout = true;
				}
				presentation.addStyleRange(range);
			}
		}

		@Override
		public IInformationPresenter getInformationPresenter(ISourceViewer sourceViewer) {
			if (informationPresenter == null) {
				informationPresenter = new InformationPresenter(new RichInformationControlCreator());
				informationPresenter.setRestoreInformationControlBounds(Activator.getDefault().getHoverDialogSettings(),
						false, true);
				informationPresenter.setInformationProvider(new BuildFileInformationProvider(),
						IDocument.DEFAULT_CONTENT_TYPE);
			}
			return informationPresenter;
		}

		@Override
		public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
			return new BuildFilePresentationReconciler();
		}

		@Override
		public IReconciler getReconciler(ISourceViewer sourceViewer) {
			return new BuildFileReconciler();
		}

		@Override
		public IContentAssistant getContentAssistant(ISourceViewer sv) {
			contentAssistant = new BuildFileContentAssistant();
			return contentAssistant;
		}

		@Override
		public ITextHover getTextHover(ISourceViewer sv, String contentType) {
			return textHover;
		}

		private Color makeColor(int c) {
			return new Color(Display.getDefault(), (c & 0x00FF0000) >>> 16, (c & 0x0000FF00) >>> 8, (c & 0x000000FF),
					(c & 0xFF000000) >>> 24);
		}

		private Color getColor(int c) {
			return typecolors.computeIfAbsent(c, this::makeColor);
		}

		@Override
		public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
			IAutoEditStrategy autoeditstrategy = new IAutoEditStrategy() {
				@Override
				public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
					// TODO Auto-generated method stub
//					if ("(".equals(command.text)) {
//						command.text += ")";
//						command.caretOffset = command.offset + 1;
//						command.shiftsCaret = false;
//					}

//					System.out.println("BuildFileEditor.Configuration.getAutoEditStrategies(...).new IAutoEditStrategy() {...}.customizeDocumentCommand() "
//							+ command.offset + " - " + command.caretOffset + " - " + command.text + " - do: " + command.doit + " - shift: "
//							+ command.shiftsCaret);
				}
			};
			return new IAutoEditStrategy[] { new DefaultIndentLineAutoEditStrategy(), autoeditstrategy };
		}
	}

	private interface ScriptProposalHolder {
		public ScriptCompletionProposal getProposal();
	}

	private class InsertOnlyCompletionProposal implements ScriptProposalHolder, ICompletionProposal,
			ICompletionProposalExtension3, ICompletionProposalExtension5, ICompletionProposalExtension6 {
		private EclipseScriptProposalEntry proposal;
		private List<? extends CompletionProposalEdit> changes;

		public InsertOnlyCompletionProposal(EclipseScriptProposalEntry proposal,
				List<? extends CompletionProposalEdit> changes) {
			this.proposal = proposal;
			this.changes = changes;
		}

		@Override
		public ScriptCompletionProposal getProposal() {
			return proposal.getProposal();
		}

		@Override
		public void apply(IDocument document) {
			try {
				int count = changes.size();
				if (count == 1) {
					InsertCompletionProposalEdit c = (InsertCompletionProposalEdit) changes.get(0);
					document.replace(c.getOffset(), c.getLength(), c.getText());
				} else {
					List<CompletionProposalEdit> modchanges = new ArrayList<>(changes);
					//TODO test this
					for (int i = 0; i < count; i++) {
						InsertCompletionProposalEdit c = (InsertCompletionProposalEdit) modchanges.get(i);
						int coffset = c.getOffset();
						document.replace(coffset, c.getLength(), c.getText());
						for (int j = i + 1; j < count; j++) {
							InsertCompletionProposalEdit c2 = (InsertCompletionProposalEdit) modchanges.get(j);
							if (c2.getOffset() > coffset) {
								TextRegionChange nregion = new TextRegionChange(
										c2.getOffset() + StringUtils.length(c.getText()) - c.getLength(),
										c2.getLength(), c2.getText());
								modchanges.set(j, new InsertCompletionProposalEdit(nregion));
							}
						}
					}
				}
			} catch (BadLocationException e) {
				//shouldnt occurr
				e.printStackTrace();
			}
		}

		@Override
		public Point getSelection(IDocument document) {
			return new Point(getProposal().getSelectionOffset(), 0);
		}

		@Override
		public String getAdditionalProposalInfo() {
			//TODO additional proposal info
			return "TODO ADDITIONAL PROPOSAL INFO string";
		}

		@Override
		public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
			return proposal;
		}

		@Override
		public IInformationControlCreator getInformationControlCreator() {
			return new RichInformationControlCreator();
		}

		@Override
		public int getPrefixCompletionStart(IDocument document, int completionOffset) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getDisplayString() {
			return getStyledDisplayString().getString();
		}

		@Override
		public Image getImage() {
			return proposal.getProposalImage();
		}

		@Override
		public IContextInformation getContextInformation() {
			// TODO Auto-generated method stub
//			return new ContextInformation("hello", "there");
			return null;
		}

		@Override
		public StyledString getStyledDisplayString() {
			StyledString result = new StyledString();
			String labelstr = proposal.getDisplayString();
			if (labelstr != null) {
				result.append(labelstr);
			}
			String typestr = proposal.getDisplayType();
			if (!ObjectUtils.isNullOrEmpty(typestr)) {
				result.append(" : " + typestr, StyledString.DECORATIONS_STYLER);
			}
			String relationstr = proposal.getDisplayRelation();
			if (!ObjectUtils.isNullOrEmpty(relationstr)) {
				result.append(" - " + relationstr, StyledString.QUALIFIER_STYLER);
			}
			return result;
		}

	}

	private ICompletionProposal createProposal(EclipseScriptProposalEntry proposalentry) {
		ScriptCompletionProposal proposal = proposalentry.getProposal();
		List<? extends CompletionProposalEdit> changes = proposal.getTextChanges();
		if (changes.isEmpty()) {
			return null;
		}
		for (CompletionProposalEdit c : changes) {
			String ckind = c.getKind();
			if (CompletionProposalEditKind.INSERT.equalsIgnoreCase(ckind)) {
				continue;
			}
			//XXX support non insert proposals
			return null;
		}
		int count = changes.size();
		for (int i = 0; i < count; i++) {
			CompletionProposalEdit c = changes.get(i);
			for (int j = i + 1; j < count; j++) {
				CompletionProposalEdit c2 = changes.get(j);
				if (CompletionProposalEdit.overlaps(c, c2)) {
					System.err.println("Overlaps: " + c + " and " + c2);
					//XXX display info?
					//invalid proposal
					return null;
				}
			}
		}
		return new InsertOnlyCompletionProposal(proposalentry, changes);
	}

	private final class BuildFileContentAssistantProcessor implements IContentAssistProcessor {
		@Override
		public String getErrorMessage() {
			// TODO Auto-generated method stub
			return "No proposals available.";
		}

		@Override
		public IContextInformationValidator getContextInformationValidator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public char[] getContextInformationAutoActivationCharacters() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public char[] getCompletionProposalAutoActivationCharacters() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
			System.out.println("BuildFileEditor.BuildFileContentAssistantProcessor.computeCompletionProposals()");
//			synchronized (updateLock) {
////				System.out.println("BuildFileEditor.BuildFileContentAssistantProcessor.computeCompletionProposals() SIZE: " + unprocessedChangeEvents.size());
//				if (!unprocessedChangeEvents.isEmpty()) {
//					//hurry up, reschedule to start
//					reschedule(modelUpdaterRunnable);
//					return new ICompletionProposal[] { new InfoCompletionsProposal("Computing proposals...") };
//				}
//			}

//			String contexttypeid = "contexttypeid";
//			String pattern = "pattern ${var1}  ${var2} ${var1}";
//			Template template = new Template("name", "description", contexttypeid, pattern, false);
//			TemplateContext context = new DocumentTemplateContext(new TemplateContextType(contexttypeid), viewer.getDocument(), offset, 0);
//			return new ICompletionProposal[] { new TemplateProposal(template, context, new Region(0, 0), null) };

			long nanos = System.nanoTime();
			ScriptSyntaxModel model = getUpdatedModel();
			if (model == null) {
				return null;
			}
			List<? extends ScriptCompletionProposal> proposals = model.getCompletionProposals(offset);
			if (ObjectUtils.isNullOrEmpty(proposals)) {
				return new ICompletionProposal[] {};
			}
			ICompletionProposal[] result = createEclipseCompletionProposals(proposals);
			System.out.println(
					"BuildFileEditor.BuildFileContentAssistant.BuildFileContentAssistant().new IContentAssistProcessor() {...}.computeCompletionProposals() "
							+ (System.nanoTime() - nanos) / 1_000_000 + " ms");
			return result;
		}
	}

	private ICompletionProposal[] createEclipseCompletionProposals(List<? extends ScriptCompletionProposal> proposals) {
		EclipseScriptProposalRoot proposalroot = EclipseScriptProposalRoot.create(proposals);
		IScriptProposalDesigner designer = ImplActivator.getDefault().getEclipseIDEPlugin()
				.getScriptProposalDesignerForSchemaIdentifiers(proposalroot.getSchemaIdentifiers());
		if (designer != null) {
			designer.process(proposalroot);
		}
		List<ICompletionProposal> resultprops = new ArrayList<>(proposals.size());
		for (EclipseScriptProposalEntry proposal : proposalroot.getProposals()) {
			ICompletionProposal prop = createProposal(proposal);
			if (prop != null) {
				resultprops.add(prop);
			}
		}
		return resultprops.toArray(new ICompletionProposal[resultprops.size()]);
	}

	private class BuildFileContentAssistant extends ContentAssistant {

		public BuildFileContentAssistant() {
			//true --> create async proposal popup
			//false --> non async

			//don't create an asynchronous proposal assistant, as the completion proposal retrievals
			//should finish FAST. Using async proposal popups cause blinking when proposals are recomputed
			super(false);

			enableColoredLabels(true);
			setContentAssistProcessor(new BuildFileContentAssistantProcessor(), IDocument.DEFAULT_CONTENT_TYPE);
//			setRepeatedInvocationMode(true);
			setRestoreCompletionProposalSize(Activator.getDefault().getProposalDialogSettings());
			setInformationControlCreator(new RichInformationControlCreator());
			setSorter((l, r) -> {
				//order non script proposal holders last
				//e.g. "Computing proposals (..%)" pseudo proposal will be ordered last
				if (l instanceof ScriptProposalHolder) {
					if (r instanceof ScriptProposalHolder) {
						ScriptProposalHolder lbf = (ScriptProposalHolder) l;
						ScriptProposalHolder rbf = (ScriptProposalHolder) r;
						return StringUtils.compareStringsNullFirst(lbf.getProposal().getSortingInformation(),
								rbf.getProposal().getSortingInformation());
					}
					return -1;
				}
				if (r instanceof ScriptProposalHolder) {
					return 1;
				}
				return 0;
			});
		}
	}

	private Map<Integer, Color> typecolors = new TreeMap<>();

	private Configuration configuration = new Configuration();

	private OutlinePage outline;

	private ScriptEditorModel editorModel = new ScriptEditorModel();

	private int currentTokenTheme = TokenStyle.THEME_LIGHT;

	private ResourceCloser singleEnvironmentsResourceCloser = new ResourceCloser();

	private IPropertyChangeListener themeListener;

	private Timer timer = new Timer(true);
	private TimerTask outlineSelectionUpdateTimerTask;
	private TimerTask outlineContentUpdaterTimerTask;

	public BuildFileEditor() {
		setSourceViewerConfiguration(configuration);
		themeListener = new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME.equals(event.getProperty())) {
					RGB rgb = (RGB) event.getNewValue();
					currentTokenTheme = rgb.getHSB()[2] < 0.4f ? TokenStyle.THEME_DARK : TokenStyle.THEME_LIGHT;
					editorModel.setTokenTheme(currentTokenTheme);
					rescheduleOutlineUpdate();
					reschedulePresentationUpdate();
				}
			}
		};
		ColorRegistry colorregistry = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();
		colorregistry.addListener(themeListener);
		boolean darktheme = isCurrentThemeDark(colorregistry);
		currentTokenTheme = darktheme ? TokenStyle.THEME_DARK : TokenStyle.THEME_LIGHT;
		editorModel.setTokenTheme(currentTokenTheme);

		editorModel.addModelListener(this);
	}

	public static boolean isCurrentThemeDark() {
		ColorRegistry colorregistry = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();
		return isCurrentThemeDark(colorregistry);
	}

	private static boolean isCurrentThemeDark(ColorRegistry colorregistry) {
		RGB rgb = colorregistry.getRGB(BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME);
		boolean darktheme = rgb.getHSB()[2] < 0.4f;
		return darktheme;
	}

	@Override
	protected boolean affectsTextPresentation(PropertyChangeEvent event) {
		if (BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME.equals(event.getProperty())) {
		}
		return true;
		// TODO Auto-generated method stub
//		return super.affectsTextPresentation(event);
	}

	protected ScriptSyntaxModel getUpdatedModel() {
		try {
			return editorModel.getUpToDateModel();
		} catch (InterruptedException e) {
			// XXX better exception displayer?
			ImplActivator.getDefault().displayException(SakerLog.SEVERITY_WARNING, "Script model updating interrupted.",
					e);
			return null;
		}
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		System.out.println("BuildFileEditor.init() start");
		super.init(site, input);
		System.out.println("BuildFileEditor.init() end");
	}

	@Override
	public void dispose() {
		timer.cancel();
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		currentTheme.getColorRegistry().removeListener(themeListener);
		super.dispose();
		for (Color c : typecolors.values()) {
			c.dispose();
		}
		outline = null;
		editorModel.removeModelListener(this);
		editorModel.close();
	}

	private final static class NullScriptModellingEngine implements ScriptModellingEngine {

		@Override
		public void destroyModel(ScriptSyntaxModel model) {
		}

		@Override
		public ScriptSyntaxModel createModel(ScriptParsingOptions parsingoptions,
				IOSupplier<? extends ByteSource> baseinputsupplier) {
			return new NullScriptSyntaxModel(parsingoptions);
		}

		@Override
		public void close() {
		}
	}

	private static final class NullScriptSyntaxModel implements ScriptSyntaxModel {

		private ScriptParsingOptions parsingOptions;

		public NullScriptSyntaxModel(ScriptParsingOptions parsingOptions) {
			this.parsingOptions = parsingOptions;
		}

		@Override
		public ScriptParsingOptions getParsingOptions() {
			return parsingOptions;
		}

		@Override
		public void invalidateModel() {
		}

		@Override
		public void createModel(IOSupplier<? extends ByteSource> scriptdatasupplier)
				throws IOException, ScriptParsingFailedException {
		}

		@Override
		public void updateModel(List<? extends TextRegionChange> events,
				IOSupplier<? extends ByteSource> scriptdatasupplier) throws IOException, ScriptParsingFailedException {
		}

		@Override
		public Set<String> getTargetNames() throws ScriptParsingFailedException {
			return Collections.emptySet();
		}

	}

	private static class StandaloneScriptModellingEnvironment implements ScriptModellingEnvironment {
		private ScriptModellingEnvironmentConfiguration modellingConfiguration;
		private ScriptSyntaxModel model;
		private ScriptModellingEngine modellingEngine;
		private SakerPath scriptPath;

		public StandaloneScriptModellingEnvironment(SakerEnvironment sakerenvironment, IFile file) throws Exception {
			IProject project = file.getProject();
			SakerPath projectpath = SakerPath.valueOf(project.getLocation().toFile().toPath());
			scriptPath = SakerPath.valueOf(file.getProjectRelativePath().toString());

			//XXX maybe include the nest repository for external script information providing?
			modellingConfiguration = new SimpleScriptModellingEnvironmentConfiguration(
					ExecutionPathConfiguration.local(projectpath), ExecutionScriptConfiguration.getDefault(),
					Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
			modellingEngine = ExecutionScriptConfiguration
					.getScriptAccessorProvider(sakerenvironment,
							ExecutionScriptConfiguration.ScriptProviderLocation.getBuiltin())
					.createModellingEngine(this);
		}

		@Override
		public NavigableSet<SakerPath> getTrackedScriptPaths() {
			return ImmutableUtils.singletonNavigableSet(scriptPath);
		}

		@Override
		public ScriptSyntaxModel getModel(SakerPath scriptpath) throws InvalidPathFormatException {
			if (this.scriptPath.equals(scriptpath)) {
				return getSingleModel();
			}
			return null;
		}

		public ScriptSyntaxModel getSingleModel() {
			synchronized (this) {
				if (model == null) {
					if (modellingEngine == null) {
						//closed
						return null;
					}
					ScriptSyntaxModel createdmodel = modellingEngine.createModel(
							new SimpleScriptParsingOptions(scriptPath),
							() -> LocalFileProvider.getInstance().openInput(scriptPath));
					model = createdmodel;
					return createdmodel;
				}
				return this.model;
			}
		}

		@Override
		public ScriptModellingEnvironmentConfiguration getConfiguration() {
			return modellingConfiguration;
		}

		@Override
		public synchronized void close() throws IOException {
			model.invalidateModel();
			model = null;
			modellingEngine.close();
			modellingEngine = null;
		}

	}

	private void doSetIFileInput(IFileEditorInput input) {
		IFile file = input.getFile();
		System.out.println("BuildFileEditor.setDocumentProvider() IFileEditorInput " + file);
		IProject project = file.getProject();
		ImplActivator activator = ImplActivator.getDefault();
		EclipseSakerIDEProject sakereclipseproject = activator.getOrCreateSakerProject(project);
		if (sakereclipseproject != null) {
			SakerPath scriptpath = sakereclipseproject
					.projectPathToExecutionPath(SakerPath.valueOf(file.getProjectRelativePath().toString()));
			editorModel.setEnvironment(sakereclipseproject.getSakerProject());
			editorModel.setScriptExecutionPath(scriptpath);
		}
		finishDoSetInput(input);
	}

//	private ScriptSyntaxModel doSetStandaloneIFileInput(IFile file, ExceptionDisplayer exceptiondisplayer) {
//		try {
//			StandaloneScriptModellingEnvironment singlemodellingenv = new StandaloneScriptModellingEnvironment(
//					new ForwardingImplSakerEnvironment(
//							ImplActivator.getDefault().getEclipseIDEPlugin().getPluginEnvironment()),
//					file);
//			singleEnvironmentsResourceCloser.add(singlemodellingenv);
//			return singlemodellingenv.getSingleModel();
//		} catch (Exception e) {
//			exceptiondisplayer.displayException(e);
//			return null;
//		}
//	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		System.out.println("BuildFileEditor.setDocumentProvider() " + input + " - " + input.getName());
		if (input instanceof IFileEditorInput) {
			doSetIFileInput((IFileEditorInput) input);
			return;
		} else {
//			throw new UnsupportedOperationException("unimplemented");
//			if (input instanceof IPathEditorInput) {
//				IPath path = ((IPathEditorInput) input).getPath();
//				SakerPath filepath = SakerPath.valueOf(path.toString());
//				System.out.println("BuildFileEditor.setDocumentProvider() IPathEditorInput " + filepath);
//
//				currenteditorinputfile = LocalFileProvider.getInstance().getPathKey(filepath);
//				modellingEnvironment = new SingleScriptModellingEnvironment(getNoProjectFileModellingEngine(), new SimpleScriptParsingOptions(filepath));
//				model = modellingEnvironment.createModelForPath(currenteditorinputfile);
//			} else if (input instanceof IURIEditorInput) {
//				IURIEditorInput uriinput = (IURIEditorInput) input;
//				URI uri = uriinput.getURI();
//				System.out.println("BuildFileEditor.setDocumentProvider() IURIEditorInput " + uri);
//				if ("file".equals(uri.getScheme())) {
//					SakerPath filepath = SakerPath.valueOf(Paths.get(uri));
//					currenteditorinputfile = LocalFileProvider.getInstance().getPathKey(filepath);
//					modellingEnvironment = new SingleScriptModellingEnvironment(getNoProjectFileModellingEngine(), new SimpleScriptParsingOptions(filepath));
//				} else {
//					//else unknown scheme.
//					//TODO maybe support jar scheme?
//					throw new IllegalArgumentException("Unknown URI scheme: " + uri);
//				}
//				model = modellingEnvironment.createModelForPath(currenteditorinputfile);
//			} else {
//				SakerPath filepath = SakerPath.valueOf("unknown");
//				SingleScriptModellingEnvironment singlemodellingenv = new SingleScriptModellingEnvironment(getNoProjectFileModellingEngine(),
//						new SimpleScriptParsingOptions(filepath));
//				modellingEnvironment = singlemodellingenv;
//				model = singlemodellingenv.createModel();
//			}
		}

		finishDoSetInput(input);
	}

	private void finishDoSetInput(IEditorInput input) {
		IDocumentProvider docprov = getDocumentProvider();
		//may be null if the editor has been closed
		if (docprov != null) {
			IDocument doc = docprov.getDocument(input);
			editorModel.resetInput(doc.get());
		}
	}

	@Override
	protected void handleCursorPositionChanged() {
		super.handleCursorPositionChanged();
		//reschedule the job
		if (outline != null && outline.ignoreSelectionListener == 0) {
			rescheduleOutlineSelectionUpdate();
		}
	}

	protected void rescheduleOutlineUpdate() {
		TimerTask currenttask = outlineContentUpdaterTimerTask;
		if (currenttask != null) {
			currenttask.cancel();
		}
		TimerTask ntask = new OutlineContentUpdaterTimerTask();
		outlineContentUpdaterTimerTask = ntask;
		timer.schedule(ntask, OUTLINE_JOB_INPUT_DELAY);
	}

	protected void rescheduleOutlineSelectionUpdate() {
		TimerTask currenttask = outlineSelectionUpdateTimerTask;
		if (currenttask != null) {
			currenttask.cancel();
		}
		TimerTask ntask = new OutlineSelectionUpdaterTimerTask();
		outlineSelectionUpdateTimerTask = ntask;
		timer.schedule(ntask, OUTLINE_JOB_INPUT_DELAY);
	}

	protected void reschedulePresentationUpdate() {
		ITextViewer textviewer = getSourceViewer();
		if (textviewer != null) {
			getSite().getShell().getDisplay().asyncExec(() -> {
				if (getSourceViewer() == textviewer) {
					TextPresentation tp = new TextPresentation(1000);
					if (tp.getExtent() == null) {
						//add an empty style to avoid NPE in buggy Eclipse code......
						tp.addStyleRange(new StyleRange());
					}
					configuration.createPresentation(tp, null);
					textviewer.changeTextPresentation(tp, false);
				}
			});
		}
	}

	@Override
	public void modelUpdated(ScriptSyntaxModel model) {
		if (model == null) {
			return;
		}
		reschedulePresentationUpdate();
		rescheduleOutlineUpdate();
	}

	private final class OutlineSelectionUpdaterTimerTask extends TimerTask {
		@Override
		public void run() {
			getSite().getShell().getDisplay().asyncExec(() -> {
				OutlinePage outlinepage = outline;
				if (outlinepage != null) {
					outlinepage.updateSelection();
				}
			});
		}
	}

	private final class OutlineContentUpdaterTimerTask extends TimerTask {
		@Override
		public void run() {
			getSite().getShell().getDisplay().asyncExec(() -> {
				OutlinePage outlinepage = outline;
				if (outlinepage != null) {
					outlinepage.updateContent();
				}
			});
		}
	}
}
