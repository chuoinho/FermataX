package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

public class XtreamAccountTest extends Assert {
	@Test
	public void catalogIdMatchingIsScopedToOneSource() {
		assertTrue(XtreamItemId.belongsToCatalog("tvxt:7:news:News:42", 7));
		assertTrue(XtreamItemId.belongsToCatalog("tvxe:7:series:Series:9:Name:1:3", 7));
		assertFalse(XtreamItemId.belongsToCatalog("tvxt:8:news:News:42", 7));
		assertFalse(XtreamItemId.belongsToCatalog("tvx:7", 7));
		assertFalse(XtreamItemId.belongsToCatalog("m3ut:7:test", 7));
	}


	@Test
	public void parsesM3uPortalUrlFromHostField() {
		XtreamAccount account = new XtreamAccount(0, null, 0,
				"http://idlib.link:2082/get.php?username=03484525&password=03484525&type=m3u&output=m3u8",
				0, null, null, 0, null, 0);

		assertEquals("http", account.getScheme());
		assertEquals("idlib.link", account.getHost());
		assertEquals(2082, account.getPort());
		assertEquals("03484525", account.getUsername());
		assertEquals("03484525", account.getPassword());
		assertEquals("m3u8", account.getOutput());
	}

	@Test
	public void parsesUserInfoPortalUrlFromHostField() {
		XtreamAccount account = new XtreamAccount(0, null, 0,
				"https://user%40mail.test:pass%23123@example.com:8443", 0, null, null, 0,
				null, 0);

		assertEquals("https", account.getScheme());
		assertEquals("example.com", account.getHost());
		assertEquals(8443, account.getPort());
		assertEquals("user@mail.test", account.getUsername());
		assertEquals("pass#123", account.getPassword());
	}

	@Test
	public void passwordAndPlaybackOptionsKeepTheCatalogRevision() {
		XtreamAccount original = account("portal.test", "user", "old-pass", 0, "Old", 20);
		XtreamAccount rotated = account("portal.test", "user", "new-pass", 1, "New", 45);

		assertTrue(XtreamSourceItem.sameCatalog(original, rotated));
		XtreamAccount resolved = XtreamSourceItem.requireAccountRevision(rotated, 0, 0);
		String liveUrl = resolved.buildLiveStreamUrl(42);
		assertTrue(liveUrl, liveUrl.contains("/live/user/new-pass/"));
		assertTrue(liveUrl, liveUrl.endsWith("/42.m3u8"));
	}

	@Test
	public void hostOrUsernameChangeCreatesANewCatalogRevision() {
		XtreamAccount original = account("portal.test", "user", "pass", 0, "Portal", 20);
		XtreamAccount newHost = account("other.test", "user", "pass", 0, "Portal", 20);
		XtreamAccount newUser = account("portal.test", "other", "pass", 0, "Portal", 20);

		assertFalse(XtreamSourceItem.sameCatalog(original, newHost));
		assertFalse(XtreamSourceItem.sameCatalog(original, newUser));
	}

	@Test(expected = IllegalStateException.class)
	public void staleCatalogItemCannotResolveAfterHostOrUsernameRevisionChanges() {
		XtreamAccount replacement = account("other.test", "user", "pass", 0, "Portal", 20);
		XtreamSourceItem.requireAccountRevision(replacement, 1, 0);
	}

	private static XtreamAccount account(String host, String user, String password, int output,
			String name, int timeout) {
		return new XtreamAccount(7, name, 0, host, 8080, user, password, output,
				"FermataX", timeout);
	}
}
