package me.aap.fermata.addon.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebUrlSyncPolicyTest {
	@Test
	public void permitsOnlyTransportablePageUrls() {
		assertTrue(WebUrlSyncPolicy.accepts("https://example.org/watch?a=1#episode2"));
		assertTrue(WebUrlSyncPolicy.accepts("http://[2001:db8::1]/watch"));
		assertFalse(WebUrlSyncPolicy.accepts("javascript:alert(1)"));
		assertFalse(WebUrlSyncPolicy.accepts("blob:https://example.org/id"));
		assertFalse(WebUrlSyncPolicy.accepts("https://u:p@example.org/"));
		assertFalse(WebUrlSyncPolicy.accepts("https://example.org/\n"));
		assertFalse(WebUrlSyncPolicy.accepts("https:///missing-host"));
		assertFalse(WebUrlSyncPolicy.accepts("https://\u4f8b\u3048.\u30c6\u30b9\u30c8/path"));
		assertFalse(WebUrlSyncPolicy.accepts(null));
		assertFalse(WebUrlSyncPolicy.accepts(""));
	}

	@Test
	public void enforcesUtf8ByteLimitWithoutTruncating() {
		String prefix = "https://example.org/";
		assertTrue(WebUrlSyncPolicy.accepts(prefix + "\u00e9".repeat(8182)));
		assertFalse(WebUrlSyncPolicy.accepts(prefix + "\u00e9".repeat(8183)));
	}
}
