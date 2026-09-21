package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioWebSessionPolicyTest {
	@Test
	public void routeTrustBoundaryRejectsCredentialsAndOtherPorts() {
		for (String url : new String[] {
				"https://user:secret@web.stremio.com/#/detail/movie/example",
				"https://web.stremio.com:444/#/detail/movie/example"}) {
			assertFalse(url, StremioWebSessionPolicy.isHostedRoute(url));
			assertFalse(url, StremioWebSessionPolicy.isPersistableRoute(url));
		}
	}

	@Test
	public void emptyAndEncodedPlayerRoutesCannotBeRecoveredAsEntryState() {
		for (String fragment : new String[] {"/player", "/player?stream=secret",
				"/player/stream", "/%70layer/stream", "/player%2Fstream"}) {
			String url = "https://web.stremio.com/#" + fragment;
			assertFalse(fragment, StremioWebSessionPolicy.isPersistableRoute(url));
			assertEquals(StremioWebSessionPolicy.HOME_URL,
					StremioWebSessionPolicy.entryUrl(false, url));
		}
	}

	@Test
	public void playerRoutesNeverBecomePersistedEntryState() {
		assertFalse(StremioWebSessionPolicy.isPersistableRoute(
				"https://web.stremio.com/#/player/encoded-stream"));
		assertTrue(StremioWebSessionPolicy.isPersistableRoute(
				"https://web.stremio.com/#/detail/movie/example"));
		assertFalse(StremioWebSessionPolicy.isPersistableRoute("https://example.com/#/player/x"));
	}

	@Test
	public void legacyPlayerRouteRestoresPreviousDetailRoute() {
		String detail = "https://web.stremio.com/#/detail/movie/example";
		assertTrue(StremioWebSessionPolicy.isDetailRoute(detail));
		assertEquals(detail, StremioWebSessionPolicy.replaceLegacyPlayerRoute(
				"https://web.stremio.com/#/player/legacy-stream", detail));
	}

	@Test
	public void legacyPlayerRouteWithoutDetailFallsBackToHome() {
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.replaceLegacyPlayerRoute(
						"https://web.stremio.com/#/player/legacy-stream",
						"https://web.stremio.com/#/search?search=example"));
	}

	@Test
	public void backFollowsPlayerDetailHomeAndParentHierarchy() {
		String player = "https://web.stremio.com/#/player/encoded-stream";
		String detail = "https://web.stremio.com/#/detail/movie/tt123";
		assertEquals(detail, StremioWebSessionPolicy.backTarget(player, detail, false));
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.backTarget(player, "https://web.stremio.com/#/discover", false));
		assertEquals(StremioWebSessionPolicy.HOME_URL,
				StremioWebSessionPolicy.backTarget(detail, detail, false));
		assertNull(StremioWebSessionPolicy.backTarget(StremioWebSessionPolicy.HOME_URL, detail, false));
		assertEquals(detail, StremioWebSessionPolicy.backTarget(detail, detail, true));
	}

	@Test
	public void activePlaybackRouteIsAllowedToRemainPersisted() {
		assertTrue(StremioWebSessionPolicy.isPersistableRoute(
				"https://web.stremio.com/#/detail/movie/tt123"));
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
