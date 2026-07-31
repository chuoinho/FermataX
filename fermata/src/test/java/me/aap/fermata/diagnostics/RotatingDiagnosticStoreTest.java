package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RotatingDiagnosticStoreTest {
	private File directory;

	@Before
	public void setUp() {
		directory = new File(System.getProperty("java.io.tmpdir"), "fx-diagnostics-" + UUID.randomUUID());
		assertTrue(directory.mkdirs());
	}

	@After
	public void tearDown() {
		delete(directory);
	}

	@Test
	public void rotatesAndKeepsConfiguredTotalBound() throws Exception {
		DiagnosticConfig config = DiagnosticConfig.builder()
				.maxFileBytes(180)
				.maxTotalBytes(520)
				.maxAgeMillis(10_000L)
				.build();
		RotatingDiagnosticStore store = new RotatingDiagnosticStore(directory, config, "test");
		String line = "{\"payload\":\"" + repeat('x', 72) + "\"}";
		for (int i = 0; i < 20; i++) store.write(line, 10_000L + i);
		store.close();

		File[] files = store.listFiles();
		long total = 0L;
		for (File file : files) {
			total += file.length();
			assertTrue(file.length() <= config.getMaxFileBytes());
		}
		assertTrue(files.length >= 2);
		assertTrue(total <= config.getMaxTotalBytes());
	}

	@Test
	public void removesExpiredJournalOnInitialization() throws Exception {
		File expired = new File(directory, "diagnostics-expired.jsonl");
		try (FileOutputStream out = new FileOutputStream(expired)) {
			out.write("old\n".getBytes(StandardCharsets.UTF_8));
		}
		assertTrue(expired.setLastModified(1000L));
		DiagnosticConfig config = DiagnosticConfig.builder()
				.maxFileBytes(256)
				.maxTotalBytes(1024)
				.maxAgeMillis(100L)
				.build();

		try (RotatingDiagnosticStore store = new RotatingDiagnosticStore(directory, config, "new")) {
			store.write("{}", 2000L);
		}

		assertFalse(expired.exists());
	}

	@Test
	public void rejectsSingleEventLargerThanRotationLimit() throws Exception {
		DiagnosticConfig config = DiagnosticConfig.builder()
				.maxFileBytes(64)
				.maxTotalBytes(256)
				.build();
		try (RotatingDiagnosticStore store = new RotatingDiagnosticStore(directory, config, "large")) {
			try {
				store.write(repeat('x', 80), 2000L);
				fail("Expected oversized event to be rejected");
			} catch (RotatingDiagnosticStore.EventTooLargeException expected) {
				assertTrue(expected.getMessage().contains("exceeds file limit"));
			}
		}
		assertTrue(new RotatingDiagnosticStore(directory, config, "large").listFiles().length == 0);
	}

	private static String repeat(char value, int count) {
		StringBuilder text = new StringBuilder(count);
		for (int i = 0; i < count; i++) text.append(value);
		return text.toString();
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}
}
