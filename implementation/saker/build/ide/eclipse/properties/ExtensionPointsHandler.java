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
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import saker.build.ide.eclipse.Activator;
import saker.build.ide.eclipse.ContributedExtensionConfiguration;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ExtensionPointsHandler<T> {
	private TreeViewer extensionsTreeViewer;
	private String noExtensionsText = "No extensions installed.";
	private List<ContributedExtensionConfiguration<T>> extensionContributors = Collections.emptyList();

	private List<Listener> modifyListeners = new ArrayList<>();

	public void setNoExtensionsText(String noExtensionsText) {
		this.noExtensionsText = noExtensionsText;
	}

	public void setExtensionContributors(List<ContributedExtensionConfiguration<T>> extensionContributors) {
		this.extensionContributors = extensionContributors;
	}

	public List<ContributedExtensionConfiguration<T>> getExtensionContributors() {
		return ImmutableUtils.unmodifiableList(extensionContributors);
	}

	public void addModifyListener(Listener listener) {
		modifyListeners.add(listener);
	}

	public void addControl(Composite extensionscomposite) {
		if (extensionContributors.isEmpty()) {
			Label resultlabel = new Label(extensionscomposite, SWT.NONE);
			resultlabel.setText(noExtensionsText);
		} else {
			extensionsTreeViewer = new TreeViewer(
					new Tree(extensionscomposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.BORDER));
			extensionsTreeViewer.setComparer(new IdentityElementComparer());
			if (extensionContributors.size() == 1) {
				extensionsTreeViewer.setAutoExpandLevel(2);
			}
			GridData data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = GridData.FILL;
			data.verticalAlignment = GridData.FILL;
			extensionsTreeViewer.getTree().setLayoutData(data);
			extensionsTreeViewer.setComparer(new IElementComparer() {
				@Override
				public int hashCode(Object element) {
					if (element instanceof ContributedExtensionConfiguration<?>) {
						return ((ContributedExtensionConfiguration<?>) element).getConfigurationElement().hashCode();
					}
					if (element instanceof LabeledItem) {
						LabeledItem label = (LabeledItem) element;
						return Objects.hash(label.getParent(), label.getLabel(), label.getValue());
					}
					return System.identityHashCode(element);
				}

				@Override
				public boolean equals(Object a, Object b) {
					if (a instanceof ContributedExtensionConfiguration<?>
							&& b instanceof ContributedExtensionConfiguration<?>) {
						ContributedExtensionConfiguration<?> aconfig = (ContributedExtensionConfiguration<?>) a;
						ContributedExtensionConfiguration<?> bconfig = (ContributedExtensionConfiguration<?>) b;
						return aconfig.getConfigurationElement() == bconfig.getConfigurationElement()
								&& aconfig.getContributor() == bconfig.getContributor();
					}
					if (a instanceof LabeledItem && b instanceof LabeledItem) {
						LabeledItem al = (LabeledItem) a;
						LabeledItem bl = (LabeledItem) b;
						return equals(al.getParent(), bl.getParent()) && Objects.equals(al.getLabel(), bl.getLabel())
								&& Objects.equals(al.getValue(), bl.getValue());
					}
					return Objects.equals(a, b);
				}
			});
			extensionsTreeViewer.setContentProvider(new ITreeContentProvider() {
				@Override
				public boolean hasChildren(Object element) {
					if (element instanceof ContributedExtensionConfiguration<?>) {
						return true;
					}
					return false;
				}

				@Override
				public Object getParent(Object element) {
					if (element instanceof LabeledItem) {
						return ((LabeledItem) element).getParent();
					}
					return null;
				}

				@Override
				public Object[] getElements(Object inputElement) {
					return ((Collection<?>) inputElement).toArray(ObjectUtils.EMPTY_OBJECT_ARRAY);
				}

				@Override
				public Object[] getChildren(Object parentElement) {
					if (parentElement instanceof ContributedExtensionConfiguration<?>) {
						ContributedExtensionConfiguration<?> extension = (ContributedExtensionConfiguration<?>) parentElement;
						IConfigurationElement configelem = extension.getConfigurationElement();
						String classattr = configelem.getAttribute("class");
						String extensionuniqueid = configelem.getDeclaringExtension().getUniqueIdentifier();
						String contributorname = configelem.getContributor().getName();

						List<LabeledItem> items = new ArrayList<>();
						items.add(new LabeledItem(parentElement, "Class", classattr));
						if (!ObjectUtils.isNullOrEmpty(extensionuniqueid)) {
							items.add(new LabeledItem(parentElement, "Extension ID", extensionuniqueid));
						}
						items.add(new LabeledItem(parentElement, "Contributor", contributorname));
						return items.toArray();
					}
					return ObjectUtils.EMPTY_OBJECT_ARRAY;
				}
			});
			extensionsTreeViewer
					.setLabelProvider(new DelegatingStyledCellLabelProvider(new ExtensionItemLabelProvider()));
			extensionsTreeViewer.addDoubleClickListener(event -> {
				ISelection selection = event.getSelection();
				if (!(selection instanceof IStructuredSelection)) {
					return;
				}
				IStructuredSelection treesel = (IStructuredSelection) selection;
				Object elem = treesel.getFirstElement();
				if (elem == null) {
					return;
				}
				if (elem instanceof ContributedExtensionConfiguration<?>) {
					@SuppressWarnings("unchecked")
					ContributedExtensionConfiguration<T> extension = (ContributedExtensionConfiguration<T>) elem;
					ContributedExtensionConfiguration<T> nextension = new ContributedExtensionConfiguration<>(
							extension.getContributor(), extension.getConfigurationElement(), !extension.isEnabled());
					extensionContributors.set(extensionContributors.indexOf(extension), nextension);
					extensionsTreeViewer.refresh();
					callModifyListeners();
				}
			});

			extensionsTreeViewer.setInput(extensionContributors);
		}
	}

	private static class LabeledItem {
		private Object parent;
		private String label;
		private String value;

		public LabeledItem(Object parent, String label, String value) {
			this.parent = parent;
			this.label = label;
			this.value = value;
		}

		public String getLabel() {
			return label;
		}

		public String getValue() {
			return value;
		}

		public Object getParent() {
			return parent;
		}

	}

	private static final class ExtensionItemLabelProvider extends LabelProvider implements IStyledLabelProvider {
		private static final Image IMG_EXTENSION_ITEM_LABEL_ENABLED;
		private static final Image IMG_EXTENSION_ITEM_LABEL_DISABLED;
		static {
			IMG_EXTENSION_ITEM_LABEL_DISABLED = Activator.getComposedImage(Activator.IMAGE_DESCRIPTOR_EXT_POINT,
					AbstractUIPlugin.imageDescriptorFromPlugin(Activator.PLUGIN_ID, "icons/cross_out.png"));
			IMG_EXTENSION_ITEM_LABEL_ENABLED = Activator.IMAGE_EXT_POINT;
		}

		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		@Override
		public StyledString getStyledText(Object element) {
			StyledString result = new StyledString();
			if (element instanceof ContributedExtensionConfiguration<?>) {
				ContributedExtensionConfiguration<?> extension = (ContributedExtensionConfiguration<?>) element;
				IConfigurationElement configelem = extension.getConfigurationElement();
				String label = configelem.getDeclaringExtension().getLabel();
				if (!ObjectUtils.isNullOrEmpty(label)) {
					result.append(label);
				} else {
					String classattr = configelem.getAttribute("class");
					result.append(classattr);
				}
			} else if (element instanceof LabeledItem) {
				LabeledItem item = (LabeledItem) element;
				result.append(item.getLabel() + ": ", StyledString.QUALIFIER_STYLER);
				String value = item.getValue();
				if (!ObjectUtils.isNullOrEmpty(value)) {
					result.append(value);
				}
			}
			return result;
		}

		@Override
		public Image getImage(Object element) {
			if (element instanceof ContributedExtensionConfiguration<?>) {
				ContributedExtensionConfiguration<?> extension = (ContributedExtensionConfiguration<?>) element;
				boolean enabled = extension.isEnabled();

				return getExtensionItemLabelImage(enabled);
			}
			return null;
		}

		private static Image getExtensionItemLabelImage(boolean enabled) {
			//PDEPluginImages
			if (enabled) {
				return IMG_EXTENSION_ITEM_LABEL_ENABLED;
			}
			return IMG_EXTENSION_ITEM_LABEL_DISABLED;
		}
	}

	public void enableAll() {
		boolean had = false;
		for (int i = 0, n = extensionContributors.size(); i < n; i++) {
			ContributedExtensionConfiguration<T> extension = extensionContributors.get(i);

			if (!extension.isEnabled()) {
				extensionContributors.set(i, new ContributedExtensionConfiguration<>(extension.getContributor(),
						extension.getConfigurationElement(), true));
			}
		}
		if (had) {
			extensionsTreeViewer.refresh();
			callModifyListeners();
		}
	}

	private void callModifyListeners() {
		for (Listener l : modifyListeners) {
			l.handleEvent(new Event());
		}
	}

}
