package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** Immutable handoff from an observed YouTube player to the stable MediaSession owner. */
record YoutubePlaybackActivation(YoutubeMediaEngine origin, YoutubeItem descriptor,
		long generation, Reason reason) {
	YoutubePlaybackActivation {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(descriptor, "descriptor");
		Objects.requireNonNull(reason, "reason");
		if (generation <= 0L) throw new IllegalArgumentException("Invalid playback generation");
	}

	String videoId() {
		return descriptor.videoId();
	}

	static PlayableItem selectSource(String videoId, @Nullable PlayableItem current,
			@Nullable PlayableItem externalOwner, Supplier<? extends PlayableItem> canonical) {
		if (matchesVideo(current, videoId)) return current;
		if (matchesVideo(externalOwner, videoId)) return externalOwner;
		return canonical.get();
	}

	static boolean matchesVideo(@Nullable PlayableItem source, String videoId) {
		if ((source == null) || (videoId == null) || videoId.isBlank()) return false;
		if (source instanceof YoutubeDescriptorItem item) {
			YoutubeItem descriptor = item.getYoutubeDescriptor();
			return (descriptor != null) && videoId.equals(descriptor.videoId());
		}
		if (source instanceof ExternalPlaybackDelegateItem external) {
			PlayableItem delegate = external.getExternalPlaybackDelegate();
			return (delegate != source) && matchesVideo(delegate, videoId);
		}
		return false;
	}

	enum Reason {
		EXPLICIT_TARGET,
		WEB_SELECTION,
		AUTO_NEXT,
		RELOAD,
		HOST_HANDOFF
	}
}
