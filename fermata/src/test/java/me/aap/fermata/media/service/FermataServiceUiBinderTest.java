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
	@Test public void routedNativeResultRejectsUnavailableHostWithoutExecuting() {
		var admission = new me.aap.fermata.auto.OpenOnCarMediaRouting.Admission(
				me.aap.fermata.ui.policy.RuntimeHostMode.AA_PROJECTION, () -> true);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY,
				FermataServiceUiBinder.dispatchRoutedPlayback(admission, () -> false,
						() -> { throw new AssertionError("not-ready host must not dispatch"); }));
	}
	@Test public void routedNativeResultDoesNotClaimTerminalRejectionWasDispatched() {
		var admission = new me.aap.fermata.auto.OpenOnCarMediaRouting.Admission(
				me.aap.fermata.ui.policy.RuntimeHostMode.PHONE, () -> true);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY,
				FermataServiceUiBinder.dispatchRoutedPlayback(admission, () -> false, () -> false));
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.LOAD_DISPATCHED,
				FermataServiceUiBinder.dispatchRoutedPlayback(admission, () -> false, () -> true));
	}
	@Test public void castActivatedDuringReadinessWaitRejectsFinalNativeDispatch() {
		var connection = me.aap.fermata.auto.AutomotiveConnectionState.get();
		var controller = me.aap.fermata.auto.AutomotiveNavigationController.get();
		me.aap.fermata.auto.AutomotiveNavigationController.Navigator navigator =
				(id, guard) -> me.aap.utils.async.Completed.completed(
						me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.OPENED);
		connection.connectionChanged(true); controller.register(navigator);
		try {
			controller.getOpenOnCarMode().setEnabled(true);
			var admission = new me.aap.fermata.auto.OpenOnCarMediaRouting.Admission(
					me.aap.fermata.ui.policy.RuntimeHostMode.AA_PROJECTION, () -> true);
			var cast = new java.util.concurrent.atomic.AtomicBoolean(false);
			var readiness = new me.aap.utils.async.Promise<Boolean>();
			var result = readiness.map(ready -> FermataServiceUiBinder.dispatchRoutedPlayback(
					admission, cast::get, () -> { throw new AssertionError("Cast must not be taken over"); }));
			cast.set(true); readiness.complete(true);
			assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY, result.peek());
		} finally { controller.unregister(navigator); connection.connectionChanged(false); }
	}
	@Test public void nativeHostLossDuringDispatchReturnsCancelled() {
		var current = new java.util.concurrent.atomic.AtomicBoolean(true);
		var admission = new me.aap.fermata.auto.OpenOnCarMediaRouting.Admission(
				me.aap.fermata.ui.policy.RuntimeHostMode.PHONE, current::get);
		assertEquals(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.CANCELLED,
				FermataServiceUiBinder.dispatchRoutedPlayback(admission, () -> false,
						() -> { current.set(false); return true; }));
	}
	@Test public void committedCarOwnerSurvivesSwitchOffButNotHostReplacement() {
		var connection = me.aap.fermata.auto.AutomotiveConnectionState.get();
		var controller = me.aap.fermata.auto.AutomotiveNavigationController.get();
		me.aap.fermata.auto.AutomotiveNavigationController.Navigator first =
				(id, guard) -> me.aap.utils.async.Completed.completed(
						me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.OPENED);
		me.aap.fermata.auto.AutomotiveNavigationController.Navigator replacement =
				(id, guard) -> me.aap.utils.async.Completed.completed(
						me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.OPENED);
		connection.connectionChanged(true); controller.register(first);
		try {
			controller.getOpenOnCarMode().setEnabled(true);
			var admission = new me.aap.fermata.auto.OpenOnCarMediaRouting.Admission(
					me.aap.fermata.ui.policy.RuntimeHostMode.AA_PROJECTION,
					controller.getOpenOnCarMode()::isEnabled);
			assertTrue(admission.commit());
			controller.getOpenOnCarMode().setEnabled(false);
			assertTrue(admission.isCurrent());
			assertTrue(admission.canAttach());
			controller.register(replacement);
			assertFalse(admission.isCurrent());
			assertFalse(admission.canAttach());
		} finally { controller.unregister(replacement); connection.connectionChanged(false); }
	}
	@Test public void sameItemOnDifferentRequestedHostStillCreatesPlaybackRequest() {
		assertTrue(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, -1, false));
		assertFalse(FermataServiceUiBinder.shouldCreatePlaybackRequest(true, -1, true));
	}
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
