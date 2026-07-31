package me.aap.fermata.diagnostics.export;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.Assume;
import org.junit.Test;

public class DiagnosticReportExporterTest {
	@Test
	public void productionCacheLimitsAreThreeReportsAndTwentyFourMiB() {
		assertEquals(3, DiagnosticReportExporter.MAX_CACHED_REPORTS);
		assertEquals(24L * 1024L * 1024L,
				DiagnosticReportExporter.MAX_CACHED_REPORT_BYTES);
	}

	@Test
	public void pruningKeepsOnlyNewestReportsWithoutTouchingUnrelatedFiles() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-report-root");
		Path reports = Files.createDirectories(root.resolve("diagnostics/reports"));
		long now = System.currentTimeMillis();
		Path first = report(reports, "20260730-010001", 1, now - 5_000L);
		Path second = report(reports, "20260730-010002", 1, now - 4_000L);
		Path third = report(reports, "20260730-010003", 1, now - 3_000L);
		Path fourth = report(reports, "20260730-010004", 1, now - 2_000L);
		Path fifth = report(reports, "20260730-010005", 1, now - 1_000L);
		Path unrelated = writeText(reports.resolve("support-notes.txt"), "keep");
		Path lookalike = writeText(
				reports.resolve("FermataX-Diagnostics-v300-not-a-date.zip"), "keep");
		Path unrelatedDirectory = Files.createDirectory(
				reports.resolve("FermataX-Diagnostics-v300-20260730-010006.zip"));

		DiagnosticReportExporter.pruneCachedReports(root.toFile(), reports.toFile(), now,
				3, Long.MAX_VALUE);

		assertFalse(Files.exists(first));
		assertFalse(Files.exists(second));
		assertTrue(Files.exists(third));
		assertTrue(Files.exists(fourth));
		assertTrue(Files.exists(fifth));
		assertTrue(Files.exists(unrelated));
		assertTrue(Files.exists(lookalike));
		assertTrue(Files.isDirectory(unrelatedDirectory));
	}

	@Test
	public void pruningEnforcesTotalByteBudgetOldestFirst() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-budget-root");
		Path reports = Files.createDirectories(root.resolve("diagnostics/reports"));
		long now = System.currentTimeMillis();
		Path first = report(reports, "20260730-020001", 10, now - 3_000L);
		Path second = report(reports, "20260730-020002", 10, now - 2_000L);
		Path third = report(reports, "20260730-020003", 10, now - 1_000L);

		DiagnosticReportExporter.pruneCachedReports(root.toFile(), reports.toFile(), now,
				3, 24L);

		assertFalse(Files.exists(first));
		assertTrue(Files.exists(second));
		assertTrue(Files.exists(third));
		assertEquals(20L, Files.size(second) + Files.size(third));
	}

	@Test
	public void clearRemovesOnlyOwnedDiagnosticReports() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-clear-root");
		Path reports = Files.createDirectories(root.resolve("diagnostics/reports"));
		Path owned = report(reports, "20260730-030001", 4, System.currentTimeMillis());
		Path unrelated = writeText(reports.resolve("other.zip"), "keep");
		Path lookalike = writeText(
				reports.resolve("FermataX-Diagnostics-v300-20260730.zip"), "keep");

		DiagnosticReportExporter.clearCachedReports(root.toFile(), reports.toFile());

		assertFalse(Files.exists(owned));
		assertTrue(Files.exists(unrelated));
		assertTrue(Files.exists(lookalike));
	}

	@Test
	public void snapshotRejectsSymlinkDirectory() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-snapshot-root");
		Path outside = Files.createTempDirectory("diagnostic-snapshot-outside");
		writeText(outside.resolve("diagnostics-test-current.jsonl"),
				"{\"schema_version\":1}\n");
		createDirectorySymlinkOrSkip(root.resolve("journal"), outside);

		assertThrows(IOException.class,
				() -> DiagnosticReportExporter.snapshotFiles(root.toFile()));
	}

	@Test
	public void cleanupDoesNotFollowDirectorySymlinks() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-cleanup-root");
		Path staging = Files.createDirectories(root.resolve("staging/session"));
		Path outside = Files.createTempDirectory("diagnostic-cleanup-outside");
		Path sentinel = writeText(outside.resolve("keep.txt"), "keep");
		createDirectorySymlinkOrSkip(staging.resolve("escape"), outside);

		DiagnosticReportExporter.deleteTree(root.resolve("staging").toFile(), staging.toFile());

		assertFalse(Files.exists(staging, NOFOLLOW_LINKS));
		assertTrue(Files.exists(sentinel));
	}

	@Test
	public void cleanupRejectsTargetOutsideCanonicalRoot() throws Exception {
		Path root = Files.createTempDirectory("diagnostic-canonical-root");
		Path outside = Files.createTempDirectory("diagnostic-canonical-outside");
		Path sentinel = writeText(outside.resolve("keep.txt"), "keep");

		assertThrows(IOException.class, () -> DiagnosticReportExporter.deleteTree(
				root.toFile(), outside.toFile()));
		assertTrue(Files.exists(sentinel));
	}

	private static Path report(Path directory, String stamp, int size, long modified)
			throws IOException {
		Path report = directory.resolve(
				"FermataX-Diagnostics-v300-" + stamp + ".zip");
		Files.write(report, new byte[size]);
		Files.setLastModifiedTime(report, FileTime.fromMillis(modified));
		return report;
	}

	private static Path writeText(Path file, String value) throws IOException {
		return Files.write(file, value.getBytes(UTF_8));
	}

	private static void createDirectorySymlinkOrSkip(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target.toAbsolutePath());
		} catch (IOException | UnsupportedOperationException | SecurityException unsupported) {
			Assume.assumeNoException("Directory symbolic links are unavailable", unsupported);
		}
	}
}
