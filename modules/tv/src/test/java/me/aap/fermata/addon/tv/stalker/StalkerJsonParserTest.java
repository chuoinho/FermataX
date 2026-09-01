package me.aap.fermata.addon.tv.stalker;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class StalkerJsonParserTest {
	private final StalkerJsonParser parser = new StalkerJsonParser();

	@Test
	public void parsesHandshakeCategoriesAndChannels() throws Exception {
		assertEquals("secret-token", parser.parseToken(json(
				"{\"js\":{\"token\":\"secret-token\",\"random\":\"x\"}}")));

		List<StalkerCategory> categories = parser.parseCategories(json(
				"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}"));
		assertEquals(List.of(new StalkerCategory("1", "News")), categories);

		List<StalkerChannel> channels = parser.parseChannels(json(
				"{\"js\":{\"data\":[{\"id\":\"10\",\"name\":\"World\",\"logo\":\"https://img.invalid/10.png\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg http://stream.invalid/live/10\"}]}}"));
		assertEquals(1, channels.size());
		assertEquals("10", channels.get(0).id());
		assertEquals("1", channels.get(0).categoryId());
	}

	@Test
	public void parsesCreateLinkAndExtendedHeaders() throws Exception {
		StalkerPlaybackLink link = parser.parseLink(json(
				"{\"js\":{\"cmd\":\"ffmpeg https://cdn.invalid/live.m3u8|User-Agent=Portal%20Agent&Referer=https%3A%2F%2Fportal.invalid%2Fc%2F\"}}"),
				Map.of("Cookie", "mac=redacted"));

		assertEquals("https://cdn.invalid/live.m3u8", link.uri().toString());
		assertEquals("Portal Agent", link.headers().get("User-Agent"));
		assertEquals("https://portal.invalid/c/", link.headers().get("Referer"));
		assertEquals("mac=redacted", link.headers().get("Cookie"));
	}

	@Test
	public void rejectsHtmlAndNonHttpPlayback() {
		assertThrows(IOException.class, () -> parser.parseToken(json("<html>error</html>")));
		assertThrows(IOException.class, () -> StalkerJsonParser.parseCommand(
				"ffmpeg udp://239.0.0.1:1234", Map.of()));
	}

	private static ByteArrayInputStream json(String value) {
		return new ByteArrayInputStream(value.getBytes(UTF_8));
	}
}
