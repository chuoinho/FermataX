package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completedNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

public class AddonManagerIsolationTest {

	@Test
	public void throwingFavoriteCallbackDoesNotBlockFollowingAddon() {
		List<String> calls = new ArrayList<>();
		ResolverAddon throwing = new ResolverAddon("throwing", calls, true, null);
		ResolverAddon following = new ResolverAddon("following", calls, false, null);

		AddonManager.notifyFavoriteChanged(List.of(throwing, following), playable(), true);

		assertEquals(List.of("throwing:favorite", "following:favorite"), calls);
	}

	@Test
	public void throwingItemResolverDoesNotChangeOrderingOrFirstHandledResult() {
		List<String> calls = new ArrayList<>();
		Item handled = item("handled");
		ResolverAddon throwing = new ResolverAddon("throwing", calls, true, null);
		ResolverAddon firstHandled = new ResolverAddon("first", calls, false, handled);
		ResolverAddon neverReached = new ResolverAddon("last", calls, false, item("last"));

		FutureSupplier<? extends Item> result = AddonManager.resolveItem(
				List.of(throwing, firstHandled, neverReached), null, "test", "test:item");

		assertNotNull(result);
		assertSame(handled, result.getOrThrow());
		assertEquals(List.of("throwing:get", "first:get"), calls);
	}

	private static PlayableItem playable() {
		return (PlayableItem) itemProxy(new Class<?>[]{PlayableItem.class}, "playable");
	}

	private static Item item(String id) {
		return (Item) itemProxy(new Class<?>[]{Item.class}, id);
	}

	private static Object itemProxy(Class<?>[] interfaces, String id) {
		return Proxy.newProxyInstance(Item.class.getClassLoader(), interfaces,
				(proxy, method, args) -> switch (method.getName()) {
					case "getId", "getName", "toString" -> id;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> null;
				});
	}

	private static final class ResolverAddon implements MediaItemResolverAddon {
		private final String name;
		private final List<String> calls;
		private final boolean throwing;
		private final Item result;

		private ResolverAddon(String name, List<String> calls, boolean throwing, Item result) {
			this.name = name;
			this.calls = calls;
			this.throwing = throwing;
			this.result = result;
		}

		@Override
		public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, String scheme,
				String id) {
			calls.add(name + ":get");
			if (throwing) throw new IllegalStateException(name);
			return (result == null) ? null : completedNull().map(ignored -> result);
		}

		@Override
		public void onFavoriteChanged(PlayableItem item, boolean favorite) {
			calls.add(name + ":favorite");
			if (throwing) throw new IllegalStateException(name);
		}

		@Override
		public int getAddonId() {
			return 0;
		}

		@Override
		public AddonInfo getInfo() {
			throw new AssertionError("Not used by callback dispatch");
		}
	}
}
