package saker.build.ide.eclipse;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class PluginUtils {
	private PluginUtils() {
		throw new UnsupportedOperationException();
	}

	public static Label createLabelWithText(Composite parent, String text) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		return label;
	}
}
