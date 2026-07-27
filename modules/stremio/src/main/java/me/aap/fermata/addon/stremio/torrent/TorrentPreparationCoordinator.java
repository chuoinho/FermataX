package me.aap.fermata.addon.stremio.torrent;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.TorrentHandle;

import me.aap.fermata.addon.stremio.torrent.StremioTorrentEngine.PreparedTorrent;
import me.aap.fermata.media.engine.PlaybackFailureException;
import me.aap.fermata.media.net.RemotePlaybackProgress;

/** Owns single-flight preparation, timeout, active selection and bounded eviction. */
final class TorrentPreparationCoordinator implements AutoCloseable {
	private static final String TAG = "StremioTorrent";
	private static final long PREPARE_TIMEOUT_MILLIS = 45_000L;
	private static final int MAX_ACTIVE_STREAMS = 2;
	private final Executor executor;
	private final BooleanSupplier closed;
	private final Consumer<PreparedTorrent> releaser;
	private final Observer observer;
	private final Runnable maintenance;
	private final Map<String, CompletableFuture<PreparedTorrent>> active =
			new LinkedHashMap<>(16, 0.75f, true);
	private final ScheduledExecutorService timeoutScheduler;
	private String activeKey;

	TorrentPreparationCoordinator(Executor executor, ScheduledExecutorService timeoutScheduler,
			BooleanSupplier closed,
			Consumer<PreparedTorrent> releaser, Observer observer, Runnable maintenance) {
		this.executor = executor;
		this.timeoutScheduler = java.util.Objects.requireNonNull(
				timeoutScheduler, "timeoutScheduler");
		this.closed = closed;
		this.releaser = releaser;
		this.observer = observer;
		this.maintenance = maintenance;
	}

	CompletableFuture<PreparedTorrent> prepare(String key,
			Consumer<RemotePlaybackProgress> progress, Preparation preparation) {
		if (closed.getAsBoolean()) return CompletableFuture.failedFuture(
				new IllegalStateException("Stremio torrent runtime is closed"));
		maintenance.run();
		TorrentProgressMapper.publish(progress, RemotePlaybackProgress.resolving());
		synchronized (active) {
			CompletableFuture<PreparedTorrent> existing = active.get(key);
			if (existing != null) {
				activate(key);
				observer.observe(existing, progress);
				return existing;
			}
			Cancellation cancellation = new Cancellation();
			CompletableFuture<PreparedTorrent> task = CompletableFuture.supplyAsync(() -> {
				try {
					return preparation.prepare(cancellation);
				} catch (Exception error) {
					if (error instanceof CancellationException) throw (CancellationException) error;
					TorrentProgressMapper.publish(progress, TorrentProgressMapper.failure(error));
					throw new CompletionException(error);
				}
			}, executor);
			CompletableFuture<PreparedTorrent> created = new CompletableFuture<>();
			ScheduledFuture<?> timeout = timeoutScheduler.schedule(() -> {
				if (created.completeExceptionally(new PlaybackFailureException(
						PlaybackFailureException.Reason.P2P_DATA_TIMEOUT))) {
					TorrentProgressMapper.publish(progress, RemotePlaybackProgress.failed(
							RemotePlaybackProgress.Failure.DATA_TIMEOUT));
					Log.w(TAG, "P2P preparation timed out");
					task.cancel(true);
				}
			}, PREPARE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			task.whenComplete((value, error) -> {
				timeout.cancel(false);
				if (error == null) {
					if (!created.complete(value)) releaser.accept(value);
					else maintenance.run();
				} else {
					created.completeExceptionally(error);
					maintenance.run();
				}
			});
			created.whenComplete((value, error) -> {
				if (error != null) {
					cancellation.cancel();
					if (!task.isDone()) task.cancel(true);
				}
			});
			active.put(key, created);
			activate(key);
			while (active.size() > MAX_ACTIVE_STREAMS) {
				var oldest = active.entrySet().iterator().next();
				active.remove(oldest.getKey());
				stop(oldest.getValue());
			}
			created.whenComplete((value, error) -> {
				if (error == null) return;
				synchronized (active) {
					active.remove(key, created);
					if (key.equals(activeKey)) activeKey = null;
				}
			});
			return created;
		}
	}

	private void activate(String selectedKey) {
		activeKey = selectedKey;
		List<CompletableFuture<PreparedTorrent>> stalePreparations = new ArrayList<>();
		for (Map.Entry<String, CompletableFuture<PreparedTorrent>> entry : active.entrySet()) {
			CompletableFuture<PreparedTorrent> future = entry.getValue();
			if (entry.getKey().equals(selectedKey)) {
				future.thenAccept(prepared -> {
					TorrentHandle handle = prepared.handle();
					boolean selected;
					synchronized (active) {
						selected = selectedKey.equals(activeKey);
					}
					if (!selected) {
						if (handle.isValid()) handle.pause();
					} else if (!closed.getAsBoolean() && handle.isValid()) {
						handle.queuePositionTop();
						handle.resume();
					}
				});
			} else if (!future.isDone()) {
				stalePreparations.add(future);
			} else if (!future.isCompletedExceptionally() && !future.isCancelled()) {
				PreparedTorrent prepared = future.getNow(null);
				if ((prepared != null) && prepared.handle().isValid()) prepared.handle().pause();
			}
		}
		for (CompletableFuture<PreparedTorrent> stale : stalePreparations) stale.cancel(true);
	}

	void release(PreparedTorrent prepared) {
		if (prepared == null) return;
		synchronized (active) {
			CompletableFuture<PreparedTorrent> owner = active.get(prepared.key());
			if ((owner != null) && owner.isDone() && !owner.isCompletedExceptionally() &&
					!owner.isCancelled() && owner.getNow(null) == prepared) {
				active.remove(prepared.key(), owner);
				if (prepared.key().equals(activeKey)) activeKey = null;
			}
		}
		releaser.accept(prepared);
		maintenance.run();
	}

	private void stop(CompletableFuture<PreparedTorrent> future) {
		if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
			PreparedTorrent prepared = future.getNow(null);
			if (prepared != null) releaser.accept(prepared);
		}
		future.cancel(true);
	}

	@Override
	public void close() {
		synchronized (active) {
			for (CompletableFuture<PreparedTorrent> future : active.values()) stop(future);
			active.clear();
			activeKey = null;
		}
	}

	@FunctionalInterface
	interface Preparation {
		PreparedTorrent prepare(Cancellation cancellation) throws Exception;
	}

	@FunctionalInterface
	interface Observer {
		void observe(CompletableFuture<PreparedTorrent> future,
				Consumer<RemotePlaybackProgress> progress);
	}

	static final class Cancellation {
		private final AtomicBoolean cancelled = new AtomicBoolean();

		void cancel() {
			cancelled.set(true);
		}

		void throwIfCancelled() {
			if (cancelled.get() || Thread.currentThread().isInterrupted()) {
				throw new CancellationException("Torrent preparation cancelled");
			}
		}
	}
}
