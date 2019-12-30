package saker.build.ide.eclipse.properties;

public interface FinishablePage {
	public default boolean canFinishPage() {
		return true;
	}

	public boolean performFinish();
}