package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.location.Location;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.diagnostics.android.DiagnosticsRuntimeTestAccess;
import me.aap.fermata.diagnostics.export.DiagnosticReportExporter;
import me.aap.fermata.ui.glass.speedo.TripLogTestAccess;

/** Everything a detailed report can carry, fed with private values; none may reach the ZIP. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DetailedReportPrivacyTest {
	private static final String[] SECRETS = {
			"21.0281", "105.8342", "token=abc123", "/storage/emulated/0/Music", "My Song",
			"user@example.com", "hunter2"};

	@org.junit.Before
	public void setUp() {
		DiagnosticsRuntimeTestAccess.reset();
	}

	@After
	public void tearDown() {
		DiagnosticsRuntimeTestAccess.reset();
	}

	@Test
	public void exportedReportHoldsNoPrivateValues() throws Exception {
		Application app = RuntimeEnvironment.getApplication();
		AndroidDiagnosticsRuntime runtime = AndroidDiagnosticsRuntime.install(app);
		runtime.setDetailedEnabled(true);

		Map<String, Object> attrs = new LinkedHashMap<>();
		attrs.put("url", "https://user:hunter2@host/a?token=abc123");
		attrs.put("uri", "file:///storage/emulated/0/Music/My Song.mp3");
		attrs.put("title", "My Song");
		attrs.put("account", "user@example.com");
		attrs.put("provider", "gps");
		runtime.recordDetailed("speedo", "speedo_fix_summary", attrs);
		new LogFailureLimiter(5, 50, 10_000).record(runtime.getRecorder(), true,
				new IllegalStateException("user@example.com at 21.0281,105.8342 token=abc123"),
				new StackTraceElement("me.aap.X", "y", "X.java", 1), 0);

		Location a = new Location("gps");
		a.setLatitude(21.0281);
		a.setLongitude(105.8342);
		a.setAccuracy(5);
		Location b = new Location(a);
		b.setLatitude(21.0291);
		TripLogTestAccess.record(app.getExternalFilesDir("trip_logs"), a, b);

		File report = DiagnosticReportExporter.create(app, runtime.getRecorder());
		String text = unzip(report);
		assertTrue(text, text.contains("trip/"));
		assertTrue(text, text.contains("speedo_fix_summary"));
		for (String secret : SECRETS) assertFalse(secret, text.contains(secret));
		assertFalse(Pattern.compile("\\b(21|105)\\.\\d{3,}").matcher(text).find());
	}

	/** All entry names and contents of the ZIP as one text. */
	private static String unzip(File zip) throws Exception {
		StringBuilder text = new StringBuilder();
		try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
				text.append(e.getName()).append('\n');
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				in.transferTo(out);
				text.append(out.toString(StandardCharsets.UTF_8)).append('\n');
			}
		}
		return text.toString();
	}
}
