package me.aap.utils.net.http;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpHeader.USER_AGENT;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class HttpFileDownloader {
	private static final DestinationCoordinator downloads = new DestinationCoordinator();
	private static final AtomicLong stagingIds = new AtomicLong();
	public static final Pref<Supplier<String>> AGENT = Pref.s("AGENT", USER_AGENT::getDefaultValue);
	public static final Pref<Supplier<String>> ETAG = Pref.s("ETAG");
	public static final Pref<Supplier<String>> CHARSET = Pref.s("CHARSET", "UTF-8");
	public static final Pref<Supplier<String>> ENCODING = Pref.s("ENCODING");
	public static final Pref<IntSupplier> RESP_TIMEOUT = Pref.i("RESP_TIMEOUT", 30);
	public static final Pref<IntSupplier> DOWNLOAD_TIMEOUT = Pref.i("DOWNLOAD_TIMEOUT", 30 * 60);
	public static final Pref<LongSupplier> TIMESTAMP = Pref.l("TIMESTAMP", 0);
	public static final Pref<IntSupplier> MAX_AGE = Pref.i("MAX_AGE", 0);
	public static final Pref<BooleanSupplier> DECODE = Pref.b("DECODE", false);
	private final TimeoutScheduler timeoutScheduler;
	private final LongSupplier monotonicClock;
	private final WorkExecutor workExecutor;
	private final Connector connector;
	private StatusListener statusListener;
	private boolean returnExistingOnFail;

	public HttpFileDownloader() {
		this((task, delay) -> {
			var timer = App.get().getScheduler().schedule(task, delay, TimeUnit.MILLISECONDS);
			return () -> timer.cancel(false);
		}, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
				task -> App.get().getExecutor().submitTask(task, null),
				(opts, consumer) -> {
					opts.keepAlive = false;
					return HttpConnection.connect(opts, consumer);
				});
	}

	HttpFileDownloader(TimeoutScheduler timeoutScheduler, LongSupplier monotonicClock) {
		this(timeoutScheduler, monotonicClock,
				task -> App.get().getExecutor().submitTask(task, null),
				(opts, consumer) -> {
					opts.keepAlive = false;
					return HttpConnection.connect(opts, consumer);
				});
	}

	HttpFileDownloader(TimeoutScheduler timeoutScheduler, LongSupplier monotonicClock,
			WorkExecutor workExecutor, Connector connector) {
		this.timeoutScheduler = timeoutScheduler;
		this.monotonicClock = monotonicClock;
		this.workExecutor = workExecutor;
		this.connector = connector;
	}

	public void setStatusListener(StatusListener statusListener) {
		this.statusListener = statusListener;
	}

	public void setReturnExistingOnFail(boolean returnExistingOnFail) {
		this.returnExistingOnFail = returnExistingOnFail;
	}

	public FutureSupplier<Status> download(String src, File dst) {
		var prefs = new BasicPreferenceStore();
		prefs.applyBooleanPref(DECODE, true);
		return download(src, dst, prefs);
	}

	public FutureSupplier<Status> download(String src, File dst, PreferenceStore prefs) {
		try {
			return download(new URL(src), dst, prefs);
		} catch (MalformedURLException ex) {
			return failed(ex);
		}
	}

	public FutureSupplier<Status> download(URL src, File dst, PreferenceStore prefs) {
		DownloadRequest request = new DownloadRequest(src, dst, prefs, statusListener);
		return downloads.submit(request);
	}

	private final class DownloadRequest implements CoordinatedRequest {
		private final URL source;
		private final File destination;
		private final PreferenceStore prefs;
		private final StatusListener listener;
		private final String destinationKey;
		private final String revisionKey;
		private final DownloadPromise promise;
		private int subscribers;
		private final AtomicBoolean finished = new AtomicBoolean();
		private Stage stage = Stage.QUEUED;
		private Cancellable operation = Cancellable.CANCELED;

		DownloadRequest(URL source, File destination, PreferenceStore prefs,
				StatusListener listener) {
			this.source = source;
			this.destination = destination;
			this.prefs = prefs;
			this.listener = listener;
			destinationKey = HttpFileDownloader.destinationKey(destination);
			revisionKey = source.toExternalForm() + '\n' + prefs.getStringPref(AGENT) + '\n' +
					prefs.getStringPref(ETAG) + '\n' + prefs.getBooleanPref(DECODE) + '\n' +
					prefs.getIntPref(RESP_TIMEOUT) + '\n' + prefs.getIntPref(DOWNLOAD_TIMEOUT);
			promise = new DownloadPromise(this::cancelOperation);
		}

		@Override
		public URL source() {
			return source;
		}

		@Override
		public String destinationKey() {
			return destinationKey;
		}

		@Override
		public String revisionKey() {
			return revisionKey;
		}

		@Override
		public FutureSupplier<Status> future() {
			return promise;
		}

		@Override
		public synchronized FutureSupplier<Status> subscribe() {
			Subscription subscription = new Subscription(this);
			subscribers++;
			promise.onCompletion(subscription::complete);
			return subscription;
		}

		private synchronized void subscriberCancelled() {
			if ((subscribers > 0) && (--subscribers == 0) && !promise.isDone()) promise.cancel();
		}

		@Override
		public boolean isDone() {
			return promise.isDone();
		}

		@Override
		public void start() {
			if (promise.isCancellationRequested()) {
				finish();
				return;
			}

			Log.d("Downloading ", source, " to ", destination);
			boolean exists = destination.isFile();

			if (exists) {
				long stamp = prefs.getLongPref(TIMESTAMP);
				int age = prefs.getIntPref(MAX_AGE);

				if ((stamp + (age * 1000L)) > System.currentTimeMillis()) {
					DownloadStatus status = new DownloadStatus(source, destination, destination.length());
					status.setEtag(prefs.getStringPref(ETAG));
					status.setCharset(prefs.getStringPref(CHARSET));
					status.setEncoding(prefs.getStringPref(ENCODING));
					Log.d("File age is less than ", age, ". Returning existing file: ", destination);
					succeed(status);
					finish();
					return;
				}
			}

			File parent = destination.getAbsoluteFile().getParentFile();
			if (parent == null) {
				fail(new IOException("Unable to create file: " + destination),
						new DownloadStatus(source, destination, 0));
				finish();
				return;
			}

			try {
				FileUtils.mkdirs(parent);
			} catch (IOException ex) {
				fail(ex, new DownloadStatus(source, destination, 0));
				finish();
				return;
			}

			HttpConnection.Opts opts = new HttpConnection.Opts();
			opts.url = source;
			opts.responseTimeout = Math.max(0, prefs.getIntPref(RESP_TIMEOUT));
			opts.userAgent = prefs.getStringPref(AGENT);
			if (exists) opts.ifNonMatch = prefs.getStringPref(ETAG);

			synchronized (this) {
				stage = Stage.PREHEADER;
			}

			try {
				Cancellable request = connector.connect(opts,
						(resp, err) -> onResponse(opts, resp, err));
				setOperation(Stage.PREHEADER, request);
			} catch (Throwable ex) {
				if (!promise.isCancellationRequested()) {
					fail(ex, new DownloadStatus(source, destination, 0));
				}
				finish();
			}
		}

		private FutureSupplier<?> onResponse(HttpConnection.Opts opts, HttpResponse response,
				Throwable error) {
			if (error != null) {
				if (!promise.isCancellationRequested()) {
					fail(error, new DownloadStatus(source, destination, 0));
				}
				finish();
				return completedVoid();
			}

			if (promise.isCancellationRequested() || finished.get()) {
				response.getConnection().close();
				finish();
				return completedVoid();
			}

			CharSequence encoding = response.getContentEncoding();
			if (encoding == null) {
				String path = opts.url.getPath();
				if ((path != null) && (path.endsWith(".gzip") || path.endsWith(".gz"))) {
					encoding = "gzip";
				}
			}

			DownloadStatus status = new DownloadStatus(opts.url, destination,
					response.getContentLength());
			status.setEtag(response.getEtag());
			status.setCharset(response.getCharset());
			status.setEncoding(encoding);
			Log.d("Response received:\n", response);

			if (response.getStatusCode() == HttpStatusCode.NOT_MODIFIED) {
				Log.d("File not modified: ", source, ". Returning existing file: ", destination);
				succeed(status);
				finish();
				return completedVoid();
			}

			int statusCode = response.getStatusCode();
			if ((statusCode < HttpStatusCode.OK) || (statusCode >= 300)) {
				fail(new HttpException("HTTP " + statusCode + " while downloading " + opts.url),
						status);
				response.getConnection().close();
				finish();
				return completedVoid();
			}

			File incomplete;
			try {
				incomplete = createStagingFile(destination, ".incomplete");
			} catch (IOException ex) {
				response.getConnection().close();
				fail(ex, status);
				finish();
				return completedVoid();
			}

			int idleTimeout = Math.max(0, prefs.getIntPref(RESP_TIMEOUT));
			int downloadTimeout = Math.max(0, prefs.getIntPref(DOWNLOAD_TIMEOUT));
			long timeoutMillis = TimeUnit.SECONDS.toMillis(downloadTimeout);
			long deadline = (timeoutMillis == 0) ? 0 : saturatedAdd(monotonicClock.getAsLong(),
					timeoutMillis);
			FutureSupplier<?> body = writePayload(response, incomplete, status, listener,
					idleTimeout, downloadTimeout);
			setOperation(Stage.BODY, body);
			return body.onCompletion((v, fail) -> bodyCompleted(body, incomplete, status, deadline,
					fail));
		}

		private void bodyCompleted(FutureSupplier<?> body, File incomplete, DownloadStatus status,
				long deadline, Throwable error) {
			clearOperation(body);

			if (error != null || promise.isCancellationRequested()) {
				delete(incomplete);
				if ((error != null) && !promise.isCancellationRequested()) fail(error, status);
				finish();
				return;
			}

			PostProcessTransfer post = new PostProcessTransfer(workExecutor, timeoutScheduler,
					monotonicClock, deadline, status.getUrl());
			setOperation(Stage.POST_PROCESS, post);
			post.onCompletion((v, fail) -> {
				clearOperation(post);
				if (fail == null) succeed(status);
				else if (!promise.isCancellationRequested()) fail(fail, status);
			});
			post.onExit(this::finish);
			post.start(token -> processDownloadedFile(status, incomplete, token));
		}

		private void processDownloadedFile(DownloadStatus status, File incomplete,
				CancellationToken token) throws Throwable {
			File staged = incomplete;

			try {
				token.check();
				if (status.getContentEncoding() == null) {
					try (InputStream in = new FileInputStream(incomplete)) {
						int first = in.read();
						token.check();
						if ((first == 0x1F) && (in.read() == 0x8B)) status.setEncoding("gzip");
					} catch (IOException ex) {
						token.check();
						Log.d(ex, "Failed to read file: ", destination);
					}
				}

				if (prefs.getBooleanPref(DECODE) && (status.getContentEncoding() != null)) {
					staged = decodeToStaging(status, incomplete, destination, token);
					status.setEncoding(null);
				}

				token.check();
				if (!promise.beginCommit()) throw new InterruptedIOException("Download was cancelled");
				try {
					replaceFile(staged, destination);
					try (PreferenceStore.Edit edit = prefs.editPreferenceStore()) {
						edit.setStringPref(ETAG, status.getEtag());
						edit.setStringPref(CHARSET, status.getCharacterEncoding());
						edit.setStringPref(ENCODING, status.getContentEncoding());
						edit.setLongPref(TIMESTAMP, System.currentTimeMillis());
					}
				} finally {
					promise.endCommit();
				}
			} finally {
				delete(incomplete);
				if (staged != incomplete) delete(staged);
			}
		}

		private void succeed(DownloadStatus status) {
			Log.d("Downloaded ", source, " to ", destination);
			promise.completeSuccess(status, listener);
		}

		private void fail(Throwable error, DownloadStatus status) {
			if (returnExistingOnFail && destination.isFile()) {
				Log.e(error, "Failed to download: ", status.getUrl(), ". Returning existing file: ",
						destination);
				status.failure = error;
				promise.completeSuccess(status, listener);
			} else {
				Log.e(error, "Failed to download ", status.getUrl(), " to ", destination);
				promise.completeFailure(error, status, listener);
			}
		}

		private void setOperation(Stage stage, Cancellable operation) {
			boolean cancel;
			synchronized (this) {
				cancel = finished.get() || promise.isCancellationRequested();
				if (!cancel) {
					this.stage = stage;
					this.operation = operation;
				}
			}
			if (cancel && shouldFinishCancelledOperation(stage, operation.cancel())) finish();
		}

		private void clearOperation(Cancellable operation) {
			synchronized (this) {
				if (this.operation == operation) this.operation = Cancellable.CANCELED;
			}
		}

		private void cancelOperation() {
			Stage stage;
			Cancellable operation;
			synchronized (this) {
				stage = this.stage;
				operation = this.operation;
				this.operation = Cancellable.CANCELED;
			}
			if (shouldFinishCancelledOperation(stage, operation.cancel())) finish();
		}

		private static boolean shouldFinishCancelledOperation(Stage stage, boolean detached) {
			return (stage == Stage.QUEUED) || ((stage == Stage.PREHEADER) && detached);
		}

		private void finish() {
			if (finished.compareAndSet(false, true)) downloads.finished(this);
		}
	}

	private enum Stage {
		QUEUED, PREHEADER, BODY, POST_PROCESS
	}

	private static File createStagingFile(File destination, String suffix) throws IOException {
		File parent = destination.getAbsoluteFile().getParentFile();
		if (parent == null) throw new IOException("Missing parent directory: " + destination);
		String prefix = destination.getName();
		if (prefix.length() < 3) prefix = (prefix + "___").substring(0, 3);

		try {
			return File.createTempFile(prefix, suffix, parent);
		} catch (IOException ex) {
			File fallback = new File(parent, prefix + '.' + stagingIds.incrementAndGet() + suffix);
			if (fallback.createNewFile()) return fallback;
			ex.addSuppressed(new IOException("Unable to create staging file: " + fallback));
			throw ex;
		}
	}

	private static String destinationKey(File destination) {
		try {
			return destination.getCanonicalPath();
		} catch (IOException ex) {
			return destination.getAbsoluteFile().toPath().normalize().toString();
		}
	}

	private static long saturatedAdd(long value, long increment) {
		long result = value + increment;
		return (((value ^ result) & (increment ^ result)) < 0) ? Long.MAX_VALUE : result;
	}

	private static void delete(File file) {
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	static File decodeToStaging(Status status, File source, File destination)
			throws IOException {
		return decodeToStaging(status, source, destination, CancellationToken.none());
	}

	static File decodeToStaging(Status status, File source, File destination,
			CancellationToken token) throws IOException {
		File staged = createStagingFile(destination, ".decoded");
		try (var in = status.getFileStream(source, true);
				 var out = new FileOutputStream(staged)) {
			copyCancellable(in, out, token);
			return staged;
		} catch (IOException ex) {
			delete(staged);
			throw ex;
		}
	}

	static void copyCancellable(InputStream input, OutputStream output, CancellationToken token)
			throws IOException {
		byte[] buffer = new byte[8192];

		for (int read; ; ) {
			token.check();
			read = input.read(buffer);
			if (read < 0) break;
			if (read == 0) continue;
			output.write(buffer, 0, read);
			token.check();
		}
	}

	static void replaceFile(File source, File destination) throws IOException {
		Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
	}

	private FutureSupplier<?> writePayload(HttpResponse resp, File dst, DownloadStatus status,
																	 StatusListener listener, int idleTimeout,
																	 int downloadTimeout) {
		BodyOutputStream out = null;

		try {
			out = new BodyOutputStream(dst, status, listener);
			FutureSupplier<?> payload = resp.writePayload(out);
			BodyTransfer transfer = new BodyTransfer(payload, out, resp.getConnection()::close,
					timeoutScheduler, monotonicClock, TimeUnit.SECONDS.toMillis(idleTimeout),
					TimeUnit.SECONDS.toMillis(downloadTimeout), status.getUrl());
			out.setActivityListener(transfer::onActivity);
			transfer.start();
			return transfer;
		} catch (Throwable ex) {
			IoUtils.close(out);
			resp.getConnection().close();
			return failed(ex);
		}
	}

	interface WorkExecutor {
		FutureSupplier<Void> submit(CheckedRunnable<Throwable> task);
	}

	interface Connector {
		Cancellable connect(HttpConnection.Opts opts,
				BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer);
	}

	interface TimeoutScheduler {
		Cancellable schedule(Runnable task, long delayMillis);
	}

	static final class BodyTransfer extends Promise<Void> {
		private final FutureSupplier<?> payload;
		private final OutputStream output;
		private final Runnable abortConnection;
		private final TimeoutScheduler scheduler;
		private final LongSupplier clock;
		private final long idleTimeout;
		private final long downloadTimeout;
		private final URL url;
		private final AtomicBoolean terminated = new AtomicBoolean();
		private volatile long lastActivity;
		private volatile Cancellable idleTimer = Cancellable.CANCELED;
		private volatile Cancellable deadlineTimer = Cancellable.CANCELED;

		BodyTransfer(FutureSupplier<?> payload, OutputStream output, Runnable abortConnection,
						 TimeoutScheduler scheduler, LongSupplier clock, long idleTimeout,
						 long downloadTimeout, URL url) {
			this.payload = payload;
			this.output = output;
			this.abortConnection = abortConnection;
			this.scheduler = scheduler;
			this.clock = clock;
			this.idleTimeout = idleTimeout;
			this.downloadTimeout = downloadTimeout;
			this.url = url;
		}

		void start() {
			lastActivity = clock.getAsLong();

			try {
				if (idleTimeout > 0) idleTimer = scheduleTimer(this::checkIdle, idleTimeout);
				if (downloadTimeout > 0) {
					deadlineTimer = scheduleTimer(this::deadlineExpired, downloadTimeout);
				}
			} catch (Throwable ex) {
				terminate(ex, true, false);
				return;
			}

			payload.onCompletion((v, fail) -> terminate(fail, fail != null, false));
		}

		void onActivity() {
			if (!terminated.get()) lastActivity = clock.getAsLong();
		}

		private void checkIdle() {
			if (terminated.get()) return;
			long remaining = idleTimeout - (clock.getAsLong() - lastActivity);

			if (remaining > 0) {
				try {
					idleTimer = scheduleTimer(this::checkIdle, remaining);
				} catch (Throwable ex) {
					terminate(ex, true, false);
				}
			} else {
				terminate(new TimeoutException("Download body idle timeout: " + url), true, false);
			}
		}

		private void deadlineExpired() {
			terminate(new TimeoutException("Download body deadline exceeded: " + url), true, false);
		}

		private Cancellable scheduleTimer(Runnable task, long delay) {
			return HttpFileDownloader.scheduleTimer(scheduler, task, delay);
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return terminate(null, true, true);
		}

		private boolean terminate(Throwable fail, boolean abort, boolean cancel) {
			if (!terminated.compareAndSet(false, true)) return false;
			idleTimer.cancel();
			deadlineTimer.cancel();
			try {
				output.close();
			} catch (Throwable closeFailure) {
				if (fail == null) fail = closeFailure;
				else fail.addSuppressed(closeFailure);
				abort = true;
			}

			if (abort) {
				payload.cancel(cancel);
				try {
					abortConnection.run();
				} catch (Throwable ex) {
					Log.d(ex, "Failed to abort HTTP download connection");
				}
			}

			if (cancel) return super.cancel(true);
			if (fail != null) return super.completeExceptionally(fail);
			return super.complete(null);
		}
	}

	interface PostProcessAction {
		void run(CancellationToken token) throws Throwable;
	}

	static final class PostProcessTransfer extends Promise<Void> {
		private final WorkExecutor executor;
		private final TimeoutScheduler scheduler;
		private final LongSupplier clock;
		private final long deadline;
		private final URL url;
		private final CancellationToken token;
		private final Promise<Void> exited = new Promise<>();
		private final AtomicBoolean executionStarted = new AtomicBoolean();
		private final AtomicBoolean executionFinished = new AtomicBoolean();
		private volatile Cancellable deadlineTimer = Cancellable.CANCELED;
		private volatile FutureSupplier<Void> submitted;

		PostProcessTransfer(WorkExecutor executor, TimeoutScheduler scheduler, LongSupplier clock,
				long deadline, URL url) {
			this.executor = executor;
			this.scheduler = scheduler;
			this.clock = clock;
			this.deadline = deadline;
			this.url = url;
			token = new CancellationToken(clock, deadline,
					new TimeoutException("Download post-processing deadline exceeded: " + url));
		}

		void start(PostProcessAction action) {
			if (deadline != 0) {
				long remaining = deadline - clock.getAsLong();
				if (remaining <= 0) deadlineExpired();
				else deadlineTimer = scheduleTimer(scheduler, this::deadlineExpired, remaining);
			}

			FutureSupplier<Void> submitted;
			try {
				submitted = executor.submit(() -> {
					executionStarted.set(true);
					Throwable failure = null;
					try {
						token.check();
						action.run(token);
						token.check();
					} catch (Throwable ex) {
						failure = ex;
					} finally {
						finishExecution(failure);
					}
				});
			} catch (Throwable ex) {
				finishExecution(ex);
				return;
			}

			this.submitted = submitted;
			if (token.isCancelled() || token.isTimedOut()) submitted.cancel();
			submitted.onCompletion((v, fail) -> {
				if ((fail != null) && !executionStarted.get()) finishExecution(fail);
			});
		}

		FutureSupplier<Void> onExit(Runnable action) {
			return exited.thenRun(action);
		}

		private void deadlineExpired() {
			token.timeout();
			FutureSupplier<Void> work = submitted;
			if (work != null) work.cancel(true);
			super.completeExceptionally(token.timeoutFailure());
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (isDone()) return false;
			token.cancel();
			FutureSupplier<Void> work = submitted;
			if (work != null) work.cancel(mayInterruptIfRunning);
			return super.cancel(mayInterruptIfRunning);
		}

		private void finishExecution(Throwable failure) {
			if (!executionFinished.compareAndSet(false, true)) return;
			deadlineTimer.cancel();

			if (!isDone()) {
				if (token.isTimedOut()) super.completeExceptionally(token.timeoutFailure());
				else if (token.isCancelled()) super.cancel(true);
				else if (failure != null) super.completeExceptionally(failure);
				else super.complete(null);
			}

			exited.complete(null);
		}
	}

	static final class CancellationToken {
		private final LongSupplier clock;
		private final long deadline;
		private final TimeoutException timeoutFailure;
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private final AtomicBoolean timedOut = new AtomicBoolean();

		CancellationToken(LongSupplier clock, long deadline, TimeoutException timeoutFailure) {
			this.clock = clock;
			this.deadline = deadline;
			this.timeoutFailure = timeoutFailure;
		}

		static CancellationToken none() {
			return new CancellationToken(() -> 0, 0, new TimeoutException());
		}

		void cancel() {
			cancelled.set(true);
		}

		void timeout() {
			timedOut.set(true);
		}

		boolean isCancelled() {
			return cancelled.get();
		}

		boolean isTimedOut() {
			return timedOut.get();
		}

		TimeoutException timeoutFailure() {
			return timeoutFailure;
		}

		void check() throws IOException {
			if ((deadline != 0) && (clock.getAsLong() >= deadline)) timeout();
			if (isTimedOut()) {
				InterruptedIOException ex = new InterruptedIOException(timeoutFailure.getMessage());
				ex.initCause(timeoutFailure);
				throw ex;
			}
			if (isCancelled() || Thread.currentThread().isInterrupted()) {
				throw new InterruptedIOException("Download post-processing cancelled");
			}
		}
	}

	private static Cancellable scheduleTimer(TimeoutScheduler scheduler, Runnable task, long delay) {
		TimeoutTask timeoutTask = new TimeoutTask(task);
		Cancellable timer = scheduler.schedule(timeoutTask, delay);
		return () -> {
			timeoutTask.clear();
			return timer.cancel();
		};
	}

	private static final class TimeoutTask implements Runnable {
		private volatile Runnable task;

		TimeoutTask(Runnable task) {
			this.task = task;
		}

		@Override
		public void run() {
			Runnable task = this.task;
			this.task = null;
			if (task != null) task.run();
		}

		void clear() {
			task = null;
		}
	}

	private static final class BodyOutputStream extends OutputStream {
		private final OutputStream output;
		private final DownloadStatus status;
		private final StatusListener listener;
		private final AtomicBoolean closed = new AtomicBoolean();
		private volatile Runnable activityListener;

		BodyOutputStream(File file, DownloadStatus status, StatusListener listener)
				throws FileNotFoundException {
			output = new FileOutputStream(file);
			this.status = status;
			this.listener = listener;
		}

		void setActivityListener(Runnable activityListener) {
			this.activityListener = activityListener;
		}

		@Override
		public void write(int b) throws IOException {
			output.write(b);
			recordProgress(1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			output.write(b, off, len);
			recordProgress(len);
		}

		private void recordProgress(int len) {
			status.bytesDownloaded += len;
			Runnable activity = activityListener;
			if (activity != null) activity.run();
			if (listener != null) listener.onProgress(status);
		}

		@Override
		public void flush() throws IOException {
			output.flush();
		}

		@Override
		public void close() throws IOException {
			if (closed.compareAndSet(false, true)) output.close();
		}
	}

	interface CancelHandler {
		void cancel();
	}

	static final class DownloadPromise extends Promise<Status> {
		private final CancelHandler cancelHandler;
		private boolean cancellationRequested;
		private boolean terminalClaimed;
		private boolean committing;

		DownloadPromise() {
			this(() -> {
			});
		}

		DownloadPromise(CancelHandler cancelHandler) {
			this.cancelHandler = cancelHandler;
		}

		synchronized boolean isCancellationRequested() {
			return cancellationRequested;
		}

		boolean completeSuccess(Status status, StatusListener listener) {
			if (!claimTerminal()) return false;
			boolean completed = super.complete(status);
			if (listener != null) {
				try {
					listener.onSuccess(status);
				} catch (Throwable ex) {
					Log.e(ex, "HTTP download success listener failed");
				}
			}
			return completed;
		}

		boolean completeFailure(Throwable failure, Status status, StatusListener listener) {
			if (!claimTerminal()) return false;
			if (status instanceof DownloadStatus downloadStatus) downloadStatus.failure = failure;
			boolean completed = super.completeExceptionally(failure);
			if (listener != null) {
				try {
					listener.onFailure(status);
				} catch (Throwable ex) {
					Log.e(ex, "HTTP download failure listener failed");
				}
			}
			return completed;
		}

		private synchronized boolean claimTerminal() {
			if (terminalClaimed || cancellationRequested || isDone()) return false;
			terminalClaimed = true;
			return true;
		}

		synchronized boolean beginCommit() {
			if (terminalClaimed || cancellationRequested || committing || isDone()) return false;
			committing = true;
			return true;
		}

		synchronized void endCommit() {
			committing = false;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			synchronized (this) {
				if (terminalClaimed || cancellationRequested || committing || isDone()) return false;
				cancellationRequested = true;
			}

			boolean cancelled = super.cancel(mayInterruptIfRunning);
			cancelHandler.cancel();
			return cancelled;
		}
	}

	interface CoordinatedRequest {
		URL source();

		String destinationKey();

		String revisionKey();

		FutureSupplier<Status> future();

		default FutureSupplier<Status> subscribe() {
			return future().fork();
		}

		default void cancelRequest() {
			future().cancel();
		}

		boolean isDone();

		void start();
	}

	static final class DestinationCoordinator {
		private final Map<String, DestinationSlot> slots = new ConcurrentHashMap<>();

		FutureSupplier<Status> submit(CoordinatedRequest request) {
			for (; ; ) {
				String key = request.destinationKey();
				DestinationSlot slot = slots.computeIfAbsent(key, unused -> new DestinationSlot());
				CoordinatedRequest cancelActive = null;
				CoordinatedRequest cancelPending = null;
				boolean start = false;

				synchronized (slot) {
					if (slots.get(key) != slot) continue;

					if (slot.active == null) {
						slot.active = request;
						start = true;
					} else if (!slot.active.isDone() &&
							slot.active.revisionKey().equals(request.revisionKey())) {
						return slot.active.subscribe();
					} else if ((slot.pending != null) && !slot.pending.isDone()
							&& slot.pending.revisionKey().equals(request.revisionKey())) {
						return slot.pending.subscribe();
					} else {
						cancelPending = slot.pending;
						slot.pending = request;
						if (!slot.active.isDone()) cancelActive = slot.active;
					}
				}

				if (cancelPending != null) cancelPending.cancelRequest();
				if (cancelActive != null) cancelActive.cancelRequest();
				if (start) request.start();
				return request.subscribe();
			}
		}

		void finished(CoordinatedRequest request) {
			String key = request.destinationKey();
			DestinationSlot slot = slots.get(key);
			if (slot == null) return;
			CoordinatedRequest next = null;

			synchronized (slot) {
				if (slots.get(key) != slot) return;

				if (slot.active == request) {
					slot.active = slot.pending;
					slot.pending = null;
					next = slot.active;
				} else if (slot.pending == request) {
					slot.pending = null;
				}

				if ((slot.active == null) && (slot.pending == null)) slots.remove(key, slot);
			}

			if (next != null) next.start();
		}
	}

	private static final class DestinationSlot {
		CoordinatedRequest active;
		CoordinatedRequest pending;
	}

	private static final class Subscription extends Promise<Status> {
		private final DownloadRequest request;

		Subscription(DownloadRequest request) {
			this.request = request;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean cancelled = super.cancel(mayInterruptIfRunning);
			if (cancelled) request.subscriberCancelled();
			return cancelled;
		}
	}

	public interface Status extends VirtualFile.Info {

		URL getUrl();

		String getEtag();

		Throwable getFailure();

		long bytesDownloaded();

		default InputStream getFileStream(boolean decode) throws IOException {
			return getFileStream(getLocalFile(), decode);
		}

		default InputStream getFileStream(File file, boolean decode) throws IOException {
			InputStream in = new FileInputStream(file);

			try {
				if (decode) {
					String enc = getContentEncoding();

					if (enc != null) {
						if ("gzip".equals(enc)) {
							return new GZIPInputStream(in);
						} else if ("deflate".equals(enc)) {
							return new InflaterInputStream(in);
						} else {
							throw new IOException("Unsupported encoding: " + enc);
						}
					}
				}
				return in;
			} catch (IOException ex) {
				IoUtils.close(in);
				throw ex;
			}
		}
	}

	public interface StatusListener {

		void onProgress(Status status);

		void onSuccess(Status status);

		void onFailure(Status status);
	}

	private static final class DownloadStatus implements Status {
		private final URL url;
		private final File file;
		private final long len;
		String etag;
		String charset;
		String encoding;
		long bytesDownloaded;
		Throwable failure;

		public DownloadStatus(URL url, File file, long len) {
			this.url = url;
			this.file = file;
			this.len = len;
		}

		@NonNull
		@Override
		public URL getUrl() {
			return url;
		}

		@NonNull
		@Override
		public File getLocalFile() {
			return file;
		}

		@Override
		public long getLength() {
			return len;
		}

		@Override
		public long bytesDownloaded() {
			return bytesDownloaded;
		}

		@Override
		public String getEtag() {
			return etag;
		}

		public void setEtag(CharSequence etag) {
			if (etag != null) this.etag = etag.toString();
		}

		@Override
		public String getCharacterEncoding() {
			return charset;
		}

		public void setCharset(CharSequence charset) {
			if (charset != null) this.charset = charset.toString().toUpperCase();
		}

		@Override
		public String getContentEncoding() {
			return encoding;
		}

		public void setEncoding(CharSequence encoding) {
			this.encoding = (encoding != null) ? encoding.toString() : null;
		}

		@Override
		public Throwable getFailure() {
			return failure;
		}

		@NonNull
		@Override
		public String toString() {
			return "DownloadStatus {" +
					"\n  source=" + url +
					"\n  destination=" + file +
					"\n  length=" + len +
					"\n  etag='" + etag + '\'' +
					"\n  charset='" + charset + '\'' +
					"\n  encoding='" + encoding + '\'' +
					"\n  bytesDownloaded=" + bytesDownloaded +
					"\n  failure=" + failure +
					"\n}";
		}
	}
}
