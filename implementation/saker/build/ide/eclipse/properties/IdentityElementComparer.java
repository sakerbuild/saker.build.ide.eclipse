package saker.build.ide.eclipse.properties;

import org.eclipse.jface.viewers.IElementComparer;

public final class IdentityElementComparer implements IElementComparer {
	@Override
	public int hashCode(Object element) {
		return System.identityHashCode(element);
	}

	@Override
	public boolean equals(Object a, Object b) {
		return a == b;
	}
}