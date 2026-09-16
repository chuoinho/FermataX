package me.aap.fermata.media.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class FermataServiceUiBinderTest {
	@Test
	public void playbackErrorAlwaysHasDisplayableText() {
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError(null, "fallback"));
		assertEquals("fallback", FermataServiceUiBinder.normalizePlaybackError("", "fallback"));
		assertEquals("network error",
				FermataServiceUiBinder.normalizePlaybackError("network error", "fallback"));
	}

	@Test
	public void selectingCurrentItemDoesNotCreateAnUnfinishablePlaybackRequest() {
		assertFalse(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, -1));
		assertFalse(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, 0));
		assertTrue(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, 1));
		assertTrue(FermataServiceUiBinder.shouldCreatePlaybackRequest(false, -1));
	}

	@Test
	public void delayedCallbackCannotFollowAReplacementPresentationHost() {
		Object oldCallback = new Object();
		Object currentCallback = new Object();

		assertTrue(FermataServiceUiBinder.ownsPresentationLease(true,
				currentCallback, currentCallback, true));
		assertFalse(FermataServiceUiBinder.ownsPresentationLease(true,
				currentCallback, oldCallback, true));
		assertFalse(FermataServiceUiBinder.ownsPresentationLease(false,
				currentCallback, currentCallback, true));
		assertFalse(FermataServiceUiBinder.ownsPresentationLease(true,
				currentCallback, currentCallback, false));
	}

	@Test
	public void controlAvailabilityRequiresTheCurrentPresentationLease() {
		Object oldCallback = new Object();
		Object currentCallback = new Object();

		assertTrue(FermataServiceUiBinder.isControlAvailable(true,
				currentCallback, currentCallback, true));
		assertFalse(FermataServiceUiBinder.isControlAvailable(false,
				currentCallback, currentCallback, true));
		assertFalse(FermataServiceUiBinder.isControlAvailable(true,
				currentCallback, oldCallback, true));
		assertFalse(FermataServiceUiBinder.isControlAvailable(true,
				currentCallback, currentCallback, false));
	}

	@Test
	public void audioErrorsKeepAPlayerBarForRetry() {
		assertTrue(FermataServiceUiBinder.shouldKeepPlayerBarOnError(true, false));
		assertFalse(FermataServiceUiBinder.shouldKeepPlayerBarOnError(true, true));
		assertFalse(FermataServiceUiBinder.shouldKeepPlayerBarOnError(false, false));
	}

	@Test
	public void controlFacadeForwardsOnlyToTheAuthoritativeMediaSession() throws Exception {
		String source = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata/media/service/FermataServiceUiBinder.java")), UTF_8);

		assertTrue(source.contains("public PlaybackSnapshot getPlaybackSnapshot()"));
		assertTrue(source.contains("return sessionCallback.getPlaybackSnapshot();"));
		assertTrue(source.contains("public void play()"));
		assertTrue(source.contains("mediaController.getTransportControls().play();"));
		assertTrue(source.contains("public void pause()"));
		assertTrue(source.contains("mediaController.getTransportControls().pause();"));
		assertTrue(source.contains("public void seekTo(long positionMillis)"));
		assertTrue(source.contains("seekTo(Math.max(0L, positionMillis));"));
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
