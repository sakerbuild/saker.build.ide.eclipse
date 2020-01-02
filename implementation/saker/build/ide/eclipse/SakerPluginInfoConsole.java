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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.IOConsoleOutputStream;

import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class SakerPluginInfoConsole extends LogHighlightingConsole implements ISakerPluginInfoConsole {
	public static final String CONSOLE_TYPE = Activator.PLUGIN_ID + ".plugin.console";

	private final EclipseSakerIDEPlugin plugin;

	private final IOConsoleOutputStream openedOutStream;
	private final IOConsoleOutputStream openedErrorStream;
	private final Object writeLock = new Object();

	public SakerPluginInfoConsole(EclipseSakerIDEPlugin plugin, String name, String consoleType) {
		super(name, consoleType);
		this.plugin = plugin;
		openedOutStream = newOutputStream();
		openedErrorStream = newOutputStream();

		openedErrorStream.setColor(Display.getDefault().getSystemColor(SWT.COLOR_RED));
	}

	public void printException(Throwable exc) {
		synchronized (writeLock) {
			try (PrintStream ps = new PrintStream(StreamUtils.closeProtectedOutputStream(openedErrorStream))) {
				exc.printStackTrace(ps);
			}
		}
	}

	public void printlnError(String message) {
		if (message == null) {
			return;
		}
		try {
			synchronized (writeLock) {
				openedErrorStream.write(message);
				openedErrorStream.write(System.lineSeparator());
			}
		} catch (IOException e) {
			// print to standard error
			e.printStackTrace();
		}
	}

	@Override
	protected synchronized void dispose() {
		IOUtils.closePrint(openedOutStream, openedErrorStream);
		super.dispose();
	}

}
