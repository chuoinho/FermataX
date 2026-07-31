package me.aap.fermata.diagnostics.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

final class EmergencyCrashWriter {
	static final int MAX_REPORT_BYTES = 128 * 1024;

	private EmergencyCrashWriter() {
	}

	static void write(File directory, long timestamp, String processName, int processId,
			Thread thread, Throwable error, List<String> breadcrumbs) {
		try {
			if (!directory.isDirectory() && !directory.mkdirs()) return;
			byte[] report = EmergencyCrashReportFormatter.format(timestamp, processName, processId,
					thread, error, breadcrumbs, MAX_REPORT_BYTES);
			File file = new File(directory, "crash-" + timestamp + '-' + processId + ".json");
			try (FileOutputStream output = new FileOutputStream(file, false)) {
				output.write(report);
				output.flush();
				output.getFD().sync();
			}
		} catch (IOException | RuntimeException ignored) {
			// The app is already crashing. Diagnostics must never replace the original failure.
		}
	}
}
