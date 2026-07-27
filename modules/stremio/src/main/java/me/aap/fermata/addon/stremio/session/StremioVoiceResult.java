package me.aap.fermata.addon.stremio.session;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Stable one-to-three result set for a single voice generation. */
public record StremioVoiceResult(
		long generation,
		Locale locale,
		List<StremioVoiceCandidate> choices) {

	public StremioVoiceResult {
		if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
		locale = Objects.requireNonNull(locale, "locale");
		choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
		if (choices.size() > 3) throw new IllegalArgumentException("at most three choices are allowed");
	}

	public StremioVoiceCandidate choice(int oneBasedIndex) {
		if ((oneBasedIndex < 1) || (oneBasedIndex > choices.size())) return null;
		return choices.get(oneBasedIndex - 1);
	}

	@Override
	public String toString() {
		return "StremioVoiceResult{generation=" + generation + ", locale=" +
				locale.toLanguageTag() + ", choices=" + choices.size() + '}';
	}
}
