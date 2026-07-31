package me.aap.fermata.diagnostics.android;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class DiagnosticsCrashHandler implements Thread.UncaughtExceptionHandler {
	private final File directory;
	private final String processName;
	private final int processId;
	private final Supplier<List<String>> breadcrumbs;
	private final Thread.UncaughtExceptionHandler previous;
	private final AtomicBoolean handling = new AtomicBoolean();
	private final AtomicBoolean delegated = new AtomicBoolean();

	DiagnosticsCrashHandler(File directory, String processName, int processId,
			Supplier<List<String>> breadcrumbs, Thread.UncaughtExceptionHandler previous) {
		this.directory = directory;
		this.processName = processName;
		this.processId = processId;
		this.breadcrumbs = breadcrumbs;
		this.previous = previous;
	}

	@Override
	public void uncaughtException(Thread thread, Throwable error) {
		try {
			if (handling.compareAndSet(false, true)) {
				List<String> snapshot = Collections.emptyList();
				List<String> supplied = breadcrumbs.get();
				if (supplied != null) snapshot = supplied;
				EmergencyCrashWriter.write(directory, System.currentTimeMillis(), processName,
						processId, thread, error, snapshot);
			}
		} catch (Throwable ignored) {
			// The emergency path must never replace the app's original failure.
		} finally {
			delegate(thread, error);
		}
	}

	Thread.UncaughtExceptionHandler getPrevious() {
		return previous;
	}

	private void delegate(Thread thread, Throwable error) {
		if (!delegated.compareAndSet(false, true)) return;
		if ((previous != null) && (previous != this)) previous.uncaughtException(thread, error);
	}
}
