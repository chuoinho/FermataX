package me.aap.fermata.addon.podcast.feed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;

public class PodcastHtmlDiscoveryTest {
	private final PodcastHtmlDiscovery discovery = new PodcastHtmlDiscovery();

	@Test
	public void findsQuotedAndUnquotedRelativeFeedLinksWithoutDuplicates() throws Exception {
		String html = """
				<html><head>
				<link rel='alternate' type='application/rss+xml' href='/feed.xml?x=1&amp;y=2'>
				<link REL=feed TYPE=application/atom+xml HREF=atom.xml>
				<link rel='alternate' type='application/rss+xml' href='/feed.xml?x=1&amp;y=2'>
				<link rel='stylesheet' type='text/css' href='style.css'>
				</head></html>
				""";

		List<String> result = discovery.discover(stream(html), "https://example.test/page/index.html");

		assertEquals(List.of("https://example.test/feed.xml?x=1&y=2",
				"https://example.test/page/atom.xml"), result);
	}

	@Test
	public void rejectsOversizedDiscoveryPage() {
		byte[] html = new byte[PodcastHtmlDiscovery.MAX_HTML_BYTES + 1];
		PodcastException error = assertThrows(PodcastException.class,
				() -> discovery.discover(new ByteArrayInputStream(html), "https://example.test"));
		assertEquals(PodcastErrorCode.TOO_LARGE, error.getCode());
	}

	private static ByteArrayInputStream stream(String value) throws IOException {
		return new ByteArrayInputStream(value.getBytes(UTF_8));
	}
}
