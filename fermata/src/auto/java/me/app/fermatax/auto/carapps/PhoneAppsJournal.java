package me.app.fermatax.auto.carapps;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;

/**
 * The Phone Apps event journal behind the report a beta tester sends. It records only while the user has Detailed
 * diagnostics on (Settings > Diagnostics), like the detailed journal; off, an event costs one preference read and
 * nothing is read or written. Turning it off keeps what was recorded; Clear diagnostic data removes it. A 1 MB ring
 * (newest kept), one line per event with wall clock and uptime, kept in the app's files across a process death.
 * Recording only touches memory; the file is appended by one background thread and rewritten when it has doubled. Values are reduced to short tokens, so no message, key, code, Wi-Fi name or address can get in.
 */
public final class PhoneAppsJournal {
	public static final int MAX_BYTES = 1024 * 1024;
	private static final int MAX_VALUE_CHARS = 64;
	/** The multi-line server logcat tail stays in Detailed diagnostics only. */
	private static final String SERVER_LOG = "server_log";
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);
	private static final Pattern EVENT = Pattern.compile("[a-z_]{1,40}");
	private static final Pattern KEY = Pattern.compile("[a-z_]{1,32}");
	private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(ssid|mac|ip_?addr|code|key|password|secret|token).*");
	private static final Pattern SECRET_VALUE = Pattern.compile("(?i).*(ssid|private|password|secret|token|code.{0,3}\\d{6}).*");
	private static final Pattern NOT_TOKEN = Pattern.compile("[^A-Za-z0-9_.:+-]");
	private static final Pattern MAC = Pattern.compile("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])");
	private static final Pattern IPV4 = Pattern.compile("(?<![0-9.])(?!127\\.0\\.0\\.1(?![0-9.]))\\d{1,3}(?:\\.\\d{1,3}){3}(?![0-9.])");
	private static final Pattern LINE = Pattern.compile("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}[+-]\\d{4} up=\\d+ "
			+ "[a-z_]{1,40}(?: [a-z_]{1,32}=[A-Za-z0-9_.:+-]{0,64})*");

	private static volatile PhoneAppsJournal instance;

	@Nullable
	private final File file;
	private final Executor io;
	private final LongSupplier wallClock;
	private final LongSupplier uptime;
	private final ArrayDeque<String> lines = new ArrayDeque<>();
	private final List<String> pending = new ArrayList<>();
	private final Object fileLock = new Object();
	private final Object loadLock = new Object();
	private final BooleanSupplier recording;
	private int bytes;
	private long fileBytes;
	private volatile boolean loaded;
	/** The file was just read: the next write replaces it whole (drops damage and a cut last line). */
	private boolean needsRewrite;

	/**
	 * A null file keeps the journal in memory only. Nothing is read or written until an event is recorded while
	 * recording is on, or the journal is read for a report.
	 */
	public PhoneAppsJournal(@Nullable File file, Executor io, LongSupplier wallClock, LongSupplier uptime,
			BooleanSupplier recording) {
		this.file = file;
		this.io = io;
		this.wallClock = wallClock;
		this.uptime = uptime;
		this.recording = recording;
	}

	/** Called once at startup; the file is read on the journal's own thread. */
	public static void install(File file) {
		if (instance != null) return;
		synchronized (PhoneAppsJournal.class) {
			if (instance != null) return;
			instance = new PhoneAppsJournal(file, Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "fermatax-carapps-journal");
				t.setDaemon(true);
				return t;
			}), System::currentTimeMillis, android.os.SystemClock::elapsedRealtime, PhoneAppsJournal::detailedOn);
		}
	}

	/** The user's Detailed diagnostics setting (Settings > Diagnostics), with its 48-hour expiry: a preference read. */
	private static boolean detailedOn() {
		AndroidDiagnosticsRuntime r = AndroidDiagnosticsRuntime.get();
		return (r != null) && r.isDetailedEnabled();
	}

	/** True while events are recorded, i.e. while Detailed diagnostics is on. */
	public static boolean recording() {
		PhoneAppsJournal j = instance;
		return (j != null) && j.isRecording();
	}

	/** Clear diagnostic data: the journal and its file go, as the detailed journal does. */
	public static void clearAll() {
		PhoneAppsJournal j = instance;
		if (j != null) j.clear();
	}

	public static void setForTest(@Nullable PhoneAppsJournal journal) {
		instance = journal;
	}

	/** The one hook for Phone Apps events: into this journal and the detailed journal, both only while Detailed diagnostics is on. */
	public static void record(String event, @Nullable Map<String, ?> attributes) {
		PhoneAppsJournal j = instance;
		if (j != null) j.add(event, attributes);
		try {
			AndroidDiagnosticsRuntime.get().recordDetailed("carapps", event, attributes);
		} catch (Throwable ignored) {
		}
	}

	/** The journal, oldest first; empty before install. */
	public static List<String> snapshot() {
		PhoneAppsJournal j = instance;
		return (j == null) ? new ArrayList<>() : j.lines();
	}

	public boolean isRecording() {
		try {
			return recording.getAsBoolean();
		} catch (RuntimeException e) {
			return false;
		}
	}

	public void add(String event, @Nullable Map<String, ?> attributes) {
		if (!isRecording() || SERVER_LOG.equals(event)) return; // off: one preference read, nothing else
		String line = format(event, attributes);
		boolean schedule;
		synchronized (this) {
			append(line);
			schedule = (file != null) && pending.isEmpty();
			if (file != null) pending.add(line);
		}
		if (schedule) {
			try {
				io.execute(this::flush);
			} catch (RuntimeException ignored) {
				// shut down: the event stays in memory
			}
		}
	}

	/** Oldest first; reads the file of earlier processes on first use (call off the main thread). */
	public List<String> lines() {
		ensureLoaded();
		synchronized (this) {
			return new ArrayList<>(lines);
		}
	}

	public void clear() {
		synchronized (fileLock) {
			synchronized (this) {
				lines.clear();
				pending.clear();
				bytes = 0;
				loaded = true;
				needsRewrite = false;
			}
			if (file != null) {
				file.delete();
				new File(file.getPath() + ".tmp").delete();
			}
			fileBytes = 0;
		}
	}

	private String format(String event, @Nullable Map<String, ?> attributes) {
		StringBuilder b = new StringBuilder(96)
				.append(TIME.format(Instant.ofEpochMilli(wallClock.getAsLong()).atZone(ZoneId.systemDefault())))
				.append(" up=").append(Math.max(0, uptime.getAsLong())).append(' ')
				.append(((event != null) && EVENT.matcher(event).matches()) ? event : "invalid");
		if (attributes != null) {
			for (Map.Entry<String, ?> e : new TreeMap<String, Object>(attributes).entrySet()) {
				String key = e.getKey();
				if (!KEY.matcher(key).matches() || SECRET_KEY.matcher(key).matches()) continue;
				b.append(' ').append(key).append('=').append(value(e.getValue()));
			}
		}
		return b.toString();
	}

	/** One short token: no spaces, no message text, no addresses other than loopback, nothing that names a secret. */
	static String value(Object v) {
		String s = NOT_TOKEN.matcher(String.valueOf(v)).replaceAll("_");
		if (SECRET_VALUE.matcher(s).matches()) return "redacted";
		s = IPV4.matcher(MAC.matcher(s).replaceAll("mac_redacted")).replaceAll("ip_redacted");
		return (s.length() > MAX_VALUE_CHARS) ? s.substring(0, MAX_VALUE_CHARS) : s;
	}

	/** Under the lock. */
	private void append(String line) {
		lines.addLast(line);
		bytes += line.length() + 1;
		while (bytes > MAX_BYTES) bytes -= lines.removeFirst().length() + 1;
	}

	/** The old lines go before the ones recorded since this process started; the next write replaces the file. */
	private void ensureLoaded() {
		if (loaded) return;
		synchronized (loadLock) {
			if (loaded) return;
			List<String> old = (file == null) ? new ArrayList<>() : read(file);
			synchronized (this) {
				List<String> recent = new ArrayList<>(lines);
				lines.clear();
				bytes = 0;
				for (String l : old) append(l);
				for (String l : recent) append(l);
				needsRewrite = (file != null);
				loaded = true;
			}
		}
	}

	private static List<String> read(File file) {
		List<String> valid = new ArrayList<>();
		try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
			long len = f.length();
			long from = Math.max(0, len - 4L * MAX_BYTES); // the file is rewritten at twice the limit: more is damage
			byte[] data = new byte[(int) (len - from)];
			f.seek(from);
			f.readFully(data);
			for (String l : new String(data, UTF_8).split("\n")) {
				if (LINE.matcher(l).matches()) valid.add(l);
			}
		} catch (IOException | RuntimeException ignored) {
			// missing, unreadable or damaged: start empty, the next rewrite replaces it
		}
		return valid;
	}

	private void flush() {
		ensureLoaded();
		List<String> batch;
		boolean whole;
		synchronized (this) {
			batch = new ArrayList<>(pending);
			pending.clear();
			whole = needsRewrite;
		}
		if (batch.isEmpty() && !whole) return;
		StringBuilder b = new StringBuilder();
		for (String l : batch) b.append(l).append('\n');
		byte[] data = b.toString().getBytes(UTF_8);
		synchronized (fileLock) {
			if (whole || (fileBytes + data.length > 2L * MAX_BYTES)) {
				rewrite();
				return;
			}
			try (FileOutputStream out = new FileOutputStream(file, true)) {
				out.write(data);
				fileBytes += data.length;
			} catch (IOException ignored) {
				// best effort: the events stay in memory for this process
			}
		}
	}

	/** Replaces the file with the journal in memory; a temp file and a rename, so a kill never leaves half of it. */
	private void rewrite() {
		synchronized (fileLock) {
			List<String> all;
			synchronized (this) {
				all = new ArrayList<>(lines);
				pending.clear(); // all of it is in this write
			}
			StringBuilder b = new StringBuilder(bytesOf(all));
			for (String l : all) b.append(l).append('\n');
			byte[] data = b.toString().getBytes(UTF_8);
			File tmp = new File(file.getPath() + ".tmp");
			try (FileOutputStream out = new FileOutputStream(tmp, false)) {
				out.write(data);
			} catch (IOException ignored) {
				return;
			}
			try {
				Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				fileBytes = data.length;
				synchronized (this) {
					needsRewrite = false;
				}
			} catch (IOException | RuntimeException e) {
				tmp.delete();
			}
		}
	}

	private static int bytesOf(List<String> all) {
		int n = 0;
		for (String l : all) n += l.length() + 1;
		return n;
	}
}
