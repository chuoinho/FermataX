package me.aap.fermata.addon.stremio.torrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Interruptible alert wait primitive; timeout checks remain bounded without active polling. */
final class TorrentWaiter implements AutoCloseable {
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition changed = lock.newCondition();
	private final AutoCloseable subscription;
	private long generation;
	private long consumedGeneration;
	private boolean closed;

	TorrentWaiter(TorrentAlertRouter router, String infoHash) {
		subscription = router.observe(infoHash, ignored -> signal());
	}

	void awaitSignal(long timeoutMillis) throws InterruptedException {
		if (timeoutMillis <= 0L) return;
		lock.lockInterruptibly();
		try {
			if (closed) return;
			if (consumedGeneration != generation) {
				consumedGeneration = generation;
				return;
			}
			long observed = generation;
			long nanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
			while (!closed && (observed == generation) && (nanos > 0L)) {
				nanos = changed.awaitNanos(nanos);
			}
			consumedGeneration = generation;
		} finally {
			lock.unlock();
		}
	}

	void signal() {
		lock.lock();
		try {
			generation++;
			changed.signalAll();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() {
		lock.lock();
		try {
			if (closed) return;
			closed = true;
			changed.signalAll();
		} finally {
			lock.unlock();
		}
		try {
			subscription.close();
		} catch (Exception ignored) {
		}
	}
}
