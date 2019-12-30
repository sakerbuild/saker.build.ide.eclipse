package saker.build.ide.eclipse;

public interface ISakerBuildInfoConsole {
	public interface BuildStateObserver {
		public void buildStarted(BuildInterfaceAccessor accessor);

		public void buildEnded(BuildInterfaceAccessor accessor);
	}

	public interface BuildInterfaceAccessor {
		public void stop();

		public void interruptAndStop();
	}

	public void addBuildStateObserver(BuildStateObserver observer);

	public void removeBuildStateObserver(BuildStateObserver observer);
}
