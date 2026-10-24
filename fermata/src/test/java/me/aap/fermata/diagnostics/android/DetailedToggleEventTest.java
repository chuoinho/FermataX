package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DetailedToggleEventTest {
	private AndroidDiagnosticsRuntime runtime;

	@After
	public void tearDown() {
		if (runtime != null) runtime.shutdown();
	}

	@Test
	public void toggleIsRecordedAndNothingDetailedFollowsDisable() throws Exception {
		Application app = RuntimeEnvironment.getApplication();
		runtime = AndroidDiagnosticsRuntime.install(app);
		runtime.setDetailedEnabled(true);
		runtime.recordDetailed("speedo", "speedo_signal_changed", null);
		runtime.setDetailedEnabled(false);
		runtime.recordDetailed("speedo", "speedo_fix_summary", null);
		assertTrue(runtime.flush(3000L));
		String journal = journal(app);
		int on = journal.indexOf("\"event\":\"detailed_changed\",\"data\":{\"enabled\":true}");
		int off = journal.indexOf("\"event\":\"detailed_changed\",\"data\":{\"enabled\":false}");
		assertTrue(journal, on >= 0);
		assertTrue(journal, off > on);
		assertTrue(journal.contains("speedo_signal_changed"));
		assertTrue(journal, !journal.contains("speedo_fix_summary"));
	}

	private static String journal(Application app) throws Exception {
		StringBuilder text = new StringBuilder();
		List<File> stack = new ArrayList<>(List.of(app.getNoBackupFilesDir(), app.getFilesDir()));
		while (!stack.isEmpty()) {
			File f = stack.remove(stack.size() - 1);
			File[] children = f.listFiles();
			if (children != null) stack.addAll(List.of(children));
			else if (f.getName().endsWith(".jsonl")) {
				text.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
			}
		}
		return text.toString();
	}
}
