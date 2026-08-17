package me.aap.fermata.addon.web.yt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class YoutubeRuntimeQaContractTest {
	@Test
	public void configuredScaleWinsFallbackImmersiveRule() {
		String cover = YoutubeVideoScaleController.script(YoutubeAddon.VideoScale.COVER);
		assertTrue(cover.contains("html.fermata-yt-immersive body video"));
		assertTrue(cover.contains("object-fit:cover !important"));
		assertFalse(cover.contains("object-fit:contain !important"));
	}

	@Test
	public void fallbackAppliesConfiguredScaleBeforeImmersiveClass() throws Exception {
		String source = read("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeFullscreenHostAdapter.java");
		int start = source.indexOf("public void enterFallbackVideoMode()");
		int end = source.indexOf("public void leaveAppVideoMode()", start);
		String body = source.substring(start, end);
		assertTrue(body.indexOf("YoutubeVideoScaleController.apply") < body.indexOf("setImmersiveVideoMode(true)"));
	}

	@Test
	public void playbackDescriptorMirrorsIntoSharedRecent() throws Exception {
		String source = read("modules/web/src/main/java/me/aap/fermata/addon/web/yt/YoutubeAddon.java");
		int start = source.indexOf("void rememberYoutubeItem");
		int end = source.indexOf("void updateYoutubeItem", start);
		String body = source.substring(start, end);
		assertTrue(body.contains("YoutubeItem played = item.playedAt"));
		assertTrue(body.contains("storeYoutubeItem(played)"));
		assertTrue(body.contains("YoutubeRecentSync.add(currentActivity(), this, played)"));
	}

	private static String read(String path) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(path)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		for (int i = 0; (i < 5) && (current != null); i++, current = current.getParent()) {
			if (Files.isDirectory(current.resolve("modules/web/src/main"))) return current;
		}
		throw new AssertionError("Unable to locate repository");
	}
}
