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