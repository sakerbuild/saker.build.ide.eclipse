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

import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public final class TableColumnEvenDistributorControlResizeListener extends ControlAdapter {
	private Table table;

	public TableColumnEvenDistributorControlResizeListener(Table table) {
		this.table = table;
	}

	@Override
	public void controlResized(ControlEvent e) {
		int colcount = table.getColumnCount();
		int w = table.getClientArea().width;
		int mod = w % colcount;
		for (int i = 0; i < colcount; i++) {
			TableColumn col = table.getColumn(i);
			int add = mod > 0 ? 1 : 0;
			--mod;
			col.setWidth(w / colcount + add);
		}
	}
}