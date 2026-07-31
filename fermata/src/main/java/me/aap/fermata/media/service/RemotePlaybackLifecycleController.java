package me.aap.fermata.media.service;

import androidx.annotation.Nullable;

import java.util.function.Consumer;
import java.util.function.BiConsumer;

import me.aap.fermata.media.net.RemotePlaybackLifecycleItem;

final class RemotePlaybackLifecycleController {
	private final Consumer<Runnable> mainPoster;
	@Nullable
	private Object owner;
	@Nullable
	private RemotePlaybackLifecycleItem lifecycle;
	private long revision = -1L;

	RemotePlaybackLifecycleController(Consumer<Runnable> mainPoster) {
		this.mainPoster = mainPoster;
	}

	void activate(Object owner, @Nullable RemotePlaybackLifecycleItem lifecycle, long revision,
			Consumer<Throwable> failureHandler) {
		if ((this.owner == owner) && (this.revision == revision)) return;
		cancel();
		if (lifecycle == null) return;
		this.owner = owner;
		this.lifecycle = lifecycle;
		this.revision = revision;
		lifecycle.onPlaybackAttemptActivated(revision, error -> mainPoster.accept(() -> {
			if ((this.owner != owner) || (this.lifecycle != lifecycle) ||
					(this.revision != revision)) return;
			failureHandler.accept(error);
		}));
	}

	void cancel() {
		RemotePlaybackLifecycleItem current = lifecycle;
		long currentRevision = revision;
		owner = null;
		lifecycle = null;
		revision = -1L;
		if (current != null) current.onPlaybackAttemptCancelled(currentRevision);
	}

	boolean allowsFallback() {
		RemotePlaybackLifecycleItem current = lifecycle;
		return (current == null) || current.onPlaybackAttemptFallback(revision);
	}

	void notifyActive(BiConsumer<RemotePlaybackLifecycleItem, Long> notification) {
		RemotePlaybackLifecycleItem current = lifecycle;
		if (current != null) notification.accept(current, revision);
	}
}
