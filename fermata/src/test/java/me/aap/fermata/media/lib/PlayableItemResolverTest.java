package me.aap.fermata.media.lib;

import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.lang.reflect.Proxy;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class PlayableItemResolverTest {
	@Test
	public void unwrapsNestedPresentationWrappers() {
		PlayableItem item = proxy(PlayableItem.class, null);
		PlayableItem wrapped = new PlayableItemWrapper(new PlayableItemWrapper(item));
		assertSame(item, PlayableItemResolver.unwrap(wrapped));
	}

	@Test
	public void wrapperDelegatesCustomMediaEngine() {
		MediaEngine engine = proxy(MediaEngine.class, null);
		PlayableItem item = proxy(PlayableItem.class, engine);
		assertSame(engine, new PlayableItemWrapper(item).getMediaEngine(null, null));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, MediaEngine engine) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
				(proxy, method, args) -> method.getName().equals("getMediaEngine") ? engine : null);
	}
}
