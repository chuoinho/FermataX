package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.app.App;

/** Owns one current Stremio attempt and rejects all callbacks from replaced attempts. */
public final class PlaybackAttemptSupervisor {
	public static final long DEFAULT_FIRST_FRAME_TIMEOUT_MILLIS = 15_000L;
	public static final long P2P_FIRST_FRAME_TIMEOUT_MILLIS = 45_000L;
	private static final AtomicLong IDS = new AtomicLong();

	public interface DeadlineScheduler {
		Cancellable schedule(Runnable task, long delayMillis);
	}

	public interface Cancellable {
		void cancel();
	}

	private final PlaybackAttemptObserver observer;
	private final DeadlineScheduler scheduler;
	private final long directFirstFrameTimeoutMillis;
	private final long p2pFirstFrameTimeoutMillis;
	private PlaybackAttempt current;
	private Cancellable firstFrameDeadline;
	private Consumer<Throwable> failureHandler;
	private long staleCallbacks;

	public PlaybackAttemptSupervisor() {
		this(PlaybackAttemptObserver.NONE, (task, delay) -> {
			ScheduledFuture<?> future = App.get().getScheduler().schedule(
					task, delay, TimeUnit.MILLISECONDS);
			return () -> future.cancel(false);
		}, DEFAULT_FIRST_FRAME_TIMEOUT_MILLIS, P2P_FIRST_FRAME_TIMEOUT_MILLIS);
	}

	PlaybackAttemptSupervisor(PlaybackAttemptObserver observer, DeadlineScheduler scheduler,
			long firstFrameTimeoutMillis) {
		this(observer, scheduler, firstFrameTimeoutMillis, firstFrameTimeoutMillis);
	}

	PlaybackAttemptSupervisor(PlaybackAttemptObserver observer, DeadlineScheduler scheduler,
			long directFirstFrameTimeoutMillis, long p2pFirstFrameTimeoutMillis) {
		this.observer = Objects.requireNonNull(observer, "observer");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		if ((directFirstFrameTimeoutMillis <= 0L) || (p2pFirstFrameTimeoutMillis <= 0L)) {
			throw new IllegalArgumentException("first-frame timeouts must be positive");
		}
		this.directFirstFrameTimeoutMillis = directFirstFrameTimeoutMillis;
		this.p2pFirstFrameTimeoutMillis = p2pFirstFrameTimeoutMillis;
	}

	public synchronized long begin(PlaybackDescriptor descriptor, long requestRevision,
			Consumer<Throwable> failureHandler) {
		cancelCurrent();
		current = new PlaybackAttempt(IDS.incrementAndGet(), requestRevision, descriptor);
		this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
		return current.operationId();
	}

	public synchronized long currentOperationId() {
		return (current == null) ? -1L : current.operationId();
	}

	public synchronized PlaybackAttempt current() {
		return current;
	}

	public synchronized long staleCallbackCount() {
		return staleCallbacks;
	}

	public synchronized void preparationStarted(long operationId) {
		PlaybackAttempt attempt = requireCurrent(operationId, PlaybackAttemptState.PREPARING);
		if (attempt == null) return;
		transition(attempt, PlaybackAttemptState.RESOLVING);
		transition(attempt, PlaybackAttemptState.PREPARING);
	}

	public synchronized void dataReady(long operationId, RemotePlaybackRequest request) {
		PlaybackAttempt attempt = requireCurrent(operationId, PlaybackAttemptState.DATA_READY);
		if (attempt == null) {
			if (request != null) request.close();
			return;
		}
		attempt.request(Objects.requireNonNull(request, "request"));
		if (attempt.state() == PlaybackAttemptState.PREPARING) {
			transition(attempt, PlaybackAttemptState.DATA_READY);
		}
	}

	public synchronized void playerReady(long requestRevision) {
		PlaybackAttempt attempt = requireRevision(requestRevision, PlaybackAttemptState.PLAYER_READY);
		if (attempt == null) return;
		if (attempt.state() == PlaybackAttemptState.DATA_READY) {
			transition(attempt, PlaybackAttemptState.PLAYER_READY);
		}
		if ((attempt.state() == PlaybackAttemptState.PLAYER_READY) &&
				attempt.hasFirstFrameSignal()) {
			transition(attempt, PlaybackAttemptState.FIRST_FRAME);
			if (attempt.hasStartedSignal()) transition(attempt, PlaybackAttemptState.PLAYING);
		} else if (attempt.state() == PlaybackAttemptState.PLAYER_READY) {
			scheduleFirstFrame(attempt);
		}
	}

	public synchronized void firstFrame(long requestRevision) {
		PlaybackAttempt attempt = requireRevision(requestRevision, PlaybackAttemptState.FIRST_FRAME);
		if (attempt == null) return;
		attempt.firstFrameSignal();
		cancelFirstFrameDeadline();
		if (attempt.state() == PlaybackAttemptState.PLAYER_READY) {
			transition(attempt, PlaybackAttemptState.FIRST_FRAME);
		}
		if ((attempt.state() == PlaybackAttemptState.FIRST_FRAME) && attempt.hasStartedSignal()) {
			transition(attempt, PlaybackAttemptState.PLAYING);
		}
	}

	public synchronized void started(long requestRevision) {
		PlaybackAttempt attempt = requireRevision(requestRevision, PlaybackAttemptState.PLAYING);
		if (attempt == null) return;
		attempt.startedSignal();
		if (attempt.state() == PlaybackAttemptState.PLAYER_READY) scheduleFirstFrame(attempt);
		if (attempt.state() == PlaybackAttemptState.FIRST_FRAME) {
			transition(attempt, PlaybackAttemptState.PLAYING);
		}
	}

	public synchronized void paused(long requestRevision) {
		if (matchesRevision(requestRevision)) cancelFirstFrameDeadline();
	}

	public synchronized void ended(long requestRevision) {
		PlaybackAttempt attempt = requireRevision(requestRevision, PlaybackAttemptState.ENDED);
		if (attempt == null) return;
		cancelFirstFrameDeadline();
		if (attempt.state() == PlaybackAttemptState.PLAYING) {
			transition(attempt, PlaybackAttemptState.ENDED);
			attempt.closeRequest();
		}
	}

	public synchronized boolean claimDecoderFallback(long requestRevision) {
		if (!matchesRevision(requestRevision) || current.state().isTerminal()) return false;
		cancelFirstFrameDeadline();
		PlaybackAttempt previous = current;
		if (!previous.claimDecoderFallback()) return false;
		previous.closeRequest();
		transition(previous, PlaybackAttemptState.CANCELLED);
		current = new PlaybackAttempt(IDS.incrementAndGet(), requestRevision,
				previous.descriptor());
		current.inheritDecoderFallback();
		return true;
	}

	public synchronized void failed(long requestRevision, Throwable error) {
		PlaybackAttempt attempt = requireRevision(requestRevision, PlaybackAttemptState.FAILED);
		if (attempt == null) return;
		fail(attempt, error);
	}

	public synchronized void failedCurrent(long operationId, Throwable error) {
		PlaybackAttempt attempt = requireCurrent(operationId, PlaybackAttemptState.FAILED);
		if (attempt != null) fail(attempt, error);
	}

	public synchronized void cancel(long requestRevision) {
		if (!matchesRevision(requestRevision)) return;
		cancelCurrent();
	}

	private void scheduleFirstFrame(PlaybackAttempt attempt) {
		cancelFirstFrameDeadline();
		long operationId = attempt.operationId();
		firstFrameDeadline = scheduler.schedule(
				() -> firstFrameTimedOut(operationId), firstFrameTimeout(attempt));
	}

	private void firstFrameTimedOut(long operationId) {
		Consumer<Throwable> handler;
		IllegalStateException failure = new IllegalStateException(
				"Player became ready but produced no video frame");
		synchronized (this) {
			PlaybackAttempt attempt = requireCurrent(operationId, PlaybackAttemptState.FAILED);
			if ((attempt == null) || (attempt.state() != PlaybackAttemptState.PLAYER_READY)) return;
			cancelFirstFrameDeadline();
			attempt.failure(failure);
			handler = failureHandler;
		}
		if (handler != null) handler.accept(failure);
	}

	private long firstFrameTimeout(PlaybackAttempt attempt) {
		return isP2p(attempt) ? p2pFirstFrameTimeoutMillis : directFirstFrameTimeoutMillis;
	}

	private static boolean isP2p(PlaybackAttempt attempt) {
		return attempt.descriptor().targetKind() == PlaybackDescriptor.TargetKind.TORRENT;
	}

	private void fail(PlaybackAttempt attempt, Throwable error) {
		cancelFirstFrameDeadline();
		attempt.failure(Objects.requireNonNull(error, "error"));
		transition(attempt, PlaybackAttemptState.FAILED);
		attempt.closeRequest();
	}

	private void cancelCurrent() {
		cancelFirstFrameDeadline();
		PlaybackAttempt attempt = current;
		if ((attempt != null) && !attempt.state().isTerminal()) {
			transition(attempt, PlaybackAttemptState.CANCELLED);
			attempt.closeRequest();
		}
		current = null;
		failureHandler = null;
	}

	private void cancelFirstFrameDeadline() {
		Cancellable deadline = firstFrameDeadline;
		firstFrameDeadline = null;
		if (deadline != null) deadline.cancel();
	}

	private PlaybackAttempt requireCurrent(long operationId, PlaybackAttemptState event) {
		PlaybackAttempt attempt = current;
		if ((attempt != null) && (attempt.operationId() == operationId) &&
				!attempt.state().isTerminal()) return attempt;
		staleCallbacks++;
		observer.onStaleCallback(operationId, event);
		return null;
	}

	private PlaybackAttempt requireRevision(long requestRevision, PlaybackAttemptState event) {
		PlaybackAttempt attempt = current;
		if ((attempt != null) && (attempt.requestRevision() == requestRevision) &&
				!attempt.state().isTerminal()) return attempt;
		staleCallbacks++;
		observer.onStaleCallback((attempt == null) ? -1L : attempt.operationId(), event);
		return null;
	}

	private boolean matchesRevision(long requestRevision) {
		return (current != null) && (current.requestRevision() == requestRevision);
	}

	private void transition(PlaybackAttempt attempt, PlaybackAttemptState next) {
		PlaybackAttemptState previous = attempt.state();
		if (attempt.transition(next)) observer.onStateChanged(attempt, previous);
	}
}
