package saker.build.ide.eclipse.properties;

import java.util.function.Consumer;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;

public abstract class TreePropertyItemChildElement<T> {
	public final TreePropertyItem<T> treeItem;

	public TreePropertyItemChildElement(TreePropertyItem<T> property) {
		this.treeItem = property;
	}

	public Image getImage() {
		return null;
	}

	public abstract String getLabel();

	public abstract String getTitle();

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((treeItem == null) ? 0 : treeItem.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TreePropertyItemChildElement<?> other = (TreePropertyItemChildElement<?>) obj;
		if (treeItem.identifier != other.treeItem.identifier) {
			return false;
		}
		return true;
	}

	public void edit(Shell shell, Consumer<? super T> editfinalizer) {
	}
}