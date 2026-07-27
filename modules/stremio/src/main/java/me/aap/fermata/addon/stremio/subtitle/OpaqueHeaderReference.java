package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identifies encrypted request headers without retaining their values in subtitle state. */
public record OpaqueHeaderReference(String value) {
	private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	public OpaqueHeaderReference {
		Objects.requireNonNull(value, "value");
		if (!SAFE_REFERENCE.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid opaque header reference");
		}
	}

	@Override
	public String toString() {
		return "OpaqueHeaderReference[<redacted>]";
	}
}
