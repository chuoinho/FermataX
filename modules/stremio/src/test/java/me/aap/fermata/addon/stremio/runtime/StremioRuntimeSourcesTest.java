package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;

public class StremioRuntimeSourcesTest {
	@Test
	public void manifestNormalizationPreservesNetworkConsent() {
		NetworkConsent consent = new NetworkConsent(true, true);
		StremioSourceInput normalized = StremioRuntimeSources.normalize(
				new StremioSourceInput("stremio://example.com/manifest.json",
						"token", consent));

		assertEquals("https://example.com/manifest.json", normalized.transportUrl());
		assertEquals("token", normalized.configurationToken());
		assertEquals(consent, normalized.networkConsent());
	}
}
