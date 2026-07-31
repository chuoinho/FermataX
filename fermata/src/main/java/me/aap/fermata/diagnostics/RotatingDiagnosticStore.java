package me.aap.fermata.diagnostics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Thread-confined JSONL store with bounded size and age retention. */
public final class RotatingDiagnosticStore implements AutoCloseable {
	private static final String PREFIX = "diagnostics-";
	private static final String SUFFIX = ".jsonl";

	private final File directory;
	private final DiagnosticConfig config;
	private final File activeFile;
	private final String activeId;
	private FileOutputStream output;
	private BufferedWriter writer;
	private long activeBytes;
	private long retainedBytes;
	private long rotationSequence;
	private boolean initialized;

	public RotatingDiagnosticStore(File directory, DiagnosticConfig config) {
		this(directory, config, "current");
	}

	RotatingDiagnosticStore(File directory, DiagnosticConfig config, String activeId) {
		if (directory == null) throw new NullPointerException("directory");
		if (config == null) throw new NullPointerException("config");
		this.directory = directory;
		this.config = config;
		String safeId = (activeId == null) ? "current" : activeId.replaceAll("[^A-Za-z0-9_-]", "_");
		this.activeId = safeId;
		activeFile = new File(directory, PREFIX + safeId + "-current" + SUFFIX);
	}

	public void write(String jsonLine, long wallTimeMillis) throws IOException {
		if (jsonLine == null) throw new NullPointerException("jsonLine");
		byte[] encoded = (jsonLine + '\n').getBytes(StandardCharsets.UTF_8);
		if (encoded.length > config.getMaxFileBytes()) {
			throw new EventTooLargeException(encoded.length, config.getMaxFileBytes());
		}
		initialize(wallTimeMillis);
		if ((activeBytes > 0L) &&
				((activeBytes + encoded.length) > config.getMaxFileBytes())) {
			rotate(wallTimeMillis);
		}
		writer.write(jsonLine);
		writer.newLine();
		activeBytes += encoded.length;
		retainedBytes += encoded.length;
		if (retainedBytes > config.getMaxTotalBytes()) {
			writer.flush();
			retainedBytes = applyRetention(wallTimeMillis);
			if (retainedBytes > config.getMaxTotalBytes()) {
				throw new IOException("Unable to enforce diagnostics storage limit");
			}
		}
	}

	public void flush() throws IOException {
		if (writer != null) writer.flush();
	}

	/** Flushes Java buffers and asks the filesystem to persist the current journal. */
	public void sync() throws IOException {
		flush();
		if (output != null) output.getFD().sync();
	}

	/** Copies a complete, immutable journal snapshot while the store remains thread-confined. */
	public void snapshot(File destination) throws IOException {
		if (destination == null) throw new NullPointerException("destination");
		flush();
		if (!destination.isDirectory() && !destination.mkdirs() && !destination.isDirectory()) {
			throw new IOException("Failed to create diagnostics snapshot directory");
		}
		File[] stale = destination.listFiles();
		if (stale != null) {
			for (File file : stale) {
				if (file.isFile() && !file.delete()) {
					throw new IOException("Failed to reset diagnostics snapshot");
				}
			}
		}
		for (File source : listFiles()) copy(source, new File(destination, source.getName()));
	}

	/** Reopens the store after a transient failure and removes an incomplete JSONL tail. */
	public void recover(long wallTimeMillis) throws IOException {
		try {
			closeWriter();
		} catch (IOException ignored) {
			// Reopening a fresh stream is still worth attempting after a failed close/flush.
		} finally {
			initialized = false;
		}
		repairIncompleteTail(activeFile);
		initialize(wallTimeMillis);
	}

	/** Performs synchronous file deletion and should only be called from a worker thread. */
	public void clear() throws IOException {
		closeWriter();
		File[] files = diagnosticFiles();
		for (File file : files) {
			if (file.exists() && !file.delete()) {
				throw new IOException("Failed to delete diagnostics file " + file.getName());
			}
		}
		activeBytes = 0L;
		retainedBytes = 0L;
		initialized = false;
	}

	/** Returns a stable snapshot for support export; callers should flush first. */
	public File[] listFiles() {
		File[] files = diagnosticFiles();
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));
		return files;
	}

	@Override
	public void close() throws IOException {
		closeWriter();
	}

	private void initialize(long wallTimeMillis) throws IOException {
		if (initialized && (writer != null)) return;
		if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
			throw new IOException("Failed to create diagnostics directory");
		}
		retainedBytes = applyRetention(wallTimeMillis);
		if (retainedBytes > config.getMaxTotalBytes()) {
			throw new IOException("Unable to enforce diagnostics storage limit");
		}
		openWriter(true);
		activeBytes = activeFile.length();
		initialized = true;
	}

	private void rotate(long wallTimeMillis) throws IOException {
		closeWriter();
		File rotated;
		do {
			rotated = new File(directory, PREFIX + activeId + '-' + wallTimeMillis + '-' +
					(++rotationSequence) + SUFFIX);
		} while (rotated.exists());

		if (activeFile.exists() && !activeFile.renameTo(rotated)) {
			copyAtomically(activeFile, rotated);
			if (!activeFile.delete()) throw new IOException("Failed to replace active diagnostics file");
		}
		retainedBytes = applyRetention(wallTimeMillis);
		if (retainedBytes > config.getMaxTotalBytes()) {
			throw new IOException("Unable to enforce diagnostics storage limit");
		}
		openWriter(false);
		activeBytes = 0L;
		initialized = true;
	}

	private long applyRetention(long wallTimeMillis) {
		File[] all = diagnosticFiles();
		long cutoff = wallTimeMillis - config.getMaxAgeMillis();
		for (File file : all) {
			if (!file.equals(activeFile) && (file.lastModified() < cutoff)) {
				//noinspection ResultOfMethodCallIgnored
				file.delete();
			}
		}

		all = diagnosticFiles();
		List<File> candidates = new ArrayList<>(Arrays.asList(all));
		candidates.sort(Comparator.comparingLong(File::lastModified));
		long total = 0L;
		for (File file : candidates) total += file.length();
		for (File file : candidates) {
			if (total <= config.getMaxTotalBytes()) break;
			if (file.equals(activeFile)) continue;
			long length = file.length();
			if (file.delete()) total -= length;
		}
		return total;
	}

	private File[] diagnosticFiles() {
		File[] files = directory.listFiles((dir, name) ->
				name.startsWith(PREFIX) && name.endsWith(SUFFIX));
		return (files == null) ? new File[0] : files;
	}

	private void closeWriter() throws IOException {
		if (writer == null) return;
		try {
			writer.close();
		} finally {
			writer = null;
			output = null;
		}
	}

	private void openWriter(boolean append) throws IOException {
		output = new FileOutputStream(activeFile, append);
		writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
	}

	private static void repairIncompleteTail(File file) throws IOException {
		if (!file.isFile() || (file.length() == 0L)) return;
		try (RandomAccessFile data = new RandomAccessFile(file, "rw")) {
			long length = data.length();
			data.seek(length - 1L);
			if (data.read() == '\n') return;
			for (long position = length - 2L; position >= 0L; position--) {
				data.seek(position);
				if (data.read() == '\n') {
					data.setLength(position + 1L);
					return;
				}
			}
			data.setLength(0L);
		}
	}

	private static void copy(File source, File target) throws IOException {
		byte[] buffer = new byte[8192];
		try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
			for (int read; (read = in.read(buffer)) >= 0; ) out.write(buffer, 0, read);
		}
	}

	private static void copyAtomically(File source, File target) throws IOException {
		File pending = new File(target.getParentFile(), target.getName() + ".tmp");
		if (pending.exists() && !pending.delete()) {
			throw new IOException("Failed to reset diagnostics rotation temp file");
		}
		byte[] buffer = new byte[8192];
		try {
			try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
					FileOutputStream fileOutput = new FileOutputStream(pending, false);
					BufferedOutputStream out = new BufferedOutputStream(fileOutput)) {
				for (int read; (read = in.read(buffer)) >= 0; ) out.write(buffer, 0, read);
				out.flush();
				fileOutput.getFD().sync();
			}
			if (!pending.renameTo(target)) {
				throw new IOException("Failed to publish diagnostics rotation file");
			}
		} finally {
			if (pending.exists()) pending.delete();
		}
	}

	static final class EventTooLargeException extends IOException {
		EventTooLargeException(long actualBytes, long maxBytes) {
			super("Diagnostic event exceeds file limit: " + actualBytes + " > " + maxBytes);
		}
	}
}
