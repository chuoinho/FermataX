package me.aap.fermata.addon.web.audio;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Pure deterministic source evaluator to ensure WebAudio graphs are only attached
 * to CORS-safe or same-origin media elements.
 *
 * Calling createMediaElementSource() on cross-origin media without CORS outputs silence
 * permanently and cannot be undone (W3C Web Audio spec). This policy deterministically
 * identifies opaque cross-origin media beforehand so the browser can bypass Web Audio
 * and preserve audible native playback.
 */
public final class WebAudioCandidatePolicy {
	private WebAudioCandidatePolicy() {}

	public enum SourceSafety {
		SAFE_BLOB,
		SAFE_DATA,
		SAFE_SAME_ORIGIN,
		SAFE_CORS_EXPLICIT,
		UNSAFE_CROSS_ORIGIN_OPAQUE
	}

	public static SourceSafety evaluateSource(@Nullable String mediaSrc, @Nullable String documentOrigin,
			@Nullable String crossOriginAttr) {
		if (mediaSrc == null || mediaSrc.isEmpty()) return SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE;
		String lower = mediaSrc.toLowerCase(Locale.ROOT);
		if (lower.startsWith("blob:")) return SourceSafety.SAFE_BLOB;
		if (lower.startsWith("data:")) return SourceSafety.SAFE_DATA;
		if (lower.startsWith("file:")) return SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE;

		boolean hasCors = (crossOriginAttr != null) &&
				("anonymous".equalsIgnoreCase(crossOriginAttr) ||
				 "use-credentials".equalsIgnoreCase(crossOriginAttr));

		if (documentOrigin != null && !documentOrigin.isEmpty()) {
			try {
				URI mediaUri = new URI(mediaSrc);
				URI docUri = new URI(documentOrigin);
				String mediaHost = mediaUri.getHost();
				String docHost = docUri.getHost();
				int mediaPort = mediaUri.getPort();
				int docPort = docUri.getPort();
				String mediaScheme = mediaUri.getScheme();
				String docScheme = docUri.getScheme();

				boolean sameScheme = (mediaScheme == null) || mediaScheme.equalsIgnoreCase(docScheme);
				boolean sameHost = (mediaHost == null) || mediaHost.equalsIgnoreCase(docHost);
				boolean samePort = ((mediaPort == -1 || mediaPort == 80 || mediaPort == 443) &&
						(docPort == -1 || docPort == 80 || docPort == 443)) || (mediaPort == docPort);

				if (sameScheme && sameHost && samePort) {
					return SourceSafety.SAFE_SAME_ORIGIN;
				}
			} catch (URISyntaxException ignored) {}
		}

		if (hasCors) {
			return SourceSafety.SAFE_CORS_EXPLICIT;
		}

		return SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE;
	}

	public static boolean isSafeToAttach(SourceSafety safety) {
		return safety != SourceSafety.UNSAFE_CROSS_ORIGIN_OPAQUE;
	}
}
