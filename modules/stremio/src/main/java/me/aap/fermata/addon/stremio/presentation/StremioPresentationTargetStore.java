package me.aap.fermata.addon.stremio.presentation;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioPlaybackSelection;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;

/** Owns all bounded, typed targets referenced by presentation stable keys. */
final class StremioPresentationTargetStore implements AutoCloseable {
	private static final int MAX_ENTRIES = 512;

	private final StremioPresentationRegistry<BrowseMedia> media =
			new StremioPresentationRegistry<>(MAX_ENTRIES);
	private final StremioPresentationRegistry<EpisodeTarget> episodes =
			new StremioPresentationRegistry<>(MAX_ENTRIES);
	private final StremioPresentationRegistry<StremioPlaybackSelection> playback =
			new StremioPresentationRegistry<>(MAX_ENTRIES);
	private final StremioPresentationRegistry<ResumeState> resume =
			new StremioPresentationRegistry<>(MAX_ENTRIES);
	private final StremioPresentationRegistry<StremioPresentationGateway.FavoriteTarget> favorites =
			new StremioPresentationRegistry<>(MAX_ENTRIES);
	private final StremioPresentationRegistry<StremioPresentationGateway.SubtitleTarget> subtitles =
			new StremioPresentationRegistry<>(MAX_ENTRIES);

	String rememberMedia(BrowseMedia value) {
		String key = StremioItemIds.meta(value);
		media.putIfAbsent(key, value);
		return key;
	}

	BrowseMedia media(String key) {
		return media.get(key);
	}

	void putMedia(String key, BrowseMedia value) {
		media.put(key, value);
	}

	String rememberEpisode(BrowseMedia media, BrowseEpisode episode, BrowseSeason season) {
		String key = StremioItemIds.episode(episode);
		episodes.put(key, new EpisodeTarget(media, episode, season));
		return key;
	}

	EpisodeTarget episode(String key) {
		return episodes.get(key);
	}

	void putEpisode(String key, BrowseMedia media, BrowseEpisode episode, BrowseSeason season) {
		episodes.put(key, new EpisodeTarget(media, episode, season));
	}

	void putPlayback(String key, StremioPlaybackSelection value) {
		playback.put(key, value);
	}

	StremioPlaybackSelection playback(String key) {
		return playback.get(key);
	}

	void rememberResume(String stableId, long positionMs, long durationMs) {
		if ((stableId == null) || stableId.isBlank() || (positionMs <= 0L)) return;
		resume.put(stableId, new ResumeState(positionMs, Math.max(durationMs, -1L)));
	}

	void forgetResume(String stableId) {
		if (stableId != null) resume.remove(stableId);
	}

	void transferResume(String persistentId, String routeId) {
		if ((persistentId == null) || (routeId == null) || persistentId.equals(routeId)) return;
		ResumeState state = resume.get(persistentId);
		if (state != null) resume.put(routeId, state);
	}

	long resumePosition(String stableId) {
		ResumeState state = resume.get(stableId);
		return (state == null) ? 0L : state.positionMs();
	}

	float resumeProgress(String stableId, StremioDuration fallbackDuration) {
		ResumeState state = resume.get(stableId);
		if (state == null) return 0f;
		long duration = (state.durationMs() > 0L) ? state.durationMs() :
				((fallbackDuration == null) ? -1L : fallbackDuration.milliseconds());
		if ((duration <= 0L) || (state.positionMs() >= duration)) return 0f;
		return Math.max(0f, Math.min(1f, (float) state.positionMs() / (float) duration));
	}

	void putFavorite(String key, StremioPresentationGateway.FavoriteTarget value) {
		favorites.put(key, value);
	}

	StremioPresentationGateway.FavoriteTarget favorite(String key) {
		return favorites.get(key);
	}

	void removeFavorite(String key) {
		favorites.remove(key);
	}

	void putSubtitle(String key, StremioPresentationGateway.SubtitleTarget value) {
		subtitles.put(key, value);
	}

	StremioPresentationGateway.SubtitleTarget subtitle(String key) {
		return subtitles.get(key);
	}

	void removeSubtitle(String key) {
		subtitles.remove(key);
	}

	@Override
	public void close() {
		media.clear();
		episodes.clear();
		playback.clear();
		resume.clear();
		favorites.clear();
		subtitles.clear();
	}

	record EpisodeTarget(BrowseMedia media, BrowseEpisode episode, BrowseSeason season) {
	}

	private record ResumeState(long positionMs, long durationMs) {
	}
}
