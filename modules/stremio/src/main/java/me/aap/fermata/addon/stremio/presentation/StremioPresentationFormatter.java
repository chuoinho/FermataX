package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;

/** Pure text and enum formatting shared by presentation route loaders. */
final class StremioPresentationFormatter {
	private static final Pattern STREAM_QUALITY = Pattern.compile(
			"(?i)(?<![a-z0-9])(2160p|1440p|1080p|720p|576p|480p|4k)(?![a-z0-9])");

	private final StremioPresentationText text;

	StremioPresentationFormatter(StremioPresentationText text) {
		this.text = Objects.requireNonNull(text, "text");
	}

	String metadata(BrowseMedia media) {
		List<String> values = new ArrayList<>(4);
		if ((media.releaseInfo() != null) && !media.releaseInfo().isBlank()) {
			values.add(media.releaseInfo());
		}
		if (media.duration() != null) values.add(media.duration().text());
		if ((media.imdbRating() != null) && !media.imdbRating().isBlank()) {
			values.add(text.label(StremioPresentationText.Label.RATING) + ' ' +
					media.imdbRating().trim());
		}
		if (!media.genres().isEmpty()) values.add(String.join(", ", media.genres()));
		return String.join(" | ", values);
	}

	static String episodeMetadata(BrowseEpisode episode) {
		List<String> values = new ArrayList<>(2);
		if ((episode.released() != null) && !episode.released().isBlank()) {
			values.add(episode.released());
		}
		StremioDuration duration = episode.duration();
		if (duration != null) values.add(duration.text());
		return String.join(" | ", values);
	}

	static String streamTitle(PlaybackDescriptor descriptor) {
		String title = descriptor.streamTitle();
		if ((title == null) || title.isBlank()) title = descriptor.streamName();
		if ((title == null) || title.isBlank()) title = descriptor.providerName();
		return title;
	}

	static String streamDetails(PlaybackDescriptor descriptor) {
		String format = switch (descriptor.targetKind()) {
			case HLS -> "HLS";
			case DASH -> "DASH";
			case DIRECT_HTTP -> "HTTP";
			case TORRENT -> "P2P";
			case EXTERNAL -> "External";
			case USENET -> "Usenet";
			case ARCHIVE -> "Archive";
			default -> "";
		};
		String quality = streamQuality(descriptor.streamTitle(), descriptor.streamName());
		return quality.isEmpty() ? format : format + " | " + quality;
	}

	static String streamQuality(String... values) {
		for (String value : values) {
			if ((value == null) || value.isBlank()) continue;
			Matcher matcher = STREAM_QUALITY.matcher(value);
			if (!matcher.find()) continue;
			String quality = matcher.group(1);
			return quality.equalsIgnoreCase("4k") ? "2160p" :
					quality.toLowerCase(java.util.Locale.ROOT);
		}
		return "";
	}

	static StremioUiModel.ProviderState providerState(
			StreamAggregationResult.ProviderStatus status) {
		return switch (status) {
			case SUCCESS -> StremioUiModel.ProviderState.READY;
			case PENDING -> StremioUiModel.ProviderState.LOADING;
			case TIMED_OUT -> StremioUiModel.ProviderState.TIMED_OUT;
			case FAILED -> StremioUiModel.ProviderState.FAILED;
		};
	}

	String typeLabel(String type) {
		return switch (type) {
			case "movie" -> text.label(StremioPresentationText.Label.MOVIES);
			case "series" -> text.label(StremioPresentationText.Label.SERIES);
			default -> type;
		};
	}

	static String catalogFilterLabel(CatalogDescriptor catalog,
			List<CatalogDescriptor> catalogs) {
		long sameName = catalogs.stream().filter(other ->
				other.route().type().equals(catalog.route().type()) &&
						other.name().equals(catalog.name())).count();
		return (sameName > 1) ? catalog.name() + " - " + catalog.providerName() : catalog.name();
	}
}
