package me.aap.fermata.addon.radio;

import android.util.JsonReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

final class RadioHttp {
	private RadioHttp() {
	}

	static <T> T request(HttpURLConnection connection, StatusExceptionFactory statusException,
									 Parser<T> parser) throws IOException {
		try {
			int code = connection.getResponseCode();
			if ((code < 200) || (code >= 300)) throw statusException.create(code);

			try (InputStream in = decode(connection, connection.getInputStream());
					 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
					 JsonReader json = new JsonReader(reader)) {
				return parser.parse(json);
			}
		} finally {
			connection.disconnect();
		}
	}

	private static InputStream decode(HttpURLConnection connection, InputStream input)
			throws IOException {
		InputStream in = new BufferedInputStream(input);
		String encoding = connection.getContentEncoding();
		if ("gzip".equalsIgnoreCase(encoding)) return new GZIPInputStream(in);
		if ("deflate".equalsIgnoreCase(encoding)) return new InflaterInputStream(in);
		return in;
	}

	interface Parser<T> {
		T parse(JsonReader reader) throws IOException;
	}

	interface StatusExceptionFactory {
		IOException create(int statusCode);
	}
}
