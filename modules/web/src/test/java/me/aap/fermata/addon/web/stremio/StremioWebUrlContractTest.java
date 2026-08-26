package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StremioWebUrlContractTest {
	@Test
	public void usesTheOfficialHostedOriginForHomeAndSearch() {
		assertEquals("https://web.stremio.com/#/", StremioWebAddon.HOME_URL);
		assertEquals("https://web.stremio.com/#/search?search=", StremioWebFragment.SEARCH_URL);
	}
}
