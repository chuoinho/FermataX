package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioWebSessionPolicyTest {
	@Test
	public void playerRoutesNeverBecomePersistedEntryState() {
		assertFalse(StremioWebSessionPolicy.isPersistableRoute(
				"https://web.stremio.com/#/player/encoded-stream"));
		assertTrue(StremioWebSessionPolicy.isPersistableRoute(
				"https://web.stremio.com/#/detail/movie/example"));
		assertFalse(StremioWebSessionPolicy.isPersistableRoute("https://example.com/#/player/x"));
	}

	@Test
	public void legacyPlayerRouteNormalizesToHome() {
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.entryUrl(false,
						"https://web.stremio.com/#/player/legacy-stream"));
	}

	@Test
	public void endedSessionAlwaysStartsAtHome() {
		String detail = "https://web.stremio.com/#/detail/movie/example";
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.entryUrl(true, detail));
		assertEquals(detail, StremioWebSessionPolicy.entryUrl(false, detail));
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.entryUrl(false, "https://web.stremio.com/#/player/old"));
	}
}
