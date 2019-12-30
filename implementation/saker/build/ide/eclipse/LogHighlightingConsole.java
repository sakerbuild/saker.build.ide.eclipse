package saker.build.ide.eclipse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.custom.LineBackgroundEvent;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.TextConsolePage;
import org.eclipse.ui.console.TextConsoleViewer;
import org.eclipse.ui.part.IPageBookViewPage;
import org.eclipse.ui.themes.ITheme;

public class LogHighlightingConsole extends IOConsole implements LineBackgroundListener {
	//IWorkbenchThemeConstants.ACTIVE_TAB_BG_END
	private static final String BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME = "org.eclipse.ui.workbench.ACTIVE_TAB_BG_END";

	public static LogHighlightingConsole findExistingConsole(String type) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		for (IConsole con : conMan.getConsoles()) {
			if (type.equals(con.getType())) {
				LogHighlightingConsole console = (LogHighlightingConsole) con;
				console.ensureInit();
				conMan.showConsoleView(console);
				return console;
			}
		}
		return null;
	}

	protected IPageBookViewPage createdPage;
	private volatile boolean initialized;

	private Map<String, Color> lightSeverityColorMap = new LinkedHashMap<>();
	private Map<String, Color> darkSeverityColorMap = new LinkedHashMap<>();
	private Collection<Color> colors = new ArrayList<>();

	private Map<String, Color> currentThemeColorMap;
	private IPropertyChangeListener themeListener;

	{
		themeListener = new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME.equals(event.getProperty())) {
					RGB rgb = (RGB) event.getNewValue();
					currentThemeColorMap = rgb.getHSB()[2] < 0.4f ? darkSeverityColorMap : lightSeverityColorMap;
				}
			}
		};
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		currentTheme.getColorRegistry().addListener(themeListener);
		RGB rgb = currentTheme.getColorRegistry().getRGB(BACKGROUND_COLOR_REGISTRY_PROPERTY_NAME);
		currentThemeColorMap = rgb.getHSB()[2] < 0.4f ? darkSeverityColorMap : lightSeverityColorMap;
	}

	public LogHighlightingConsole(String name, String consoleType) {
		super(name, consoleType, null, StandardCharsets.UTF_8, true);
	}

	@Override
	protected void init() {
		super.init();

		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				Color errorlight = new Color(Display.getDefault(), 254, 231, 224);
				Color infolight = new Color(Display.getDefault(), 244, 247, 254);
				Color warnlight = new Color(Display.getDefault(), 254, 243, 218);
				Color successlight = new Color(Display.getDefault(), 217, 242, 221);

				Color errordark = new Color(Display.getDefault(), 130, 0, 3);
				Color infodark = new Color(Display.getDefault(), 0, 80, 160);
				Color warndark = new Color(Display.getDefault(), 148, 133, 27);
				Color successdark = new Color(Display.getDefault(), 0, 110, 3);

				//put in prefix order
				lightSeverityColorMap.put("fatal error", errorlight);
				lightSeverityColorMap.put("error", errorlight);
				lightSeverityColorMap.put("info", infolight);
				lightSeverityColorMap.put("warning", warnlight);
				lightSeverityColorMap.put("success", successlight);

				darkSeverityColorMap.put("fatal error", errordark);
				darkSeverityColorMap.put("error", errordark);
				darkSeverityColorMap.put("info", infodark);
				darkSeverityColorMap.put("warning", warndark);
				darkSeverityColorMap.put("success", successdark);

				colors.add(errorlight);
				colors.add(infolight);
				colors.add(warnlight);
				colors.add(successlight);
				colors.add(errordark);
				colors.add(infodark);
				colors.add(warndark);
				colors.add(successdark);
			}
		});
	}

	@Override
	protected void dispose() {
		super.dispose();
		ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		currentTheme.getColorRegistry().removeListener(themeListener);
		for (Color c : colors) {
			c.dispose();
		}
	}

	@Override
	public IPageBookViewPage createPage(IConsoleView view) {
		createdPage = super.createPage(view);
		return createdPage;
	}

//	private static boolean isAnySideAlphabetic(String str, String found, int idx) {
//		if (idx > 0) {
//			char c = str.charAt(idx - 1);
//			if (Character.isAlphabetic(c)) {
//				return true;
//			}
//		}
//		int afteridx = idx + found.length();
//		if (afteridx < str.length()) {
//			char c = str.charAt(afteridx);
//			if (Character.isAlphabetic(c)) {
//				return true;
//			}
//		}
//		return false;
//	}
//
//	public static String getSeverityFromLine(String line) {
//		line = line.toLowerCase(Locale.US);
//		for (String severitystr : SEVERITIES) {
//			int idx = line.indexOf(severitystr);
//			if (idx >= 0) {
//				if (!isAnySideAlphabetic(line, severitystr, idx)) {
//					return severitystr;
//				}
//			}
//		}
//		return null;
//	}

	@Override
	public void lineGetBackground(LineBackgroundEvent event) {
		String line = event.lineText.toLowerCase(Locale.US);
		for (Entry<String, Color> entry : currentThemeColorMap.entrySet()) {
			String severitystr = entry.getKey();
			int startidx = 0;
			int sevstrlen = severitystr.length();
			int linelen = line.length();
			while (startidx + sevstrlen <= linelen) {
				int idx = line.indexOf(severitystr, startidx);
				if (idx < 0) {
					break;
				}
				if (idx + sevstrlen == linelen) {
					//if no characters are after the severity, do not highlight the line
					break;
				}
				startidx = idx + sevstrlen;
				if (idx > 0) {
					char beforec = line.charAt(idx - 1);
					if (beforec != ')' && beforec != ']' && beforec != ':' && !Character.isWhitespace(beforec)) {
						continue;
					}
				}
				if (idx + sevstrlen < linelen) {
					char afterc = line.charAt(idx + sevstrlen);
					if (afterc != ':' && !Character.isWhitespace(afterc)) {
						continue;
					}
				}
				event.lineBackground = entry.getValue();
				return;
			}
		}
//		Matcher matcher = ProjectBuilder.CONSOLE_MARKER_PATTERN.matcher(event.lineText);
//		if (matcher.matches()) {
//			String severity = matcher.group(ProjectBuilder.CONSOLE_MARKER_GROUP_SEVERITY);
//			if (severity != null) {
//				event.lineBackground = severityColorMap.get(severity.toLowerCase());
//			}
//		}
	}

	public synchronized void ensureInit() {
		if (initialized) {
			return;
		}
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				if (!(createdPage instanceof TextConsolePage)) {
					return;
				}
				TextConsoleViewer viewer = ((TextConsolePage) createdPage).getViewer();
				if (viewer == null) {
					return;
				}
				StyledText widget = viewer.getTextWidget();
				if (widget == null) {
					return;
				}
				widget.addLineBackgroundListener(LogHighlightingConsole.this);

				doInitialization(widget);

				initialized = true;
			}
		});
	}

	protected void doInitialization(StyledText widget) {
	}

}