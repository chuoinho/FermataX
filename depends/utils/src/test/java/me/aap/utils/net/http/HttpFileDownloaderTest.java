package me.aap.utils.net.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.pref.BasicPreferenceStore;

class HttpFileDownloaderTest {
	@TempDir
	Path temp;

	@Test
	void bodyIdleTimeoutIsResetByProgress() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		Promise<Void> payload = new Promise<>();
		TrackingOutputStream output = new TrackingOutputStream();
		AtomicBoolean connectionClosed = new AtomicBoolean();
		HttpFileDownloader.BodyTransfer transfer = newTransfer(payload, output, connectionClosed,
				scheduler, 1_000, 10_000);

		scheduler.advance(900);
		transfer.onActivity();
		scheduler.advance(999);
		assertFalse(transfer.isDone());

		scheduler.advance(1);
		assertTrue(transfer.isDone());
		assertInstanceOf(TimeoutException.class, transfer.getFailure());
		assertTrue(transfer.getFailure().getMessage().contains("idle timeout"));
		assertTrue(payload.isCancelled());
		assertTrue(output.closed);
		assertTrue(connectionClosed.get());
	}

	@Test
	void bodyDeadlineIsNotResetByProgress() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		Promise<Void> payload = new Promise<>();
		TrackingOutputStream output = new TrackingOutputStream();
		AtomicBoolean connectionClosed = new AtomicBoolean();
		HttpFileDownloader.BodyTransfer transfer = newTransfer(payload, output, connectionClosed,
				scheduler, 1_000, 3_000);

		scheduler.advance(900);
		transfer.onActivity();
		scheduler.advance(900);
		transfer.onActivity();
		scheduler.advance(900);
		transfer.onActivity();
		scheduler.advance(300);

		assertTrue(transfer.isDone());
		assertInstanceOf(TimeoutException.class, transfer.getFailure());
		assertTrue(transfer.getFailure().getMessage().contains("deadline exceeded"));
		assertTrue(payload.isCancelled());
		assertTrue(output.closed);
		assertTrue(connectionClosed.get());
	}

	@Test
	void successfulBodyClosesOutputButKeepsConnectionOpen() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		Promise<Void> payload = new Promise<>();
		TrackingOutputStream output = new TrackingOutputStream();
		AtomicBoolean connectionClosed = new AtomicBoolean();
		HttpFileDownloader.BodyTransfer transfer = newTransfer(payload, output, connectionClosed,
				scheduler, 1_000, 3_000);

		payload.complete(null);
		scheduler.advance(10_000);

		assertTrue(transfer.isDoneNotFailed());
		assertTrue(output.closed);
		assertFalse(connectionClosed.get());
		assertFalse(payload.isCancelled());
	}

	@Test
	void failedBodyClosesOutputAndConnection() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		Promise<Void> payload = new Promise<>();
		TrackingOutputStream output = new TrackingOutputStream();
		AtomicBoolean connectionClosed = new AtomicBoolean();
		HttpFileDownloader.BodyTransfer transfer = newTransfer(payload, output, connectionClosed,
				scheduler, 1_000, 3_000);

		payload.completeExceptionally(new IllegalStateException("write failed"));
		scheduler.advance(10_000);

		assertTrue(transfer.isFailed());
		assertInstanceOf(IllegalStateException.class, transfer.getFailure());
		assertTrue(output.closed);
		assertTrue(connectionClosed.get());
	}

	@Test
	void cancellationAbortsPayloadAndCleansResources() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		Promise<Void> payload = new Promise<>();
		TrackingOutputStream output = new TrackingOutputStream();
		AtomicBoolean connectionClosed = new AtomicBoolean();
		HttpFileDownloader.BodyTransfer transfer = newTransfer(payload, output, connectionClosed,
				scheduler, 1_000, 3_000);
		assertTrue(transfer.cancel(false));
		scheduler.advance(10_000);

		assertTrue(transfer.isCancelled());
		assertTrue(payload.isCancelled());
		assertTrue(output.closed);
		assertTrue(connectionClosed.get());
	}

	@Test
	void cancellationBeforeHeadersCancelsRequestWhenConnectorSupportsIt() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		ManualWorker worker = new ManualWorker();
		TrackingConnector connector = new TrackingConnector();
		HttpFileDownloader downloader = new HttpFileDownloader(scheduler, scheduler::now, worker,
				connector);
		File destination = temp.resolve("preheader.bin").toFile();
		FutureSupplier<HttpFileDownloader.Status> download = downloader.download(
				new URL("http://localhost/preheader"), destination, new BasicPreferenceStore());

		assertTrue(download.cancel(false));
		assertTrue(connector.cancelled.get());
		assertTrue(download.isCancelled());
		assertFalse(destination.exists());
	}

	@Test
	void cancellationWhileConnectorIsBeingAssignedReleasesDestination() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		ManualWorker worker = new ManualWorker();
		AtomicReference<HttpFileDownloader> downloaderRef = new AtomicReference<>();
		AtomicReference<FutureSupplier<HttpFileDownloader.Status>> replacement = new AtomicReference<>();
		AtomicInteger connections = new AtomicInteger();
		AtomicBoolean firstConnectorCancelled = new AtomicBoolean();
		File destination = temp.resolve("connector-race.bin").toFile();
		URL replacementUrl = new URL("http://localhost/replacement");

		HttpFileDownloader.Connector connector = (opts, consumer) -> {
			int connection = connections.incrementAndGet();
			if (connection == 1) {
				replacement.set(downloaderRef.get().download(
						replacementUrl, destination,
						new BasicPreferenceStore()));
				return () -> {
					firstConnectorCancelled.set(true);
					return true;
				};
			}
			return () -> true;
		};
		HttpFileDownloader downloader = new HttpFileDownloader(scheduler, scheduler::now, worker,
				connector);
		downloaderRef.set(downloader);

		FutureSupplier<HttpFileDownloader.Status> superseded = downloader.download(
				new URL("http://localhost/superseded"), destination, new BasicPreferenceStore());

		assertTrue(firstConnectorCancelled.get());
		assertEquals(2, connections.get());
		assertTrue(superseded.isDone());
		assertTrue(replacement.get().cancel(false));
	}

	@Test
	void destinationCoordinatorRunsOnlyLatestRevisionAfterOldCleanup() throws Exception {
		HttpFileDownloader.DestinationCoordinator coordinator =
				new HttpFileDownloader.DestinationCoordinator();
		List<String> starts = new ArrayList<>();
		File destination = temp.resolve("revision.txt").toFile();
		FakeRequest old = new FakeRequest(coordinator, destination,
				new URL("http://localhost/old"), starts, "old");
		FakeRequest duplicateOld = new FakeRequest(coordinator, destination,
				new URL("http://localhost/old"), starts, "duplicate");
		FakeRequest middle = new FakeRequest(coordinator, destination,
				new URL("http://localhost/middle"), starts, "middle");
		FakeRequest newest = new FakeRequest(coordinator, destination,
				new URL("http://localhost/newest"), starts, "newest");

		FutureSupplier<HttpFileDownloader.Status> firstSubscriber = coordinator.submit(old);
		FutureSupplier<HttpFileDownloader.Status> duplicateSubscriber = coordinator.submit(duplicateOld);
		assertNotSame(firstSubscriber, duplicateSubscriber);
		coordinator.submit(middle);
		coordinator.submit(newest);

		assertTrue(old.cancelled.get());
		assertTrue(middle.cancelled.get());
		assertEquals(List.of("old"), starts);

		old.commitAndFinish("old-content");
		assertEquals(List.of("old", "newest"), starts);
		newest.commitAndFinish("new-content");

		assertEquals("new-content", readText(destination));
		assertFalse(duplicateOld.started.get());
		assertFalse(middle.started.get());
	}

	@Test
	void terminalListenerRunsAfterFutureCompletionAndCanReadResult() throws Exception {
		HttpFileDownloader.DownloadPromise promise = new HttpFileDownloader.DownloadPromise();
		List<String> events = new ArrayList<>();
		HttpFileDownloader.Status status = status(temp.resolve("listener.bin").toFile(), null);
		promise.onCompletion((value, failure) -> events.add("future"));
		HttpFileDownloader.StatusListener listener = new HttpFileDownloader.StatusListener() {
			@Override public void onProgress(HttpFileDownloader.Status value) { }
			@Override public void onSuccess(HttpFileDownloader.Status value) {
				events.add("listener:" + promise.isDone());
				assertSame(status, promise.peek());
			}
			@Override public void onFailure(HttpFileDownloader.Status value) {
				throw new AssertionError("Unexpected failure");
			}
		};

		assertTrue(promise.completeSuccess(status, listener));
		assertFalse(promise.completeSuccess(status, listener));
		assertEquals(List.of("future", "listener:true"), events);
	}

	@Test
	void deadlineDuringDecodeStopsBeforeSuccessAndSignalsExit() throws Exception {
		ManualScheduler scheduler = new ManualScheduler();
		ManualWorker worker = new ManualWorker();
		AtomicBoolean exited = new AtomicBoolean();
		HttpFileDownloader.PostProcessTransfer post = new HttpFileDownloader.PostProcessTransfer(
				worker, scheduler, scheduler::now, 1_000, new URL("http://localhost/decode"));
		post.onExit(() -> exited.set(true));
		post.start(token -> {
			byte[] payload = new byte[16 * 1024];
			OutputStream output = new ByteArrayOutputStream() {
				private boolean advanced;

				@Override
				public void write(byte[] bytes, int offset, int length) {
					super.write(bytes, offset, length);
					if (!advanced) {
						advanced = true;
						scheduler.advance(1_000);
					}
				}
			};
			HttpFileDownloader.copyCancellable(new ByteArrayInputStream(payload), output, token);
		});

		worker.runNext();

		assertInstanceOf(TimeoutException.class, post.getFailure());
		assertTrue(exited.get());
	}

	@Test
	void cancellationCannotOvertakeAnAcquiredCommitLease() throws Exception {
		File destination = temp.resolve("commit.txt").toFile();
		File staged = temp.resolve("commit.staged").toFile();
		writeText(destination, "old");
		writeText(staged, "new");
		HttpFileDownloader.DownloadPromise promise = new HttpFileDownloader.DownloadPromise();
		assertTrue(promise.beginCommit());
		assertFalse(promise.cancel(false));
		HttpFileDownloader.replaceFile(staged, destination);
		promise.endCommit();
		assertTrue(promise.completeSuccess(status(destination, null), null));

		assertFalse(promise.isCancelled());
		assertTrue(promise.isDone());
		assertEquals("new", readText(destination));
		assertFalse(staged.exists());
	}

	@Test
	void decodeFailureDoesNotTruncateExistingDestination() throws Exception {
		File destination = temp.resolve("target.txt").toFile();
		File payload = temp.resolve("payload.gz").toFile();
		Files.write(destination.toPath(), "existing".getBytes(StandardCharsets.UTF_8));
		Files.write(payload.toPath(), "not-gzip".getBytes(StandardCharsets.UTF_8));
		HttpFileDownloader.Status status = status(payload, "gzip");

		assertThrows(Exception.class,
				() -> HttpFileDownloader.decodeToStaging(status, payload, destination));
		assertEquals("existing", new String(Files.readAllBytes(destination.toPath()),
				StandardCharsets.UTF_8));
	}

	@Test
	void completedStagingFileAtomicallyReplacesDestination() throws Exception {
		File destination = temp.resolve("target.txt").toFile();
		File staged = temp.resolve("staged.txt").toFile();
		Files.write(destination.toPath(), "old".getBytes(StandardCharsets.UTF_8));
		Files.write(staged.toPath(), "new".getBytes(StandardCharsets.UTF_8));

		HttpFileDownloader.replaceFile(staged, destination);

		assertEquals("new", new String(Files.readAllBytes(destination.toPath()),
				StandardCharsets.UTF_8));
		assertFalse(staged.exists());
	}

	private static HttpFileDownloader.Status status(File file, String encoding)
			throws MalformedURLException {
		return new HttpFileDownloader.Status() {
			@Override public URL getUrl() { try { return new URL("http://localhost/test"); }
				catch (MalformedURLException ex) { throw new AssertionError(ex); } }
			@Override public String getEtag() { return null; }
			@Override public Throwable getFailure() { return null; }
			@Override public long bytesDownloaded() { return file.length(); }
			@Override public long getLength() { return file.length(); }
			@Override public File getLocalFile() { return file; }
			@Override public String getContentEncoding() { return encoding; }
		};
	}

	private static void writeText(File file, String value) throws IOException {
		Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
	}

	private static String readText(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private static HttpFileDownloader.BodyTransfer newTransfer(Promise<Void> payload,
			TrackingOutputStream output, AtomicBoolean connectionClosed, ManualScheduler scheduler,
			long idleTimeout, long deadline) throws MalformedURLException {
		HttpFileDownloader.BodyTransfer transfer = new HttpFileDownloader.BodyTransfer(payload,
				output, () -> connectionClosed.set(true), scheduler, scheduler::now, idleTimeout,
				deadline, new URL("http://localhost/test"));
		transfer.start();
		return transfer;
	}

	private static final class FakeRequest implements HttpFileDownloader.CoordinatedRequest {
		private final HttpFileDownloader.DestinationCoordinator coordinator;
		private final File destination;
		private final URL source;
		private final List<String> starts;
		private final String name;
		private final AtomicBoolean started = new AtomicBoolean();
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final Promise<HttpFileDownloader.Status> future =
				new Promise<>() {
					@Override
					public boolean cancel(boolean mayInterruptIfRunning) {
						cancelled.set(true);
						return super.cancel(mayInterruptIfRunning);
					}
				};

		FakeRequest(HttpFileDownloader.DestinationCoordinator coordinator, File destination,
				URL source, List<String> starts, String name) {
			this.coordinator = coordinator;
			this.destination = destination;
			this.source = source;
			this.starts = starts;
			this.name = name;
		}

		@Override public URL source() { return source; }
		@Override public String destinationKey() {
			try {
				return destination.getCanonicalPath();
			} catch (IOException ex) {
				throw new AssertionError(ex);
			}
		}
		@Override public String revisionKey() { return source.toExternalForm(); }
		@Override public FutureSupplier<HttpFileDownloader.Status> future() { return future; }
		@Override public boolean isDone() { return future.isDone(); }
		@Override public void start() {
			started.set(true);
			starts.add(name);
		}

		void commitAndFinish(String content) throws IOException {
			writeText(destination, content);
			future.complete(null);
			coordinator.finished(this);
		}
	}

	private static final class TrackingConnector implements HttpFileDownloader.Connector {
		private final AtomicBoolean cancelled = new AtomicBoolean();
		@SuppressWarnings("unused")
		private BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer;

		@Override
		public Cancellable connect(HttpConnection.Opts opts,
				BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
			this.consumer = consumer;
			return () -> {
				cancelled.set(true);
				return true;
			};
		}
	}

	private static final class ManualWorker implements HttpFileDownloader.WorkExecutor {
		private final ArrayDeque<Work> work = new ArrayDeque<>();

		@Override
		public FutureSupplier<Void> submit(CheckedRunnable<Throwable> task) {
			Promise<Void> result = new Promise<>();
			work.addLast(new Work(task, result));
			return result;
		}

		void runNext() throws Exception {
			Work next = work.removeFirst();
			try {
				next.task.run();
				next.result.complete(null);
			} catch (Throwable ex) {
				next.result.completeExceptionally(ex);
				if (ex instanceof Exception exception) throw exception;
				if (ex instanceof Error error) throw error;
				throw new RuntimeException(ex);
			}
		}
	}

	private record Work(CheckedRunnable<Throwable> task, Promise<Void> result) {
	}

	private static final class TrackingOutputStream extends ByteArrayOutputStream {
		private boolean closed;

		@Override
		public void close() {
			closed = true;
		}
	}

	private static final class ManualScheduler implements HttpFileDownloader.TimeoutScheduler {
		private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>();
		private long now;
		private long sequence;

		@Override
		public Cancellable schedule(Runnable task, long delayMillis) {
			ScheduledTask scheduled = new ScheduledTask(now + Math.max(0, delayMillis), sequence++, task);
			tasks.add(scheduled);
			return () -> {
				if (scheduled.cancelled) return false;
				scheduled.cancelled = true;
				return true;
			};
		}

		long now() {
			return now;
		}

		void advance(long millis) {
			long target = now + millis;

			while (!tasks.isEmpty() && (tasks.peek().at <= target)) {
				ScheduledTask scheduled = tasks.remove();
				now = scheduled.at;
				if (!scheduled.cancelled) scheduled.task.run();
			}

			now = target;
		}
	}

	private static final class ScheduledTask implements Comparable<ScheduledTask> {
		private final long at;
		private final long sequence;
		private final Runnable task;
		private boolean cancelled;

		ScheduledTask(long at, long sequence, Runnable task) {
			this.at = at;
			this.sequence = sequence;
			this.task = task;
		}

		@Override
		public int compareTo(ScheduledTask other) {
			int timeOrder = Long.compare(at, other.at);
			return (timeOrder != 0) ? timeOrder : Long.compare(sequence, other.sequence);
		}
	}
}
