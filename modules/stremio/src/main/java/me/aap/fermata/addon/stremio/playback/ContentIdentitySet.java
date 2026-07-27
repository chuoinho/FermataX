package me.aap.fermata.addon.stremio.playback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.addon.stremio.protocol.CapabilityMatcher;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

/** Canonical and source-scoped IDs that may safely participate in provider request planning. */
public final class ContentIdentitySet {
	private static final Pattern IMDB = Pattern.compile("(?i)^(?:imdb:)?(tt[0-9]{5,12})$");
	private static final Pattern TMDB = Pattern.compile(
			"(?i)^tmdb:(?:(movie|tv|series):)?([0-9]{1,12})$");

	private final String type;
	private final List<String> canonicalVideoIds;
	private final Map<String, List<String>> providerVideoIds;

	private ContentIdentitySet(String type, List<String> canonicalVideoIds,
			Map<String, List<String>> providerVideoIds) {
		this.type = requireText(type, "type").toLowerCase(Locale.ROOT);
		this.canonicalVideoIds = safeIds(canonicalVideoIds, "canonicalVideoIds");
		var scoped = new LinkedHashMap<String, List<String>>();
		for (var entry : Objects.requireNonNull(providerVideoIds, "providerVideoIds").entrySet()) {
			scoped.put(requireText(entry.getKey(), "sourceUuid"),
					safeIds(entry.getValue(), "providerVideoIds"));
		}
		this.providerVideoIds = Map.copyOf(scoped);
	}

	public static ContentIdentitySet from(String sourceUuid, String type, String contentId,
			String videoId, int season, int episode) {
		String normalizedType = requireText(type, "type").toLowerCase(Locale.ROOT);
		String source = requireText(sourceUuid, "sourceUuid");
		String content = requireText(contentId, "contentId");
		String video = requireText(videoId, "videoId");
		LinkedHashSet<String> canonical = new LinkedHashSet<>();

		addCanonicalVideoId(canonical, normalizedType, video, season, episode);
		addCanonicalVideoId(canonical, normalizedType, content, season, episode);
		List<String> scoped = video.equals(content) ? List.of(video) : List.of(video, content);
		return new ContentIdentitySet(normalizedType, List.copyOf(canonical),
				Map.of(source, scoped));
	}

	/** Compatibility path for callers that predate source-scoped identity planning. */
	public static ContentIdentitySet legacy(String type, String videoId) {
		return new ContentIdentitySet(type, List.of(requireText(videoId, "videoId")), Map.of());
	}

	public String type() {
		return type;
	}

	public List<String> canonicalVideoIds() {
		return canonicalVideoIds;
	}

	public List<String> candidateIds(String providerSourceUuid) {
		LinkedHashSet<String> candidates = new LinkedHashSet<>();
		List<String> local = providerVideoIds.get(providerSourceUuid);
		if (local != null) candidates.addAll(local);
		candidates.addAll(canonicalVideoIds);
		return List.copyOf(candidates);
	}

	public Optional<String> select(StremioManifest manifest, String providerSourceUuid,
			String resource) {
		Objects.requireNonNull(manifest, "manifest");
		String name = requireText(resource, "resource");
		for (String id : candidateIds(providerSourceUuid)) {
			if (CapabilityMatcher.supports(manifest, name, type, id)) return Optional.of(id);
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "ContentIdentitySet[type=" + type + ", canonical=" +
				canonicalVideoIds.size() + ", providers=" + providerVideoIds.size() + ']';
	}

	private static void addCanonicalVideoId(LinkedHashSet<String> output, String type,
			String value, int season, int episode) {
		Matcher imdb = IMDB.matcher(value.trim());
		if (imdb.matches()) {
			String id = imdb.group(1).toLowerCase(Locale.ROOT);
			output.add(isEpisode(type, season, episode) ?
					id + ':' + season + ':' + episode : id);
			return;
		}
		Matcher tmdb = TMDB.matcher(value.trim());
		if (!tmdb.matches()) return;
		String mediaType = tmdb.group(1);
		if (mediaType == null) mediaType = "series".equals(type) ? "tv" : "movie";
		else if (mediaType.equalsIgnoreCase("series")) mediaType = "tv";
		String id = "tmdb:" + mediaType.toLowerCase(Locale.ROOT) + ':' + tmdb.group(2);
		output.add(isEpisode(type, season, episode) ?
				id + ':' + season + ':' + episode : id);
	}

	private static boolean isEpisode(String type, int season, int episode) {
		return "series".equals(type) && season >= 0 && episode >= 0;
	}

	private static List<String> safeIds(List<String> values, String field) {
		var copy = new ArrayList<String>();
		for (String value : Objects.requireNonNull(values, field)) {
			String id = requireText(value, field);
			if (id.contains("://") || id.indexOf('\u0000') >= 0) {
				throw new IllegalArgumentException(field + " contains an unsafe ID");
			}
			if (!copy.contains(id)) copy.add(id);
		}
		return List.copyOf(copy);
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		String text = value.trim();
		if (text.isEmpty()) throw new IllegalArgumentException(field + " is blank");
		return text;
	}
}
