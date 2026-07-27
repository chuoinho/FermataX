package me.aap.fermata.addon.web.yt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies deterministic age, identity and count limits before YouTube items are persisted. */
final class YoutubeRetentionPolicy {
	private static final long FUTURE_TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;
	private final int maxItems;
	private final long maxAgeMillis;

	YoutubeRetentionPolicy(int maxItems, long maxAgeMillis) {
		if (maxItems < 0) throw new IllegalArgumentException("Maximum item count cannot be negative");
		if (maxAgeMillis < 0L) throw new IllegalArgumentException("Maximum age cannot be negative");
		this.maxItems = maxItems;
		this.maxAgeMillis = maxAgeMillis;
	}

	List<YoutubeItem> retain(List<YoutubeItem> items, long nowMillis) {
		if (nowMillis < 0L) throw new IllegalArgumentException("Current time cannot be negative");
		if ((maxItems == 0) || items.isEmpty()) return List.of();

		long oldest = (maxAgeMillis > nowMillis) ? 0L : nowMillis - maxAgeMillis;
		List<YoutubeItem> sorted = new ArrayList<>(items.size());
		for (YoutubeItem item : items) {
			if ((item == null) || (item.lastPlayedAtMillis() < oldest)) continue;
			long latestAllowed = (Long.MAX_VALUE - FUTURE_TIMESTAMP_TOLERANCE_MS < nowMillis) ?
					Long.MAX_VALUE : nowMillis + FUTURE_TIMESTAMP_TOLERANCE_MS;
			sorted.add((item.lastPlayedAtMillis() > latestAllowed) ?
					item.playedAt(nowMillis) : item);
		}
		sorted.sort(Comparator.comparingLong(YoutubeItem::lastPlayedAtMillis).reversed());

		List<YoutubeItem> retained = new ArrayList<>(Math.min(maxItems, sorted.size()));
		Set<String> videoIds = new HashSet<>();
		for (YoutubeItem item : sorted) {
			if (!videoIds.add(item.videoId())) continue;
			retained.add(item);
			if (retained.size() == maxItems) break;
		}
		return List.copyOf(retained);
	}
}
