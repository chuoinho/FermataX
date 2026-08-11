package me.aap.fermata.media.service;

import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

/** One ownership-guarded timeline source shared by Playerbar and SmartTop. */
public record PlaybackTimelineSnapshot(
		PlayableItem item,
		long presentationGeneration,
		PlaybackTimelinePolicy.Mode mode,
		long positionMillis,
		long durationMillis,
		boolean playing) {
	public PlaybackTimelineSnapshot {
		Objects.requireNonNull(item, "item");
		Objects.requireNonNull(mode, "mode");
		if ((presentationGeneration <= 0L) || (positionMillis < 0L) || (durationMillis < 0L)) {
			throw new IllegalArgumentException("Invalid playback timeline values");
		}
	}
}
