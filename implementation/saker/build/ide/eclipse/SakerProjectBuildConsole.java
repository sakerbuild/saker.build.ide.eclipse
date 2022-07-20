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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

import saker.build.exception.ScriptPositionedExceptionView;
import saker.build.file.path.SakerPath;
import saker.build.ide.eclipse.hyperlink.IFileHyperLink;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerProjectBuildConsole extends LogHighlightingConsole implements ISakerBuildInfoConsole {
	private static final String CONSOLE_PATTERN_STACKTRACE = "\\s*Execution error (([a-zA-Z]:\\\\)?[^:]+):([0-9]+)(:([0-9]+)-([0-9]+))?(:(.*))?";
	private static final Pattern PATTERN_CONSOLE_PATTERN_STACKTRACE = Pattern.compile(CONSOLE_PATTERN_STACKTRACE);
	private static final int STACKTRACE_GROUP_PATH = 1;
	private static final int STACKTRACE_GROUP_LINE = 3;
	private static final int STACKTRACE_GROUP_STARTPOS = 5;
	private static final int STACKTRACE_GROUP_LENGTH = 6;
	private static final int STACKTRACE_GROUP_MESSAGE = 8;

	private static final String CONSOLE_MARKER_STR_PATTERN = "[ \t]*(\\[(?:.*?)\\])?[ \t]*(((.*?)(:(-?[0-9]+)(:([0-9]*)(-([0-9]+))?)?)?):)?[ ]*([wW]arning|[eE]rror|[iI]nfo|[sS]uccess|[fF]atal [eE]rror):[ ]*(.*)";
	private static final int CONSOLE_MARKER_GROUP_DISPLAY_ID = 1;
	private static final int CONSOLE_MARKER_GROUP_PATHANDLOCATION = 3;
	private static final int CONSOLE_MARKER_GROUP_FILEPATH = 4;
	private static final int CONSOLE_MARKER_GROUP_LINE = 6;
	private static final int CONSOLE_MARKER_GROUP_LINESTART = 8;
	private static final int CONSOLE_MARKER_GROUP_LINEEND = 10;
	private static final int CONSOLE_MARKER_GROUP_SEVERITY = 11;
	private static final int CONSOLE_MARKER_GROUP_MESSAGE = 12;
	private static final Pattern CONSOLE_MARKER_PATTERN = Pattern.compile(CONSOLE_MARKER_STR_PATTERN);

	private static final Map<String, Integer> MARKER_SEVERITY_MAP = new HashMap<>();

	static {
		MARKER_SEVERITY_MAP.put("error", IMarker.SEVERITY_ERROR);
		MARKER_SEVERITY_MAP.put("fatal error", IMarker.SEVERITY_ERROR);
		MARKER_SEVERITY_MAP.put("info", IMarker.SEVERITY_INFO);
		MARKER_SEVERITY_MAP.put("warning", IMarker.SEVERITY_WARNING);
	}

	private EclipseSakerIDEProject project;

	private final Set<BuildStateObserver> buildStateObservers = Collections.synchronizedSet(new HashSet<>());

	public SakerProjectBuildConsole(EclipseSakerIDEProject project, String name, String consoleType) {
		super(name, consoleType);
		this.project = project;
		this.addPatternMatchListener(new BuildErrorPatternMatcher(project, this));
		this.addPatternMatchListener(new ExecutionErrorPatternMatcher(project, this));
		this.addPatternMatchListener(new BuildErrorStackPatternMatcher(project, this));
	}

	public EclipseSakerIDEProject getProject() {
		return project;
	}

	//protected method made public
	@Override
	public void setName(String name) {
		super.setName(name);
	}

	public void startBuild(BuildInterfaceAccessor buildaccessor) {
		buildStateObservers.forEach(o -> o.buildStarted(buildaccessor));
	}

	public void endBuild(BuildInterfaceAccessor buildaccessor) {
		buildStateObservers.forEach(o -> o.buildEnded(buildaccessor));
	}

	@Override
	public void addBuildStateObserver(BuildStateObserver observer) {
		buildStateObservers.add(observer);
	}

	@Override
	public void removeBuildStateObserver(BuildStateObserver observer) {
		buildStateObservers.remove(observer);
	}

	public void printCompleteStackTrace(ScriptPositionedExceptionView exc) {
		try (IOConsoleOutputStream err = newOutputStream()) {
			Display display = PlatformUI.getWorkbench().getDisplay();
			err.setColor(display.getSystemColor(SWT.COLOR_RED));
			try (PrintStream ps = new PrintStream(err)) {
				ps.println();
				ps.println("Complete build exception stacktrace:");
				SakerLog.printFormatException(exc, ps, CommonExceptionFormat.FULL);
			}
		} catch (IOException e) {
			project.displayException(SakerLog.SEVERITY_WARNING,
					"Failed to print complete stacktrace for project: " + project.getProject().getName(), e);
		}
	}

	@Override
	protected void doInitialization(StyledText widget) {
		super.doInitialization(widget);
		widget.addLineStyleListener(new LineStyleListener() {
			@Override
			public void lineGetStyle(LineStyleEvent ls) {
				String txt = ls.lineText;
				if (txt.length() < 2) {
					return;
				}
				if (txt.charAt(0) != '[') {
					return;
				}
				int closeidx = txt.indexOf(']');
				if (closeidx < 0) {
					return;
				}
				//XXX WHY do we subtract 2 from the close index?
				//this makes no sense
				//given a line:
				//[abc]message
				//we want to style "abc" in the brackets
				//the index of the closing bracket is 4
				//if we pass 3 as the length of the style range, 
				//    the style gets applied to the closing bracket as well
				//so WHY DO WE NEED TO SUBTRACT TWO?!

				//if we pass 0 as the length, a single character is highlighted (based on tests)

				StyleRange nstyle = new StyleRange(ls.lineOffset + 1, closeidx - 2, null, null, SWT.ITALIC);
				if (ls.styles == null) {
					ls.styles = new StyleRange[] { nstyle };
				} else {
					ls.styles = ArrayUtils.appended(ls.styles, nstyle);
				}
			}
		});
	}

	public static final class BuildErrorStackPatternMatcher implements IPatternMatchListener {
		public static final String STRPATTERN = "[ \\t]+at[ \\t]+(.*):([0-9]+):([0-9]+)-([0-9]+)";
		public static final Pattern PATTERN = Pattern.compile(STRPATTERN);

		private final EclipseSakerIDEProject project;
		private final LogHighlightingConsole console;

		public BuildErrorStackPatternMatcher(EclipseSakerIDEProject project, LogHighlightingConsole console) {
			this.project = project;
			this.console = console;
		}

		@Override
		public void connect(TextConsole console) {
		}

		@Override
		public void disconnect() {
		}

		@Override
		public void matchFound(PatternMatchEvent event) {
			String input;
			try {
				input = console.getDocument().get(event.getOffset(), event.getLength());
				Matcher matcher = PATTERN.matcher(input);
				if (!matcher.matches()) {
					return;
				}
				String path = matcher.group(1);
				if (ObjectUtils.isNullOrEmpty(path)) {
					return;
				}
				String linenum = matcher.group(2);
				if (ObjectUtils.isNullOrEmpty(linenum)) {
					return;
				}
				int linenumber = Integer.parseInt(linenum);
				String linestart = matcher.group(3);
				String lineend = matcher.group(4);

				SakerPath filepath = SakerPath.valueOf(path);
				IFile projectfile = project.getIFileAtExecutionPath(filepath);
				if (projectfile != null) {
					console.addHyperlink(new IFileHyperLink(projectfile, linenumber, linestart, lineend),
							event.getOffset() + matcher.start(1), matcher.end(4) - matcher.start(1));
				}
//				else if (projectfile instanceof IFileStore) {
//					IFileStore fstore = (IFileStore) projectfile;
//					console.addHyperlink(new IFileStoreHyperLink(fstore, linenumber, linestart, lineend),
//							event.getOffset() + matcher.start(1), matcher.end(4) - matcher.start(1));
//				}
			} catch (IllegalArgumentException | BadLocationException e) {
				//for parsing errors
			}
		}

		@Override
		public String getPattern() {
			return STRPATTERN;
		}

		@Override
		public int getCompilerFlags() {
			return 0;
		}

		@Override
		public String getLineQualifier() {
			return null;
		}

	}

	public static final class BuildErrorPatternMatcher implements IPatternMatchListener {
		private final EclipseSakerIDEProject project;
		private final LogHighlightingConsole console;

		public BuildErrorPatternMatcher(EclipseSakerIDEProject sakerideproject, LogHighlightingConsole console) {
			this.project = sakerideproject;
			this.console = console;
		}

		@Override
		public void matchFound(PatternMatchEvent event) {
			try {
				String input = console.getDocument().get(event.getOffset(), event.getLength());
				Matcher matcher = CONSOLE_MARKER_PATTERN.matcher(input);
				if (!matcher.matches()) {
					return;
				}
				String pathwithlocation = matcher.group(CONSOLE_MARKER_GROUP_PATHANDLOCATION);
				String file = matcher.group(CONSOLE_MARKER_GROUP_FILEPATH);
				if (file != null) {
					file = file.trim();
				}
				if (pathwithlocation != null) {
					pathwithlocation = pathwithlocation.trim();
				}
				String line = matcher.group(CONSOLE_MARKER_GROUP_LINE);
				int linenumber = line == null ? 1 : Integer.parseInt(line);
				if (linenumber <= 0) {
					linenumber = 1;
				}

				if (!ObjectUtils.isNullOrEmpty(pathwithlocation)) {
					String linestart = matcher.group(CONSOLE_MARKER_GROUP_LINESTART);
					String lineend = matcher.group(CONSOLE_MARKER_GROUP_LINEEND);

					try {
						SakerPath filepath = SakerPath.valueOf(file);
						IFile projectfile = project.getIFileAtExecutionPath(filepath);
						if (projectfile != null) {
							String message = matcher.group(CONSOLE_MARKER_GROUP_MESSAGE);
							String displayid = matcher.group(CONSOLE_MARKER_GROUP_DISPLAY_ID);
							String severity = matcher.group(CONSOLE_MARKER_GROUP_SEVERITY);

							if (message != null) {
								message = message.trim();
							}
							console.addHyperlink(new IFileHyperLink(projectfile, linenumber, linestart, lineend),
									event.getOffset() + matcher.start(CONSOLE_MARKER_GROUP_PATHANDLOCATION),
									pathwithlocation.length());

							if (severity != null) {
								IMarker marker = projectfile.createMarker(ProjectBuilder.MARKER_TYPE);
								marker.setAttribute(IMarker.SEVERITY, MARKER_SEVERITY_MAP
										.getOrDefault(severity.toLowerCase(), IMarker.SEVERITY_INFO));
								marker.setAttribute(IMarker.LINE_NUMBER, linenumber);
								// if (linestart != null && linestart.length() > 0) {
								// marker.setAttribute(IMarker.CHAR_START, Integer.parseInt(linestart));
								// }
								// if (lineend != null && lineend.length() > 0) {
								// marker.setAttribute(IMarker.CHAR_END, Integer.parseInt(lineend));
								// }
								StringBuilder markermessagesb = new StringBuilder();
								if (!ObjectUtils.isNullOrEmpty(displayid)) {
									markermessagesb.append(displayid);
								}
								markermessagesb.append(severity);
								if (!ObjectUtils.isNullOrEmpty(message)) {
									markermessagesb.append(": ");
									markermessagesb.append(message);
								}
								String markermessage = markermessagesb.toString();
								marker.setAttribute(IMarker.MESSAGE, markermessage);
							}
						}
//						else if (projectfile instanceof IFileStore) {
//							IFileStore fstore = (IFileStore) projectfile;
//							console.addHyperlink(new IFileStoreHyperLink(fstore, linenumber, linestart, lineend),
//									event.getOffset() + matcher.start(CONSOLE_MARKER_GROUP_PATHANDLOCATION),
//									pathwithlocation.length());
//						}
					} catch (CoreException | IllegalArgumentException e) {
						//for parsing errors
					}
				}
			} catch (BadLocationException e1) {
			}
		}

		@Override
		public void disconnect() {
			// patternConsole = null;
		}

		@Override
		public void connect(TextConsole console) {
			// patternConsole = console;
		}

		@Override
		public String getPattern() {
			return CONSOLE_MARKER_STR_PATTERN;
		}

		@Override
		public String getLineQualifier() {
			return null;
		}

		@Override
		public int getCompilerFlags() {
			return 0;
		}
	}

	public static final class ExecutionErrorPatternMatcher implements IPatternMatchListener {
		private final EclipseSakerIDEProject project;
		private final LogHighlightingConsole console;

		public ExecutionErrorPatternMatcher(EclipseSakerIDEProject project, LogHighlightingConsole console) {
			this.project = project;
			this.console = console;
		}

		@Override
		public void matchFound(PatternMatchEvent event) {
			try {
				Matcher matcher = PATTERN_CONSOLE_PATTERN_STACKTRACE
						.matcher(console.getDocument().get(event.getOffset(), event.getLength()));
				if (!matcher.matches()) {
					return;
				}
				String path = matcher.group(STACKTRACE_GROUP_PATH);
				String line = matcher.group(STACKTRACE_GROUP_LINE);
				String startpos = matcher.group(STACKTRACE_GROUP_STARTPOS);
				String length = matcher.group(STACKTRACE_GROUP_LENGTH);
				String message = matcher.group(STACKTRACE_GROUP_MESSAGE);

				int istartpos = startpos == null ? -1 : Integer.parseInt(startpos);
				int ilength = length == null ? -1 : Integer.parseInt(length);

				if (path != null) {
					path = path.trim();
				}
				String passlength = startpos == null ? null : ((istartpos + ilength) + "");

				SakerPath filepath = SakerPath.valueOf(path);
				IFile projectfile = project.getIFileAtExecutionPath(filepath);

				int linkend = matcher.end(STACKTRACE_GROUP_LINE) - matcher.start(STACKTRACE_GROUP_PATH);
				int linkstart = event.getOffset() + matcher.start(STACKTRACE_GROUP_PATH);
				if (projectfile != null) {
					console.addHyperlink(new IFileHyperLink(projectfile, Integer.parseInt(line), startpos, passlength),
							linkstart, linkend);
				}
//				else if (projectfile instanceof IFileStore) {
//					IFileStore fstore = (IFileStore) projectfile;
//					console.addHyperlink(new IFileStoreHyperLink(fstore, Integer.parseInt(line), startpos, passlength),
//							linkstart, linkend);
//				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void disconnect() {
		}

		@Override
		public void connect(TextConsole console) {
		}

		@Override
		public String getPattern() {
			return CONSOLE_PATTERN_STACKTRACE;
		}

		@Override
		public String getLineQualifier() {
			return null;
		}

		@Override
		public int getCompilerFlags() {
			return 0;
		}
	}

}
