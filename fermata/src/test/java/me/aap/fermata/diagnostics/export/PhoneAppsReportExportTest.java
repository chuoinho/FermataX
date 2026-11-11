package me.aap.fermata.diagnostics.export;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/** The Phone Apps report rides in the diagnostics ZIP and is shared alone from Readiness; Mobile has none. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class PhoneAppsReportExportTest {
	private final CarAppsFeature.Backend saved = CarAppsFeature.backend();

	@After public void tearDown() {
		CarAppsFeature.setBackend(saved);
	}

	private static void useReport(String text) {
		CarAppsFeature.setBackend(new CarAppsFeature.Backend() {
			@Override public FutureSupplier<Boolean> requestRoot() {
				return null;
			}

			@Override public ActivityFragment createFragment() {
				return null;
			}

			@Override public String report(Context ctx) {
				return text;
			}
		});
	}

	private static String entry(File zip, String name) throws Exception {
		try (ZipFile z = new ZipFile(zip)) {
			ZipEntry e = z.getEntry(name);
			return (e == null) ? null : new String(z.getInputStream(e).readAllBytes(), UTF_8);
		}
	}

	@Test public void theExportZipHoldsThePhoneAppsReport() throws Exception {
		Context ctx = RuntimeEnvironment.getApplication();
		useReport("=== FermataX Phone Apps report ===\nchannel: adb\n");
		File zip = DiagnosticReportExporter.create(ctx, null);
		assertEquals("=== FermataX Phone Apps report ===\nchannel: adb\n", entry(zip, "phone_apps_report.txt"));
	}

	@Test public void withoutPhoneAppsThereIsNoReport() throws Exception {
		Context ctx = RuntimeEnvironment.getApplication();
		CarAppsFeature.setBackend(null); // the Mobile flavor never installs a backend
		assertNull(CarAppsFeature.report(ctx));
		assertNull(entry(DiagnosticReportExporter.create(ctx, null), "phone_apps_report.txt"));
	}

	@Test public void aReportFailingThePrivacyCheckIsLeftOut() throws Exception {
		Context ctx = RuntimeEnvironment.getApplication();
		useReport("see https://example.com/x");
		assertNull(entry(DiagnosticReportExporter.create(ctx, null), "phone_apps_report.txt"));
	}

	@Test public void theSharedReportIsOneTextFileTheProviderServesAndClearRemoves() throws Exception {
		Context ctx = RuntimeEnvironment.getApplication();
		useReport("first");
		File f = DiagnosticReportExporter.createPhoneAppsReport(ctx);
		useReport("second");
		assertEquals(f, DiagnosticReportExporter.createPhoneAppsReport(ctx));
		assertEquals("second", new String(Files.readAllBytes(f.toPath()), UTF_8));
		assertTrue(f.getName().endsWith(".txt"));
		assertEquals("in the FileProvider path", new File(ctx.getCacheDir(), "diagnostics/reports").getCanonicalFile(),
				f.getParentFile().getCanonicalFile());

		DiagnosticReportExporter.clearCachedReports(ctx);
		assertFalse(f.exists());
	}

	/** Mobile builds the same main code: it must reach Phone Apps only through the backend, never by name. */
	@Test public void mainCodeNeverNamesThePhoneAppsImplementation() throws Exception {
		for (String file : new String[]{"diagnostics/export/DiagnosticReportExporter.java",
				"ui/fragment/SettingsDiagnosticsManager.java", "ui/fragment/ReadinessPrefsBuilder.java"}) {
			String src = source(file);
			assertFalse(file, src.contains("me.app.fermatax.auto.carapps.PhoneApps"));
		}
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata").resolve(name);
		if (!Files.isRegularFile(file)) file = root.resolve("fermata/src/main/java/me/aap/fermata").resolve(name);
		return new String(Files.readAllBytes(file), UTF_8);
	}
}
