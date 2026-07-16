package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class XtreamEpgProgramTest extends Assert {

	@Test
	public void decodeTextReadsBase64AndKeepsPlainText() {
		String encoded = Base64.getEncoder().encodeToString("Evening News".getBytes(StandardCharsets.UTF_8));

		assertEquals("Evening News", XtreamEpgProgram.decodeText(encoded));
		assertEquals("Plain title", XtreamEpgProgram.decodeText("Plain title"));
		assertEquals("News", XtreamEpgProgram.decodeText("News"));
	}

	@Test
	public void parseTimeAcceptsEpochSecondsMillisAndUtcText() {
		assertEquals(1700000000000L, XtreamEpgProgram.parseTime("1700000000"));
		assertEquals(1700000000123L, XtreamEpgProgram.parseTime("1700000000123"));
		assertEquals(1704067200000L, XtreamEpgProgram.parseTime("2024-01-01 00:00:00"));
	}

	@Test
	public void accountBuildsTimeshiftUrl() {
		XtreamAccount account = new XtreamAccount(0, "Test", 0, "example.com", 8080,
				"user", "pass", 0, null, 0);

		assertEquals("http://example.com:8080/timeshift/user/pass/90/2024-01-01:00-00/42.ts",
				account.buildTimeshiftStreamUrl(42, 1704067200000L, 90 * 60000L));
	}
}
