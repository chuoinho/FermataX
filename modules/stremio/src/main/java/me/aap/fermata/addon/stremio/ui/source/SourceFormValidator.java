package me.aap.fermata.addon.stremio.ui.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Local validation only; DNS and SSRF policy remain the gateway's responsibility. */
public final class SourceFormValidator {
	private SourceFormValidator() {
	}

	public static SourceUiError validate(SourceUiDraft draft) {
		if (draft == null) return SourceUiError.INVALID_URL;
		String value = draft.transportUrl().trim();
		if (value.isEmpty()) return SourceUiError.INVALID_URL;

		final URI uri;
		try {
			uri = new URI(value);
		} catch (URISyntaxException error) {
			return SourceUiError.INVALID_URL;
		}
		String scheme = uri.getScheme();
		if (scheme == null) return SourceUiError.INVALID_URL;
		scheme = scheme.toLowerCase(Locale.ROOT);
		if (!scheme.equals("https") && !scheme.equals("http") && !scheme.equals("stremio")) {
			return SourceUiError.INVALID_URL;
		}
		if (uri.getRawUserInfo() != null) return SourceUiError.INVALID_URL;
		if (scheme.equals("http") && !draft.consent().allowCleartext()) {
			return SourceUiError.CLEARTEXT_CONSENT_REQUIRED;
		}
		String host = uri.getHost();
		if ((host == null) || host.isBlank()) return SourceUiError.INVALID_URL;
		if (isPrivateLiteral(host) && !draft.consent().allowLan()) {
			return SourceUiError.LAN_CONSENT_REQUIRED;
		}
		return SourceUiError.NONE;
	}

	private static boolean isPrivateLiteral(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		if (h.equals("localhost") || h.equals("::1") || h.startsWith("fe80:")) return true;
		String[] parts = h.split("\\.", -1);
		if (parts.length != 4) return false;
		int[] address = new int[4];
		for (int i = 0; i < parts.length; i++) {
			try {
				address[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException error) {
				return false;
			}
			if ((address[i] < 0) || (address[i] > 255)) return false;
		}
		return (address[0] == 10) || (address[0] == 127) ||
				((address[0] == 169) && (address[1] == 254)) ||
				((address[0] == 172) && (address[1] >= 16) && (address[1] <= 31)) ||
				((address[0] == 192) && (address[1] == 168));
	}
}
