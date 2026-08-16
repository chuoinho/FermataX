package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class VideoViewportAuthorityContractTest {
	@Test
	public void surfaceWaitPreservesTheOriginalPreflightTransaction() throws Exception {
		String body = coreSource("ui/view/BodyLayout.java");
		assertTrue(body.contains("startPlaybackRequest(i, originalMode, requestedMode)"));
		assertTrue(body.contains("PlaybackLayoutPolicy.getModeAfterRejectedPlayRequest("));
		assertFalse(body.contains("onSurfaceCreated(() -> playItem(i))"));
	}

	@Test
	public void rejectedRequestCannotOverwriteAConcurrentModeChange() throws Exception {
		String body = coreSource("ui/view/BodyLayout.java");
		assertTrue(body.contains("currentVideoModeRequired || (getMode() == requestedMode)"));
	}

	private static String coreSource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
