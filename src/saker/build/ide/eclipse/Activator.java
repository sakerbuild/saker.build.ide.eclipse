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
package saker.build.ide.eclipse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.CompositeImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {
	private static final String SAKER_BUILD_RUNTIME_JAR_PATH = "saker.build.jar";
	private static final String IDESUPPORT_RUNTIME_JAR_PATH = "saker.build-ide.jar";

	// The plug-in ID
	public static final String PLUGIN_ID = "saker.build.ide.eclipse"; //$NON-NLS-1$

	public static final String EXTENSION_POINT_ID_IDE_CONFIGURATION_PARSER = "saker.build.ide.eclipse.extension.ideConfigurationTypeHandler";
	public static final String EXTENSION_POINT_ID_ENVIRONMENT_USER_PARAMETER_CONTRIBUTOR = "saker.build.ide.eclipse.extension.environmentUserParameterContributor";
	public static final String EXTENSION_POINT_ID_EXECUTION_USER_PARAMETER_CONTRIBUTOR = "saker.build.ide.eclipse.extension.executionUserParameterContributor";
	public static final String EXTENSION_POINT_ID_SCRIPT_OUTLINE_DESIGNER = "saker.build.ide.eclipse.extension.scriptOutlineDesigner";
	public static final String EXTENSION_POINT_ID_SCRIPT_INFORMATION_DESIGNER = "saker.build.ide.eclipse.extension.scriptInformationDesigner";
	public static final String EXTENSION_POINT_ID_SCRIPT_PROPOSAL_DESIGNER = "saker.build.ide.eclipse.extension.scriptProposalDesigner";

	public static final ImageDescriptor IMAGE_DESCRIPTOR_EXT_POINT = getImageDescriptor("icons/ext_point_obj.png");
	public static final Image IMAGE_EXT_POINT = createImage(IMAGE_DESCRIPTOR_EXT_POINT);

	// The shared instance
	private static Activator plugin;

	private JarFile ideSupportJar;
	private JarFile sakerJar;

	private ImplementationClassLoader implClassLoader;
	private Object implActivator;

	public ImplementationClassLoader getImplClassLoader() {
		return implClassLoader;
	}

	public Object getImplActivator() {
		return implActivator;
	}

	public IDialogSettings getProposalDialogSettings() {
		return getDialogSettingsSection("BuildEditorProposalDialog");
	}

	public IDialogSettings getHoverDialogSettings() {
		return getDialogSettingsSection("BuildEditorFileHoverDialog");
	}

	private synchronized IDialogSettings getDialogSettingsSection(String sectionname) {
		IDialogSettings settings = getDialogSettings();
		IDialogSettings section = settings.getSection(sectionname);
		if (section == null) {
			return settings.addNewSection(sectionname);
		}
		return section;
	}

	private static JarFile createMultiReleaseJarFile(Path jarpath) throws IOException {
		try {
			Class<?> versionclass = Class.forName("java.lang.Runtime$Version", false, null);
			Constructor<JarFile> constructor = JarFile.class.getConstructor(File.class, boolean.class, int.class,
					versionclass);
			Method versionmethod = Runtime.class.getMethod("version");
			Object runtimeversion = versionmethod.invoke(null);
			return constructor.newInstance(jarpath.toFile(), true, ZipFile.OPEN_READ, runtimeversion);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			return new JarFile(jarpath.toFile());
		}
	}

	@Override
	public void start(BundleContext context) throws Exception {
		plugin = Activator.this;
		super.start(context);

		try {
			Path sakerjarpath = exportEmbeddedJar(SAKER_BUILD_RUNTIME_JAR_PATH);
			Path idesupportjarpath = exportEmbeddedJar(IDESUPPORT_RUNTIME_JAR_PATH);

			this.sakerJar = createMultiReleaseJarFile(sakerjarpath);
			this.ideSupportJar = createMultiReleaseJarFile(idesupportjarpath);

			implClassLoader = new ImplementationClassLoader(context.getBundle(),
					Arrays.asList(sakerJar, ideSupportJar));
			Class<?> c = Class.forName("saker.build.ide.eclipse.ImplActivator", false, implClassLoader);
			implActivator = c.getConstructor().newInstance();
			c.getMethod("start", ImplementationStartArguments.class).invoke(implActivator,
					new ImplementationStartArguments(this, context, sakerjarpath));
		} catch (Exception e) {
			getLog().error("Failed to start saker.build plugin.", e);
		}

	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		if (implActivator instanceof AutoCloseable) {
			try {
				((AutoCloseable) implActivator).close();
			} catch (Exception e) {
				getLog().error("Failed to close saker.build plugin implementation.", e);
			}
		}
		try {
			super.stop(context);
		} finally {
			//close the jars after the super.stop is called, as the jars are backing the implementation classloader,
			// so they might still be used in some callbacks
			if (ideSupportJar != null) {
				try {
					ideSupportJar.close();
				} catch (IOException e) {
					getLog().error("Failed to close saker.build IDE support implementation JAR.", e);
				}
			}
			if (sakerJar != null) {
				try {
					sakerJar.close();
				} catch (IOException e) {
					getLog().error("Failed to close saker.build implementation JAR.", e);
				}
			}
		}
	}

	public static Path getAbsolutePath(IResource resource) {
		return Paths.get(resource.getLocationURI());
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		Activator r = plugin;
		if (r == null) {
			throw new IllegalStateException("Plugin not inited.");
		}
		return r;
	}

	private Path exportEmbeddedJar(String jarname) throws IOException {
		URL jarentry = FileLocator.resolve(getBundle().getEntry(jarname));
		URLConnection conn = jarentry.openConnection();
		File jarfile = getStateLocation().append(jarname).toFile();
		long existinglast = jarfile.lastModified();
		Path result = jarfile.toPath();
		try (InputStream is = conn.getInputStream()) {
			long lastmodified = conn.getLastModified();
			if (existinglast == 0 || existinglast != lastmodified) {
				Files.copy(is, result, StandardCopyOption.REPLACE_EXISTING);
				Files.setLastModifiedTime(result, FileTime.fromMillis(lastmodified));
			}
		}
		return result;
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in relative path
	 *
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	public static ImageDescriptor getComposedImageDescriptor(ImageDescriptor main, ImageDescriptor sub) {
		if (main == null) {
			if (sub == null) {
				return null;
			}
			return sub;
		}
		if (sub == null) {
			return main;
		}
		return new CompositeImageDescriptor() {
			@Override
			protected Point getSize() {
				CachedImageDataProvider libimg = createCachedImageDataProvider(main);
				return new Point(libimg.getWidth(), libimg.getHeight());
			}

			@Override
			protected void drawCompositeImage(int width, int height) {
				drawImage(createCachedImageDataProvider(main), 0, 0);
				drawImage(createCachedImageDataProvider(sub), 0, 0);
			}
		};
	}

	public static Image getComposedImage(ImageDescriptor main, ImageDescriptor sub) {
		ImageDescriptor descriptor = getComposedImageDescriptor(main, sub);
		if (descriptor == null) {
			return null;
		}
		return descriptor.createImage(false);
	}

	public static Image getImageFromPlugin(String pluginId, String imageFilePath) {
		ImageDescriptor descriptor = AbstractUIPlugin.imageDescriptorFromPlugin(pluginId, imageFilePath);
		if (descriptor == null) {
			return null;
		}
		return descriptor.createImage(false);
	}

	public static Image createImage(ImageDescriptor descriptor) {
		if (descriptor == null) {
			return null;
		}
		return descriptor.createImage(false);
	}
}
