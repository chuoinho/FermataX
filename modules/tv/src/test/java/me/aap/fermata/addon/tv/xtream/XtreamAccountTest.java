package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

public class XtreamAccountTest extends Assert {

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
}
