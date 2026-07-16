package me.aap.fermata.addon.podcast.feed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.Test;

import me.aap.fermata.addon.podcast.net.PodcastHttpClient;

public class PodcastFeedLoaderTest {
	@Test
	public void discoversHtmlThenParsesRelativeRss() throws Exception {
		FakeHttp http = new FakeHttp(
				new Response("https://example.test/show", "text/html", """
						<html><head><link rel="alternate" type="application/rss+xml"
						 href="/feed.xml"></head></html>
						"""),
				new Response("https://example.test/feed.xml", "application/rss+xml", """
						<rss><channel><title>Discovered</title><item><guid>one</guid><title>Episode</title>
						<enclosure url="/one.mp3"/></item></channel></rss>
						"""));
		PodcastFeedLoader loader = new PodcastFeedLoader(http, new PodcastFeedParser(),
				new PodcastHtmlDiscovery());

		PodcastLoadedFeed loaded = loader.loadNow(
				PodcastFeedRequest.publicFeed("https://example.test/show"));

		assertEquals("Discovered", loaded.getFeed().getTitle());
		assertEquals("https://example.test/one.mp3",
				loaded.getFeed().getEpisodes().get(0).getMediaUrl());
		assertEquals("https://example.test/feed.xml", loaded.getFinalUrl());
		assertFalse(loaded.isNotModified());
		assertEquals(List.of("https://example.test/show", "https://example.test/feed.xml"),
				http.requestUrls());
	}

	@Test
	public void sendsBasicAuthAndHandlesNotModifiedWithoutParsing() throws Exception {
		FakeHttp http = new FakeHttp(new Response("https://example.test/private", null, null, 304));
		PodcastFeedLoader loader = new PodcastFeedLoader(http, new PodcastFeedParser(),
				new PodcastHtmlDiscovery());
		PodcastFeedRequest request = new PodcastFeedRequest("https://example.test/private",
				"driver", "secret", "tag-1", "date-1", false);

		PodcastLoadedFeed loaded = loader.loadNow(request);

		assertTrue(loaded.isNotModified());
		assertNull(loaded.getFeed());
		PodcastHttpClient.DocumentRequest sent = http.requests.get(0);
		assertTrue(sent.getAuthorization().startsWith("Basic "));
		assertEquals("tag-1", sent.getEtag());
		assertEquals("date-1", sent.getLastModified());
	}

	@Test
	public void sniffsUtf8BomBeforeHtmlAndRss() throws Exception {
		FakeHttp html = new FakeHttp(
				new Response("https://example.test/show", null, "\uFEFF  <html><head>" +
						"<link rel=\"alternate\" type=\"application/rss+xml\" href=\"/feed\">" +
						"</head></html>"),
				new Response("https://example.test/feed", null,
						"\uFEFF<rss><channel><title>BOM feed</title></channel></rss>"));
		PodcastLoadedFeed loaded = new PodcastFeedLoader(html, new PodcastFeedParser(),
				new PodcastHtmlDiscovery()).loadNow(
				PodcastFeedRequest.publicFeed("https://example.test/show"));

		assertEquals("BOM feed", loaded.getFeed().getTitle());
	}

	private record Response(String finalUrl, String contentType, String body, int status) {
		Response(String finalUrl, String contentType, String body) {
			this(finalUrl, contentType, body, 200);
		}
	}

	private static final class FakeHttp extends PodcastHttpClient {
		private final Queue<Response> responses = new ArrayDeque<>();
		final List<DocumentRequest> requests = new ArrayList<>();

		FakeHttp(Response... responses) {
			this.responses.addAll(List.of(responses));
		}

		@Override
		public <T> DocumentResponse<T> requestDocument(DocumentRequest request,
				DocumentParser<T> parser) throws IOException {
			requests.add(request);
			Response response = responses.remove();
			T body = (response.status == 304) ? null : parser.parse(
					new ByteArrayInputStream(response.body.getBytes(UTF_8)),
					response.contentType, response.finalUrl);
			return new DocumentResponse<>(response.status, response.finalUrl,
					response.contentType, null, null, body);
		}

		List<String> requestUrls() {
			return requests.stream().map(DocumentRequest::getUrl).toList();
		}
	}
}
