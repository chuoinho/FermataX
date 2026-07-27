package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

/** Preserves the provider value while exposing a normalized value when it is unambiguous. */
public record StremioDuration(String text, long milliseconds) {
	public static final long UNKNOWN = -1L;

	public StremioDuration {
		Objects.requireNonNull(text, "text");
		if (text.isBlank()) throw new IllegalArgumentException("text cannot be blank");
		if (milliseconds < UNKNOWN) throw new IllegalArgumentException("Invalid milliseconds");
	}

	@Override
	public String toString() {
		return "StremioDuration[text=<redacted>, milliseconds=" + milliseconds + "]";
	}
}
