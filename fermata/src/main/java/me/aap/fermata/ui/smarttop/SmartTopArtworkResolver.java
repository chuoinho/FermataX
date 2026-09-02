package me.aap.fermata.ui.smarttop;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;

/** Enforces direct-item artwork provenance and a transport-safe URI allowlist. */
final class SmartTopArtworkResolver {
	private SmartTopArtworkResolver() {
	}

	@Nullable
	static Uri directArtworkUri(@Nullable MediaMetadataCompat metadata) {
		if (metadata == null) return null;
		String value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
		if ((value == null) || value.isBlank()) return null;
		Uri uri = Uri.parse(value);
		return (uri.getScheme() == null) ? null : uri;
	}

	static boolean isAllowed(Context context, @Nullable Uri uri) {
		if ((uri == null) || isKnownAnimated(uri)) return false;
		String scheme = uri.getScheme();
		if (scheme == null) return false;
		return switch (scheme.toLowerCase(Locale.ROOT)) {
			case "http", "https", ContentResolver.SCHEME_FILE,
					ContentResolver.SCHEME_ANDROID_RESOURCE -> true;
			case ContentResolver.SCHEME_CONTENT -> isTrustedContentAuthority(context, uri);
			default -> false;
		};
	}

	static boolean isProvenAudioRoot(@Nullable BrowsableItem root) {
		return (root instanceof ExtRoot external) &&
				isProvenAudioCapability(external.getRouteCapability());
	}

	static boolean isProvenAudioAddon(@Nullable AddonInfo info) {
		return (info != null) && (info.hasCapability(AddonCapability.RADIO) ||
				info.hasCapability(AddonCapability.PODCAST) ||
				info.hasCapability(AddonCapability.AUDIOBOOK));
	}

	private static boolean isProvenAudioCapability(@Nullable AddonCapability capability) {
		return (capability == AddonCapability.RADIO) ||
				(capability == AddonCapability.PODCAST) ||
				(capability == AddonCapability.AUDIOBOOK);
	}

	private static boolean isTrustedContentAuthority(Context context, Uri uri) {
		String authority = uri.getAuthority();
		return context.getPackageName().equals(authority) || MediaStore.AUTHORITY.equals(authority);
	}

	private static boolean isKnownAnimated(Uri uri) {
		String path = uri.getPath();
		if (path == null) return false;
		path = path.toLowerCase(Locale.ROOT);
		return path.endsWith(".gif") || path.endsWith(".apng");
	}
}
