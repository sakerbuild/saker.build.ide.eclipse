package saker.build.ide.eclipse.extension.ideconfig;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

public interface IIDEProjectConfigurationEntry {
	public default IIDEProjectConfigurationEntry[] getSubEntries() {
		return null;
	}

	public void setSelected(boolean selected);

	public boolean isSelected();

	public default String getLabel() {
		return this.toString();
	}

	public default StyledString getStyledLabel() {
		return new StyledString(getLabel());
	}

	public default Image getImage() {
		return null;
	}
}
