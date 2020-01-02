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

import org.eclipse.core.runtime.IProgressMonitor;

import saker.build.runtime.execution.ExecutionProgressMonitor;
import saker.build.task.TaskProgressMonitor;

public class WrappedProgressMonitor implements ExecutionProgressMonitor {
	private IProgressMonitor monitor;
//	private Map<Thread, String> taskNameMap = new IdentityHashMap<>();

	public WrappedProgressMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	@Override
	public boolean isCancelled() {
		return monitor.isCanceled();
	}

//	@Override
//	public synchronized String setTaskName(String name) {
//		String prev;
//		Thread thread = Thread.currentThread();
//		if (name == null) {
//			prev = taskNameMap.remove(thread);
//		} else {
//			prev = taskNameMap.put(thread, name);
//		}
//		monitor.setTaskName(String.join("\n", taskNameMap.values()));
//		return prev;
//	}
//
//	@Override
//	public synchronized String getTaskName() {
//		return taskNameMap.get(Thread.currentThread());
//	}

	@Override
	public TaskProgressMonitor startTaskProgress() {
		return new TaskProgressMonitor() {
			@Override
			public boolean isCancelled() {
				return WrappedProgressMonitor.this.isCancelled();
			}
		};
	}

}
