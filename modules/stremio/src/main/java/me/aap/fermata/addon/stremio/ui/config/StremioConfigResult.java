package me.aap.fermata.addon.stremio.ui.config;

import java.util.Objects;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.security.StremioUrlRedactor;

/** Final provider configuration URL whose diagnostic representation is always redacted. */
public final class StremioConfigResult {
	private final String url;

	StremioConfigResult(String url) {
		this.url = Objects.requireNonNull(url, "url");
	}

	public void consumeUrl(Consumer<String> consumer) {
		Objects.requireNonNull(consumer, "consumer").accept(url);
	}

	public String redactedUrl() {
		return StremioUrlRedactor.forMessage(url);
	}

	@Override
	public String toString() {
		return "StremioConfigResult[url=" + redactedUrl() + ']';
	}
}
