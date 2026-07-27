package me.aap.fermata.addon.stremio.subtitle;

import java.util.List;
import java.util.Objects;

public record SubtitleAggregationResult(
		List<SubtitleDescriptor> subtitles,
		List<SubtitleProviderFailure> failures,
		boolean truncated,
		boolean complete) {

	public SubtitleAggregationResult(List<SubtitleDescriptor> subtitles,
			List<SubtitleProviderFailure> failures, boolean truncated) {
		this(subtitles, failures, truncated, true);
	}

	public SubtitleAggregationResult {
		subtitles = List.copyOf(Objects.requireNonNull(subtitles, "subtitles"));
		failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
	}
}
