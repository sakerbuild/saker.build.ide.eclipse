package saker.build.ide.eclipse.properties;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import saker.build.file.path.SakerPath;
import saker.build.ide.support.SakerIDEProject;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ValidationUtils {
	private ValidationUtils() {
		throw new UnsupportedOperationException();
	}

	public static final int VALIDATION_SCRIPTING = 1;
	public static final int VALIDATION_REPOSITORY = 2;

	public static String validateClassPathLocation(IDEProjectProperties properties,
			ClassPathLocationIDEProperty property, int validationtype, String tagidentifier) {
		if (property == null) {
			return "Missing class path definition for: " + tagidentifier;
		}
		return property.accept(new ClassPathLocationIDEProperty.Visitor<String, Void>() {
			@Override
			public String visit(JarClassPathLocationIDEProperty property, Void param) {
				String connname = property.getConnectionName();
				if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(connname)
						|| SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(connname)) {
					//these are fine
				} else {
					DaemonConnectionIDEProperty connprop = getDaemonConnectionPropertyForConnectionName(
							properties.getConnections(), connname);
					if (connprop == null) {
						return "Invalid JAR class path daemon connection name for: " + tagidentifier;
					}
				}
				String jarpath = property.getJarPath();
				if (ObjectUtils.isNullOrEmpty(jarpath)) {
					return "Empty JAR class path for: " + tagidentifier;
				} else {
					try {
						SakerPath.valueOf(jarpath);
					} catch (RuntimeException e) {
						return "Invalid JAR class path format for: " + tagidentifier;
					}
				}
				return null;
			}

			@Override
			public String visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
				String urlstr = property.getUrl();
				if (ObjectUtils.isNullOrEmpty(urlstr)) {
					return "Missing URL for JAR class path: " + tagidentifier;
				} else {
				}
				try {
					URL url = new URL(urlstr);
					String protocol = url.getProtocol();
					if (!"http".equals(protocol) && !"https".equals(protocol)) {
						return "Invalid URL protocol: " + urlstr + " for: " + tagidentifier
								+ " (expected http opr https)";
					}
				} catch (MalformedURLException e) {
					return "Invalid URL format: " + urlstr + " for: " + tagidentifier + " (" + e.getMessage() + ")";
				}
				return null;
			}

			@Override
			public String visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
				if (validationtype != VALIDATION_SCRIPTING) {
					return "Invalid class path for: " + tagidentifier;
				}
				return null;
			}

			@Override
			public String visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
				if (validationtype != VALIDATION_REPOSITORY) {
					return "Invalid class path for: " + tagidentifier;
				}
				return null;
			}
		}, null);
	}

	public static String validateServiceEnumerator(ClassPathServiceEnumeratorIDEProperty property, int validationtype,
			String tagidentifier) {
		if (property == null) {
			return "Missing class definition for: " + tagidentifier;
		}
		return property.accept(new ClassPathServiceEnumeratorIDEProperty.Visitor<String, Void>() {
			@Override
			public String visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
				String serviceclass = property.getServiceClass();
				if (ObjectUtils.isNullOrEmpty(serviceclass)) {
					return "Missing service loader class for: " + tagidentifier;
				}
				return null;
			}

			@Override
			public String visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
				String serviceclass = property.getClassName();
				if (ObjectUtils.isNullOrEmpty(serviceclass)) {
					return "Missing class name for: " + tagidentifier;
				}
				return null;
			}

			@Override
			public String visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
				if (validationtype != VALIDATION_SCRIPTING) {
					return "Invalid class specification for: " + tagidentifier;
				}
				return null;
			}

			@Override
			public String visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
				if (validationtype != VALIDATION_REPOSITORY) {
					return "Invalid class specification for: " + tagidentifier;
				}
				return null;
			}
		}, null);
	}

	public static DaemonConnectionIDEProperty getDaemonConnectionPropertyForConnectionName(
			Collection<? extends DaemonConnectionIDEProperty> connections, String name) {
		if (name == null) {
			return null;
		}
		if (ObjectUtils.isNullOrEmpty(connections)) {
			return null;
		}
		for (DaemonConnectionIDEProperty prop : connections) {
			if (name.equals(prop.getConnectionName())) {
				return prop;
			}
		}
		return null;
	}
}
