package me.aap.fermata.addon.audiobook.remote;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.security.AudiobookCredential;
import me.aap.fermata.addon.audiobook.security.AudiobookCredentialStore;
import me.aap.fermata.addon.audiobook.util.AudiobookIds;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;

/** Bounded OPDS 1.x/2.0 XML feed reader for audio acquisitions. */
public final class OpdsCatalogClient {
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 30_000;
	private static final int MAX_XML_BYTES = 16 * 1024 * 1024;
	private static final int MAX_PAGES = 20;
	private final AudiobookCredentialStore credentials;

	public OpdsCatalogClient(AudiobookCredentialStore credentials) {
		this.credentials = credentials;
	}

	public FutureSupplier<OpdsCatalogSnapshot> connect(String endpoint, String username,
			String password, String bearerToken) {
		return App.get().execute(() -> {
			String normalized = normalizeEndpoint(endpoint);
			AudiobookCredential credential = credential(normalized, username, password, bearerToken);
			boolean authenticated = !credential.authorization().isEmpty();
			if (authenticated && !credentials.isAvailable()) throw new IOException(
					"Encrypted credential storage is unavailable");
			String reference = authenticated ? AudiobookIds.source("opds-credential",
					normalized + '\n' + credential.username() + '\n' + bearerToken) : null;
			if ((reference != null) && !credentials.save(reference, credential)) {
				throw new IOException("Could not save encrypted OPDS credentials");
			}
			AudiobookSource source = source(normalized, credential.username(), reference,
					System.currentTimeMillis());
			try {
				return load(source, credential);
			} catch (Throwable error) {
				if (reference != null) credentials.remove(reference);
				throw error;
			}
		});
	}

	public FutureSupplier<OpdsCatalogSnapshot> refresh(AudiobookSource source) {
		return App.get().execute(() -> load(source, requireCredential(source)));
	}

	public java.util.Map<String, String> requestHeaders(AudiobookSource source) {
		AudiobookCredential credential = credentials.load(source.getCredentialRef());
		String authorization = (credential == null) ? "" : credential.authorization();
		return authorization.isEmpty() ? java.util.Map.of() :
				java.util.Map.of("Authorization", authorization);
	}

	public void removeCredentials(AudiobookSource source) {
		credentials.remove(source.getCredentialRef());
	}

	private OpdsCatalogSnapshot load(AudiobookSource source, AudiobookCredential credential)
			throws IOException {
		List<OpdsCatalogSnapshot.Entry> entries = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		List<String> pages = new ArrayList<>();
		pages.add(source.getEndpoint());
		for (int index = 0; (index < MAX_PAGES) && (index < pages.size()); index++) {
			String page = pages.get(index);
			if (!visited.add(page)) continue;
			ParsedFeed feed = parse(fetch(page, credential), page, source);
			entries.addAll(feed.entries());
			if (feed.next() != null) pages.add(feed.next());
			pages.addAll(feed.navigation());
		}
		return new OpdsCatalogSnapshot(source, entries);
	}

	private static ParsedFeed parse(InputStream input, String base, AudiobookSource source)
			throws IOException {
		try (InputStream stream = input) {
			PushbackInputStream sniffed = new PushbackInputStream(stream, 1);
			int first;
			do first = sniffed.read(); while ((first >= 0) && Character.isWhitespace(first));
			if (first >= 0) sniffed.unread(first);
			if ((first == '{') || (first == '[')) return parseJson(readAll(sniffed), base, source);
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(sniffed, null);
			List<RawEntry> raw = new ArrayList<>();
			RawEntry current = null;
			String text = null;
			String next = null;
			List<String> navigationLinks = new ArrayList<>();
			for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
					event = parser.next()) {
				if (event == XmlPullParser.START_TAG) {
					String name = local(parser.getName());
					if (name.equals("entry") || name.equals("item")) {
						current = new RawEntry();
						raw.add(current);
					} else if (name.equals("link")) {
						String href = parser.getAttributeValue(null, "href");
						if (href == null) href = parser.getAttributeValue(null, "url");
						String rel = parser.getAttributeValue(null, "rel");
						if (href != null) {
							String resolved = resolve(base, href);
							String type = parser.getAttributeValue(null, "type");
							if ((current == null) && "next".equalsIgnoreCase(rel)) next = resolved;
							else if (current == null && isNavigationLink(rel, type)) navigationLinks.add(resolved);
							else if (current != null) current.link(resolved, rel, type);
						}
					} else if (name.equals("enclosure") && (current != null)) {
						String href = parser.getAttributeValue(null, "url");
						if (href != null) current.link(resolve(base, href), "enclosure",
								parser.getAttributeValue(null, "type"));
					} else if (isTextElement(name)) {
						text = name;
					}
				} else if (event == XmlPullParser.TEXT) {
					if ((current != null) && (text != null)) current.text(text, parser.getText());
				} else if (event == XmlPullParser.END_TAG) {
					String name = local(parser.getName());
					if (name.equals("entry") || name.equals("item")) current = null;
					if (isTextElement(name)) text = null;
				}
			}
			List<OpdsCatalogSnapshot.Entry> entries = new ArrayList<>();
			long now = System.currentTimeMillis();
			for (RawEntry value : raw) {
				if (value.title.isEmpty() || value.acquisitions.isEmpty()) continue;
				String identity = value.id.isEmpty() ? value.acquisitions.get(0).url : value.id;
				String bookId = AudiobookIds.book("opds", source.getId() + '/' + identity);
				AudiobookBook book = new AudiobookBook(bookId, source.getId(), identity, value.title,
						value.author, "", value.description, value.artwork, value.language, 0, null,
						0, 0, false, now, now);
				List<AudiobookChapter> chapters = new ArrayList<>();
				for (int i = 0; i < value.acquisitions.size(); i++) {
					Acquisition acquisition = value.acquisitions.get(i);
					String chapterId = AudiobookIds.chapter(bookId + '/' + i + '/' + acquisition.url);
					chapters.add(new AudiobookChapter(bookId, chapterId, i,
							value.title, acquisition.url, acquisition.type, 0, 0, 0,
							false, null, 0));
				}
				entries.add(new OpdsCatalogSnapshot.Entry(book, chapters));
			}
			navigationLinks.addAll(navigation(raw));
			return new ParsedFeed(entries, next, navigationLinks);
		} catch (Exception error) {
			if (error instanceof IOException io) throw io;
			throw new IOException("Could not parse OPDS catalog", error);
		}
	}

	private static ParsedFeed parseJson(String json, String base, AudiobookSource source)
			throws IOException {
		try {
			JSONObject root = new JSONObject(json);
			JSONArray publications = root.optJSONArray("publications");
			if (publications == null) publications = root.optJSONArray("items");
			List<OpdsCatalogSnapshot.Entry> entries = new ArrayList<>();
			long now = System.currentTimeMillis();
			if (publications != null) for (int index = 0; index < publications.length(); index++) {
				JSONObject publication = publications.optJSONObject(index);
				if (publication == null) continue;
				JSONObject metadata = publication.optJSONObject("metadata");
				if (metadata == null) metadata = publication;
				String title = metadata.optString("title", "").trim();
				if (title.isEmpty()) continue;
				String identity = metadata.optString("identifier",
						publication.optString("id", "")).trim();
				String author = author(metadata.opt("author"));
				String artwork = image(metadata.opt("images"));
				if (!artwork.isEmpty()) artwork = resolve(base, artwork);
				List<Acquisition> acquisitions = new ArrayList<>();
				JSONArray links = publication.optJSONArray("links");
				if (links != null) for (int linkIndex = 0; linkIndex < links.length(); linkIndex++) {
					JSONObject link = links.optJSONObject(linkIndex);
					if (link == null) continue;
					String href = link.optString("href", "");
					String type = link.optString("type", "").toLowerCase(Locale.ROOT);
					String rel = link.optString("rel", "").toLowerCase(Locale.ROOT);
					if (!href.isEmpty() && (rel.contains("acquisition") || rel.equals("enclosure") ||
							type.startsWith("audio/"))) acquisitions.add(new Acquisition(
							resolve(base, href), type));
				}
				if (acquisitions.isEmpty()) continue;
				if (identity.isEmpty()) identity = acquisitions.get(0).url();
				String bookId = AudiobookIds.book("opds", source.getId() + '/' + identity);
				AudiobookBook book = new AudiobookBook(bookId, source.getId(), identity, title,
						author, "", metadata.optString("description", ""), artwork,
						metadata.optString("language", ""), 0, null, 0, 0, false, now, now);
				List<AudiobookChapter> chapters = new ArrayList<>();
				for (int i = 0; i < acquisitions.size(); i++) {
					Acquisition acquisition = acquisitions.get(i);
					chapters.add(new AudiobookChapter(bookId,
							AudiobookIds.chapter(bookId + '/' + i + '/' + acquisition.url()), i,
							title, acquisition.url(), acquisition.type(), 0, 0, 0, false, null, 0));
				}
				entries.add(new OpdsCatalogSnapshot.Entry(book, chapters));
			}
			String next = null;
			List<String> navigation = new ArrayList<>();
			JSONArray rootLinks = root.optJSONArray("links");
			if (rootLinks != null) for (int index = 0; index < rootLinks.length(); index++) {
				JSONObject link = rootLinks.optJSONObject(index);
				if ((link != null) && "next".equalsIgnoreCase(link.optString("rel", ""))) {
					next = resolve(base, link.optString("href", "")); break;
				}
			}
			JSONArray navigationArray = root.optJSONArray("navigation");
			if (navigationArray != null) for (int index = 0; index < navigationArray.length(); index++) {
				JSONObject item = navigationArray.optJSONObject(index);
				if (item == null) continue;
				String href = item.optString("href", "");
				if (!href.isEmpty()) navigation.add(resolve(base, href));
			}
			return new ParsedFeed(entries, next, navigation);
		} catch (Exception error) {
			if (error instanceof IOException io) throw io;
			throw new IOException("Could not parse OPDS JSON catalog", error);
		}
	}

	private InputStream fetch(String endpoint, AudiobookCredential credential) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS); connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("Accept", "application/atom+xml, application/opds+json, " +
				"application/rss+xml, application/xml, text/xml");
		connection.setRequestProperty("Accept-Encoding", "gzip");
		connection.setRequestProperty("User-Agent", "FermataX/" + BuildConfig.VERSION_NAME);
		String authorization = credential.authorization();
		if (!authorization.isEmpty()) connection.setRequestProperty("Authorization", authorization);
		int status = connection.getResponseCode();
		if ((status < 200) || (status >= 300)) {
			connection.disconnect(); throw new IOException("OPDS catalog returned HTTP " + status);
		}
		long length = connection.getContentLengthLong();
		if (length > MAX_XML_BYTES) {
			connection.disconnect(); throw new IOException("OPDS feed is too large");
		}
		InputStream stream = new BufferedInputStream(connection.getInputStream());
		if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) stream = new GZIPInputStream(stream);
		return new BoundedInputStream(stream, MAX_XML_BYTES, connection);
	}

	private AudiobookCredential requireCredential(AudiobookSource source) throws IOException {
		if (source.getCredentialRef() == null) {
			return new AudiobookCredential(source.getEndpoint(), "", "", null, null);
		}
		AudiobookCredential credential = credentials.load(source.getCredentialRef());
		if (credential == null) throw new IOException("OPDS credentials are unavailable");
		return credential;
	}

	private static AudiobookCredential credential(String endpoint, String username, String password,
			String bearerToken) {
		String token = (bearerToken == null) ? "" : bearerToken.trim();
		return new AudiobookCredential(endpoint, (username == null) ? "" : username.trim(), token,
				null, (password == null) ? null : password);
	}

	private static AudiobookSource source(String endpoint, String username, String reference, long now) {
		String host = URI.create(endpoint).getHost();
		return new AudiobookSource(AudiobookIds.source("opds", endpoint + '\n' + username),
				AudiobookSourceType.OPDS, "OPDS - " + ((host == null) ? endpoint : host),
				endpoint, reference, now, now);
	}

	static String normalizeEndpoint(String endpoint) throws IOException {
		try {
			URI uri = new URI(endpoint == null ? "" : endpoint.trim());
			String scheme = uri.getScheme();
			if ((scheme == null) || (!scheme.equalsIgnoreCase("http") &&
					!scheme.equalsIgnoreCase("https")) || uri.getHost() == null ||
					uri.getFragment() != null) {
				throw new IOException("Enter a valid OPDS HTTP or HTTPS URL");
			}
			String path = uri.getPath();
			if ((path == null) || path.equals("/")) path = "";
			else while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
			return new URI(scheme.toLowerCase(Locale.ROOT), uri.getUserInfo(),
					uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(), path, uri.getQuery(), null).toString();
		} catch (URISyntaxException error) {
			throw new IOException("Enter a valid OPDS HTTP or HTTPS URL", error);
		}
	}

	static OpdsCatalogSnapshot parseFixture(String content, String base, AudiobookSource source)
			throws IOException {
		ParsedFeed feed = parse(new java.io.ByteArrayInputStream(
				content.getBytes(StandardCharsets.UTF_8)), base, source);
		return new OpdsCatalogSnapshot(source, feed.entries());
	}

	private static String author(Object value) {
		if (value instanceof JSONObject object) return object.optString("name", "").trim();
		if (value instanceof JSONArray array) return author(array.opt(0));
		return (value == null) ? "" : String.valueOf(value).trim();
	}

	private static String image(Object value) {
		if (value instanceof JSONObject object) {
			for (String key : new String[]{"cover", "thumbnail", "default"}) {
				String image = object.optString(key, "").trim();
				if (!image.isEmpty()) return image;
			}
		}
		return (value instanceof String) ? ((String) value).trim() : "";
	}

	private static String readAll(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[16 * 1024];
		for (int read; (read = input.read(buffer)) != -1; ) {
			if (output.size() + read > MAX_XML_BYTES) throw new IOException("OPDS feed is too large");
			output.write(buffer, 0, read);
		}
		return output.toString(StandardCharsets.UTF_8);
	}

	private static boolean isTextElement(String name) {
		return name.equals("title") || name.equals("id") || name.equals("name") ||
				name.equals("summary") || name.equals("content") || name.equals("description") ||
				name.equals("language");
	}

	private static boolean isNavigationLink(String rel, String type) {
		String relation = rel == null ? "" : rel.toLowerCase(Locale.ROOT);
		String mediaType = type == null ? "" : type.toLowerCase(Locale.ROOT);
		return relation.contains("subsection") || relation.contains("navigation") ||
				mediaType.contains("atom") || mediaType.contains("opds");
	}

	private static String local(String name) {
		int colon = name.indexOf(':'); return (colon < 0) ? name : name.substring(colon + 1);
	}

	private static String resolve(String base, String href) throws IOException {
		try { return new URL(new URL(base), href).toString(); }
		catch (Exception error) { throw new IOException("Invalid OPDS link", error); }
	}

	private static List<String> navigation(List<RawEntry> entries) {
		List<String> result = new ArrayList<>();
		for (RawEntry entry : entries) result.addAll(entry.navigation);
		return result;
	}

	private record ParsedFeed(List<OpdsCatalogSnapshot.Entry> entries, String next,
			List<String> navigation) { }
	private record Acquisition(String url, String type) { }

	private static final class RawEntry {
		String id = "", title = "", author = "", description = "", artwork = "", language = "";
		final List<Acquisition> acquisitions = new ArrayList<>();
		final List<String> navigation = new ArrayList<>();
		void text(String element, String value) {
			String text = value == null ? "" : value.trim();
			if (element.equals("id")) id = text;
			else if (element.equals("title")) title = text;
			else if (element.equals("name")) author = text;
			else if (element.equals("summary") || element.equals("content") || element.equals("description")) description = text;
			else if (element.equals("language")) language = text;
		}
		void link(String url, String rel, String type) {
			String mediaType = type == null ? "" : type.toLowerCase(Locale.ROOT);
			String relation = rel == null ? "" : rel.toLowerCase(Locale.ROOT);
			if (relation.contains("cover") || relation.contains("thumbnail")) { artwork = url; return; }
			if (relation.equals("next")) return;
			if (relation.contains("acquisition") || relation.equals("enclosure") || mediaType.startsWith("audio/")) {
				acquisitions.add(new Acquisition(url, mediaType));
			} else if (relation.contains("subsection") || relation.contains("navigation") ||
					mediaType.contains("atom") || mediaType.contains("opds")) {
				navigation.add(url);
			}
		}
	}

	private static final class BoundedInputStream extends InputStream {
		private final InputStream input; private final int max; private final HttpURLConnection connection; private int total;
		BoundedInputStream(InputStream input, int max, HttpURLConnection connection) { this.input = input; this.max = max; this.connection = connection; }
		@Override public int read() throws IOException { int value = input.read(); if (value >= 0 && ++total > max) throw new IOException("OPDS feed is too large"); return value; }
		@Override public int read(byte[] buffer, int offset, int length) throws IOException { int value = input.read(buffer, offset, length); if (value > 0 && (total += value) > max) throw new IOException("OPDS feed is too large"); return value; }
		@Override public void close() throws IOException { try { input.close(); } finally { connection.disconnect(); } }
	}
}
