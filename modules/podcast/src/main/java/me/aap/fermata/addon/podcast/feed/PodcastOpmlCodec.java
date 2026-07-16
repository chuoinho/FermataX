package me.aap.fermata.addon.podcast.feed;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import me.aap.fermata.addon.podcast.model.PodcastOpmlEntry;
import me.aap.fermata.addon.podcast.security.PodcastUrlRedactor;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastOpmlCodec {
	public static final int MAX_ENTRIES = 5000;
	private static final int MAX_DEPTH = 64;
	public List<PodcastOpmlEntry> parse(InputStream input) throws IOException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			PodcastXmlSecurity.configure(factory);
			XMLReader reader = factory.newSAXParser().getXMLReader();
			PodcastXmlSecurity.configure(reader);
			Handler handler = new Handler();
			reader.setEntityResolver(handler);
			reader.setContentHandler(handler);
			reader.parse(new InputSource(PodcastXmlSecurity.guard(input)));
			return handler.result();
		} catch (ParserConfigurationException | SAXException ex) {
			throw new IOException("Invalid OPML podcast file", ex);
		}
	}

	public void write(List<PodcastOpmlEntry> entries, OutputStream output,
			boolean includePrivateTokenUrls) throws IOException {
		try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
			writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			writer.write("<opml version=\"2.0\"><head><title>FermataX Podcasts</title></head><body>\n");
			for (PodcastOpmlEntry entry : entries) {
				String normalized = PodcastUrls.normalizeHttpUrl(entry.feedUrl());
				if (normalized == null) continue;
				if (PodcastUrlRedactor.containsSecrets(normalized) && !includePrivateTokenUrls) continue;
				String exportUrl = removeUserInfo(normalized);
				if (exportUrl == null) continue;
				writer.write("  <outline type=\"rss\" text=\"");
				writer.write(escape(entry.title()));
				writer.write("\" xmlUrl=\"");
				writer.write(escape(exportUrl));
				writer.write("\"/>\n");
			}
			writer.write("</body></opml>\n");
		}
	}

	private static String removeUserInfo(String value) {
		try {
			java.net.URI uri = new java.net.URI(value);
			return PodcastUrls.composeHttpUrl(uri.getScheme(), null, uri.getHost(), uri.getPort(),
					uri.getRawPath(), uri.getRawQuery());
		} catch (java.net.URISyntaxException ex) {
			return null;
		}
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("\"", "&quot;")
				.replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String attr(Attributes attributes, String name) {
		String value = attributes.getValue(name);
		if (value != null) return value.trim();
		for (int i = 0; i < attributes.getLength(); i++) {
			if (name.equalsIgnoreCase(attributes.getLocalName(i)) ||
					name.equalsIgnoreCase(attributes.getQName(i))) {
				return attributes.getValue(i).trim();
			}
		}
		return "";
	}

	private static final class Handler extends DefaultHandler {
		private final Map<String, PodcastOpmlEntry> entries = new LinkedHashMap<>();
		private int depth;
		private boolean opml;

		@Override
		public InputSource resolveEntity(String publicId, String systemId) {
			return new InputSource(new StringReader(""));
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			if (++depth > MAX_DEPTH) throw new SAXException("OPML nesting is too deep");
			String name = ((localName == null) || localName.isEmpty()) ? qName : localName;
			if (depth == 1) {
				opml = "opml".equalsIgnoreCase(name);
				if (!opml) throw new SAXException("Expected OPML document");
			}
			if (!"outline".equalsIgnoreCase(name) || (entries.size() == MAX_ENTRIES)) return;
			String url = PodcastUrls.normalizeHttpUrl(attr(attributes, "xmlUrl"));
			if (url == null) return;
			String title = attr(attributes, "title");
			if (title.isEmpty()) title = attr(attributes, "text");
			entries.putIfAbsent(PodcastUrls.identity(url), new PodcastOpmlEntry(title, url));
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			depth--;
		}

		List<PodcastOpmlEntry> result() throws SAXException {
			if (!opml) throw new SAXException("Expected OPML document");
			return List.copyOf(entries.values());
		}
	}
}
