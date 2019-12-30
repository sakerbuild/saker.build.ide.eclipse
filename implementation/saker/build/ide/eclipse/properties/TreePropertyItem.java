package saker.build.ide.eclipse.properties;

import java.util.Iterator;

public class TreePropertyItem<T> {
	public final int identifier;
	public T property;

	public TreePropertyItem(int identifier, T property) {
		this.identifier = identifier;
		this.property = property;
	}

	@Override
	public int hashCode() {
		return identifier;
	}

	public static void deletePropertyItem(Iterable<? extends TreePropertyItem<?>> items, TreePropertyItem<?> item) {
		if (items == null) {
			return;
		}
		for (Iterator<? extends TreePropertyItem<?>> it = items.iterator(); it.hasNext();) {
			TreePropertyItem<?> listitem = it.next();
			if (item.identifier == listitem.identifier) {
				it.remove();
			}
		}
	}
}