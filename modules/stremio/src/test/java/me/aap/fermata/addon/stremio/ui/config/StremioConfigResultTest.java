package me.aap.fermata.addon.stremio.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class StremioConfigResultTest {
	@Test
	public void tokenizedUrlIsAvailableOnlyThroughExplicitConsumer() {
		String raw = "https://provider.example.invalid/super-secret/manifest.json?token=hidden";
		StremioConfigResult result = new StremioConfigResult(raw);
		AtomicReference<String> consumed = new AtomicReference<>();

		result.consumeUrl(consumed::set);

		assertEquals(raw, consumed.get());
		assertFalse(result.toString().contains("super-secret"));
		assertFalse(result.toString().contains("hidden"));
		assertFalse(result.redactedUrl().contains("super-secret"));
	}

	@Test
	public void stremioSchemeNeverLeaksConfiguration() {
		StremioConfigResult result = new StremioConfigResult(
				"stremio://provider.example/private-token/manifest.json");

		assertFalse(result.toString().contains("private-token"));
	}
}
