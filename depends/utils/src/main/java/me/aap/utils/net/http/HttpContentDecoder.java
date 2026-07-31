package me.aap.utils.net.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Decodes the content encodings used by the app's URL-connection clients. */
public final class HttpContentDecoder {
	private HttpContentDecoder() {
	}

	public static InputStream decodeBuffered(HttpURLConnection connection, InputStream source)
			throws IOException {
		return decode(new BufferedInputStream(source), connection.getContentEncoding());
	}

	public static InputStream decode(InputStream source, CharSequence encoding) throws IOException {
		if (equalsIgnoreCase("gzip", encoding)) return new GZIPInputStream(source);
		if (equalsIgnoreCase("deflate", encoding)) return new InflaterInputStream(source);
		return source;
	}

	private static boolean equalsIgnoreCase(String expected, CharSequence actual) {
		return (actual != null) && expected.equalsIgnoreCase(actual.toString());
	}
}
