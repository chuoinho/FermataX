package me.aap.fermata.media.service;

/**
 * Keeps service startup side effects explicit and exactly-once across build variants.
 */
public final class MediaServiceRuntimeGate {
	private boolean addonsAttached;
	private boolean lastItemPrepared;
	private boolean playbackLifetimeStarted;

	public static boolean allowsAutomaticPrepare(boolean autoBuild) {
		return !autoBuild;
	}

	public boolean takeAutomaticPrepare(boolean autoBuild) {
		if (!allowsAutomaticPrepare(autoBuild) || lastItemPrepared) return false;
		lastItemPrepared = true;
		return true;
	}

	public boolean takeAddonAttachOnCreate(boolean autoBuild) {
		return !autoBuild && takeAddonAttach();
	}

	public boolean takeAddonAttachOnInternalBind() {
		return takeAddonAttach();
	}

	public boolean takeAddonDetachOnDestroy() {
		if (!addonsAttached) return false;
		addonsAttached = false;
		return true;
	}

	public boolean takePlaybackLifetimeStart() {
		if (playbackLifetimeStarted) return false;
		playbackLifetimeStarted = true;
		return true;
	}

	public boolean takePlaybackLifetimeStop() {
		if (!playbackLifetimeStarted) return false;
		playbackLifetimeStarted = false;
		return true;
	}

	public void playbackLifetimeStartFailed() {
		playbackLifetimeStarted = false;
	}

	private boolean takeAddonAttach() {
		if (addonsAttached) return false;
		addonsAttached = true;
		return true;
	}
}
