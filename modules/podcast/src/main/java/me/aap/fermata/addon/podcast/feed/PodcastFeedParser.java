package me.aap.fermata.addon.podcast.feed;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Comparator;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import me.aap.fermata.addon.podcast.model.PodcastEpisode;
import me.aap.fermata.addon.podcast.model.PodcastFeed;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastFeedParser {
	public static final int MAX_EPISODES = 2000;
	private static final int MAX_DEPTH = 64;
	private static final int MAX_TEXT = 64 * 1024;
	private static final Comparator<RetainedEpisode> EPISODE_AGE = Comparator
			.comparingLong((RetainedEpisode value) -> value.episode.getPublicationMs())
			.thenComparingLong(RetainedEpisode::order);
	public PodcastFeed parse(InputStream input, String baseUrl) throws IOException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			PodcastXmlSecurity.configure(factory);
			XMLReader reader = factory.newSAXParser().getXMLReader();
			PodcastXmlSecurity.configure(reader);
			Handler handler = new Handler(baseUrl);
			reader.setEntityResolver(handler);
			reader.setContentHandler(handler);
			reader.parse(new InputSource(PodcastXmlSecurity.guard(input)));
			return handler.build();
		} catch (ParserConfigurationException | SAXException error) {
			throw new IOException("Invalid or unsupported podcast feed", error);
		}
	}

	private static final class Handler extends DefaultHandler {
		private static final String ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd";
		private final String baseUrl;
		private final StringBuilder text = new StringBuilder(1024);
		private final Map<String, RetainedEpisode> episodes = new LinkedHashMap<>();
		private final PriorityQueue<RetainedEpisode> oldest = new PriorityQueue<>(EPISODE_AGE);
		private long episodeOrder;
		private PodcastEpisode.Builder episode;
		private TextTarget target = TextTarget.NONE;
		private int targetDepth;
		private int depth;
		private boolean rss;
		private boolean atom;
		private String title = "";
		private String author = "";
		private String description = "";
		private String artworkUrl = "";
		private String websiteUrl = "";
		private String selfUrl = "";
		private String language = "";
		private boolean explicit;

		Handler(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		@Override
		public InputSource resolveEntity(String publicId, String systemId) {
			return new InputSource(new StringReader(""));
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			if (++depth > MAX_DEPTH) throw new SAXException("Podcast feed nesting is too deep");
			String name = name(localName, qName);
			if (depth == 1) {
				rss = "rss".equals(name) || "rdf".equals(name);
				atom = "feed".equals(name);
				if (!rss && !atom) throw new SAXException("Expected RSS or Atom feed");
			}

			if ((rss && "item".equals(name)) || (atom && "entry".equals(name))) {
				episode = new PodcastEpisode.Builder();
				clearTarget();
				return;
			}

			if ((episode != null) && "enclosure".equals(name)) {
				setMedia(attributes, "url", "type", "length");
				return;
			}
			if (atom && "link".equals(name)) {
				String rel = attr(attributes, "rel");
				String href = resolve(attr(attributes, "href"));
				if (episode != null) {
					if ("enclosure".equalsIgnoreCase(rel)) {
						episode.mediaUrl = href;
						episode.mimeType = attr(attributes, "type");
						episode.mediaLength = number(attr(attributes, "length"), -1);
					} else if ((rel.isEmpty() || "alternate".equalsIgnoreCase(rel)) &&
							episode.permalink == null) {
						episode.permalink = href;
					}
				} else if ("self".equalsIgnoreCase(rel)) {
					selfUrl = href;
				} else if (rel.isEmpty() || "alternate".equalsIgnoreCase(rel)) {
					websiteUrl = href;
				}
				return;
			}

			if ("image".equals(name) && ITUNES.equals(uri)) {
				String href = resolve(attr(attributes, "href"));
				if (episode == null) artworkUrl = prefer(artworkUrl, href);
				else episode.artworkUrl = prefer(episode.artworkUrl, href);
				return;
			}
			if ("thumbnail".equals(name)) {
				String url = resolve(attr(attributes, "url"));
				if (episode == null) artworkUrl = prefer(artworkUrl, url);
				else episode.artworkUrl = prefer(episode.artworkUrl, url);
				return;
			}
			if ("content".equals(name) && (episode != null) && !attr(attributes, "url").isEmpty()) {
				setMedia(attributes, "url", "type", "fileSize");
				return;
			}

			TextTarget next = target(name, uri);
			if (next != TextTarget.NONE) beginText(next);
		}

		@Override
		public void characters(char[] chars, int start, int length) {
			if ((target == TextTarget.NONE) || (targetDepth > depth) || (text.length() >= MAX_TEXT)) return;
			text.append(chars, start, Math.min(length, MAX_TEXT - text.length()));
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			String name = name(localName, qName);
			if ((target != TextTarget.NONE) && (targetDepth == depth)) commitText();
			if ((episode != null) && ((rss && "item".equals(name)) ||
					(atom && "entry".equals(name)))) addEpisode();
			depth--;
		}

		PodcastFeed build() throws SAXException {
			if (!rss && !atom) throw new SAXException("Expected RSS or Atom feed");
			List<PodcastEpisode> list = new ArrayList<>(episodes.size());
			for (RetainedEpisode value : episodes.values()) list.add(value.episode);
			if (title.isEmpty() && list.isEmpty()) throw new SAXException("Podcast feed is empty");
			return new PodcastFeed(title, author, description, artworkUrl, websiteUrl, selfUrl,
					language, explicit, list);
		}

		private TextTarget target(String name, String uri) {
			if (episode != null) {
				return switch (name) {
					case "title" -> TextTarget.EPISODE_TITLE;
					case "guid", "id" -> TextTarget.EPISODE_GUID;
					case "description", "summary", "content", "encoded" ->
							TextTarget.EPISODE_DESCRIPTION;
					case "author", "creator", "name" -> TextTarget.EPISODE_AUTHOR;
					case "link" -> rss ? TextTarget.EPISODE_LINK : TextTarget.NONE;
					case "pubDate", "published", "updated" -> TextTarget.EPISODE_DATE;
					case "duration" -> TextTarget.EPISODE_DURATION;
					case "season" -> TextTarget.EPISODE_SEASON;
					case "episode" -> TextTarget.EPISODE_NUMBER;
					case "explicit" -> TextTarget.EPISODE_EXPLICIT;
					default -> TextTarget.NONE;
				};
			}
			return switch (name) {
				case "title" -> TextTarget.FEED_TITLE;
				case "description", "subtitle" -> TextTarget.FEED_DESCRIPTION;
				case "author", "managingEditor", "name" -> TextTarget.FEED_AUTHOR;
				case "language" -> TextTarget.FEED_LANGUAGE;
				case "link" -> rss ? TextTarget.FEED_LINK : TextTarget.NONE;
				case "url" -> TextTarget.FEED_IMAGE;
				case "explicit" -> TextTarget.FEED_EXPLICIT;
				default -> TextTarget.NONE;
			};
		}

		private void beginText(TextTarget next) {
			target = next;
			targetDepth = depth;
			text.setLength(0);
		}

		private void clearTarget() {
			target = TextTarget.NONE;
			targetDepth = 0;
			text.setLength(0);
		}

		private void commitText() {
			String value = text.toString().trim();
			switch (target) {
				case FEED_TITLE -> title = prefer(title, value);
				case FEED_DESCRIPTION -> description = longer(description, value);
				case FEED_AUTHOR -> author = prefer(author, value);
				case FEED_LANGUAGE -> language = prefer(language, value);
				case FEED_LINK -> websiteUrl = prefer(websiteUrl, resolve(value));
				case FEED_IMAGE -> artworkUrl = prefer(artworkUrl, resolve(value));
				case FEED_EXPLICIT -> explicit = bool(value);
				case EPISODE_TITLE -> episode.title = prefer(episode.title, value);
				case EPISODE_GUID -> episode.guid = prefer(episode.guid, value);
				case EPISODE_DESCRIPTION -> episode.description = longer(episode.description, value);
				case EPISODE_AUTHOR -> episode.author = prefer(episode.author, value);
				case EPISODE_LINK -> episode.permalink = prefer(episode.permalink, resolve(value));
				case EPISODE_DATE -> {
					if (episode.publicationMs == 0) episode.publicationMs = date(value);
				}
				case EPISODE_DURATION -> episode.durationMs = duration(value);
				case EPISODE_SEASON -> episode.seasonNumber = integer(value);
				case EPISODE_NUMBER -> episode.episodeNumber = integer(value);
				case EPISODE_EXPLICIT -> episode.explicit = bool(value);
				case NONE -> { }
			}
			clearTarget();
		}

		private void addEpisode() {
			PodcastEpisode built = PodcastEpisode.build(episode);
			episode = null;
			clearTarget();
			if ((built == null) || !built.isPlayable() || episodes.containsKey(built.getKey())) return;
			RetainedEpisode retained = new RetainedEpisode(built, episodeOrder++);
			if (episodes.size() < MAX_EPISODES) {
				episodes.put(built.getKey(), retained);
				oldest.add(retained);
				return;
			}
			RetainedEpisode first = oldest.peek();
			if ((built.getPublicationMs() <= 0) || ((first != null) &&
					(EPISODE_AGE.compare(retained, first) <= 0))) return;
			if (first != null) {
				oldest.remove();
				episodes.remove(first.episode.getKey());
			}
			episodes.put(built.getKey(), retained);
			oldest.add(retained);
		}

		private void setMedia(Attributes attributes, String urlName, String typeName,
				String lengthName) {
			String url = resolve(attr(attributes, urlName));
			if ((episode.mediaUrl == null) || episode.mediaUrl.isEmpty()) episode.mediaUrl = url;
			String type = attr(attributes, typeName);
			if ((episode.mimeType == null) || episode.mimeType.isEmpty()) episode.mimeType = type;
			long length = number(attr(attributes, lengthName), -1);
			if (episode.mediaLength < 0) episode.mediaLength = length;
		}

		private String resolve(String value) {
			if ((value == null) || (value = value.trim()).isEmpty()) return "";
			try {
				URI resolved = (baseUrl == null) ? new URI(value) : new URI(baseUrl).resolve(value);
				String normalized = PodcastUrls.normalizeHttpUrl(resolved.toString());
				return (normalized == null) ? "" : normalized;
			} catch (URISyntaxException | IllegalArgumentException error) {
				return "";
			}
		}
	}

	private record RetainedEpisode(PodcastEpisode episode, long order) {
	}

	private static String name(String localName, String qName) {
		String name = ((localName == null) || localName.isEmpty()) ? qName : localName;
		int colon = (name == null) ? -1 : name.indexOf(':');
		if (colon != -1) name = name.substring(colon + 1);
		return (name == null) ? "" : name;
	}

	private static String attr(Attributes attributes, String name) {
		String value = attributes.getValue(name);
		if (value != null) return value.trim();
		for (int i = 0; i < attributes.getLength(); i++) {
			String local = attributes.getLocalName(i);
			String qName = attributes.getQName(i);
			if (name.equals(local) || name.equals(qName)) return attributes.getValue(i).trim();
		}
		return "";
	}

	private static String prefer(String current, String candidate) {
		return ((current == null) || current.isEmpty()) ? ((candidate == null) ? "" : candidate) : current;
	}

	private static String longer(String current, String candidate) {
		if (candidate == null) return (current == null) ? "" : current;
		return ((current == null) || (candidate.length() > current.length())) ? candidate : current;
	}

	private static boolean bool(String value) {
		return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) ||
				"explicit".equalsIgnoreCase(value) || "1".equals(value);
	}

	private static int integer(String value) {
		try {
			return Math.max((int) Double.parseDouble(value), 0);
		} catch (NumberFormatException error) {
			return 0;
		}
	}

	private static long number(String value, long fallback) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException error) {
			return fallback;
		}
	}

	static long duration(String value) {
		if ((value == null) || (value = value.trim()).isEmpty()) return -1;
		try {
			String[] fields = value.split(":");
			double seconds = 0;
			for (String field : fields) seconds = (seconds * 60) + Double.parseDouble(field);
			return (seconds < 0) ? -1 : (long) (seconds * 1000);
		} catch (NumberFormatException error) {
			return -1;
		}
	}

	static long date(String value) {
		if ((value == null) || (value = value.trim()).isEmpty()) return 0;
		try {
			return Instant.parse(value).toEpochMilli();
		} catch (DateTimeParseException ignore) {
		}
		try {
			return OffsetDateTime.parse(value).toInstant().toEpochMilli();
		} catch (DateTimeParseException ignore) {
		}
		try {
			return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
					.toInstant().toEpochMilli();
		} catch (DateTimeParseException ignore) {
		}
		for (String pattern : List.of("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z")) {
			try {
				java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern, Locale.US);
				format.setLenient(true);
				java.util.Date parsed = format.parse(value);
				if (parsed != null) return parsed.getTime();
			} catch (java.text.ParseException ignore) {
			}
		}
		return 0;
	}

	private enum TextTarget {
		NONE,
		FEED_TITLE,
		FEED_DESCRIPTION,
		FEED_AUTHOR,
		FEED_LANGUAGE,
		FEED_LINK,
		FEED_IMAGE,
		FEED_EXPLICIT,
		EPISODE_TITLE,
		EPISODE_GUID,
		EPISODE_DESCRIPTION,
		EPISODE_AUTHOR,
		EPISODE_LINK,
		EPISODE_DATE,
		EPISODE_DURATION,
		EPISODE_SEASON,
		EPISODE_NUMBER,
		EPISODE_EXPLICIT
	}
}
