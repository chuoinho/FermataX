package me.aap.fermata.ui.smarttop;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Immutable description of pixels rendered behind SmartTopCard foreground content. */
public record SmartTopBackground(
		Kind kind,
		@Nullable Uri artworkUri,
		@Nullable Bitmap artworkBitmap,
		String identity) {
	public SmartTopBackground {
		Objects.requireNonNull(kind, "kind");
		identity = Objects.requireNonNull(identity, "identity");
		if (identity.isBlank()) throw new IllegalArgumentException("Background identity is blank");
		boolean hasArtwork = (artworkUri != null) || (artworkBitmap != null);
		if ((kind == Kind.ARTWORK) != hasArtwork ||
				((artworkUri != null) && (artworkBitmap != null))) {
			throw new IllegalArgumentException("Artwork backgrounds carry exactly one artwork source");
		}
	}

	public static SmartTopBackground artwork(Uri uri, String itemIdentity) {
		Objects.requireNonNull(uri, "uri");
		return new SmartTopBackground(Kind.ARTWORK, uri, null,
				"art:" + token(itemIdentity) + ':' + token(uri.toString()));
	}

	public static SmartTopBackground artwork(Bitmap bitmap, String itemIdentity) {
		SmartTopThumbnail thumbnail = SmartTopThumbnail.fromBitmap(itemIdentity,
				Objects.requireNonNull(bitmap, "bitmap"));
		if (thumbnail == null) throw new IllegalArgumentException("Invalid artwork bitmap");
		Bitmap normalized = thumbnail.bitmap();
		return new SmartTopBackground(Kind.ARTWORK, null, normalized,
				"art:" + token(itemIdentity) + ':' +
						Integer.toHexString(System.identityHashCode(normalized)));
	}

	/** Keeps a validated direct source until SmartTopBinder normalizes it off the UI thread. */
	static SmartTopBackground artworkSource(Bitmap bitmap, String itemIdentity) {
		SmartTopThumbnail source = SmartTopThumbnail.fromSource(itemIdentity,
				Objects.requireNonNull(bitmap, "bitmap"));
		if (source == null) throw new IllegalArgumentException("Invalid artwork bitmap");
		return new SmartTopBackground(Kind.ARTWORK, null, source.bitmap(),
				"art:" + token(itemIdentity) + ':' +
						Integer.toHexString(System.identityHashCode(source.bitmap())));
	}

	public static SmartTopBackground audioSpectrum(String sourceIdentity) {
		return new SmartTopBackground(Kind.AUDIO_SPECTRUM, null, null,
				"audio:" + token(sourceIdentity));
	}

	public static SmartTopBackground sourceFallback(String sourceIdentity) {
		return new SmartTopBackground(Kind.SOURCE_FALLBACK, null, null,
				"source:" + token(sourceIdentity));
	}

	public static SmartTopBackground empty() {
		return new SmartTopBackground(Kind.EMPTY, null, null, "empty");
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
