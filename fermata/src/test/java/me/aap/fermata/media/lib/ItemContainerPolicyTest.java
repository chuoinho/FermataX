package me.aap.fermata.media.lib;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.Test;

public class ItemContainerPolicyTest {
	@Test
	public void persistentIdentityOverridesEphemeralRuntimeId() {
		MediaLib.Item item = (MediaLib.Item) Proxy.newProxyInstance(
				MediaLib.Item.class.getClassLoader(),
				new Class<?>[]{MediaLib.Item.class, PersistentMediaItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getId" -> "stremio:stream:temporary";
					case "getPersistentId" -> "stremio:video:stable";
					default -> null;
				});

		assertEquals("stremio:video:stable", PersistentMediaItem.idOf(item));
	}

	@Test
	public void missingIdsArePrunedOnlyAfterDefinitiveResolution() {
		assertTrue(ItemContainer.shouldPruneMissing(false, false));
		assertFalse(ItemContainer.shouldPruneMissing(true, false));
		assertFalse(ItemContainer.shouldPruneMissing(false, true));
		assertFalse(ItemContainer.shouldPruneMissing(true, true));
	}

	@Test
	public void interleavedAddonOrderSurvivesDisableMutationAndReenable() {
		List<String> stored = List.of(
				"youtube:video:newest", "tv:channel:news",
				"youtube:video:older", "radio:station:jazz");
		List<String> unresolved = List.of("youtube:video:newest", "youtube:video:older");

		String[] disabled = ItemContainer.mergeChildIds(
				new String[]{"tv:channel:news", "radio:station:jazz"}, unresolved, stored, 30);
		assertArrayEquals(stored.toArray(new String[0]), disabled);

		String[] afterVisiblePlayback = ItemContainer.mergeChildIds(
				new String[]{"radio:station:rock", "tv:channel:news", "radio:station:jazz"},
				unresolved, stored, 30);
		assertArrayEquals(new String[]{
				"radio:station:rock", "youtube:video:newest", "tv:channel:news",
				"youtube:video:older", "radio:station:jazz"
		}, afterVisiblePlayback);

		assertArrayEquals(afterVisiblePlayback, ItemContainer.mergeChildIds(
				afterVisiblePlayback, List.of(), List.of(afterVisiblePlayback), 30));
	}

	@Test
	public void quotaTrimsExactMergedSequenceFromTheTail() {
		List<String> stored = List.of(
				"youtube:video:newest", "tv:channel:news",
				"youtube:video:older", "radio:station:jazz");
		List<String> unresolved = List.of("youtube:video:newest", "youtube:video:older");

		assertArrayEquals(new String[]{
				"radio:station:rock", "youtube:video:newest",
				"tv:channel:news", "youtube:video:older"
		}, ItemContainer.mergeChildIds(
				new String[]{"radio:station:rock", "tv:channel:news", "radio:station:jazz"},
				unresolved, stored, 4));
	}
}
