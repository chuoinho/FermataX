package me.app.fermatax.auto.carapps;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** The Phone Apps journal: only while Detailed diagnostics is on; then a 1 MB ring kept across a process death. */
public class PhoneAppsJournalTest {
	private final AtomicLong clock = new AtomicLong(1_700_000_000_000L);
	private final AtomicBoolean detailed = new AtomicBoolean(true);

	private PhoneAppsJournal journal(File file) {
		return new PhoneAppsJournal(file, Runnable::run, clock::incrementAndGet, () -> 42L, detailed::get);
	}

	private static File tempFile() throws Exception {
		File dir = Files.createTempDirectory("journal").toFile();
		return new File(dir, "carapps_journal.txt");
	}

	private static int bytes(List<String> lines) {
		int n = 0;
		for (String l : lines) n += l.length() + 1;
		return n;
	}

	/** Runs the queued background writes only when told, so a big test does not open the file per event. */
	private static final class Queue implements java.util.concurrent.Executor {
		final List<Runnable> tasks = new ArrayList<>();

		@Override public synchronized void execute(Runnable r) {
			tasks.add(r);
		}

		synchronized void drain() {
			while (!tasks.isEmpty()) tasks.remove(0).run();
		}
	}

	@Test public void keepsTheNewestEventsWithinOneMegabyte() throws Exception {
		File file = tempFile();
		Queue io = new Queue();
		PhoneAppsJournal j = new PhoneAppsJournal(file, io, clock::incrementAndGet, () -> 42L, detailed::get);
		int n = 25_000; // about 70 bytes each: well over 1 MB
		for (int i = 0; i < n; i++) {
			j.add("server_retry", Map.of("attempt", i));
			if (i % 1000 == 0) io.drain();
		}
		io.drain();

		List<String> lines = j.lines();
		assertTrue(bytes(lines) + " bytes", bytes(lines) <= PhoneAppsJournal.MAX_BYTES);
		assertTrue("close to the bound, not far under it", bytes(lines) > PhoneAppsJournal.MAX_BYTES - 200);
		assertTrue(lines.get(lines.size() - 1).endsWith("server_retry attempt=" + (n - 1)));
		assertFalse("the oldest are gone", lines.get(0).endsWith("attempt=0"));
		assertEquals(1024 * 1024, PhoneAppsJournal.MAX_BYTES);
		assertTrue(file.length() + " bytes on disk", file.length() <= 3L * PhoneAppsJournal.MAX_BYTES);
		assertEquals("the file holds the same ring", lines, journal(file).lines());
	}

	@Test public void offRecordsNothingAndTouchesNoFile() throws Exception {
		File file = tempFile();
		detailed.set(false);
		AtomicLong ioTasks = new AtomicLong();
		PhoneAppsJournal j = new PhoneAppsJournal(file, r -> {
			ioTasks.incrementAndGet();
			r.run();
		}, clock::incrementAndGet, () -> 42L, detailed::get);
		for (int i = 0; i < 100; i++) j.add("server_starting", Map.of("attempt", i));

		assertTrue(j.lines().isEmpty());
		assertFalse("no file is created", file.exists());
		assertEquals("no background write", 0, ioTasks.get());
		assertFalse(j.isRecording());
	}

	@Test public void turningItOnAndOffAtRuntimeStartsAndStopsRecording() throws Exception {
		File file = tempFile();
		PhoneAppsJournal j = journal(file);
		detailed.set(false);
		j.add("server_ping", Map.of("state", "sent"));
		detailed.set(true);
		j.add("server_ping", Map.of("state", "unanswered"));
		detailed.set(false);
		j.add("server_starting", Map.of("attempt", 0));

		List<String> lines = j.lines();
		assertEquals(1, lines.size());
		assertTrue(lines.get(0).endsWith("state=unanswered"));
		assertEquals("turning it off keeps what was recorded, like the detailed journal", lines, journal(file).lines());
	}

	@Test public void clearRemovesTheJournalAndItsFile() throws Exception {
		File file = tempFile();
		PhoneAppsJournal j = journal(file);
		j.add("server_died", Map.of("panel_off", false));
		assertTrue(file.exists());
		j.clear();
		assertTrue(j.lines().isEmpty());
		assertFalse(file.exists());
		assertTrue(journal(file).lines().isEmpty());
	}

	@Test public void eachLineHasWallClockUptimeEventAndSortedAttributes() throws Exception {
		PhoneAppsJournal j = journal(tempFile());
		j.add("server_failed", Map.of("reason", "no_wifi", "attempt", 2));
		String line = j.lines().get(0);
		assertTrue(line, line.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}[+-]\\d{4} up=42 server_failed attempt=2 reason=no_wifi"));
	}

	@Test public void survivesAProcessDeath() throws Exception {
		File file = tempFile();
		PhoneAppsJournal first = journal(file);
		first.add("server_ping", Map.of("state", "sent"));
		first.add("server_ping", Map.of("state", "unanswered"));

		PhoneAppsJournal second = journal(file); // a new process reads what the old one wrote
		second.add("server_starting", Map.of("attempt", 0));

		List<String> lines = second.lines();
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).endsWith("state=sent"));
		assertTrue(lines.get(1).endsWith("state=unanswered"));
		assertTrue(lines.get(2).endsWith("server_starting attempt=0"));
		assertEquals(lines, journal(file).lines());
	}

	@Test public void aCorruptFileKeepsOnlyItsValidLines() throws Exception {
		File file = tempFile();
		PhoneAppsJournal j = journal(file);
		j.add("server_died", Map.of("panel_off", false));
		String valid = j.lines().get(0);
		byte[] garbage = new byte[]{(byte) 0xff, (byte) 0xfe, 0, 1, 2, '\n'};
		Files.write(file.toPath(), garbage);
		Files.write(file.toPath(), ("not a journal line\n" + valid + "\n" + valid.substring(0, 20)).getBytes(UTF_8),
				java.nio.file.StandardOpenOption.APPEND);

		PhoneAppsJournal recovered = journal(file);
		assertEquals(List.of(valid), recovered.lines());
		recovered.add("server_starting", Map.of("attempt", 1));
		assertEquals(2, journal(file).lines().size());
	}

	@Test public void anUnreadableFileIsAnEmptyJournal() throws Exception {
		File dirAsFile = Files.createTempDirectory("journal-dir").toFile(); // a directory cannot be read as a file
		PhoneAppsJournal j = journal(dirAsFile);
		j.add("server_starting", Map.of("attempt", 0));
		assertEquals(1, j.lines().size());
	}

	@Test public void concurrentWritersLoseNothingAndKeepTheirOrder() throws Exception {
		File file = tempFile();
		ExecutorService io = Executors.newSingleThreadExecutor();
		PhoneAppsJournal j = new PhoneAppsJournal(file, io, System::currentTimeMillis, () -> 1L, () -> true);
		int threads = 6, each = 40;
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> writers = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			int id = t;
			Thread w = new Thread(() -> {
				try {
					go.await();
				} catch (InterruptedException e) {
					return;
				}
				for (int i = 0; i < each; i++) j.add("server_retry", Map.of("attempt", i, "source", "w" + id));
			});
			writers.add(w);
			w.start();
		}
		go.countDown();
		for (Thread w : writers) w.join();
		io.shutdown();
		assertTrue(io.awaitTermination(10, TimeUnit.SECONDS));

		List<String> lines = j.lines();
		assertEquals(threads * each, lines.size());
		int[] last = new int[threads];
		java.util.Arrays.fill(last, -1);
		for (String l : lines) {
			int id = Integer.parseInt(l.substring(l.indexOf("source=w") + 8).trim());
			int attempt = Integer.parseInt(l.substring(l.indexOf("attempt=") + 8, l.indexOf(' ', l.indexOf("attempt="))));
			assertEquals("in order per writer", last[id] + 1, attempt);
			last[id] = attempt;
		}
		assertEquals(lines, journal(file).lines());
	}

	@Test public void secretsAndNetworkIdentifiersNeverReachIt() throws Exception {
		PhoneAppsJournal j = journal(tempFile());
		Map<String, Object> hostile = new LinkedHashMap<>();
		hostile.put("code", "123456");
		hostile.put("ssid", "HomeNet");
		hostile.put("bssid", "aa:bb:cc:dd:ee:ff");
		hostile.put("pairing_key", "-----BEGIN PRIVATE KEY-----");
		hostile.put("error", "java.net.ConnectException: failed to connect to /192.168.1.20 (port 5555)");
		hostile.put("reason", "aa:bb:cc:dd:ee:ff");
		hostile.put("from", "127.0.0.1:37123");
		j.add("server_failed", hostile);
		j.add("Bad Event Name!", Map.of("reason", "x"));
		String text = String.join("\n", j.lines());

		assertFalse(text, text.contains("123456"));
		assertFalse(text, text.toLowerCase().contains("ssid"));
		assertFalse(text, text.contains("PRIVATE"));
		assertFalse(text, text.contains("192.168"));
		assertFalse(text, text.contains("aa:bb"));
		assertTrue(text, text.contains("from=127.0.0.1:37123"));
		assertTrue(text, text.contains(" invalid reason=x"));
		for (String l : j.lines()) assertFalse("one event per line", l.contains("\n"));
	}

	@Test public void theServerLogTailStaysOutOfTheJournal() throws Exception {
		PhoneAppsJournal j = journal(tempFile());
		j.add("server_log", Map.of("reason", "server_died", "lines", "anything\nat all"));
		assertTrue(j.lines().isEmpty());
	}
}
