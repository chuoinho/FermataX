package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

public class HttpContentDecoderTest {
	private static final byte[] CONTENT = "FermataX content".getBytes(UTF_8);

	@Test
	public void identityReturnsOriginalStream() throws Exception {
		InputStream source = new ByteArrayInputStream(CONTENT);

		assertSame(source, HttpContentDecoder.decode(source, null));
		assertSame(source, HttpContentDecoder.decode(source, "identity"));
	}

	@Test
	public void decodesGzipAndDeflate() throws Exception {
		assertArrayEquals(CONTENT, HttpContentDecoder.decode(
				new ByteArrayInputStream(compress(true)), "GZIP").readAllBytes());
		assertArrayEquals(CONTENT, HttpContentDecoder.decode(
				new ByteArrayInputStream(compress(false)), "deflate").readAllBytes());
	}

	@Test
	public void connectionVariantAlwaysBuffers() throws Exception {
		InputStream source = new ByteArrayInputStream(CONTENT);
		InputStream decoded = HttpContentDecoder.decodeBuffered(connection("identity"), source);

		assertNotSame(source, decoded);
		assertArrayEquals(CONTENT, decoded.readAllBytes());
	}

	private static byte[] compress(boolean gzip) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (var compressed = gzip ? new GZIPOutputStream(output) :
				new DeflaterOutputStream(output)) {
			compressed.write(CONTENT);
		}
		return output.toByteArray();
	}

	private static HttpURLConnection connection(String encoding) throws Exception {
		return new HttpURLConnection(new URL("http://localhost")) {
			@Override public String getContentEncoding() {
				return encoding;
			}

			@Override public void disconnect() {
			}

			@Override public boolean usingProxy() {
				return false;
			}

			@Override public void connect() {
			}
		};
	}
}
