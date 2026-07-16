package me.aap.fermata.addon.podcast.refresh;

import static me.aap.utils.async.Completed.completed;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.addon.podcast.data.PodcastRepository;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.utils.async.FutureSupplier;

public final class PodcastRefreshCoordinator implements Closeable {
	private static final long AUTO_TTL_MS = 30 * 60 * 1000L;
	private final PodcastRepository repository;
	private final Map<String, FutureSupplier<PodcastSubscription>> inFlight = new HashMap<>();
	private boolean closed;

	public PodcastRefreshCoordinator(PodcastRepository repository) {
		this.repository = repository;
	}

	public FutureSupplier<PodcastSubscription> auto(PodcastSubscription subscription) {
		long now = System.currentTimeMillis();
		if ((subscription.getNextRefreshMs() > now) || ((subscription.getFailureCount() == 0) &&
				((now - subscription.getLastCheckedMs()) < AUTO_TTL_MS))) {
			return completed(subscription);
		}
		return refresh(subscription.getFeedKey());
	}

	public FutureSupplier<PodcastSubscription> manual(String feedKey) {
		return refresh(feedKey);
	}

	private synchronized FutureSupplier<PodcastSubscription> refresh(String feedKey) {
		if (closed) return me.aap.utils.async.Completed.failed(
				new IllegalStateException("Podcast refresh coordinator is closed"));
		FutureSupplier<PodcastSubscription> existing = inFlight.get(feedKey);
		if (existing != null) return existing.fork();
		FutureSupplier<PodcastSubscription> refresh = repository.refresh(feedKey);
		inFlight.put(feedKey, refresh);
		refresh.onCompletion((result, error) -> requestCompleted(feedKey, refresh));
		return refresh.fork();
	}

	private synchronized void requestCompleted(String feedKey,
			FutureSupplier<PodcastSubscription> refresh) {
		inFlight.remove(feedKey, refresh);
	}

	@Override
	public synchronized void close() {
		closed = true;
		for (FutureSupplier<?> refresh : inFlight.values()) refresh.cancel();
		inFlight.clear();
	}
}
