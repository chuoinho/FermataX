package me.aap.fermata.addon.stremio.subtitle;

import java.net.URI;
import java.util.Locale;

public enum SubtitleFormat {
	SUBRIP(true),
	WEBVTT(true),
	ASS(true),
	SSA(true),
	TTML(true),
	MICRODVD(false),
	SAMI(false),
	SBV(false),
	UNKNOWN(false);

	private final boolean supported;

	SubtitleFormat(boolean supported) {
		this.supported = supported;
	}

	public boolean isSupported() {
		return supported;
	}

	/**
	 * Stremio subtitle providers are allowed to return a download endpoint without
	 * a file extension. The existing text parser can read SRT/WebVTT payloads from
	 * such an endpoint, while known unsupported formats must remain excluded.
	 */
	public boolean isEngineReadable(URI uri) {
		if ((this == SUBRIP) || (this == WEBVTT)) return true;
		if ((this != UNKNOWN) || (uri == null)) return false;
		String path = uri.getPath();
		if ((path == null) || path.isEmpty()) return false;
		int slash = path.lastIndexOf('/');
		return path.lastIndexOf('.') <= slash;
	}

	public static SubtitleFormat classify(URI uri, String hint) {
		SubtitleFormat hinted = classifyValue(hint);
		if (hinted != UNKNOWN) return hinted;
		String path = uri.getPath();
		if (path == null) return UNKNOWN;
		int dot = path.lastIndexOf('.');
		return (dot < 0) ? UNKNOWN : classifyValue(path.substring(dot + 1));
	}

	private static SubtitleFormat classifyValue(String value) {
		if (value == null) return UNKNOWN;
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		int semicolon = normalized.indexOf(';');
		if (semicolon >= 0) normalized = normalized.substring(0, semicolon).trim();
		return switch (normalized) {
			case "srt", "subrip", "application/x-subrip", "application/srt" -> SUBRIP;
			case "vtt", "webvtt", "text/vtt" -> WEBVTT;
			case "ass", "text/x-ass", "application/x-ass" -> ASS;
			case "ssa", "text/x-ssa", "application/x-ssa" -> SSA;
			case "ttml", "dfxp", "application/ttml+xml" -> TTML;
			case "sub", "microdvd", "text/x-microdvd" -> MICRODVD;
			case "smi", "sami", "application/x-sami" -> SAMI;
			case "sbv" -> SBV;
			default -> UNKNOWN;
		};
	}
}
