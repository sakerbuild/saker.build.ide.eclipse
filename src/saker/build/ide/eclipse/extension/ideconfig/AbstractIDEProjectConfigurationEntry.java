package saker.build.ide.eclipse.extension.ideconfig;

public class AbstractIDEProjectConfigurationEntry implements IIDEProjectConfigurationEntry {
	private boolean selected = true;

	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	@Override
	public boolean isSelected() {
		return selected;
	}
}
