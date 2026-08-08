package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class PlaybackTransportDispatcherTest {
	@Test
	public void nativeYoutubeTransportIsConsumedOnlyByTheCurrentOwningEngine() {
		AtomicInteger prepares = new AtomicInteger();
		MediaEngine owner = proxy(MediaEngine.class, (method, args) -> {
			if (method.equals("prepare")) prepares.incrementAndGet();
			return defaultValue(method);
		});
		MediaEngine replacement = proxy(MediaEngine.class,
				(method, args) -> defaultValue(method));
		PlayableItem command = item(true);

		assertTrue(PlaybackTransportDispatcher.dispatch(command, owner, owner));
		assertEquals(1, prepares.get());
		assertTrue(PlaybackTransportDispatcher.dispatch(command, owner, replacement));
		assertEquals(1, prepares.get());
		assertFalse(PlaybackTransportDispatcher.dispatch(item(false), owner, owner));
	}

	@Test
	public void collectionCandidateDoesNotDispatchYoutubeNativeTransport() {
		AtomicInteger prepares = new AtomicInteger();
		MediaEngine youtube = proxy(MediaEngine.class, (method, args) -> {
			if (method.equals("prepare")) prepares.incrementAndGet();
			return defaultValue(method);
		});

		assertFalse(PlaybackTransportDispatcher.dispatch(item(false), youtube, youtube));
		assertEquals(0, prepares.get());
	}

	private static PlayableItem item(boolean transport) {
		return proxy(PlayableItem.class, (method, args) ->
				method.equals("isPlaybackTransportCommand") ? transport : defaultValue(method));
	}

	private static Object defaultValue(String method) {
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
				(proxy, method, args) -> handler.invoke(method.getName(), args));
	}

	private interface Handler {
		Object invoke(String method, Object[] args);
	}
}
