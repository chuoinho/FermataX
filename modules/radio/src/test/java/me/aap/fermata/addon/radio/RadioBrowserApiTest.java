package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.util.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RadioBrowserApiTest {

	@Test
	public void paginationUrlsAreCorrect() {
		String countryPath = RadioBrowserApi.countryStationsPath("VN", 100, 100);
		assertTrue(countryPath.contains("offset=100"));
		assertTrue(countryPath.contains("limit=100"));
		assertTrue(countryPath.contains("countrycode=VN"));
		assertTrue(countryPath.contains("hidebroken=true"));

		String tagPath = RadioBrowserApi.tagStationsPath("jazz", 200, 100);
		assertTrue(tagPath.contains("offset=200"));
		assertTrue(tagPath.contains("limit=100"));
		assertTrue(tagPath.contains("tagList=jazz"));

		String langPath = RadioBrowserApi.languageStationsPath("vietnamese", 50, 100);
		assertTrue(langPath.contains("offset=50"));
		assertTrue(langPath.contains("limit=100"));
		assertTrue(langPath.contains("language=vietnamese"));
	}

	@Test
	public void languagesUrlIsCorrect() {
		assertEquals("languages?hidebroken=true&order=stationcount&reverse=true",
				RadioBrowserApi.languagesPath());
	}

	@Test
	public void searchByNamePathAlwaysHasLimit50() {
		String path = RadioBrowserApi.searchByNamePath("Hà Nội Radio & 100%", 0, 50);
		assertTrue(path.startsWith("stations/search?hidebroken=true&order=clickcount&reverse=true&offset=0&limit=50&name="));
		assertTrue(path.contains("limit=50"));
		assertTrue(!path.contains(" "));
		assertTrue(path.contains("%26"));
	}

	@Test
	public void parsesLanguageDirectoryJson() throws IOException {
		String json = "[" +
				"{\"name\":\"vietnamese\",\"iso_639\":\"vi\",\"stationcount\":42}," +
				"{\"name\":\"empty\",\"iso_639\":\"em\",\"stationcount\":0}" +
				"]";
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			List<RadioBrowserApi.DirectoryEntry> entries =
					RadioBrowserApi.readDirectories(reader, 0, false, true, 1);
			assertEquals(1, entries.size());
			RadioBrowserApi.DirectoryEntry e = entries.get(0);
			assertEquals("vietnamese", e.name);
			assertEquals("vi", e.code);
			assertEquals(42, e.stationCount);
		}
	}
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
