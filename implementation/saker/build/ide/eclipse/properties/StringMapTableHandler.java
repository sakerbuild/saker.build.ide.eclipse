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
package saker.build.ide.eclipse.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.ContributedExtensionConfiguration;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;

public class StringMapTableHandler {
	public static final IContentProposal[] EMPTY_ICONTENTPROPOSALS_ARRAY = new IContentProposal[0];

	//ctrl+space on windows, ctrl+space on mac
	//    (command+space opens up the spotlight on mac)
	//see SWTKeyLookup
	public static final KeyStroke KEY_STROKE_CODE_COMPLETION;
	static {
		KeyStroke keystroke = null;
		try {
			keystroke = KeyStroke.getInstance("Ctrl+Space");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		KEY_STROKE_CODE_COMPLETION = keystroke;
	}

	public static final String E_EMPTY_KEY = "empty-key";
	public static final String E_DUPLICATE_KEY = "duplicate-key";

	private Table table;
	private Set<Entry<String, String>> entries = new LinkedHashSet<>();
	private List<ExtensionProvidedEntry> extensionEntries = Collections.emptyList();

	private String keyValDialogTitle = "";
	private String addButtonLabel = "";
	private String keyValDialogBaseMessage = "";

	private String keyColumnTitle = "Key";
	private String valueColumnTitle = "Value";

	private List<Listener> modifyListeners = new ArrayList<>();

	private IContentProposalProvider keyContentProposalProvider;
	private Function<String, IContentProposalProvider> valueContentProposalSupplier;

	public StringMapTableHandler() {
	}

	/**
	 * Overrides {@link #setKeyContentProposalProvider(IContentProposalProvider)}.
	 */
	public void setKeyProposalSupplier(Supplier<String[]> keyProposalSupplier) {
		if (keyProposalSupplier != null) {
			this.keyContentProposalProvider = new IContentProposalProvider() {
				@Override
				public IContentProposal[] getProposals(String contents, int position) {
					String[] proposals = keyProposalSupplier.get();
					if (ObjectUtils.isNullOrEmpty(proposals)) {
						return EMPTY_ICONTENTPROPOSALS_ARRAY;
					}
					String prefix = contents.substring(0, position);

					ArrayList<ContentProposal> list = new ArrayList<>();
					for (String proposal : proposals) {
						if (StringUtils.startsWithIgnoreCase(proposal, prefix)) {
							list.add(new ContentProposal(proposal));
						}
					}
					return list.toArray(new IContentProposal[list.size()]);
				}
			};
		} else {
			this.keyContentProposalProvider = null;
		}
	}

	public void setKeyContentProposalProvider(IContentProposalProvider keyContentProposalProvider) {
		this.keyContentProposalProvider = keyContentProposalProvider;
	}

	public void setValueContentProposalArraySupplier(Function<String, String[]> valueProposalSupplier) {
		if (valueProposalSupplier != null) {
			this.valueContentProposalSupplier = keyname -> {
				String[] proposals = valueProposalSupplier.apply(keyname);
				if (ObjectUtils.isNullOrEmpty(proposals)) {
					return null;
				}
				return new IContentProposalProvider() {
					@Override
					public IContentProposal[] getProposals(String contents, int position) {
						String prefix = contents.substring(0, position);
						ArrayList<ContentProposal> list = new ArrayList<>();
						for (String proposal : proposals) {
							if (StringUtils.startsWithIgnoreCase(proposal, prefix)) {
								list.add(new ContentProposal(proposal));
							}
						}
						return list.toArray(new IContentProposal[list.size()]);
					}
				};

			};
		} else {
			this.valueContentProposalSupplier = null;
		}
	}

	/**
	 * Overrides {@link #setValueContentProposalArraySupplier(Function)}.
	 * <p>
	 * Argument for the function is the key value.
	 */
	public void setValueContentProposalSupplier(
			Function<String, IContentProposalProvider> valueContentProposalSupplier) {
		this.valueContentProposalSupplier = valueContentProposalSupplier;
	}

	public void setKeyValDialogTitle(String keyValDialogTitle) {
		this.keyValDialogTitle = keyValDialogTitle;
	}

	public void setKeyValDialogBaseMessage(String keyValDialogBaseMessage) {
		this.keyValDialogBaseMessage = keyValDialogBaseMessage;
	}

	public void setKeyColumnTitle(String keyColumnTitle) {
		this.keyColumnTitle = keyColumnTitle;
	}

	public void setValueColumnTitle(String valueColumnTitle) {
		this.valueColumnTitle = valueColumnTitle;
	}

	public void setAddButtonLabel(String addButtonLabel) {
		this.addButtonLabel = addButtonLabel;
	}

	protected IContentProposalProvider getKeyContentProposalProvider() {
		return keyContentProposalProvider;
	}

	protected Function<String, IContentProposalProvider> getValueContentProposalSupplier() {
		return valueContentProposalSupplier;
	}

	public final void setEntries(Map<String, String> options) {
		if (options == null) {
			this.setEntries(Collections.emptySet());
		} else {
			this.setEntries(options.entrySet());
		}
	}

	public void setEntries(Set<? extends Entry<String, String>> options) {
		this.entries.clear();
		if (!ObjectUtils.isNullOrEmpty(options)) {
			for (Entry<String, String> entry : options) {
				this.entries.add(ImmutableUtils.makeImmutableMapEntry(entry.getKey(), entry.getValue()));
			}
		}
		if (table != null) {
			populateTableColumns();
			table.getParent().requestLayout();
		}
	}

	public void setExtensionEntries(List<ExtensionProvidedEntry> extensionEntries) {
		if (extensionEntries == null) {
			extensionEntries = Collections.emptyList();
		}
		this.extensionEntries = ImmutableUtils.makeImmutableList(extensionEntries);
		if (table != null) {
			populateTableColumns();
			table.getParent().requestLayout();
		}
	}

	public void selectEntriesWithKey(String key) {
		for (int i = 0; i < table.getItems().length; i++) {
			TableItem item = table.getItems()[i];
			@SuppressWarnings("unchecked")
			Entry<String, ?> entry = (Entry<String, ?>) item.getData();
			if (Objects.equals(key, entry.getKey())) {
				table.select(i);
			}
		}
	}

	public PropertiesValidationErrorResult validate() {
		Set<String> keys = new TreeSet<>();
		for (Entry<String, String> entry : entries) {
			String key = entry.getKey();
			if (ObjectUtils.isNullOrEmpty(key)) {
				return new PropertiesValidationErrorResult(E_EMPTY_KEY, key);
			}
			if (!keys.add(key)) {
				return new PropertiesValidationErrorResult(E_DUPLICATE_KEY, key);
			}
		}
		return null;
	}

	public void addModifyListener(Listener listener) {
		modifyListeners.add(listener);
	}

	public void addControl(Composite composite) {
		table = new Table(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		TableColumn keycol = new TableColumn(table, SWT.NONE);
		TableColumn valcol = new TableColumn(table, SWT.NONE);
		keycol.setText(keyColumnTitle);
		valcol.setText(valueColumnTitle);
		keycol.setResizable(false);
		valcol.setResizable(false);

		table.setLinesVisible(true);
		table.setHeaderVisible(true);

		GridData tablegd = new GridData();
		tablegd.grabExcessHorizontalSpace = true;
		tablegd.grabExcessVerticalSpace = true;
		tablegd.horizontalAlignment = GridData.FILL;
		tablegd.verticalAlignment = GridData.FILL;
		table.setLayoutData(tablegd);

		table.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.DEL) {
					TableItem[] sel = table.getSelection();
					if (sel.length == 0) {
						return;
					}
					for (TableItem selti : sel) {
						entries.remove(selti.getData());
					}
					populateTableColumns();
					callModifyListeners();
				}
			}
		});
		table.addControlListener(new TableColumnEvenDistributorControlResizeListener(table));
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				TableItem[] sel = table.getSelection();
				if (sel.length == 0) {
					return;
				}
				Object data = sel[0].getData();
				if (data instanceof ExtensionProvidedEntry) {
					//TODO display some info about it
					return;
				}
				@SuppressWarnings("unchecked")
				Entry<String, String> itementry = (Entry<String, String>) data;
				String key = itementry.getKey();
				String value = itementry.getValue();
				KeyValueDialog dialog = createEntryDialog(table.getShell(), key, value);
				dialog.setModifying(true);
				dialog.create();
				if (dialog.open() == Window.OK) {
					entries.remove(itementry);
					entries.add(ImmutableUtils.makeImmutableMapEntry(dialog.getParameterName(),
							dialog.getParameterValue()));
					populateTableColumns();
					callModifyListeners();
				}
			}
		});
		Button addbutton = new Button(composite, SWT.PUSH);
		addbutton.setText(addButtonLabel);
		addbutton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			KeyValueDialog dialog = createEntryDialog(table.getShell(), "", "");
			dialog.create();
			if (dialog.open() == Window.OK) {
				String paramname = dialog.getParameterName();
				entries.add(ImmutableUtils.makeImmutableMapEntry(paramname, dialog.getParameterValue()));
				populateTableColumns();
				callModifyListeners();
			}
		}));

		populateTableColumns();
	}

	public Table getTable() {
		return table;
	}

	public Set<? extends Entry<String, String>> getEntries() {
		return entries;
	}

	public Map<String, String> getEntriesMap() {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (Entry<String, String> entry : entries) {
			result.putIfAbsent(entry.getKey(), entry.getValue());
		}
		return result;
	}

	public static class ExtensionProvidedEntry {
		private String key;
		private String value;
		private Collection<ContributedExtensionConfiguration<?>> contributors;

		public ExtensionProvidedEntry(String key, String value,
				Collection<ContributedExtensionConfiguration<?>> contributors) {
			this.key = key;
			this.value = value;
			this.contributors = contributors;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public Collection<ContributedExtensionConfiguration<?>> getContributors() {
			return contributors;
		}
	}

	private void callModifyListeners() {
		for (Listener l : modifyListeners) {
			l.handleEvent(new Event());
		}
	}

	private KeyValueDialog createEntryDialog(Shell shell, String key, String value) {
		KeyValueDialog result = new KeyValueDialog(shell, key, value);
		result.setDialogTitle(keyValDialogTitle);
		result.setBaseMessage(keyValDialogBaseMessage);
		return result;
	}

	private void populateTableColumns() {
		Table table = this.table;
		if (ObjectUtils.isNullOrEmpty(entries) && ObjectUtils.isNullOrEmpty(extensionEntries)) {
			table.setItemCount(0);
		} else {
			table.setItemCount(entries.size() + extensionEntries.size());
			int i = 0;
			for (Entry<String, String> entry : entries) {
				TableItem item = table.getItem(i++);
				item.setText(0, ObjectUtils.nullDefault(entry.getKey(), ""));
				item.setText(1, ObjectUtils.nullDefault(entry.getValue(), ""));
				item.setData(entry);
				item.setImage((Image) null);
			}
			if (!ObjectUtils.isNullOrEmpty(extensionEntries)) {
				for (ExtensionProvidedEntry entry : extensionEntries) {
					TableItem item = table.getItem(i++);
					item.setText(0, ObjectUtils.nullDefault(entry.getKey(), ""));
					item.setText(1, ObjectUtils.nullDefault(entry.getValue(), ""));
					item.setData(entry);
					item.setImage(Activator.IMAGE_EXT_POINT);
				}
			}
		}
		table.getParent().requestLayout();
	}

	private class KeyValueDialog extends TitleAreaDialog {
		private boolean modifying;
		private Text paramNameText;
		private Text paramValueText;

		private final String initialParamName;
		private String paramName;
		private String paramValue;

		private String baseMessage;
		private String dialogTitle;

		private IContentProposalProvider keyContentProposalProvider;
		private Function<String, IContentProposalProvider> valueContentProposalSupplier;

		public KeyValueDialog(Shell parentShell, String paramName, String paramValue) {
			super(parentShell);
			this.paramName = paramName;
			this.paramValue = paramValue;
			this.initialParamName = paramName;
			this.keyContentProposalProvider = getKeyContentProposalProvider();
			this.valueContentProposalSupplier = getValueContentProposalSupplier();
		}

		public void setDialogTitle(String dialogTitle) {
			this.dialogTitle = dialogTitle;
		}

		@Override
		public boolean isHelpAvailable() {
			return false;
		}

		@Override
		protected void configureShell(Shell newShell) {
			super.configureShell(newShell);
			newShell.setText(dialogTitle);
		}

		@Override
		public void create() {
			super.create();
			setTitle(dialogTitle);
			resetMessage();
		}

		private void resetMessage() {
			setMessage(baseMessage, IMessageProvider.NONE);
		}

		public void setBaseMessage(String baseMessage) {
			this.baseMessage = baseMessage;
		}

		public void setModifying(boolean modifying) {
			this.modifying = modifying;
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			container.setLayout(new GridLayout(2, false));

			createParamName(container);
			createParamValue(container);

			if (KEY_STROKE_CODE_COMPLETION != null
					&& (keyContentProposalProvider != null || valueContentProposalSupplier != null)) {
				Label assistInfoLabel = new Label(container, SWT.NONE);
				assistInfoLabel.setText("Content assist is available. (" + KEY_STROKE_CODE_COMPLETION.format() + ")");
				assistInfoLabel.setLayoutData(GridDataFactory.swtDefaults().span(2, 1).create());
			}

			Listener modifylistener = event -> {
				Button okbutton = getButton(IDialogConstants.OK_ID);
				if (okbutton == null) {
					return;
				}
				updateOkButton(okbutton);
			};
			paramNameText.addListener(SWT.Modify, modifylistener);
			paramValueText.addListener(SWT.Modify, modifylistener);

			return area;
		}

		@Override
		protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
			Button result = super.createButton(parent, id, label, defaultButton);
			if (id == IDialogConstants.OK_ID) {
				updateOkButton(result);
			}
			return result;
		}

		private void updateOkButton(Button okbutton) {
			String key = paramNameText.getText();
			if (key.isEmpty()) {
				okbutton.setEnabled(false);
				resetMessage();
				return;
			}
			if (modifying) {
				if (key.equals(initialParamName)) {
					okbutton.setEnabled(true);
					resetMessage();
					return;
				}
				//the key was changed
			}
			if (isParameterAlreadyPresent()) {
				setMessage("Entry with key: " + key + " already exists.", IMessageProvider.ERROR);
				okbutton.setEnabled(false);
			} else {
				okbutton.setEnabled(true);
				resetMessage();
			}
		}

		private void createParamName(Composite container) {
			Label lbtFirstName = new Label(container, SWT.NONE);
			lbtFirstName.setText("Name:");

			GridData dataFirstName = new GridData();
			dataFirstName.grabExcessHorizontalSpace = true;
			dataFirstName.horizontalAlignment = GridData.FILL;

			paramNameText = new Text(container, SWT.BORDER);
			paramNameText.setLayoutData(dataFirstName);
			paramNameText.setText(ObjectUtils.nullDefault(paramName, ""));
			paramNameText.setMessage("Name");

			if (KEY_STROKE_CODE_COMPLETION != null && keyContentProposalProvider != null) {
				ContentProposalAdapter adapter = new ContentProposalAdapter(paramNameText, new TextContentAdapter(),
						keyContentProposalProvider, KEY_STROKE_CODE_COMPLETION, null);
				adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
			}
		}

		private boolean isParameterAlreadyPresent() {
			String key = paramNameText.getText();
			for (Entry<String, String> entry : entries) {
				if (key.equals(entry.getKey())) {
					return true;
				}
			}
			return false;
		}

		private void createParamValue(Composite container) {
			Label lbtLastName = new Label(container, SWT.NONE);
			lbtLastName.setText("Value:");

			GridData dataLastName = new GridData();
			dataLastName.grabExcessHorizontalSpace = true;
			dataLastName.horizontalAlignment = GridData.FILL;
			paramValueText = new Text(container, SWT.BORDER);
			paramValueText.setLayoutData(dataLastName);
			paramValueText.setText(ObjectUtils.nullDefault(paramValue, ""));
			paramValueText.setMessage("Value");

			if (KEY_STROKE_CODE_COMPLETION != null && valueContentProposalSupplier != null) {
				ContentProposalAdapter adapter = new ContentProposalAdapter(paramValueText, new TextContentAdapter(),
						new IContentProposalProvider() {
							@Override
							public IContentProposal[] getProposals(String contents, int position) {
								IContentProposalProvider provider = valueContentProposalSupplier
										.apply(paramNameText.getText());
								if (provider == null) {
									return EMPTY_ICONTENTPROPOSALS_ARRAY;
								}
								return provider.getProposals(contents, position);
							}
						}, KEY_STROKE_CODE_COMPLETION, null);
				adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
			}
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected void okPressed() {
			paramName = paramNameText.getText();
			paramValue = paramValueText.getText();
			super.okPressed();
		}

		public String getParameterName() {
			return paramName;
		}

		public String getParameterValue() {
			return paramValue;
		}
	}
}
