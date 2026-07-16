package me.aap.fermata.addon.podcast.feed;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

final class PodcastXmlSecurity {
	private static final byte[] DOCTYPE = "<!DOCTYPE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	private static final String ACCESS_EXTERNAL_DTD =
			"http://javax.xml.XMLConstants/property/accessExternalDTD";
	private static final String ACCESS_EXTERNAL_SCHEMA =
			"http://javax.xml.XMLConstants/property/accessExternalSchema";

	private PodcastXmlSecurity() {
	}

	static void configure(SAXParserFactory factory) {
		factory.setNamespaceAware(true);
		try {
			factory.setXIncludeAware(false);
		} catch (UnsupportedOperationException ignored) {
		}
		feature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
		feature(factory, "http://xml.org/sax/features/external-general-entities", false);
		feature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
		feature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	}

	static void configure(XMLReader reader) {
		property(reader, ACCESS_EXTERNAL_DTD, "");
		property(reader, ACCESS_EXTERNAL_SCHEMA, "");
	}

	static InputStream guard(InputStream source) throws IOException {
		PushbackInputStream input = new PushbackInputStream(source, 3);
		byte[] prefix = new byte[3];
		int count = 0;
		while (count < prefix.length) {
			int read = input.read(prefix, count, prefix.length - count);
			if (read == -1) break;
			count += read;
		}
		if ((count >= 2) && ((((prefix[0] & 0xFF) == 0xFF) &&
				((prefix[1] & 0xFF) == 0xFE)) || (((prefix[0] & 0xFF) == 0xFE) &&
				((prefix[1] & 0xFF) == 0xFF)))) {
			throw new IOException("UTF-16 podcast XML is not supported");
		}
		if (count > 0) input.unread(prefix, 0, count);
		return new DoctypeGuard(input);
	}

	private static void feature(SAXParserFactory factory, String name, boolean value) {
		try {
			factory.setFeature(name, value);
		} catch (ParserConfigurationException | SAXNotRecognizedException |
				SAXNotSupportedException ignored) {
			// The stream guard and entity resolver remain the security boundary on older Android.
		}
	}

	private static void property(XMLReader reader, String name, String value) {
		try {
			reader.setProperty(name, value);
		} catch (SAXNotRecognizedException | SAXNotSupportedException ignored) {
		}
	}

	private static final class DoctypeGuard extends FilterInputStream {
		private int matched;

		DoctypeGuard(InputStream input) {
			super(input);
		}

		@Override
		public int read() throws IOException {
			int value = super.read();
			if (value != -1) inspect(value);
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = super.read(buffer, offset, length);
			for (int i = 0; i < read; i++) inspect(buffer[offset + i] & 0xFF);
			return read;
		}

		private void inspect(int value) throws IOException {
			int upper = ((value >= 'a') && (value <= 'z')) ? value - ('a' - 'A') : value;
			if (upper == DOCTYPE[matched]) {
				if (++matched == DOCTYPE.length) throw new IOException(
						"Podcast XML document type declarations are not allowed");
			} else {
				matched = (upper == DOCTYPE[0]) ? 1 : 0;
			}
		}
	}
}
