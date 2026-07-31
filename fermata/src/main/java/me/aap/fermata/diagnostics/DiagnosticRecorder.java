package me.aap.fermata.diagnostics;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Public local-first diagnostics facade. Recording never performs file I/O on the caller thread;
 * events are sanitized, bounded, and written by one private daemon worker.
 */
public final class DiagnosticRecorder implements AutoCloseable {
	private static final int MAX_BATCH_SIZE = 64;
	private static final long MIN_RECOVERY_DELAY_MILLIS = 1000L;
	private static final long MAX_RECOVERY_DELAY_MILLIS = 60_000L;

	private final DiagnosticConfig config;
	private final DiagnosticClock clock;
	private final DiagnosticSanitizer sanitizer;
	private final String sessionId;
	private final String processName;
	private final int processId;
	private final BoundedEventQueue queue;
	private final BreadcrumbBuffer breadcrumbs;
	private final RotatingDiagnosticStore store;
	private final ConcurrentLinkedQueue<StoreCommand> commands = new ConcurrentLinkedQueue<>();
	private final ReentrantReadWriteLock producerBarrier = new ReentrantReadWriteLock();
	private final Object workerLock = new Object();
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicLong accepted = new AtomicLong();
	private final AtomicLong written = new AtomicLong();
	private final AtomicLong writeFailures = new AtomicLong();
	private final EnumMap<DiagnosticPriority, AtomicLong> dropped =
			new EnumMap<>(DiagnosticPriority.class);
	private volatile Thread worker;

	private volatile DetailedDiagnosticsState detailedState;
	private volatile boolean accepting = true;
	private volatile boolean running = true;
	private volatile boolean storageHealthy = true;
	private volatile long nextRecoveryElapsedMillis;
	private volatile long workerRestartDelayMillis;
	private int consecutiveStorageFailures;

	private DiagnosticRecorder(Builder builder) {
		config = builder.config;
		clock = builder.clock;
		sessionId = (builder.sessionId == null) ? UUID.randomUUID().toString() : builder.sessionId;
		sanitizer = new DiagnosticSanitizer(config.getMaxStringChars(), sessionId);
		processName = builder.processName;
		processId = builder.processId;
		detailedState = builder.detailedState;
		queue = new BoundedEventQueue(config.getQueueCapacity(), config.getMaxQueueBytes());
		breadcrumbs = new BreadcrumbBuffer(config.getBreadcrumbCapacity());
		store = new RotatingDiagnosticStore(builder.directory, config, sessionId);
		for (DiagnosticPriority priority : DiagnosticPriority.values()) {
			dropped.put(priority, new AtomicLong());
		}
		ensureWorker();
	}

	public static DiagnosticRecorder create(File directory, DiagnosticConfig config) {
		return builder(directory).config(config).build();
	}

	public static Builder builder(File directory) {
		return new Builder(directory);
	}

	/** Records an event after enriching it with session, process, thread, and clock fields. */
	public boolean record(DiagnosticEvent event) {
		if ((event == null) || !accepting || !ensureWorker()) return false;
		producerBarrier.readLock().lock();
		try {
			return recordLocked(event);
		} finally {
			producerBarrier.readLock().unlock();
		}
	}

	private boolean recordLocked(DiagnosticEvent event) {
		if ((event == null) || !accepting) return false;
		long wallTime = clock.wallTimeMillis();
		if ((event.getScope() == DiagnosticScope.DETAILED) && !isDetailedEnabled(wallTime)) {
			return false;
		}

		Thread caller = Thread.currentThread();
		DiagnosticEvent enriched;
		try {
			enriched = event.withEnvelope(sequence.incrementAndGet(), wallTime,
					clock.elapsedRealtimeMillis(), sessionId, processName, processId,
					caller.getName(), caller.getId(), sanitizer);
		} catch (RuntimeException ignored) {
			dropped.get(event.getPriority()).incrementAndGet();
			return false;
		}

		breadcrumbs.add(enriched);
		BoundedEventQueue.OfferResult result;
		try {
			result = queue.offer(enriched);
		} catch (RuntimeException ignored) {
			dropped.get(event.getPriority()).incrementAndGet();
			return false;
		}
		DiagnosticEvent lost = result.getDropped();
		if (lost != null) dropped.get(lost.getPriority()).incrementAndGet();
		if (!result.isAccepted()) return false;
		accepted.incrementAndGet();
		return true;
	}

	public boolean recordEssential(String category, String name, DiagnosticPriority priority,
			String operationId, Map<String, ?> attributes) {
		return record(DiagnosticEvent.builder(category, name)
				.scope(DiagnosticScope.ESSENTIAL)
				.priority(priority)
				.operationId(operationId)
				.attributes(attributes)
				.build());
	}

	public boolean recordDetailed(String category, String name, String operationId,
			Map<String, ?> attributes) {
		return record(DiagnosticEvent.builder(category, name)
				.scope(DiagnosticScope.DETAILED)
				.priority(DiagnosticPriority.DETAIL)
				.operationId(operationId)
				.attributes(attributes)
				.build());
	}

	public boolean recordError(String category, String name, String operationId, Throwable error,
			Map<String, ?> attributes) {
		return record(DiagnosticEvent.builder(category, name)
				.scope(DiagnosticScope.ESSENTIAL)
				.priority(DiagnosticPriority.ERROR)
				.operationId(operationId)
				.attributes(attributes)
				.error(error)
				.build());
	}

	public DiagnosticOperation beginOperation(String category, String operationName,
			Map<String, ?> attributes) {
		return new DiagnosticOperation(this, category, operationName, UUID.randomUUID().toString(),
				(attributes == null) ? Collections.emptyMap() : attributes);
	}

	public void setDetailedEnabled(boolean enabled) {
		detailedState = enabled ? wallTimeMillis -> true : DetailedDiagnosticsState.DISABLED;
	}

	/** Supports caller-owned preferences and the 48-hour expiry without coupling core to Android. */
	public void setDetailedState(DetailedDiagnosticsState state) {
		detailedState = (state == null) ? DetailedDiagnosticsState.DISABLED : state;
	}

	public boolean isDetailedEnabled() {
		return isDetailedEnabled(clock.wallTimeMillis());
	}

	public List<String> snapshotBreadcrumbs() {
		return breadcrumbs.snapshot();
	}

	/** Waits for accepted events to be written and flushed. Never performs file I/O itself. */
	public boolean flush(long timeoutMillis) {
		return requestCommand(CommandType.FLUSH, null, timeoutMillis, true);
	}

	/** Creates a stable journal snapshot on the writer thread. */
	public boolean createSnapshot(File destination, long timeoutMillis) {
		if (destination == null) throw new NullPointerException("destination");
		return requestCommand(CommandType.SNAPSHOT, destination, timeoutMillis, true);
	}

	/** Records one high-value event and synchronizes it before allowing later producers through. */
	public boolean recordAndSync(DiagnosticEvent event, long timeoutMillis) {
		if ((event == null) || !accepting || !ensureWorker() ||
				(Thread.currentThread() == worker)) return false;
		StoreCommand command;
		producerBarrier.writeLock().lock();
		try {
			long wallTime = clock.wallTimeMillis();
			if ((event.getScope() == DiagnosticScope.DETAILED) && !isDetailedEnabled(wallTime)) {
				return false;
			}
			Thread caller = Thread.currentThread();
			DiagnosticEvent enriched;
			try {
				enriched = event.withEnvelope(sequence.incrementAndGet(), wallTime,
						clock.elapsedRealtimeMillis(), sessionId, processName, processId,
						caller.getName(), caller.getId(), sanitizer);
				enriched.encodedJson();
			} catch (RuntimeException ignored) {
				dropped.get(event.getPriority()).incrementAndGet();
				return false;
			}
			breadcrumbs.add(enriched);
			accepted.incrementAndGet();
			command = StoreCommand.writeSync(enriched);
			commands.add(command);
			queue.wake();
		} finally {
			producerBarrier.writeLock().unlock();
		}
		return command.await(timeoutMillis);
	}

	/** Schedules deletion of journal files and breadcrumbs on the writer thread. */
	public void clear() {
		requestCommand(CommandType.CLEAR, null, 0L, false);
	}

	/** Schedules deletion and waits up to the supplied timeout. */
	public boolean clear(long timeoutMillis) {
		return requestCommand(CommandType.CLEAR, null, timeoutMillis, true);
	}

	public Stats getStats() {
		EnumMap<DiagnosticPriority, Long> dropSnapshot = new EnumMap<>(DiagnosticPriority.class);
		for (Map.Entry<DiagnosticPriority, AtomicLong> entry : dropped.entrySet()) {
			dropSnapshot.put(entry.getKey(), entry.getValue().get());
		}
		return new Stats(accepted.get(), written.get(), writeFailures.get(),
				Collections.unmodifiableMap(dropSnapshot), queue.size(), storageHealthy);
	}

	@Override
	public void close() {
		if (!accepting && !running) return;
		producerBarrier.writeLock().lock();
		try {
			accepting = false;
		} finally {
			producerBarrier.writeLock().unlock();
		}
		flush(Math.max(1000L, config.getFlushIntervalMillis() * 2L));
		running = false;
		queue.wake();
		Thread activeWorker = worker;
		if (activeWorker == null) return;
		try {
			activeWorker.join(Math.max(1000L, config.getFlushIntervalMillis() * 2L));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean requestCommand(CommandType type, File destination, long timeoutMillis,
			boolean wait) {
		if (!running || !ensureWorker() || (Thread.currentThread() == worker)) return false;
		StoreCommand command;
		producerBarrier.writeLock().lock();
		try {
			command = StoreCommand.create(type, sequence.get(), destination);
			if (type == CommandType.CLEAR) breadcrumbs.clear();
			commands.add(command);
			queue.wake();
		} finally {
			producerBarrier.writeLock().unlock();
		}
		return !wait || command.await(timeoutMillis);
	}

	private boolean isDetailedEnabled(long wallTimeMillis) {
		try {
			return detailedState.isEnabled(wallTimeMillis);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private void writerLoop() {
		boolean restart = true;
		try {
			long lastFlush = clock.elapsedRealtimeMillis();
			workerRestartDelayMillis = 0L;
			if (!storageHealthy) ensureStorageHealthy(true);
			while (running || !queue.isEmpty() || !commands.isEmpty()) {
				processReadyCommands();
				DiagnosticEvent event = null;
				try {
					event = queue.poll(config.getFlushIntervalMillis());
				} catch (InterruptedException ignored) {
					if (!running) Thread.currentThread().interrupt();
				}

				int batch = 0;
				while (event != null) {
					processCommandsBefore(event.getSequence());
					write(event);
					if (++batch >= MAX_BATCH_SIZE) break;
					processReadyCommands();
					event = queue.poll();
				}

				long now = clock.elapsedRealtimeMillis();
				if ((now - lastFlush) >= config.getFlushIntervalMillis()) {
					flushStore();
					lastFlush = now;
				}
				processReadyCommands();
			}
			flushStore();
			processReadyCommands();
		} catch (Throwable failure) {
			if (isFatal(failure)) {
				restart = false;
				throw failure;
			}
			long previousDelay = workerRestartDelayMillis;
			workerRestartDelayMillis = Math.min(5000L,
					(previousDelay <= 0L) ? 100L : previousDelay * 2L);
			markStorageFailure();
		} finally {
			try {
				store.close();
			} catch (IOException ignored) {
				writeFailures.incrementAndGet();
			}
			StoreCommand command;
			while ((command = commands.poll()) != null) command.complete(false);
			synchronized (workerLock) {
				if (Thread.currentThread() == worker) {
					worker = null;
					if (running && restart) startWorkerLocked();
				}
			}
		}
	}

	private boolean write(DiagnosticEvent event) {
		if (!ensureStorageHealthy(false)) {
			dropped.get(event.getPriority()).incrementAndGet();
			return false;
		}
		String encoded;
		try {
			encoded = event.encodedJson();
		} catch (RuntimeException ignored) {
			dropped.get(event.getPriority()).incrementAndGet();
			return false;
		}
		try {
			store.write(encoded, event.getWallTimeMillis());
			written.incrementAndGet();
			return true;
		} catch (RotatingDiagnosticStore.EventTooLargeException ignored) {
			dropped.get(event.getPriority()).incrementAndGet();
		} catch (IOException | RuntimeException ignored) {
			markStorageFailure();
			dropped.get(event.getPriority()).incrementAndGet();
		}
		return false;
	}

	private void flushStore() {
		if (!ensureStorageHealthy(false)) return;
		try {
			store.flush();
		} catch (IOException ignored) {
			markStorageFailure();
		}
	}

	private void processReadyCommands() {
		DiagnosticEvent next = queue.peek();
		processCommandsBefore((next == null) ? Long.MAX_VALUE : next.getSequence());
	}

	private void processCommandsBefore(long nextEventSequence) {
		for (;;) {
			StoreCommand command = commands.peek();
			if ((command == null) || (command.targetSequence >= nextEventSequence)) return;
			commands.poll();
			processCommand(command);
		}
	}

	private void processCommand(StoreCommand command) {
		boolean success = false;
		try {
			if (command.type == CommandType.CLEAR) {
				store.clear();
				markStorageHealthy();
				success = true;
			} else if (command.type == CommandType.WRITE_SYNC) {
				if (ensureStorageHealthy(true) && write(command.event)) {
					store.sync();
					success = true;
				}
			} else if (ensureStorageHealthy(true)) {
				switch (command.type) {
					case FLUSH:
						store.flush();
						break;
					case SNAPSHOT:
						store.snapshot(command.destination);
						break;
					default:
						throw new IllegalStateException("Unsupported diagnostics command");
				}
				success = true;
			}
		} catch (IOException | RuntimeException ignored) {
			markStorageFailure();
		} finally {
			command.complete(success);
		}
	}

	private boolean ensureStorageHealthy(boolean force) {
		if (storageHealthy) return true;
		long now = clock.elapsedRealtimeMillis();
		if (!force && (now < nextRecoveryElapsedMillis)) return false;
		try {
			store.recover(clock.wallTimeMillis());
			markStorageHealthy();
			return true;
		} catch (IOException | RuntimeException ignored) {
			markStorageFailure();
			return false;
		}
	}

	private void markStorageHealthy() {
		storageHealthy = true;
		consecutiveStorageFailures = 0;
		nextRecoveryElapsedMillis = 0L;
	}

	private void markStorageFailure() {
		writeFailures.incrementAndGet();
		storageHealthy = false;
		int shift = Math.min(6, consecutiveStorageFailures++);
		long delay = Math.min(MAX_RECOVERY_DELAY_MILLIS,
				MIN_RECOVERY_DELAY_MILLIS << shift);
		nextRecoveryElapsedMillis = clock.elapsedRealtimeMillis() + delay;
	}

	private boolean ensureWorker() {
		if (!running) return false;
		Thread current = worker;
		if ((current != null) && current.isAlive()) return true;
		synchronized (workerLock) {
			current = worker;
			if ((current != null) && current.isAlive()) return true;
			startWorkerLocked();
			return true;
		}
	}

	private void startWorkerLocked() {
		long delay = workerRestartDelayMillis;
		Thread replacement = new Thread(() -> {
			if (delay > 0L) {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
			writerLoop();
		}, "FermataX-Diagnostics");
		replacement.setDaemon(true);
		worker = replacement;
		replacement.start();
	}

	private static boolean isFatal(Throwable failure) {
		return (failure instanceof ThreadDeath) || (failure instanceof VirtualMachineError);
	}

	public static final class Builder {
		private final File directory;
		private DiagnosticConfig config = DiagnosticConfig.defaults();
		private DiagnosticClock clock = DiagnosticClock.SYSTEM;
		private DetailedDiagnosticsState detailedState = DetailedDiagnosticsState.DISABLED;
		private String sessionId;
		private String processName = "unknown";
		private int processId;

		private Builder(File directory) {
			if (directory == null) throw new NullPointerException("directory");
			this.directory = directory;
		}

		public Builder config(DiagnosticConfig value) {
			if (value == null) throw new NullPointerException("config");
			config = value;
			return this;
		}

		public Builder clock(DiagnosticClock value) {
			if (value == null) throw new NullPointerException("clock");
			clock = value;
			return this;
		}

		public Builder detailedState(DetailedDiagnosticsState value) {
			detailedState = (value == null) ? DetailedDiagnosticsState.DISABLED : value;
			return this;
		}

		public Builder sessionId(String value) {
			sessionId = value;
			return this;
		}

		public Builder process(String name, int id) {
			processName = (name == null) ? "unknown" : name;
			processId = id;
			return this;
		}

		public DiagnosticRecorder build() {
			return new DiagnosticRecorder(this);
		}
	}

	public static final class Stats {
		private final long accepted;
		private final long written;
		private final long writeFailures;
		private final Map<DiagnosticPriority, Long> dropped;
		private final int queued;
		private final boolean storageHealthy;

		private Stats(long accepted, long written, long writeFailures,
				Map<DiagnosticPriority, Long> dropped, int queued, boolean storageHealthy) {
			this.accepted = accepted;
			this.written = written;
			this.writeFailures = writeFailures;
			this.dropped = dropped;
			this.queued = queued;
			this.storageHealthy = storageHealthy;
		}

		public long getAccepted() {
			return accepted;
		}

		public long getWritten() {
			return written;
		}

		public long getWriteFailures() {
			return writeFailures;
		}

		public Map<DiagnosticPriority, Long> getDropped() {
			return dropped;
		}

		public int getQueued() {
			return queued;
		}

		public boolean isStorageHealthy() {
			return storageHealthy;
		}
	}

	private enum CommandType {
		FLUSH,
		WRITE_SYNC,
		CLEAR,
		SNAPSHOT
	}

	private static final class StoreCommand {
		private final CountDownLatch completed = new CountDownLatch(1);
		private final CommandType type;
		private final long targetSequence;
		private final File destination;
		private final DiagnosticEvent event;
		private volatile boolean success;

		private StoreCommand(CommandType type, long targetSequence, File destination,
				DiagnosticEvent event) {
			this.type = type;
			this.targetSequence = targetSequence;
			this.destination = destination;
			this.event = event;
		}

		static StoreCommand create(CommandType type, long targetSequence, File destination) {
			return new StoreCommand(type, targetSequence, destination, null);
		}

		static StoreCommand writeSync(DiagnosticEvent event) {
			return new StoreCommand(CommandType.WRITE_SYNC, event.getSequence(), null, event);
		}

		void complete(boolean success) {
			this.success = success;
			completed.countDown();
		}

		boolean await(long timeoutMillis) {
			if (timeoutMillis <= 0L) return false;
			try {
				return completed.await(timeoutMillis, TimeUnit.MILLISECONDS) && success;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
	}
}
