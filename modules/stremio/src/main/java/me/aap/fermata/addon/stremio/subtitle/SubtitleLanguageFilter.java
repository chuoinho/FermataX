package me.aap.fermata.addon.stremio.subtitle;

import java.util.List;
import java.util.Objects;

/** Applies the user's explicit Stremio subtitle language before tracks reach the UI/player. */
public final class SubtitleLanguageFilter {
	private SubtitleLanguageFilter() {
	}

	public static SubtitleAggregationResult apply(SubtitleAggregationResult result,
			List<String> languageTags) {
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(languageTags, "languageTags");
		List<SubtitleLanguage> languages = languageTags.stream()
				.filter(tag -> tag != null && !tag.isBlank())
				.map(SubtitleLanguageNormalizer::normalize)
				.filter(language -> !language.isUnknown())
				.distinct().toList();
		if (languages.isEmpty()) return result;
		List<SubtitleDescriptor> filtered = result.subtitles().stream()
				.filter(descriptor -> matches(descriptor.language(), languages)).toList();
		return new SubtitleAggregationResult(filtered, result.failures(), result.truncated());
	}

	private static boolean matches(SubtitleLanguage candidate,
			List<SubtitleLanguage> languages) {
		if (candidate.isUnknown()) return false;
		for (SubtitleLanguage language : languages) {
			if (candidate.tag().equals(language.tag()) ||
					candidate.baseLanguage().equals(language.baseLanguage())) return true;
		}
		return false;
	}
}
