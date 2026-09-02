package me.aap.fermata.ui.smarttop;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Immutable description of pixels rendered behind SmartTopCard foreground content. */
public record SmartTopBackground(
		Kind kind,
		@Nullable Uri artworkUri,
		String identity) {
	public SmartTopBackground {
		Objects.requireNonNull(kind, "kind");
		identity = Objects.requireNonNull(identity, "identity");
		if (identity.isBlank()) throw new IllegalArgumentException("Background identity is blank");
		if ((kind == Kind.ARTWORK) != (artworkUri != null)) {
			throw new IllegalArgumentException("Only artwork backgrounds carry a URI");
		}
	}

	public static SmartTopBackground artwork(Uri uri, String itemIdentity) {
		Objects.requireNonNull(uri, "uri");
		return new SmartTopBackground(Kind.ARTWORK, uri,
				"art:" + token(itemIdentity) + ':' + token(uri.toString()));
	}

	public static SmartTopBackground audioSpectrum(String sourceIdentity) {
		return new SmartTopBackground(Kind.AUDIO_SPECTRUM, null,
				"audio:" + token(sourceIdentity));
	}

	public static SmartTopBackground sourceFallback(String sourceIdentity) {
		return new SmartTopBackground(Kind.SOURCE_FALLBACK, null,
				"source:" + token(sourceIdentity));
	}

	public static SmartTopBackground empty() {
		return new SmartTopBackground(Kind.EMPTY, null, "empty");
	}

	private static String token(String value) {
		return Integer.toHexString(Objects.requireNonNull(value, "identity source").hashCode());
	}

	public enum Kind {
		ARTWORK,
		AUDIO_SPECTRUM,
		SOURCE_FALLBACK,
		EMPTY
	}
}
