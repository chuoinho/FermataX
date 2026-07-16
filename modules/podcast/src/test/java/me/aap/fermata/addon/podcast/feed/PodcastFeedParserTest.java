package me.aap.fermata.addon.podcast.feed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;

import org.junit.Test;

import me.aap.fermata.addon.podcast.model.PodcastEpisode;
import me.aap.fermata.addon.podcast.model.PodcastFeed;

public class PodcastFeedParserTest {
	private final PodcastFeedParser parser = new PodcastFeedParser();

	@Test
	public void parsesRssItunesMetadataRelativeUrlsAndDurations() throws Exception {
		PodcastFeed feed = parse("""
				<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
				 xmlns:content="http://purl.org/rss/1.0/modules/content/">
				 <channel>
				  <title>Road Stories</title><description>Feed description</description>
				  <language>en</language><itunes:author>Driver</itunes:author>
				  <itunes:image href="/cover.jpg"/><itunes:explicit>yes</itunes:explicit>
				  <link>https://example.test/show</link>
				  <item>
				   <guid>episode-1</guid><title>First drive</title>
				   <content:encoded><![CDATA[Long description]]></content:encoded>
				   <pubDate>Tue, 14 Jul 2026 08:30:00 +0000</pubDate>
				   <itunes:duration>01:02:03</itunes:duration><itunes:season>2</itunes:season>
				   <itunes:episode>4</itunes:episode>
				   <itunes:explicit>true</itunes:explicit>
				   <enclosure url="audio/one.mp3" type="audio/mpeg" length="12345"/>
				  </item>
				 </channel>
				</rss>
				""", "https://example.test/podcast/feed.xml");

		assertEquals("Road Stories", feed.getTitle());
		assertEquals("Driver", feed.getAuthor());
		assertEquals("https://example.test/cover.jpg", feed.getArtworkUrl());
		assertEquals("https://example.test/show", feed.getWebsiteUrl());
		assertTrue(feed.isExplicit());
		assertEquals(1, feed.getEpisodes().size());
		PodcastEpisode episode = feed.getEpisodes().get(0);
		assertEquals(PodcastEpisode.IdentityKind.GUID, episode.getIdentityKind());
		assertEquals("First drive", episode.getTitle());
		assertEquals("Long description", episode.getDescription());
		assertEquals("https://example.test/podcast/audio/one.mp3", episode.getMediaUrl());
		assertEquals("audio/mpeg", episode.getMimeType());
		assertEquals(12345, episode.getMediaLength());
		assertEquals(3_723_000, episode.getDurationMs());
		assertEquals(2, episode.getSeasonNumber());
		assertEquals(4, episode.getEpisodeNumber());
		assertTrue(episode.isExplicit());
		assertTrue(episode.getPublicationMs() > 0);
	}

	@Test
	public void parsesAtomFeedAndEnclosureFallbackIdentity() throws Exception {
		PodcastFeed feed = parse("""
				<feed xmlns="http://www.w3.org/2005/Atom">
				 <title>Atom Road</title><subtitle>Atom description</subtitle>
				 <author><name>Atom Host</name></author>
				 <link rel="self" href="/feed.atom"/>
				 <link rel="alternate" href="/show"/>
				 <entry>
				  <title>Atom episode</title><summary>Episode summary</summary>
				  <published>2026-07-14T09:00:00Z</published>
				  <link rel="alternate" href="/episodes/one"/>
				  <link rel="enclosure" href="/media/one.m4a" type="audio/mp4" length="999"/>
				 </entry>
				</feed>
				""", "https://atom.test/start");

		assertEquals("Atom Road", feed.getTitle());
		assertEquals("Atom Host", feed.getAuthor());
		assertEquals("https://atom.test/feed.atom", feed.getSelfUrl());
		assertEquals("https://atom.test/show", feed.getWebsiteUrl());
		PodcastEpisode episode = feed.getEpisodes().get(0);
		assertEquals(PodcastEpisode.IdentityKind.ENCLOSURE, episode.getIdentityKind());
		assertEquals("https://atom.test/media/one.m4a", episode.getMediaUrl());
		assertEquals("https://atom.test/episodes/one", episode.getPermalink());
	}

	@Test
	public void duplicateGuidKeepsOnePlayableEpisodeAndMissingMediaIsOmitted() throws Exception {
		PodcastFeed feed = parse("""
				<rss><channel><title>Duplicates</title>
				 <item><guid>same</guid><title>One</title>
				  <enclosure url="https://media.test/one.mp3"/></item>
				 <item><guid>same</guid><title>Duplicate</title>
				  <enclosure url="https://media.test/two.mp3"/></item>
				 <item><guid>no-media</guid><title>Text only</title></item>
				</channel></rss>
				""", "https://example.test/feed");

		assertEquals(1, feed.getEpisodes().size());
		assertEquals("One", feed.getEpisodes().get(0).getTitle());
	}

	@Test
	public void duplicateEnclosureFallbackAlsoKeepsOneEpisode() throws Exception {
		PodcastFeed feed = parse("""
				<rss><channel><title>Duplicates</title>
				 <item><title>One</title><enclosure url="https://media.test/same.mp3"/></item>
				 <item><title>Two</title><enclosure url="https://media.test/same.mp3"/></item>
				</channel></rss>
				""", "https://example.test/feed");

		assertEquals(1, feed.getEpisodes().size());
		assertEquals("One", feed.getEpisodes().get(0).getTitle());
	}

	@Test
	public void rejectsHtmlDoctypeAndMalformedDocuments() {
		assertThrows(IOException.class, () -> parse("<html><body>login</body></html>",
				"https://example.test"));
		assertThrows(IOException.class, () -> parse("""
				<!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<rss><channel><title>&xxe;</title></channel></rss>
				""", "https://example.test"));
		assertThrows(IOException.class, () -> parse("<rss><channel><title>broken</channel></rss>",
				"https://example.test"));
		assertThrows(IOException.class, () -> parse("<!doctype rss><rss/>",
				"https://example.test"));
		byte[] utf16 = {(byte) 0xFF, (byte) 0xFE, '<', 0, 'r', 0, 's', 0, 's', 0, '/', 0, '>', 0};
		assertThrows(IOException.class, () -> parser.parse(new ByteArrayInputStream(utf16),
				"https://example.test"));
	}

	@Test
	public void capsEpisodesWithoutBuildingAnUnboundedFeedList() throws Exception {
		StringBuilder xml = new StringBuilder("<rss><channel><title>Large</title>");
		for (int i = 0; i < PodcastFeedParser.MAX_EPISODES + 100; i++) {
			xml.append("<item><guid>").append(i).append("</guid><title>Episode ")
					.append(i).append("</title><enclosure url=\"https://media.test/")
					.append(i).append(".mp3\"/></item>");
		}
		xml.append("</channel></rss>");

		PodcastFeed feed = parse(xml.toString(), "https://example.test/feed");
		assertEquals(PodcastFeedParser.MAX_EPISODES, feed.getEpisodes().size());
		assertFalse(feed.getEpisodes().get(0).getKey().isEmpty());
	}

	@Test
	public void capsTextAndRejectsExcessiveDepthAndLateMalformedFeed() throws Exception {
		String description = "x".repeat(70 * 1024);
		PodcastFeed bounded = parse("<rss><channel><title>Text</title><item><guid>one</guid>" +
				"<description>" + description + "</description>" +
				"<enclosure url=\"https://media.test/one.mp3\"/></item></channel></rss>",
				"https://example.test/feed");
		assertEquals(64 * 1024, bounded.getEpisodes().get(0).getDescription().length());

		String deep = "<rss><channel><title>Deep</title>" + "<x>".repeat(70) +
				"</x>".repeat(70) + "</channel></rss>";
		assertThrows(IOException.class, () -> parse(deep, "https://example.test/feed"));

		StringBuilder malformed = new StringBuilder("<rss><channel><title>Late failure</title>");
		for (int i = 0; i < 500; i++) {
			malformed.append("<item><guid>").append(i)
					.append("</guid><enclosure url=\"https://media.test/").append(i)
					.append(".mp3\"/></item>");
		}
		malformed.append("<item><title>broken</channel></rss>");
		assertThrows(IOException.class, () -> parse(malformed.toString(),
				"https://example.test/feed"));
	}

	@Test
	public void oldestFirstFeedRetainsNewestBoundedEpisodes() throws Exception {
		StringBuilder xml = new StringBuilder("<rss><channel><title>Oldest first</title>");
		for (int i = 0; i < PodcastFeedParser.MAX_EPISODES + 10; i++) {
			xml.append("<item><guid>").append(i).append("</guid><title>Episode ")
					.append(i).append("</title><pubDate>")
					.append(Instant.ofEpochSecond(1_700_000_000L + i))
					.append("</pubDate><enclosure url=\"https://media.test/")
					.append(i).append(".mp3\"/></item>");
		}
		xml.append("</channel></rss>");

		PodcastFeed feed = parse(xml.toString(), "https://example.test/feed");

		assertEquals(PodcastFeedParser.MAX_EPISODES, feed.getEpisodes().size());
		assertFalse(feed.getEpisodes().stream().anyMatch(e -> e.getGuid().equals("0")));
		assertFalse(feed.getEpisodes().stream().anyMatch(e -> e.getGuid().equals("9")));
		assertTrue(feed.getEpisodes().stream().anyMatch(e -> e.getGuid().equals("10")));
		assertTrue(feed.getEpisodes().stream().anyMatch(e -> e.getGuid().equals("2009")));
	}

	@Test
	public void durationAndDateHelpersRejectInvalidValues() {
		assertEquals(90_000, PodcastFeedParser.duration("1:30"));
		assertEquals(90_500, PodcastFeedParser.duration("90.5"));
		assertEquals(-1, PodcastFeedParser.duration("bad"));
		assertEquals(0, PodcastFeedParser.date("bad"));
	}

	private PodcastFeed parse(String xml, String baseUrl) throws IOException {
		return parser.parse(new ByteArrayInputStream(xml.getBytes(UTF_8)), baseUrl);
	}
}
