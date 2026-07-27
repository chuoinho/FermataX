package me.aap.fermata.addon.stremio.protocol.model;

public record ManifestBehaviorHints(
		boolean configurable,
		boolean configurationRequired,
		boolean adult,
		boolean p2p) {
	public static final ManifestBehaviorHints NONE =
			new ManifestBehaviorHints(false, false, false, false);
}
