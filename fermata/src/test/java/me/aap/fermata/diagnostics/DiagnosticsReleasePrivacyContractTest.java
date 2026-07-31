package me.aap.fermata.diagnostics;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class DiagnosticsReleasePrivacyContractTest {
	@Test
	public void releaseDoesNotExposeLegacyLogOrExportIt() throws Exception {
		Path root = repositoryRoot();
		String logger = read(root, "depends/utils/src/main/java/me/aap/utils/log/AndroidLogger.java");
		String app = read(root,
				"fermata/src/main/java/me/aap/fermata/FermataApplication.java");
		String exporter = read(root,
				"fermata/src/main/java/me/aap/fermata/diagnostics/export/DiagnosticReportExporter.java");

		assertTrue(logger.contains("if (BuildConfig.D) android.util.Log"));
		assertTrue(app.contains("if (!BuildConfig.D) return null"));
		assertFalse(app.contains("getExternalFilesDir(null)"));
		assertFalse(exporter.contains("Fermata.log"));
		assertFalse(exporter.contains("Set.of(\"legacy\""));
	}

	private static String read(Path root, String relative) throws Exception {
		return new String(Files.readAllBytes(root.resolve(relative)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
