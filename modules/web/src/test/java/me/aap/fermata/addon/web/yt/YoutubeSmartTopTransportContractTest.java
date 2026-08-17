package me.aap.fermata.addon.web.yt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks SmartTop/media-session Previous/Next routing for persisted YouTube history items. */
public class YoutubeSmartTopTransportContractTest {
	@Test
	public void persistedYoutubeItemsExposeStableTransportCommands() throws Exception {
		String engine = source();
		int playable = engine.indexOf("static class YoutubePlayableItem");
		int transport = engine.indexOf("private static class TransportItem", playable);
		String body = engine.substring(playable, transport);
		assertTrue(body.contains("return completed(transportItem(PREV_ID, getParent()))"));
		assertTrue(body.contains("return completed(transportItem(NEXT_ID, getParent()))"));
	}

	@Test
	public void engineAcceptsEquivalentTransportIdsAndDispatchesToWebPlayer() throws Exception {
		String engine = source();
		int prepare = engine.indexOf("public void prepare(PlayableItem source)");
		int start = engine.indexOf("public void start()", prepare);
		String body = engine.substring(prepare, start);
		assertTrue(body.contains("String sourceId = source.getOrigId()"));
		assertTrue(body.contains("NEXT_ID.equals(sourceId)"));
		assertTrue(body.contains("PREV_ID.equals(sourceId)"));
		assertTrue(body.contains("web.next()"));
		assertTrue(body.contains("web.prev()"));
	}

	private static String source() throws Exception {
		Path root = repositoryRoot();
		return new String(Files.readAllBytes(root.resolve(
				"modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeMediaEngine.java")), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		for (int i = 0; (i < 5) && (current != null); i++, current = current.getParent()) {
			if (Files.isDirectory(current.resolve("modules/web/src/main"))) return current;
		}
		throw new AssertionError("Unable to locate repository");
	}
}
