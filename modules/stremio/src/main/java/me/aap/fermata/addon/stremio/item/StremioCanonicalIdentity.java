package me.aap.fermata.addon.stremio.item;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;

/** Normalizes cross-provider identities without retaining provider payloads or URLs. */
public record StremioCanonicalIdentity(String scope, String contentId) {
	private static final Pattern IMDB = Pattern.compile("(?i)^(?:imdb:)?(tt[0-9]{5,12})$");
	private static final Pattern TMDB = Pattern.compile(
			"(?i)^tmdb:(?:(movie|tv|series):)?([0-9]{1,12})$");

	public StremioCanonicalIdentity {
		Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(contentId, "contentId");
	}

	@Nullable
	public static StremioCanonicalIdentity from(String type, String providerContentId) {
		if ((type == null) || (providerContentId == null)) return null;
		String normalizedType = type.strip().toLowerCase(Locale.ROOT);
		String candidate = providerContentId.strip();
		Matcher imdb = IMDB.matcher(candidate);
		if (imdb.matches()) {
			return new StremioCanonicalIdentity("canonical:imdb",
					"imdb:" + imdb.group(1).toLowerCase(Locale.ROOT));
		}

		Matcher tmdb = TMDB.matcher(candidate);
		if (!tmdb.matches()) return null;
		String mediaType = tmdb.group(1);
		if (mediaType == null) {
			mediaType = "series".equals(normalizedType) ? "tv" : "movie";
		} else if ("series".equalsIgnoreCase(mediaType)) {
			mediaType = "tv";
		}
		return new StremioCanonicalIdentity("canonical:tmdb",
				"tmdb:" + mediaType.toLowerCase(Locale.ROOT) + ':' + tmdb.group(2));
	}

	public StremioPlaybackIdentity playbackIdentity(String type, String providerVideoId,
			int seasonNumber, int episodeNumber) {
		String videoIdentity = ((seasonNumber >= 0) && (episodeNumber >= 0)) ?
				contentId + ":s" + seasonNumber + ":e" + episodeNumber : contentId;
		return StremioPlaybackIdentity.canonical(type, contentId, videoIdentity);
	}

	public String durableProviderId() {
		return contentId.startsWith("imdb:") ? contentId.substring(5) : contentId;
	}
}
