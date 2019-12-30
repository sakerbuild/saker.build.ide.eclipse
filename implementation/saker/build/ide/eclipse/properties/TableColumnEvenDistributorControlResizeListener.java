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