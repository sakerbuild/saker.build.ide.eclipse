package saker.build.ide.eclipse.properties;

import java.util.Objects;

import org.eclipse.jface.viewers.IElementComparer;

public final class TreePropertyItemIdentifierElementComparer implements IElementComparer {
	@Override
	public int hashCode(Object element) {
		return Objects.hashCode(element);
	}

	@Override
	public boolean equals(Object a, Object b) {
		if (a instanceof TreePropertyItem && b instanceof TreePropertyItem) {
			return ((TreePropertyItem<?>) a).identifier == ((TreePropertyItem<?>) b).identifier;
		}
		return Objects.equals(a, b);
	}
}