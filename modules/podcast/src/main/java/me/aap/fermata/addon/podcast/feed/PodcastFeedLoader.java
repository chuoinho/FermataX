package me.aap.fermata.addon.podcast.feed;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.podcast.model.PodcastFeed;
import me.aap.fermata.addon.podcast.net.PodcastErrorCode;
import me.aap.fermata.addon.podcast.net.PodcastException;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient.DocumentRequest;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient.DocumentResponse;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

public final class PodcastFeedLoader implements PodcastFeedProvider {
	private static final int MAX_DISCOVERY_ATTEMPTS = 5;
	private final PodcastHttpClient http;
	private final PodcastFeedParser parser;
	private final PodcastHtmlDiscovery discovery;

	public PodcastFeedLoader() {
		this(new PodcastHttpClient(), new PodcastFeedParser(), new PodcastHtmlDiscovery());
	}

	public PodcastFeedLoader(PodcastHttpClient http, PodcastFeedParser parser,
			PodcastHtmlDiscovery discovery) {
		this.http = http;
		this.parser = parser;
		this.discovery = discovery;
	}

	@Override
	public FutureSupplier<PodcastLoadedFeed> load(PodcastFeedRequest request) {
		return App.get().execute(() -> loadNow(request));
	}

	PodcastLoadedFeed loadNow(PodcastFeedRequest request) throws IOException {
		ParsedDocument document = request(request.getUrl(), request, true);
		if (document.response.isNotModified()) return loaded(document.response, null, true);
		if (document.feed != null) return loaded(document.response, document.feed, false);

		IOException last = null;
		List<String> candidates = document.candidates;
		for (int i = 0; (i < candidates.size()) && (i < MAX_DISCOVERY_ATTEMPTS); i++) {
			String candidate = candidates.get(i);
			try {
				ParsedDocument feed = request(candidate, request,
						sameOrigin(document.response.getFinalUrl(), candidate));
				if (feed.feed != null) return loaded(feed.response, feed.feed, false);
			} catch (IOException ex) {
				last = ex;
			}
		}
		if (last != null) throw last;
		throw new PodcastException(PodcastErrorCode.INVALID_CONTENT,
				"No RSS or Atom feed was found on this page");
	}

	private ParsedDocument request(String url, PodcastFeedRequest source, boolean includeAuth)
			throws IOException {
		DocumentRequest request = new DocumentRequest(url,
				"application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.2",
				includeAuth ? source.getAuthorization() : null, source.getEtag(),
				source.getLastModified(), source.isAuthenticatedDowngradeAllowed());
		DocumentResponse<ParsedDocument> response =
				http.requestDocument(request, this::parseDocument);
		ParsedDocument document = response.getBody();
		if (document == null) document = ParsedDocument.discovery(List.of());
		document.response = response;
		return document;
	}

	private ParsedDocument parseDocument(InputStream input, String contentType, String finalUrl)
			throws IOException {
		PushbackInputStream source = new PushbackInputStream(new BufferedInputStream(input), 512);
		byte[] prefix = new byte[512];
		int read = source.read(prefix);
		if (read > 0) source.unread(prefix, 0, read);
		String start = (read <= 0) ? "" : new String(prefix, 0, read,
				StandardCharsets.UTF_8);
		if (!start.isEmpty() && (start.charAt(0) == '\uFEFF')) start = start.substring(1);
		start = start.stripLeading().toLowerCase(Locale.ROOT);
		boolean html = ((contentType != null) &&
				contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) ||
				start.startsWith("<!doctype html") || start.startsWith("<html");
		if (html) return ParsedDocument.discovery(discovery.discover(source, finalUrl));
		return ParsedDocument.feed(parser.parse(source, finalUrl));
	}

	private static PodcastLoadedFeed loaded(DocumentResponse<ParsedDocument> response,
			PodcastFeed feed, boolean notModified) {
		return new PodcastLoadedFeed(feed, response.getFinalUrl(), response.getEtag(),
				response.getLastModified(), notModified);
	}

	private static boolean sameOrigin(String first, String second) {
		try {
			URI a = new URI(first);
			URI b = new URI(second);
			return lower(a.getScheme()).equals(lower(b.getScheme())) &&
					lower(a.getHost()).equals(lower(b.getHost())) && port(a) == port(b);
		} catch (URISyntaxException | NullPointerException ex) {
			return false;
		}
	}

	private static int port(URI uri) {
		if (uri.getPort() != -1) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}

	private static final class ParsedDocument {
		DocumentResponse<ParsedDocument> response;
		final PodcastFeed feed;
		final List<String> candidates;

		private ParsedDocument(PodcastFeed feed, List<String> candidates) {
			this.feed = feed;
			this.candidates = candidates;
		}

		static ParsedDocument feed(PodcastFeed feed) {
			return new ParsedDocument(feed, List.of());
		}

		static ParsedDocument discovery(List<String> candidates) {
			return new ParsedDocument(null, candidates);
		}
	}
}
