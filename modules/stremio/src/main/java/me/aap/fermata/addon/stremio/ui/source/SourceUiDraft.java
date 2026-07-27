package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;

/** Sensitive editor state. Never log or persist this object from the UI layer. */
public final class SourceUiDraft {
	private final String transportUrl;
	private final String configurationToken;
	private final SourceUiConsent consent;

	public SourceUiDraft(String transportUrl, String configurationToken,
			SourceUiConsent consent) {
		this.transportUrl = Objects.requireNonNull(transportUrl, "transportUrl");
		this.configurationToken = (configurationToken == null) ? "" : configurationToken;
		this.consent = Objects.requireNonNull(consent, "consent");
	}

	public String transportUrl() {
		return transportUrl;
	}

	public String configurationToken() {
		return configurationToken;
	}

	public SourceUiConsent consent() {
		return consent;
	}

	@Override
	public String toString() {
		return "SourceUiDraft[redacted, consent=" + consent + ']';
	}
}
