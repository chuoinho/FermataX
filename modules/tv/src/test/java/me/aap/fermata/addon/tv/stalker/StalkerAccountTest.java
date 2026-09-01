package me.aap.fermata.addon.tv.stalker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class StalkerAccountTest {
	@Test
	public void normalizesPortalAndMac() {
		StalkerAccount account = new StalkerAccount(4, null,
				"portal.example.invalid/stalker_portal/c/", "00-1a-79-01-02-aF",
				null, null, null, 30);

		assertTrue(account.isComplete());
		assertEquals("http://portal.example.invalid/stalker_portal", account.getPortal());
		assertEquals("00:1A:79:01:02:AF", account.getMac());
		assertEquals("portal.example.invalid", account.getName());
		assertEquals(List.of(
				"http://portal.example.invalid/stalker_portal/server/load.php",
				"http://portal.example.invalid/stalker_portal/portal.php"),
				account.getEndpointCandidates());
	}

	@Test
	public void preservesExplicitEndpoint() {
		StalkerAccount account = new StalkerAccount(1, "Home", "https://tv.invalid/portal.php",
				"001A79000001", "serial", "device", "agent", 15);

		assertEquals(List.of("https://tv.invalid/portal.php"), account.getEndpointCandidates());
		assertEquals("https://tv.invalid/c/", account.getPortalReferer());
		assertEquals("agent", account.getUserAgent());
	}

	@Test
	public void rejectsInvalidIdentityAndScheme() {
		assertFalse(new StalkerAccount(0, null, "ftp://portal.invalid/c", "00:1A:79:00:00:01",
				null, null, null, 30).isComplete());
		assertFalse(new StalkerAccount(0, null, "http://portal.invalid/c", "not-a-mac",
				null, null, null, 30).isComplete());
	}

	@Test
	public void redactsIdentityAndQueryBearingUrls() {
		StalkerAccount account = new StalkerAccount(1, "Home", "https://portal.invalid/c/",
				"00:1A:79:01:02:03", "serial-secret", "device-secret", null, 30);
		String redacted = account.redact("MAC 00:1A:79:01:02:03 serial-secret device-secret " +
				"https://stream.invalid/live.m3u8?token=secret");

		assertFalse(redacted.contains("01:02:03"));
		assertFalse(redacted.contains("serial-secret"));
		assertFalse(redacted.contains("device-secret"));
		assertFalse(redacted.contains("token=secret"));
	}
}
