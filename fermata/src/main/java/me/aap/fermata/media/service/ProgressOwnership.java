package me.aap.fermata.media.service;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** Live playback-ownership capability used by progress orchestration. */
interface ProgressOwnership {
	LastPlayedLease captureLastPlayed(PlayableItem item);

	boolean isStillLastPlayedOwner(LastPlayedLease lease);

	boolean isCurrentEngineSource(PlayableItem item);

	record LastPlayedLease(PlayableItem item, long playbackRequestRevision) {}
}
