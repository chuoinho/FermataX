package me.aap.fermata.addon.tv.stalker;

import java.net.URI;
import java.util.Map;

import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.async.FutureSupplier;

final class StalkerPlayback {
	private StalkerPlayback() {
	}

	static FutureSupplier<RemotePlaybackRequest> prepare(
			FutureSupplier<StalkerPlaybackLink> link, String diagnostic) {
		return link.map(value -> {
			PlaybackRequestProfile profile = profile(value.uri(), diagnostic);
			Map<String, String> headers = value.headers();
			return new RemotePlaybackRequest(value.uri(), profile, ignored -> headers);
		});
	}

	static PlaybackRequestProfile profile(URI target, String diagnostic) {
		return PlaybackRequestProfile.builder(target, diagnostic)
				.headerReference(PlaybackRequestProfile.HeaderReference.of("stalker-playback"))
				.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.SAME_ORIGIN)
				.build();
	}
}
