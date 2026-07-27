package me.aap.fermata.addon.stremio.playback;

/** One stream classification policy shared by native and compatibility surfaces. */
public final class StremioStreamEligibilityPolicy {
	private StremioStreamEligibilityPolicy() {
	}

	public static Kind classify(PlaybackDescriptor descriptor) {
		return switch (descriptor.targetKind()) {
			case DIRECT_HTTP, HLS, DASH ->
					(descriptor.targetValue() == null) ? Kind.UNSUPPORTED : Kind.DIRECT;
			case TORRENT -> (descriptor.sourceTarget() instanceof
					me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget) ?
					Kind.TORRENT : Kind.UNSUPPORTED;
			default -> Kind.UNSUPPORTED;
		};
	}

	public enum Kind {
		DIRECT,
		TORRENT,
		UNSUPPORTED
	}
}
