package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.junit.Test;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.VideoOutputCoordinator;

public class YoutubeBrowserHostClaimTest {
	@Test public void claimingAnotherBrowserDoesNotPrepareThePreviousOwnersItem() throws Exception {
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		field(callback, "videoOutput", new VideoOutputCoordinator());
		YoutubeSessionEngine session = new YoutubeSessionEngine(null, null, callback, null);
		PlayableItem previous = (PlayableItem) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[]{PlayableItem.class}, (proxy, method, args) -> null);
		field(session, "source", previous);
		Host next = allocate(Host.class);
		assertTrue(session.attach(next, false));
		assertSame(previous, session.getSource());
		assertTrue(session.owns(next));
		assertEquals(0, next.prepares);
	}

	private static final class Host extends YoutubeMediaEngine {
		int prepares;
		Host() { super(null, null); }
		@Override boolean belongsTo(YoutubeAddon addon) { return true; }
		@Override boolean carHost() { return false; }
		@Override public void prepare(PlayableItem item) { prepares++; }
	}
	private static void field(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true); field.set(target, value);
	}
	@SuppressWarnings("unchecked")
	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
		return (T) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
	}
}
