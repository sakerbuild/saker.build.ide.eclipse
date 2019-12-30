package saker.build.ide.eclipse;

import java.nio.file.Path;

import org.osgi.framework.BundleContext;

public class ImplementationStartArguments {
	public final Activator activator;
	public final BundleContext bundleContext;
	public final Path sakerJarPath;

	public ImplementationStartArguments(Activator activator, BundleContext bundleContext, Path sakerJarPath) {
		this.activator = activator;
		this.bundleContext = bundleContext;
		this.sakerJarPath = sakerJarPath;
	}

}
