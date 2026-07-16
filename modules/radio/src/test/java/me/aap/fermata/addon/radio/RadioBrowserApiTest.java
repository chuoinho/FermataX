package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;

public class RadioBrowserApiTest {
	@Test
	public void disconnectsConnectionOnHttpFailure() throws Exception {
		FakeConnection connection = new FakeConnection(500, "[]");

		assertThrows(IOException.class,
				() -> RadioHttp.request(connection, code -> new IOException("HTTP " + code),
						reader -> null));

		assertTrue(connection.disconnected);
	}

	@Test
	public void disconnectsConnectionWhenParserFails() throws Exception {
		FakeConnection connection = new FakeConnection(200, "[]");

		assertThrows(IOException.class, () -> RadioHttp.request(connection,
				code -> new IOException("HTTP " + code), reader -> {
			throw new IOException("parser failed");
		}));

		assertTrue(connection.disconnected);
	}

	private static final class FakeConnection extends HttpURLConnection {
		private final int responseCode;
		private final byte[] body;
		private boolean disconnected;

		FakeConnection(int responseCode, String body) throws Exception {
			super(new URL("https://example.test"));
			this.responseCode = responseCode;
			this.body = body.getBytes();
		}

		@Override
		public int getResponseCode() {
			return responseCode;
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(body);
		}

		@Override
		public void disconnect() {
			disconnected = true;
		}

		@Override
		public boolean usingProxy() {
			return false;
		}

		@Override
		public void connect() {
		}
	}
}
