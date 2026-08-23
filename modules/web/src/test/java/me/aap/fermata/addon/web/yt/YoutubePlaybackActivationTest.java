package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import me.aap.fermata.addon.external.ExternalPlaybackDelegateItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class YoutubePlaybackActivationTest {
	@Test
	public void explicitSourceWinsWhenItsVideoMatches() {
		PlayableItem explicit = youtube("B");
		PlayableItem external = external(youtube("B"));
		PlayableItem canonical = youtube("B");
		AtomicBoolean canonicalRequested = new AtomicBoolean();

		assertSame(explicit, YoutubePlaybackActivation.selectSource(
				"B", explicit, external, () -> {
					canonicalRequested.set(true);
					return canonical;
				}));
		assertFalse(canonicalRequested.get());
	}

	@Test
	public void matchingExternalOwnerIsPreserved() {
		PlayableItem external = external(youtube("B"));
		PlayableItem canonical = youtube("B");

		assertSame(external, YoutubePlaybackActivation.selectSource(
				"B", youtube("A"), external, () -> canonical));
	}

	@Test
	public void autoNextDropsOwnersForThePreviousVideo() {
		PlayableItem current = youtube("A");
		PlayableItem external = external(youtube("A"));
		PlayableItem canonical = youtube("B");

		assertSame(canonical, YoutubePlaybackActivation.selectSource(
				"B", current, external, () -> canonical));
		assertTrue(YoutubePlaybackActivation.matchesVideo(external, "A"));
		assertFalse(YoutubePlaybackActivation.matchesVideo(external, "B"));
	}

	private static PlayableItem youtube(String videoId) {
		YoutubeItem descriptor = new YoutubeItem(videoId,
				"https://m.youtube.com/watch?v=" + videoId, videoId, 1L);
		return proxy(new Class<?>[]{PlayableItem.class, YoutubeDescriptorItem.class},
				(method, args) -> switch (method.getName()) {
					case "getYoutubeDescriptor" -> descriptor;
					case "getId", "getOrigId" -> descriptor.stableId();
					default -> null;
				});
	}

	private static PlayableItem external(PlayableItem delegate) {
		return proxy(new Class<?>[]{PlayableItem.class, ExternalPlaybackDelegateItem.class},
				(method, args) -> switch (method.getName()) {
					case "getExternalPlaybackDelegate" -> delegate;
					case "getId", "getOrigId" -> "external:" + delegate.getId();
					default -> null;
				});
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<?>[] types, Invocation invocation) {
		return (T) Proxy.newProxyInstance(YoutubePlaybackActivationTest.class.getClassLoader(),
				types, (proxy, method, args) -> {
					Object value = invocation.invoke(method, args);
					if ((value != null) || !method.getReturnType().isPrimitive()) return value;
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
