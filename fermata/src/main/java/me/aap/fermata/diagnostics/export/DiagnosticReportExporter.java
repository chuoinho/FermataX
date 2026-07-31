package me.aap.fermata.diagnostics.export;

import static android.os.Build.VERSION.SDK_INT;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.diagnostics.DiagnosticRecorder;

/** Creates a local, explicitly user-exported support bundle without legacy logs or raw traces. */
public final class DiagnosticReportExporter {
	static final int MAX_CACHED_REPORTS = 3;
	static final long MAX_CACHED_REPORT_BYTES = 24L * 1024L * 1024L;
	private static final long REPORT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L;
	private static final String REPORT_PREFIX = "FermataX-Diagnostics-v";
	private static final String REPORT_SUFFIX = ".zip";
	private static final Pattern REPORT_NAME = Pattern.compile(
			"FermataX-Diagnostics-v[A-Za-z0-9._-]+-[0-9]{8}-[0-9]{6}(?:-[0-9]+)?\\.zip");
	private static final Pattern JOURNAL_NAME = Pattern.compile(
			"diagnostics-[A-Za-z0-9_-]+-(?:current|[0-9]+-[0-9]+)\\.jsonl");
	private static final Pattern CRASH_NAME = Pattern.compile("crash-[0-9]+-[0-9]+\\.json");
	private static final Pattern FORBIDDEN_PAYLOAD = Pattern.compile(
			"(?i)(?:https?://|file:/{1,3}|content://|magnet:\\?|(?:proxy-)?authorization\\s*[:=]|" +
					"(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]+|(?:password|passwd|client_secret|" +
					"access_token|refresh_token|api_key)\\s*[:=]|Fermata\\.log|tombstone)");

	private DiagnosticReportExporter() {
	}

	public static File create(Context context, DiagnosticRecorder recorder) throws IOException {
		File cacheRoot = requireRootDirectory(context.getCacheDir());
		File noBackupRoot = requireRootDirectory(context.getNoBackupFilesDir());
		File reportDir = requireSafeDirectory(cacheRoot,
				new File(cacheRoot, "diagnostics/reports"), true);
		long now = System.currentTimeMillis();
		pruneCachedReports(cacheRoot, reportDir, now, MAX_CACHED_REPORTS,
				MAX_CACHED_REPORT_BYTES, null);
		File stagingRoot = requireSafeDirectory(cacheRoot,
				new File(cacheRoot, "diagnostics/staging"), true);
		File staging = requireSafeDirectory(stagingRoot,
				new File(stagingRoot, UUID.randomUUID().toString()), true);
		File report = null;
		try {
			File journal = requireSafeDirectory(staging, new File(staging, "journal"), true);
			if ((recorder != null) && !recorder.createSnapshot(journal, 5000L)) {
				throw new IOException("Unable to create diagnostics journal snapshot");
			}
			copyCrashSnapshot(noBackupRoot, new File(noBackupRoot, "diagnostics/crash"),
					staging, new File(staging, "crash"));
			File stagedReport = createSafeFile(staging, new File(staging, "report.zip"));
			try (ZipOutputStream zip = new ZipOutputStream(
					new BufferedOutputStream(new FileOutputStream(stagedReport)), UTF_8)) {
				writeText(zip, "manifest.json", manifest(context, recorder));
				for (File source : snapshotFiles(staging)) {
					validateSafePayload(source);
					String parent = source.getParentFile().getName();
					copy(zip, source, parent + '/' + source.getName());
				}
			}
			long reportBytes = stagedReport.length();
			if (reportBytes > MAX_CACHED_REPORT_BYTES) {
				throw new IOException("Diagnostic report exceeds cache retention limit");
			}
			pruneCachedReports(cacheRoot, reportDir, now, MAX_CACHED_REPORTS - 1,
					MAX_CACHED_REPORT_BYTES - reportBytes, null);
			report = publishReport(staging, stagedReport, reportDir);
			pruneCachedReports(cacheRoot, reportDir, System.currentTimeMillis(),
					MAX_CACHED_REPORTS, MAX_CACHED_REPORT_BYTES, report);
			return report;
		} catch (IOException | RuntimeException failure) {
			deletePublishedReport(reportDir, report);
			throw failure;
		} finally {
			try {
				deleteTree(stagingRoot, staging);
			} catch (IOException ignored) {
				// Staging cleanup must not replace the export result.
			}
		}
	}

	@RequiresApi(Build.VERSION_CODES.Q)
	public static String saveToDownloads(Context context, File report) throws IOException {
		String name = report.getName();
		ContentResolver resolver = context.getContentResolver();
		ContentValues values = new ContentValues();
		values.put(MediaStore.Downloads.DISPLAY_NAME, name);
		values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
		values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
		values.put(MediaStore.Downloads.IS_PENDING, 1);
		Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
		if (uri == null) throw new IOException("Unable to create Downloads entry");

		boolean complete = false;
		try (OutputStream output = resolver.openOutputStream(uri, "w");
				BufferedInputStream input = new BufferedInputStream(new FileInputStream(report))) {
			if (output == null) throw new IOException("Unable to open Downloads entry");
			copy(input, output);
			complete = true;
		} finally {
			if (complete) {
				ContentValues ready = new ContentValues();
				ready.put(MediaStore.Downloads.IS_PENDING, 0);
				resolver.update(uri, ready, null, null);
			} else {
				resolver.delete(uri, null, null);
			}
		}
		return name;
	}

	public static boolean supportsDirectDownloads() {
		return SDK_INT >= Build.VERSION_CODES.Q;
	}

	public static void clearCachedReports(Context context) {
		try {
			File cacheRoot = requireRootDirectory(context.getCacheDir());
			clearCachedReports(cacheRoot, new File(cacheRoot, "diagnostics/reports"));
		} catch (IOException ignored) {
			// A malformed cache tree is safer left untouched.
		}
	}

	public static void copyToUri(Context context, File report, Uri destination) throws IOException {
		try (OutputStream output = context.getContentResolver().openOutputStream(destination, "w");
				BufferedInputStream input = new BufferedInputStream(new FileInputStream(report))) {
			if (output == null) throw new IOException("Unable to open report destination");
			copy(input, output);
		}
	}

	static List<File> snapshotFiles(File root) throws IOException {
		File safeRoot = requireRootDirectory(root);
		List<File> files = new ArrayList<>();
		for (String name : new String[]{"journal", "crash"}) {
			File requested = new File(safeRoot, name);
			if (!Files.exists(requested.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
			File directory = requireSafeDirectory(safeRoot, requested, false);
			File[] children = directory.listFiles();
			if (children == null) continue;
			for (File file : children) {
				Pattern expected = "journal".equals(name) ? JOURNAL_NAME : CRASH_NAME;
				if (expected.matcher(file.getName()).matches() &&
						isContainedFile(safeRoot, directory, file)) {
					files.add(file);
				}
			}
		}
		return files;
	}

	private static void copyCrashSnapshot(File sourceRoot, File sourceDirectory,
			File stagingRoot, File destination) throws IOException {
		if (!Files.exists(sourceDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
		File safeSource = requireSafeDirectory(sourceRoot, sourceDirectory, false);
		File safeDestination = requireSafeDirectory(stagingRoot, destination, true);
		File[] crashes = safeSource.listFiles();
		if (crashes == null) return;
		for (File crash : crashes) {
			if (!CRASH_NAME.matcher(crash.getName()).matches() ||
					!isContainedFile(sourceRoot, safeSource, crash)) continue;
			copyFile(crash, createSafeFile(safeDestination,
					new File(safeDestination, crash.getName())));
		}
	}

	private static boolean isContainedFile(File root, File directory, File file)
			throws IOException {
		Path directoryPath = directory.toPath();
		Path filePath = file.toPath();
		if (Files.isSymbolicLink(directoryPath) || Files.isSymbolicLink(filePath) ||
				!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS) ||
				!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) return false;
		File canonicalRoot = root.getCanonicalFile();
		File canonicalDirectory = directory.getCanonicalFile();
		File canonicalFile = file.getCanonicalFile();
		return isWithin(canonicalDirectory, canonicalRoot) &&
				canonicalFile.getParentFile().equals(canonicalDirectory);
	}

	private static void validateSafePayload(File source) throws IOException {
		byte[] bytes = Files.readAllBytes(source.toPath());
		String text = new String(bytes, UTF_8);
		if (FORBIDDEN_PAYLOAD.matcher(text).find()) {
			throw new IOException("Diagnostics privacy validation failed");
		}
		if (source.getName().endsWith(".jsonl")) {
			for (String line : text.split("\\n")) {
				if (line.isEmpty()) continue;
				if (!line.startsWith("{") || !line.endsWith("}") ||
						!line.contains("\"schema_version\":1")) {
					throw new IOException("Invalid diagnostics journal snapshot");
				}
			}
		} else if (!text.startsWith("{") || !text.endsWith("}")) {
			throw new IOException("Invalid diagnostics crash snapshot");
		}
	}

	private static String manifest(Context context, DiagnosticRecorder recorder) {
		StringBuilder manifest = new StringBuilder(512).append('{').append(
				"\"schema_version\":1," +
				"\"package\":\"" + json(context.getPackageName()) + "\"," +
				"\"version_name\":\"" + json(BuildConfig.VERSION_NAME) + "\"," +
				"\"version_code\":" + BuildConfig.VERSION_CODE + ',' +
				"\"build_type\":\"" + json(BuildConfig.BUILD_TYPE) + "\"," +
				"\"android_api\":" + SDK_INT + ',' +
				"\"device_manufacturer\":\"" + json(Build.MANUFACTURER) + "\"," +
				"\"device_model\":\"" + json(Build.MODEL) + "\"," +
				"\"exported_at_ms\":" + System.currentTimeMillis());
		if (recorder != null) {
			DiagnosticRecorder.Stats stats = recorder.getStats();
			manifest.append(",\"recorder\":{")
					.append("\"accepted\":").append(stats.getAccepted()).append(',')
					.append("\"written\":").append(stats.getWritten()).append(',')
					.append("\"write_failures\":").append(stats.getWriteFailures()).append(',')
					.append("\"queued\":").append(stats.getQueued()).append(',')
					.append("\"storage_healthy\":").append(stats.isStorageHealthy())
					.append('}');
		}
		return manifest.append('}').toString();
	}

	private static String reportName() {
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
		String version = BuildConfig.VERSION_NAME.replaceAll("[^A-Za-z0-9._-]", "_");
		if (version.isEmpty()) version = "unknown";
		return REPORT_PREFIX + version + '-' + stamp + REPORT_SUFFIX;
	}

	private static void writeText(ZipOutputStream zip, String name, String value) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value.getBytes(UTF_8));
		zip.closeEntry();
	}

	private static void copy(ZipOutputStream zip, File source, String name) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source))) {
			copy(input, zip);
		}
		zip.closeEntry();
	}

	private static void copy(BufferedInputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[8192];
		for (int read; (read = input.read(buffer)) >= 0; ) output.write(buffer, 0, read);
	}

	private static void copyFile(File source, File destination) throws IOException {
		try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
				BufferedOutputStream output = new BufferedOutputStream(
						new FileOutputStream(destination, false))) {
			copy(input, output);
		}
	}

	static void clearCachedReports(File root, File directory) throws IOException {
		if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
		File safeDirectory = requireSafeDirectory(root, directory, false);
		for (File report : cachedReports(safeDirectory)) Files.delete(report.toPath());
	}

	static void pruneCachedReports(File root, File directory, long now, int maxCount,
			long maxBytes) throws IOException {
		pruneCachedReports(root, directory, now, maxCount, maxBytes, null);
	}

	private static void pruneCachedReports(File root, File directory, long now, int maxCount,
			long maxBytes, File protectedReport) throws IOException {
		if ((maxCount < 0) || (maxBytes < 0L)) {
			throw new IllegalArgumentException("Invalid diagnostics cache limit");
		}
		File safeDirectory = requireSafeDirectory(root, directory, false);
		List<File> reports = cachedReports(safeDirectory);
		for (Iterator<File> iterator = reports.iterator(); iterator.hasNext(); ) {
			File report = iterator.next();
			if (!samePath(report, protectedReport) &&
					((now - report.lastModified()) > REPORT_MAX_AGE_MILLIS)) {
				Files.delete(report.toPath());
				iterator.remove();
			}
		}
		reports.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
		long totalBytes = 0L;
		for (File report : reports) totalBytes = saturatedAdd(totalBytes, report.length());
		while ((reports.size() > maxCount) || (totalBytes > maxBytes)) {
			File victim = null;
			for (File candidate : reports) {
				if (!samePath(candidate, protectedReport)) {
					victim = candidate;
					break;
				}
			}
			if (victim == null) break;
			long length = victim.length();
			Files.delete(victim.toPath());
			reports.remove(victim);
			totalBytes = Math.max(0L, totalBytes - length);
		}
		if ((reports.size() > maxCount) || (totalBytes > maxBytes)) {
			throw new IOException("Unable to enforce diagnostics report cache limit");
		}
	}

	static void deleteTree(File root, File target) throws IOException {
		File safeRoot = requireRootDirectory(root);
		Path rootPath = safeRoot.toPath().toAbsolutePath().normalize();
		Path targetPath = target.toPath().toAbsolutePath().normalize();
		if (targetPath.equals(rootPath) || !targetPath.startsWith(rootPath)) {
			throw new IOException("Diagnostics cleanup target is outside its root");
		}
		if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) return;
		if (!Files.isSymbolicLink(targetPath)) {
			Path canonicalTarget = target.getCanonicalFile().toPath();
			if (!canonicalTarget.startsWith(safeRoot.toPath())) {
				throw new IOException("Diagnostics cleanup target escaped its root");
			}
		}
		Files.walkFileTree(targetPath, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
					throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
				throw error;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException error)
					throws IOException {
				if (error != null) throw error;
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static File requireRootDirectory(File root) throws IOException {
		Path path = root.toPath().toAbsolutePath().normalize();
		if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Invalid diagnostics root directory");
		}
		return path.toFile().getCanonicalFile();
	}

	private static File requireSafeDirectory(File root, File directory, boolean create)
			throws IOException {
		File safeRoot = requireRootDirectory(root);
		Path lexicalRoot = root.toPath().toAbsolutePath().normalize();
		Path requested = directory.toPath().toAbsolutePath().normalize();
		if (!requested.startsWith(lexicalRoot)) {
			throw new IOException("Diagnostics directory is outside its root");
		}
		Path current = safeRoot.toPath();
		for (Path component : lexicalRoot.relativize(requested)) {
			current = current.resolve(component.toString());
			if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
				if (!create) throw new IOException("Diagnostics directory does not exist");
				try {
					Files.createDirectory(current);
				} catch (FileAlreadyExistsException ignored) {
					// Validate the raced entry below.
				}
			}
			if (Files.isSymbolicLink(current) ||
					!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Diagnostics directory contains a symbolic link");
			}
		}
		File canonical = current.toFile().getCanonicalFile();
		if (!isWithin(canonical, safeRoot)) {
			throw new IOException("Diagnostics directory escaped its root");
		}
		return canonical;
	}

	private static File createSafeFile(File root, File file) throws IOException {
		File safeRoot = requireRootDirectory(root);
		Path requested = file.toPath().toAbsolutePath().normalize();
		if (!requested.getParent().equals(safeRoot.toPath())) {
			throw new IOException("Diagnostics file is outside its directory");
		}
		Files.createFile(requested);
		return requested.toFile();
	}

	private static File publishReport(File stagingRoot, File stagedReport, File reportDirectory)
			throws IOException {
		if (!isContainedFile(stagingRoot, stagingRoot, stagedReport)) {
			throw new IOException("Invalid staged diagnostics report");
		}
		String name = reportName();
		String base = name.substring(0, name.length() - REPORT_SUFFIX.length());
		for (int collision = 0; collision < 1000; collision++) {
			String candidateName = (collision == 0) ? name :
					base + '-' + collision + REPORT_SUFFIX;
			File candidate = new File(reportDirectory, candidateName);
			Path target = candidate.toPath().toAbsolutePath().normalize();
			if (!target.getParent().equals(reportDirectory.toPath()) ||
					Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
			try {
				Files.move(stagedReport.toPath(), target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				try {
					Files.move(stagedReport.toPath(), target);
				} catch (FileAlreadyExistsException raced) {
					continue;
				}
			} catch (FileAlreadyExistsException ignored) {
				continue;
			}
			if (!REPORT_NAME.matcher(candidate.getName()).matches() ||
					!isContainedFile(reportDirectory, reportDirectory, candidate)) {
				Files.deleteIfExists(target);
				throw new IOException("Published diagnostics report failed validation");
			}
			return candidate;
		}
		throw new IOException("Unable to allocate diagnostics report name");
	}

	private static void deletePublishedReport(File reportDirectory, File report) {
		if ((report == null) || !REPORT_NAME.matcher(report.getName()).matches()) return;
		try {
			if (isContainedFile(reportDirectory, reportDirectory, report)) {
				Files.deleteIfExists(report.toPath());
			}
		} catch (IOException ignored) {
		}
	}

	private static List<File> cachedReports(File directory) throws IOException {
		if (Files.isSymbolicLink(directory.toPath()) ||
				!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Invalid diagnostics report directory");
		}
		File[] children = directory.listFiles();
		List<File> reports = new ArrayList<>();
		if (children == null) return reports;
		for (File child : children) {
			if (REPORT_NAME.matcher(child.getName()).matches() &&
					isContainedFile(directory, directory, child)) reports.add(child);
		}
		return reports;
	}

	private static boolean samePath(File first, File second) {
		return (first != null) && (second != null) &&
				first.toPath().toAbsolutePath().normalize()
						.equals(second.toPath().toAbsolutePath().normalize());
	}

	private static boolean isWithin(File child, File root) {
		Path childPath = child.toPath();
		Path rootPath = root.toPath();
		return childPath.equals(rootPath) || childPath.startsWith(rootPath);
	}

	private static long saturatedAdd(long left, long right) {
		return (left > (Long.MAX_VALUE - right)) ? Long.MAX_VALUE : left + right;
	}

	private static String json(String value) {
		if (value == null) return "";
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\': escaped.append("\\\\"); break;
				case '"': escaped.append("\\\""); break;
				case '\n': escaped.append("\\n"); break;
				case '\r': escaped.append("\\r"); break;
				case '\t': escaped.append("\\t"); break;
				default:
					if (c < 0x20) escaped.append(String.format(Locale.US, "\\u%04x", (int) c));
					else escaped.append(c);
			}
		}
		return escaped.toString();
	}
}
