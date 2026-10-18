package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.Test;

import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class YoutubeMediaEngineOwnershipTest {
	@Test
	public void currentEngineKeepsOwnershipAcrossHostTransitions() {
		assertTrue(YoutubeMediaEngine.canClaimExternalPlayback(true, false, true, false));
		assertTrue(YoutubeMediaEngine.canClaimExternalPlayback(true, false, false, false));
	}

	@Test
	public void queuedCallbackFromInactiveWebViewCannotReclaimAutoPlayback() {
		assertFalse(YoutubeMediaEngine.canClaimExternalPlayback(false, false, true, true));
	}

	@Test
	public void generationZeroCallbackFromInactiveWebViewCannotReclaimMobilePlayback() {
		assertFalse(YoutubeMediaEngine.canClaimExternalPlayback(false, false, false, true));
	}

	@Test
	public void activeMobileWebViewCanClaimLegitimateFirstPlayback() {
		assertTrue(YoutubeMediaEngine.canClaimExternalPlayback(false, true, false, false));
	}

	@Test
	public void activeAutoWebViewStillRequiresPlaybackIntent() {
		assertFalse(YoutubeMediaEngine.canClaimExternalPlayback(false, true, true, false));
		assertTrue(YoutubeMediaEngine.canClaimExternalPlayback(false, true, true, true));
	}

	@Test
	public void newlyClaimedPlaybackRestoresAudioOnlyOnce() {
		assertTrue(YoutubeMediaEngine.shouldRestoreAudibleAfterClaim(false, true));
		assertFalse(YoutubeMediaEngine.shouldRestoreAudibleAfterClaim(true, true));
		assertFalse(YoutubeMediaEngine.shouldRestoreAudibleAfterClaim(false, false));
	}

	@Test
	public void explicitPhoneSelectionForwardsWhenAutomotiveHostOwnsPresentation() {
		assertTrue(YoutubePlaybackHostPolicy.shouldForward(
				false, false, false, true));
		assertFalse(YoutubePlaybackHostPolicy.shouldForward(
				false, false, false, false));
		assertFalse(YoutubePlaybackHostPolicy.shouldForward(
				true, true, false, true));
		assertFalse(YoutubePlaybackHostPolicy.shouldForward(
				false, true, true, true));
	}

	@Test
	public void externalOwnerRejectsYouTubeAutoNextIdentityChange() {
		assertTrue(YoutubeMediaEngine.acceptsExternalPlaybackVideo("video-a", "video-a"));
		assertFalse(YoutubeMediaEngine.acceptsExternalPlaybackVideo("video-a", "video-c"));
		assertTrue(YoutubeMediaEngine.acceptsExternalPlaybackVideo("", "video-c"));
	}

	@Test
	public void externallyOwnedYoutubeKeepsTitleAndBackParentOwnership() {
		BrowsableItem parent = proxy(BrowsableItem.class, (method, args) -> null);
		PlayableItem delegate = proxy(PlayableItem.class, (method, args) -> switch (method.getName()) {
			case "getId", "getOrigId" -> "youtube:video:test-id";
			default -> null;
		});
		PlayableItem owner = proxy(new Class<?>[]{PlayableItem.class,
				ExternalPlaybackDelegateItem.class}, (method, args) -> switch (method.getName()) {
			case "getExternalPlaybackDelegate" -> delegate;
			case "getParent" -> parent;
			case "getId", "getOrigId" -> "stremio:video:test-id";
			default -> null;
		});

		assertTrue(YoutubeMediaEngine.isYoutubeItem(owner));
		assertSame(parent, YoutubeFragment.externalPlaybackParent(owner));
	}

	@Test
	public void staleSignalRejectedWhenOnHomePage() {
		YoutubeTargetPrepareGate gate = new YoutubeTargetPrepareGate();
		// Sitting on home page without target prepare gate: stale video signal must be rejected.
		assertFalse(YoutubeMediaEngine.matchesLoadedPage(
				"https://m.youtube.com/",
				"https://m.youtube.com/watch?v=oldVideo",
				"oldVideo",
				1L,
				gate,
				null,
				null));
	}

	@Test
	public void matchingSpaTransitionAcceptedDuringTargetPrepare() {
		YoutubeTargetPrepareGate gate = new YoutubeTargetPrepareGate();
		gate.begin("newVideo", 5L);

		// Stale signal with different video or older generation while on home page must be rejected.
		assertFalse(YoutubeMediaEngine.matchesLoadedPage(
				"https://m.youtube.com/",
				"https://m.youtube.com/watch?v=oldVideo",
				"oldVideo",
				4L,
				gate,
				null,
				null));

		// Signal matching recorded target video and generation during SPA transition must be accepted.
		assertTrue(YoutubeMediaEngine.matchesLoadedPage(
				"https://m.youtube.com/",
				"https://m.youtube.com/watch?v=newVideo",
				"newVideo",
				5L,
				gate,
				null,
				null));
	}

	@Test
	public void directVideoPageUrlMatchesLoadedPage() {
		assertTrue(YoutubeMediaEngine.matchesLoadedPage(
				"https://m.youtube.com/watch?v=directVid",
				"https://m.youtube.com/watch?v=directVid",
				"directVid",
				0L,
				null,
				null,
				null));

		assertFalse(YoutubeMediaEngine.matchesLoadedPage(
				"https://m.youtube.com/watch?v=otherVid",
				"https://m.youtube.com/watch?v=directVid",
				"directVid",
				0L,
				null,
				null,
				null));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Invocation invocation) {
		return (T) proxy(new Class<?>[]{type}, invocation);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<?>[] types, Invocation invocation) {
		return (T) Proxy.newProxyInstance(YoutubeMediaEngineOwnershipTest.class.getClassLoader(),
				types, (proxy, method, args) -> {
					Object value = invocation.invoke(method, args);
					if (value != null || !method.getReturnType().isPrimitive()) return value;
					if (method.getReturnType() == boolean.class) return false;
					if (method.getReturnType() == long.class) return 0L;
					if (method.getReturnType() == int.class) return 0;
					return null;
				});
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(java.lang.reflect.Method method, Object[] args);
	}

}
