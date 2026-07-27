package me.aap.fermata.addon.external;

import me.aap.fermata.addon.AddonCapability;

/** External targets that can be handed to an addon-owned playback surface. */
public enum ExternalPlaybackTargetKind {
	YOUTUBE_ID(AddonCapability.YOUTUBE),
	EXTERNAL_HTTP(AddonCapability.WEB);

	private final AddonCapability capability;

	ExternalPlaybackTargetKind(AddonCapability capability) {
		this.capability = capability;
	}

	public AddonCapability getCapability() {
		return capability;
	}
}
