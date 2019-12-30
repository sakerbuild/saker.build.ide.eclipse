package saker.build.ide.eclipse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.ToolBarManager;
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
import org.eclipse.jface.text.ITextViewerExtension;
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
import org.eclipse.jface.text.source.IVerticalRuler;
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
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
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
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.ide.eclipse.extension.script.information.IScriptInformationDesigner;
import saker.build.ide.eclipse.extension.script.outline.IScriptOutlineDesigner;
import saker.build.ide.eclipse.extension.script.proposal.IScriptProposalDesigner;
import saker.build.ide.eclipse.script.information.BuildScriptScriptInformationEntry;
import saker.build.ide.eclipse.script.information.BuildScriptScriptInformationRoot;
import saker.build.ide.eclipse.script.outline.BuildScriptOutlineEntry;
import saker.build.ide.eclipse.script.outline.BuildScriptOutlineRoot;
import saker.build.ide.eclipse.script.proposal.BuildScriptProposalEntry;
import saker.build.ide.eclipse.script.proposal.BuildScriptProposalRoot;
import saker.build.ide.support.ExceptionDisplayer;
import saker.build.ide.support.SakerIDEPlugin.PluginResourceListener;
import saker.build.ide.support.SakerIDEProject.ProjectResourceListener;
import saker.build.runtime.environment.ForwardingImplSakerEnvironment;
import saker.build.runtime.environment.SakerEnvironment;
import saker.build.runtime.environment.SakerEnvironmentImpl;
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
import saker.build.scripting.model.ScriptToken;
import saker.build.scripting.model.ScriptTokenInformation;
import saker.build.scripting.model.SimplePartitionedTextContent;
import saker.build.scripting.model.SimpleScriptModellingEnvironmentConfiguration;
import saker.build.scripting.model.SimpleTextPartition;
import saker.build.scripting.model.StructureOutlineEntry;
import saker.build.scripting.model.TextPartition;
import saker.build.scripting.model.TextRegionChange;
import saker.build.scripting.model.TokenStyle;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.ResourceCloser;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;

public class BuildFileEditor extends TextEditor implements VerifyKeyListener {
	public static final String ID = "saker.build.ide.eclipse.script.editor";

	//IWorkbenchThemeConstants.ACTIVE_TAB_BG_END
	private static final String BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME = "org.eclipse.ui.workbench.ACTIVE_TAB_BG_END";

	private static final int UPDATE_JOB_INPUT_DELAY = 500;
	private static final int OUTLINE_JOB_INPUT_DELAY = 200;

	private static final Image OUTLINE_IMAGE = Activator.getImageDescriptor("icons/icon.png").createImage();

	private static class OutlineElement {
		protected OutlineElement parent;
		protected BuildScriptOutlineEntry element;

		public OutlineElement(OutlineElement parent, BuildScriptOutlineEntry element) {
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
				BuildScriptOutlineEntry entry = elem.element;
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
				BuildScriptOutlineEntry entry = elem.element;
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
			reschedule(outlineUpdater);
		}

		@Override
		public void dispose() {
			outline = null;
			cancel(outlineUpdater);
			super.dispose();
		}

		@Override
		public Object[] getElements(Object inputElement) {
			BuildScriptOutlineRoot root;
			try {
				ScriptSyntaxModel m = BuildFileEditor.this.model;
				if (m == null) {
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
				ScriptStructureOutline structureoutline = m.getStructureOutline();
				if (structureoutline == null) {
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
				root = new BuildScriptOutlineRoot(structureoutline);
			} catch (Exception e) {
				ImplActivator.getDefault().getEclipseIDEPlugin().displayException(e);
				return ObjectUtils.EMPTY_OBJECT_ARRAY;
			}
			IScriptOutlineDesigner designer = getScriptOutlineDesigner(root);
			if (designer != null) {
				designer.process(root);
			}
			List<? extends BuildScriptOutlineEntry> outlineroots = root.getRootEntries();
			OutlineElement[] result = new OutlineElement[outlineroots.size()];
			int i = 0;
			for (BuildScriptOutlineEntry tree : outlineroots) {
				result[i++] = new OutlineElement(null, tree);
			}
			return result;
		}

		private IScriptOutlineDesigner getScriptOutlineDesigner(BuildScriptOutlineRoot outlineroot) {
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

			BuildScriptOutlineEntry tree = elem.element;
			List<? extends BuildScriptOutlineEntry> children = tree.getChildren();
			OutlineElement[] result = new OutlineElement[children.size()];
			int i = 0;
			for (BuildScriptOutlineEntry child : children) {
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
			BuildScriptOutlineEntry tree = elem.element;
			return !tree.getChildren().isEmpty();
		}

		private void updateContent(ScriptSyntaxModel model) {
			try {
				ignoreSelectionListener++;

				TreeViewer treeviewer = getTreeViewer();
				treeviewer.refresh();
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
			} finally {
				ignoreSelectionListener--;
			}
		}

		private OutlineElement findSelectionOutlineElement(OutlineElement[] roots, int offset, int length) {
			for (OutlineElement elem : roots) {
				BuildScriptOutlineEntry t = elem.element;
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

		private OutlineElement findSelectionOutlineElement(List<? extends BuildScriptOutlineEntry> roots, int offset,
				int length, OutlineElement parent) {
			for (BuildScriptOutlineEntry t : roots) {
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

		private String generateInformationHtml(Object input, Color bgcol) {
			List<BuildScriptScriptInformationEntry> entries;
			if (input instanceof ScriptTokenInformation) {
				ScriptTokenInformation tokeninfo = (ScriptTokenInformation) input;

				BuildScriptScriptInformationRoot inforoot = new BuildScriptScriptInformationRoot(tokeninfo);

				IScriptInformationDesigner designer = ImplActivator.getDefault().getEclipseIDEPlugin()
						.getScriptInformationDesignerForSchemaIdentifier(inforoot.getSchemaIdentifier());
				if (designer != null) {
					designer.process(inforoot);
				}
				entries = BuildScriptScriptInformationRoot
						.getFilteredBuildScriptScriptInformationEntries(inforoot.getEntries());
			} else if (input instanceof PartitionedTextContent) {
				entries = new ArrayList<>();
				for (TextPartition partition : BuildScriptScriptInformationRoot
						.getFilteredTextPartitions(((PartitionedTextContent) input))) {
					entries.add(new BuildScriptScriptInformationEntry(partition));
				}
			} else if (input instanceof BuildScriptProposalEntry) {
				entries = new ArrayList<>();
				List<? extends BuildScriptScriptInformationEntry> infoentries = BuildScriptScriptInformationRoot
						.getFilteredBuildScriptScriptInformationEntries(
								((BuildScriptProposalEntry) input).getInformationEntries());
				if (infoentries != null) {
					for (BuildScriptScriptInformationEntry entry : infoentries) {
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

		private static String generateInformationHtml(List<? extends BuildScriptScriptInformationEntry> entries,
				Color bgcol) {
			Set<String> sections = new LinkedHashSet<>();

			StringBuilder sectionsb = new StringBuilder();

			for (Iterator<? extends BuildScriptScriptInformationEntry> it = entries.iterator(); it.hasNext();) {
				sectionsb.setLength(0);
				BuildScriptScriptInformationEntry entry = it.next();
				String title = entry.getTitle();
				if (!ObjectUtils.isNullOrEmpty(title)) {
					sectionsb.append("<div class=\"ptitle\">");
					String iconsrc = entry.getIconSource();
					if (!ObjectUtils.isNullOrEmpty(iconsrc)) {
						sectionsb.append("<img src=\"");
						sectionsb.append(iconsrc);
						sectionsb.append("\">");
					}
					sectionsb.append(escapeHtml(title));
					sectionsb.append("</div>");
					String subtitle = entry.getSubTitle();
					if (subtitle != null) {
						sectionsb.append("<div class=\"psubtitle\">");
						sectionsb.append(escapeHtml(subtitle));
						sectionsb.append("</div>");
					}
				}
				FormattedTextContent formattedinput = entry.getContent();
				format_appender:
				if (formattedinput != null) {
					Set<String> formats = formattedinput.getAvailableFormats();
					if (formats.contains(FormattedTextContent.FORMAT_HTML)) {
						String formattedtext = formattedinput.getFormattedText(FormattedTextContent.FORMAT_HTML);
						if (!ObjectUtils.isNullOrEmpty(formattedtext)) {
							//should not be null, but just in case of client error
							sectionsb.append("<div class=\"pcontent\">");
							sectionsb.append(formattedtext);
							sectionsb.append("</div>");
							break format_appender;
						}
					}
					if (formats.contains(FormattedTextContent.FORMAT_PLAINTEXT)) {
						String formattedtext = formattedinput.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT);
						if (formattedtext != null) {
							//should not be null, but just in case of client error
							sectionsb.append("<div class=\"pcontent\" style=\"white-space: pre-line;\">");
							sectionsb.append(escapeHtml(formattedtext));
							sectionsb.append("</div>");
							break format_appender;
						}
					}
					for (String f : formats) {
						String formattedtext = formattedinput.getFormattedText(f);
						if (ObjectUtils.isNullOrEmpty(formattedtext)) {
							continue;
						}
						//should not be null, but just in case of client error
						sectionsb.append("<div class=\"pcontent\" style=\"white-space: pre-line;\">");
						sectionsb.append(escapeHtml(formattedtext));
						sectionsb.append("</div>");
						break format_appender;
					}
				}
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

		private static void appendHtmlHeader(StringBuilder contentsb, Color bgcol) {
			contentsb.append("<html><head><style CHARSET=\"ISO-8859-1\" TYPE=\"text/css\">/* Font definitions */\r\n");
			contentsb.append(
					"html { font-family: 'Segoe UI',sans-serif; font-size: 9pt; font-style: normal; font-weight: normal; }\r\n");
			contentsb.append(
					"body, h1, h2, h3, h4, h5, h6, p, table, td, caption, th, ul, ol, dl, li, dd, dt { font-size: 1em; }\r\n");
			contentsb.append(".ptitle { font-weight: bold; }\r\n");
			contentsb.append(
					".ptitle>img { display: inline-block; height: 1.3em; width: auto; margin-right: 0.2em; vertical-align: text-bottom; }\r\n");
			contentsb.append(".psubtitle { font-weight: normal; font-style: italic; margin-left: 6px; }\r\n");
			contentsb.append(".pcontent { margin-top: 10px; margin-left: 6px; }\r\n");
			contentsb.append("hr { opacity: 0.5; }\r\n");
			contentsb.append("pre { font-family: monospace; }</style></head><body style=\"background-color: #");
			contentsb.append(Integer.toHexString(bgcol.getRed() << 16 | bgcol.getGreen() << 8 | bgcol.getBlue()));
			contentsb.append(";\">");
		}

		private static void appendHtmlFooter(StringBuilder contentsb) {
			contentsb.append("</body></html>");
		}

		private static String escapeHtml(String text) {
			return text.replace("<", "&lt;").replace(">", "&gt;");
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

//	private class ChangeEventUpdaterJob extends Job {
//		private Configuration configuration;
//
//		private final AtomicReference<ITextViewer> textViewer = new AtomicReference<>();
//
//		public ChangeEventUpdaterJob(Configuration configuration) {
//			super("Processing file changes");
//			this.configuration = configuration;
//			setPriority(Job.INTERACTIVE);
//			setSystem(true);
//		}
//
//		@Override
//		protected IStatus run(IProgressMonitor monitor) {
//			System.out.println("BuildFileEditor.changeEventProcessorJob.new Job() {...}.run() processing events");
//
//			ArrayList<TextRegionChange> events;
//			synchronized (unprocessedChangeEvents) {
//				events = new ArrayList<>(unprocessedChangeEvents);
//				unprocessedChangeEvents.clear();
//			}
//			ScriptSyntaxModel model = BuildFileEditor.this.model;
//			if (model == null) {
//				return Status.OK_STATUS;
//			}
//			try {
//				ITextViewer textviewer = this.textViewer.getAndSet(null);
//				if (textviewer != null) {
//					//updating cannot be cancelled
//					model.updateModel(events, BuildFileEditor.this::getContentInputStream, ProgressMonitor.nullMonitor());
//				}
//				//only update representation if successfully updated the model
//				OutlinePage outlinepage = outline;
//				Display.getDefault().asyncExec(() -> {
//					if (textviewer != null) {
//						TextPresentation tp = new TextPresentation(1000);
//						configuration.createPresentation(tp, null);
//						textviewer.changeTextPresentation(tp, false);
//					}
//					if (outlinepage != null) {
//						outlinepage.updateContent(model);
//					}
//				});
//			} catch (IOException e) {
//				e.printStackTrace();
//				if (monitor.isCanceled()) {
//					return Status.CANCEL_STATUS;
//				}
//			}
//			return Status.OK_STATUS;
//		}
//
//		public void rescheduleUpdate(ITextViewer textviewer, long delay) {
//			this.textViewer.set(textviewer);
//			cancel();
//			schedule(delay);
//		}
//
//		public void rescheduleNonUpdate(long delay) {
//			cancel();
//			schedule(delay);
//		}
//
//		public void rescheduleCursorChange(int cursorposition, long delay) {
//
//		}
//	}

	private class BuildFileTextHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {
		@Override
		public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
			ScriptSyntaxModel m = model;
			if (m == null) {
				return null;
			}
			int offset = hoverRegion.getOffset();
			int hoverlen = hoverRegion.getLength();
			int hoveroffset = hoverRegion.getOffset();
			Iterator<? extends ScriptToken> it = m.getTokens(hoveroffset, hoverlen).iterator();
			while (it.hasNext()) {
				ScriptToken token = it.next();
				int tokenoffset = token.getOffset();
				int tokenlen = token.getLength();
				int tokenendoffset = tokenoffset + tokenlen;
				if (tokenoffset > offset || tokenendoffset < offset) {
					continue;
				}
				ScriptTokenInformation tokeninfo = m.getTokenInformation(token);
				if (tokeninfo != null) {
					PartitionedTextContent description = tokeninfo.getDescription();
					if (description != null) {
						if (tokenendoffset == offset) {
							//we should prefer the next token if it starts at the offset as the region
							if (it.hasNext()) {
								ScriptToken ntoken = it.next();
								if (ntoken.getOffset() == offset) {
									ScriptTokenInformation ntokeninfo = m.getTokenInformation(ntoken);
									if (ntokeninfo != null) {
										PartitionedTextContent ndescription = ntokeninfo.getDescription();
										if (ndescription != null) {
											return tokeninfo;
										}
									}
								}
							}
						}
						return tokeninfo;
					}
				}
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
			throw new UnsupportedOperationException();
		}

		@Override
		public IInformationControlCreator getHoverControlCreator() {
			return new HoverInformationControlCreator();
		}
	}

	public class Configuration extends SourceViewerConfiguration {

		private final class BuildFileReconciler implements IReconciler {
			private final class BuildFileReconcilingStrategy implements IReconcilingStrategy {
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
				ScriptSyntaxModel model = BuildFileEditor.this.model;
				if (model != null) {
					model.invalidateModel();
				}
				this.textViewer = viewer;
				viewer.invalidateTextPresentation();
				this.textViewer.addTextListener(this);
			}

			@Override
			public void uninstall() {
				this.textViewer.removeTextListener(this);
				this.textViewer = null;

				cancel(modelUpdaterRunnable);
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
				ScriptSyntaxModel model = BuildFileEditor.this.model;
				if (model == null
//						|| event.getDocumentEvent() == null
				) {
					//if document event is null, then it is a visual change only
					return;
				}
				TextRegionChange dataevent = new TextRegionChange(event.getOffset(), event.getLength(),
						event.getText());

				//reschedule will be at updateContentDependentActions
				synchronized (updateLock) {
					unprocessedChangeEvents.add(dataevent);
					textContent.replace(dataevent.getOffset(), dataevent.getOffset() + dataevent.getLength(),
							Objects.toString(dataevent.getText(), ""));
				}
				System.out.println("BuildFileEditor.textChanged() " + dataevent.getOffset() + " ("
						+ dataevent.getLength() + ") -> " + StringUtils.length(dataevent.getText())
//								+ " " + dataevent.replacedText + " -> " + dataevent.text
				);
				reschedule(modelUpdaterRunnable, UPDATE_JOB_INPUT_DELAY);
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

		private Entry<ScriptSyntaxModel, Map<String, Set<? extends TokenStyle>>> modelCachedStyles = null;

//		private final ChangeEventUpdaterJob changeEventProcessorJob = new ChangeEventUpdaterJob(this);

		public Configuration() {
		}

		private ScriptToken getTokenForRegion(IRegion region) {
			ScriptSyntaxModel model = BuildFileEditor.this.model;
			Iterator<? extends ScriptToken> it = model.getTokens(region.getOffset(), region.getLength()).iterator();
			while (it.hasNext()) {
				ScriptToken token = it.next();
				if (token.getOffset() == region.getOffset() && token.getLength() == region.getLength()) {
					return token;
				}
			}
			return null;
		}

		@Override
		public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
			return new RichInformationControlCreator();
		}

		public void createPresentation(TextPresentation presentation, ITypedRegion damage) {
			ScriptSyntaxModel model = BuildFileEditor.this.model;
			if (model == null) {
				return;
			}
			System.out.println("BuildFileEditor.Configuration.createPresentation() " + presentation + " - " + damage);
			long nanos = System.nanoTime();
//			System.out.println("BuildFileEditor.Configuration.createPresentation() " + model);

//			int debugi = 0;

			int offset = 0;
			int length = 0;
			if (damage != null) {
				offset = damage.getOffset();
				length = damage.getLength();
			}

			Map<String, Set<? extends TokenStyle>> styles;
			if (modelCachedStyles == null || modelCachedStyles.getKey() != model) {
				styles = model.getTokenStyles();
				modelCachedStyles = ImmutableUtils.makeImmutableMapEntry(model, styles);
			} else {
				styles = modelCachedStyles.getValue();
			}

			Iterator<? extends ScriptToken> it = model.getTokens(offset, length).iterator();
			while (it.hasNext()) {
				ScriptToken token = it.next();
				if (token.isEmpty()) {
					continue;
				}

//				if (true) {
//					boolean white = debugi++ % 2 == 0;
//					Color fg = white ? getColor(0xFFFFFFFF) : getColor(0xFF000000);
//					Color bg = white ? getColor(0xFF000000) : getColor(0xFFFFFFFF);
//					StyleRange range = new StyleRange(token.getOffset(), token.getLength(), fg, bg);
//					presentation.addStyleRange(range);
//					continue;
//				}

				TokenStyle style = findAppropriateStyle(styles.get(token.getType()), currentTokenTheme);
				if (style != null) {
					Color fg = null;
					Color bg = null;
					int fgc = style.getForegroundColor();
					int bgc = style.getBackgroundColor();
					int s = style.getStyle();
					if (fgc != TokenStyle.COLOR_UNSPECIFIED) {
						fg = getColor(fgc);
					}
//					if (selectedtoken != null && token.getType().equals(selectedtoken.getType())) {
//						bgc = 0xFF808080;
//					}
					if (bgc != TokenStyle.COLOR_UNSPECIFIED) {
						bg = getColor(bgc);
					}
					StyleRange range = new StyleRange(token.getOffset(), token.getLength(), fg, bg);
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
//			System.out.println("BuildFileEditor.Configuration.createPresentation() " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
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

		private TokenStyle findAppropriateStyle(Set<? extends TokenStyle> styles, int theme) {
			if (styles == null) {
				return null;
			}
			Iterator<? extends TokenStyle> it = styles.iterator();
			if (!it.hasNext()) {
				return null;
			}

			TokenStyle first = it.next();
			TokenStyle notheme = null;
			if (((first.getStyle() & theme) == theme)) {
				return first;
			}
			if (((first.getStyle() & TokenStyle.THEME_MASK) == 0)) {
				notheme = first;
			}
			while (it.hasNext()) {
				TokenStyle ts = it.next();
				if (((ts.getStyle() & theme) == theme)) {
					return ts;
				}
				if (((ts.getStyle() & TokenStyle.THEME_MASK) == 0)) {
					notheme = ts;
				}
			}
			return notheme == null ? first : notheme;
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
		private BuildScriptProposalEntry proposal;
		private List<? extends CompletionProposalEdit> changes;

		public InsertOnlyCompletionProposal(BuildScriptProposalEntry proposal,
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
			reschedule(modelUpdaterRunnable);
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

	private ICompletionProposal createProposal(BuildScriptProposalEntry proposalentry) {
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
		BuildScriptProposalRoot proposalroot = new BuildScriptProposalRoot(proposals);
		IScriptProposalDesigner designer = ImplActivator.getDefault().getEclipseIDEPlugin()
				.getScriptProposalDesignerForSchemaIdentifiers(proposalroot.getSchemaIdentifiers());
		if (designer != null) {
			designer.process(proposalroot);
		}
		List<ICompletionProposal> resultprops = new ArrayList<>(proposals.size());
		for (BuildScriptProposalEntry proposal : proposalroot.getProposals()) {
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
			super(true);

			enableColoredLabels(true);
			setContentAssistProcessor(new BuildFileContentAssistantProcessor(), IDocument.DEFAULT_CONTENT_TYPE);
			setRepeatedInvocationMode(true);
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

	private abstract static class TimedRunnable implements Runnable {
		private static final AtomicIntegerFieldUpdater<BuildFileEditor.TimedRunnable> AIFU_state = AtomicIntegerFieldUpdater
				.newUpdater(BuildFileEditor.TimedRunnable.class, "state");

		public static final int STATE_NONE = 0;
		public static final int STATE_RUNNING = 1;
		public static final int STATE_FINISHED = 2;

		private volatile int state = STATE_NONE;

		private static final AtomicInteger IdCounter = new AtomicInteger(0);

		private volatile long executeTime;
		private final int id;

		public TimedRunnable() {
			this.id = IdCounter.getAndIncrement();
		}

		public int getState() {
			return state;
		}

		protected abstract void runImpl();

		@Override
		public final void run() {
			state = STATE_RUNNING;
			try {
				runImpl();
			} finally {
				synchronized (this) {
					if (AIFU_state.compareAndSet(this, STATE_RUNNING, STATE_FINISHED)) {
						//we were rescheduled
						notifyAll();
					}
				}
			}
		}

		public boolean isFinished() {
			return state == STATE_FINISHED;
		}

		public synchronized void notifyUnfinished() {
			notifyAll();
		}

		public void joinFinish() throws InterruptedException {
			synchronized (this) {
				if (isFinished()) {
					return;
				}
				wait();
			}
		}

		public void rescheduled() {
			state = STATE_NONE;
		}

		public void setDelay(long delay) {
			this.executeTime = nanoMillis() + delay;
		}

		public void setInstant() {
			//set the execution time to in the past
			setDelay(-1000);
		}

		public static int compareTo(TimedRunnable l, TimedRunnable r) {
			int cmp = Long.compare(l.executeTime, r.executeTime);
			if (cmp != 0) {
				return cmp;
			}
			return Integer.compare(l.id, r.id);
		}
	}

	private static final AtomicReferenceFieldUpdater<BuildFileEditor, ScriptSyntaxModel> ARFU_model = AtomicReferenceFieldUpdater
			.newUpdater(BuildFileEditor.class, ScriptSyntaxModel.class, "model");
	private volatile ScriptSyntaxModel model;

	private Map<Integer, Color> typecolors = new TreeMap<>();

	private Configuration configuration = new Configuration();

	private OutlinePage outline;

	private NavigableSet<TimedRunnable> updateRunnables = new TreeSet<>(TimedRunnable::compareTo);
	private Thread updaterThread;

	private StringBuilder textContent = new StringBuilder();
	private LinkedList<TextRegionChange> unprocessedChangeEvents = new LinkedList<>();
	private final Object updateLock = new Object();

	private TimedRunnable modelUpdaterRunnable = new TimedRunnable() {
		@Override
		protected void runImpl() {
			ScriptSyntaxModel model = BuildFileEditor.this.model;
			if (model == null) {
				return;
			}

			long nanos = System.nanoTime();
//			System.out.println("BuildFileEditor.updaterRunnable.new TimedRunnable() {...}.runImpl() START UPDATE");

			String textstring;
			ArrayList<TextRegionChange> events;
			synchronized (updateLock) {
				textstring = textContent.toString();
				events = new ArrayList<>(unprocessedChangeEvents);
			}

			try {
				model.updateModel(new ArrayList<>(events),
						() -> new UnsyncByteArrayInputStream(textstring.getBytes(StandardCharsets.UTF_8)));
				synchronized (updateLock) {
					for (TextRegionChange e : events) {
						if (unprocessedChangeEvents.pollFirst() != e) {
							throw new IllegalStateException("illegal event state.");
						}
					}
				}
				//only update representation if successfully updated the model
				cancel(presentationUpdater);
				presentationUpdater.run();
				cancel(outlineUpdater);
				outlineUpdater.run();
			} catch (Exception e) {
				e.printStackTrace();
			}
//			System.out.println("BuildFileEditor.updaterRunnable.new TimedRunnable() {...}.runImpl() " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
		}
	};
	private TimedRunnable presentationUpdater = new TimedRunnable() {
		@Override
		protected void runImpl() {
			ITextViewer textviewer = getSourceViewer();
			if (textviewer != null) {
				Display.getDefault().asyncExec(() -> {
					//make sure still the same viewer
					if (getSourceViewer() == textviewer) {
						try {
							TextPresentation tp = new TextPresentation(1000);
							if (tp.isEmpty()) {
								//to avoid some nullpointer exception 
//								java.lang.NullPointerException
//									at org.eclipse.jface.text.source.AnnotationPainter.applyTextPresentation(AnnotationPainter.java:1015)
//									at org.eclipse.jface.text.TextViewer.changeTextPresentation(TextViewer.java:4722)
								tp.addStyleRange(new StyleRange());
							}
							configuration.createPresentation(tp, null);
							textviewer.changeTextPresentation(tp, false);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		}
	};
	private TimedRunnable outlineUpdater = new TimedRunnable() {
		@Override
		protected void runImpl() {
			ScriptSyntaxModel model = BuildFileEditor.this.model;
			if (model == null) {
				return;
			}
			OutlinePage outlinepage = outline;
			if (outlinepage != null) {
				Display.getDefault().asyncExec(() -> {
					if (outline == outlinepage) {
						//make sure wasnt disposed
						outlinepage.updateContent(model);
					}
				});
			}
		}
	};

	private int currentTokenTheme = TokenStyle.THEME_LIGHT;

	private EclipseSakerIDEProject propertiesChangeListenerProject;
	private ScriptRelatedPropertiesChangeHandler projectPropertiesChangeListener;
	private ResourceCloser singleEnvironmentsResourceCloser = new ResourceCloser();

	private IPropertyChangeListener themeListener;

	public BuildFileEditor() {
		setSourceViewerConfiguration(configuration);
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		themeListener = new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME.equals(event.getProperty())) {
					RGB rgb = (RGB) event.getNewValue();
					currentTokenTheme = rgb.getHSB()[2] < 0.4f ? TokenStyle.THEME_DARK : TokenStyle.THEME_LIGHT;
					reschedule(presentationUpdater);
					reschedule(outlineUpdater);
				}
			}
		};
		currentTheme.getColorRegistry().addListener(themeListener);
		RGB rgb = currentTheme.getColorRegistry().getRGB(BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME);
		currentTokenTheme = rgb.getHSB()[2] < 0.4f ? TokenStyle.THEME_DARK : TokenStyle.THEME_LIGHT;
	}

	@Override
	protected boolean affectsTextPresentation(PropertyChangeEvent event) {
		if (BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME.equals(event.getProperty())) {
		}
		return true;
		// TODO Auto-generated method stub
//		return super.affectsTextPresentation(event);
	}

	private void runUpdaterThread() {
		try {
			while (!Thread.interrupted()) {
				TimedRunnable first = null;
				getter:
				synchronized (updateRunnables) {
					while (updateRunnables.isEmpty()) {
						updateRunnables.wait();
					}
					TimedRunnable got = updateRunnables.first();
					long currentmillis = nanoMillis();
					long exectime;
					long towait;
					synchronized (got) {
						exectime = got.executeTime;
						towait = exectime - currentmillis;
						if (towait <= 0) {
							//remove the first
							updateRunnables.pollFirst();
							first = got;
							break getter;
						}
					}
					updateRunnables.wait(towait);
					if (updateRunnables.isEmpty()) {
						continue;
					}
					TimedRunnable nfirst = updateRunnables.first();
					if (got != nfirst || got.executeTime != exectime) {
						//the first runnable changed while we were waiting for it
						//or the execution time of the runnable was changed
						continue;
					}
					updateRunnables.pollFirst();
					first = got;
				}
				try {
					first.run();
				} catch (Throwable t) {
//					Status status = new Status(Status.ERROR, Activator.PLUGIN_ID, "Error during updating editor model.", t);
//					StatusManager.getManager().handle(status);
//					Activator.getDefault().getLog().log(status);
					t.printStackTrace();
				}
			}
		} catch (InterruptedException e) {
		}
	}

	private static long nanoMillis() {
		return System.nanoTime() / 1_000_00;
	}

	private void reschedule(TimedRunnable timedrun, long delay) {
		synchronized (updateRunnables) {
			synchronized (timedrun) {
				timedrun.setDelay(delay);
				timedrun.rescheduled();
			}
			updateRunnables.remove(timedrun);
			updateRunnables.add(timedrun);
			updateRunnables.notifyAll();
		}

	}

	private void reschedule(TimedRunnable timedrun) {
		synchronized (updateRunnables) {
			synchronized (timedrun) {
				timedrun.setInstant();
				timedrun.rescheduled();
			}
			updateRunnables.remove(timedrun);
			updateRunnables.add(timedrun);
			updateRunnables.notifyAll();
		}
	}

	private void cancel(TimedRunnable timedrun) {
//		System.out.println("BuildFileEditor.cancel()");
		synchronized (updateRunnables) {
			synchronized (timedrun) {
				updateRunnables.remove(timedrun);
			}
		}
		timedrun.notifyUnfinished();
	}

	private ScriptSyntaxModel getUpdatedModel() {
		long nanos = System.nanoTime();
		reschedule(modelUpdaterRunnable);
		try {
			modelUpdaterRunnable.joinFinish();
		} catch (InterruptedException e) {
			//don't wait
		}
		System.out.println("BuildFileEditor.getUpdatedModel() " + (System.nanoTime() - nanos) / 1_000_000 + " ms");
		return model;
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		System.out.println("BuildFileEditor.init() start");
		updaterThread = new Thread(this::runUpdaterThread);
		updaterThread.start();
		super.init(site, input);
		System.out.println("BuildFileEditor.init() end");
	}

	@Override
	public void dispose() {
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		currentTheme.getColorRegistry().removeListener(themeListener);
		cancel(modelUpdaterRunnable);
		cancel(outlineUpdater);
		cancel(presentationUpdater);
		Thread ut = updaterThread;
		if (ut != null) {
			ut.interrupt();
		}
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer instanceof ITextViewerExtension) {
			((ITextViewerExtension) sourceViewer).removeVerifyKeyListener(this);
		}
		super.dispose();
		for (Color c : typecolors.values()) {
			c.dispose();
		}
		if (ut != null) {
			try {
				ut.join();
			} catch (InterruptedException e) {
				//dont wait. exceptions in the updater thread are handled there
			}
		}
		disposeModel();
		disposeProjectPropertiesChangeHandler();
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
		ScriptSyntaxModel model;
		IProject project = file.getProject();
		ImplActivator activator = ImplActivator.getDefault();
		EclipseSakerIDEProject sakereclipseproject = activator.getOrCreateSakerProject(project);
		EclipseSakerIDEPlugin eclipsesakerplugin = activator.getEclipseIDEPlugin();
		if (sakereclipseproject == null) {
			model = doSetStandaloneIFileInput(file, eclipsesakerplugin);
		} else {
			ScriptModellingEnvironment modellingenvironment = sakereclipseproject.getScriptingEnvironment();
			if (modellingenvironment == null) {
				model = doSetStandaloneIFileInput(file, sakereclipseproject);
			} else {
				SakerPath scriptpath = sakereclipseproject
						.projectPathToExecutionPath(SakerPath.valueOf(file.getProjectRelativePath().toString()));
				model = modellingenvironment.getModel(scriptpath);
			}
			disposeProjectPropertiesChangeHandler();
			propertiesChangeListenerProject = sakereclipseproject;
			projectPropertiesChangeListener = new IFileInputSettingProjectPropertiesChangeHandler(input);
			sakereclipseproject.addProjectResourceListener(projectPropertiesChangeListener);
			eclipsesakerplugin.addPluginResourceListener(projectPropertiesChangeListener);
		}
		finishDoSetInput(input, model);
	}

	private void disposeProjectPropertiesChangeHandler() {
		if (projectPropertiesChangeListener != null) {
			propertiesChangeListenerProject.removeProjectResourceListener(projectPropertiesChangeListener);
			propertiesChangeListenerProject.getPlugin().removePluginResourceListener(projectPropertiesChangeListener);
			projectPropertiesChangeListener = null;
		}
	}

	private interface ScriptRelatedPropertiesChangeHandler extends ProjectResourceListener, PluginResourceListener {
	}

	private final class IFileInputSettingProjectPropertiesChangeHandler
			implements ScriptRelatedPropertiesChangeHandler {
		private final IFileEditorInput input;

		IFileInputSettingProjectPropertiesChangeHandler(IFileEditorInput input) {
			this.input = input;
		}

		@Override
		public void scriptModellingEnvironmentClosing(ScriptModellingEnvironment env) {
			System.out.println(
					"BuildFileEditor.IFileInputSettingProjectPropertiesChangeHandler.scriptModellingEnvironmentClosing()");
			disposeModel();
		}

		@Override
		public void scriptModellingEnvironmentCreated(ScriptModellingEnvironment env) {
			System.out.println(
					"BuildFileEditor.IFileInputSettingProjectPropertiesChangeHandler.scriptModellingEnvironmentCreated()");
			doSetIFileInput(input);
		}

		@Override
		public void environmentClosing(SakerEnvironmentImpl environment) {
			System.out.println("BuildFileEditor.IFileInputSettingProjectPropertiesChangeHandler.environmentClosing()");
			disposeModel();
		}

		@Override
		public void environmentCreated(SakerEnvironmentImpl environment) {
			System.out.println("BuildFileEditor.IFileInputSettingProjectPropertiesChangeHandler.environmentCreated()");
			doSetIFileInput(input);
		}
	}

	private ScriptSyntaxModel doSetStandaloneIFileInput(IFile file, ExceptionDisplayer exceptiondisplayer) {
		try {
			StandaloneScriptModellingEnvironment singlemodellingenv = new StandaloneScriptModellingEnvironment(
					new ForwardingImplSakerEnvironment(
							ImplActivator.getDefault().getEclipseIDEPlugin().getPluginEnvironment()),
					file);
			singleEnvironmentsResourceCloser.add(singlemodellingenv);
			return singlemodellingenv.getSingleModel();
		} catch (Exception e) {
			exceptiondisplayer.displayException(e);
			return null;
		}
	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		disposeModel();
		super.doSetInput(input);
		ScriptSyntaxModel model;
		System.out.println("BuildFileEditor.setDocumentProvider() " + input + " - " + input.getName());
		if (input instanceof IFileEditorInput) {
			doSetIFileInput((IFileEditorInput) input);
			return;
		} else {
			model = null;
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

		finishDoSetInput(input, model);
	}

	private void finishDoSetInput(IEditorInput input, ScriptSyntaxModel model) {
		IDocumentProvider docprov = getDocumentProvider();
		//may be null if the editor has been closed
		if (docprov != null) {
			IDocument doc = docprov.getDocument(input);
			synchronized (updateLock) {
				textContent.setLength(0);
				textContent.append(doc.get());
			}
		}
		this.model = model;

		reschedule(modelUpdaterRunnable);
	}

	private void disposeModel() {
		ScriptSyntaxModel model = BuildFileEditor.this.model;
		if (model != null) {
			synchronized (updateLock) {
				unprocessedChangeEvents.clear();
			}
			model.invalidateModel();
			try {
				singleEnvironmentsResourceCloser.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			modellingEnvironment.destroyModel(model);
//			if (modellingEnvironment instanceof SingleScriptModellingEnvironment) {
//				try {
//					modellingEnvironment.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
			ARFU_model.compareAndSet(this, model, null);
		}
	}

	private InputStream getContentInputStream(ITextViewer sv) {
		IDocument doc = sv.getDocument();
		return new UnsyncByteArrayInputStream(doc.get().getBytes());
	}

	private InputStream getContentInputStream() {
		ISourceViewer sv = getSourceViewer();
		return getContentInputStream(sv);
	}

	private int getCaretPosition() {
		ISelection selection = doGetSelection();
		if (selection instanceof ITextSelection) {
			return ((ITextSelection) selection).getOffset();
		}
		return -1;
	}

	@Override
	protected void handleCursorPositionChanged() {
		super.handleCursorPositionChanged();
		//reschedule the job
		if (outline != null && outline.ignoreSelectionListener == 0) {
			reschedule(outlineUpdater, OUTLINE_JOB_INPUT_DELAY);
		}
	}

	@Override
	protected ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
		ISourceViewer result = super.createSourceViewer(parent, ruler, styles);
		if (result instanceof ITextViewerExtension) {
			((ITextViewerExtension) result).prependVerifyKeyListener(this);
		}
		return result;
	}

	@Override
	public void createPartControl(Composite parent) {
		System.out.println("BuildFileEditor.createPartControl()");
		super.createPartControl(parent);
		reschedule(modelUpdaterRunnable);
	}

	@Override
	public void verifyKey(VerifyEvent event) {
		// TODO Auto-generated method stub
//		System.out.println("BuildFileEditor.createSourceViewer(...).new VerifyKeyListener() {...}.verifyKey() " + event);
	}

	@Override
	protected void editorContextMenuAboutToShow(IMenuManager menu) {
		// TODO Auto-generated method stub
		super.editorContextMenuAboutToShow(menu);
	}

}
