package me.aap.fermata.addon.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.media.lib.MediaLib.Item;

public class PodcastRootItemTest {
	@Test
	public void rootCarriesPodcastRouteAndStableActionIds() {
		PodcastRootItem root = new PodcastRootItem(null);

		assertEquals(AddonCapability.PODCAST, root.getRouteCapability());
		assertSame(root, root.getItem(null, PodcastRootItem.ID).peek());
		assertNull(root.getItem(null, "Other"));
		assertNull(root.getItem("radio", PodcastRootItem.ID));

		List<Item> actions = root.getUnsortedChildren().peek();
		assertEquals(PodcastAction.values().length, actions.size());
		for (PodcastAction action : PodcastAction.values()) {
			PodcastActionItem item = root.getActionItem(action);
			assertEquals("podcast:action:" + action.itemName, item.getId());
			assertSame(item, root.getItem(PodcastRootItem.SCHEME, item.getId()).peek());
		}
	}

	@Test
	public void rootAcceptsOnlyPodcastItemIds() {
		PodcastRootItem root = new PodcastRootItem(null);

		assertTrue(root.isChildItemId(PodcastRootItem.ID));
		assertTrue(root.isChildItemId("podcast:action:search"));
		assertFalse(root.isChildItemId("radio:station:one"));
		assertFalse(root.isChildItemId("Podcast-copy"));
		assertNull(root.getItem(PodcastRootItem.SCHEME, "podcast:unknown").peek());
	}

	@Test
	public void menuIdsMapOnlyToPodcastActions() {
		for (PodcastAction action : PodcastAction.values()) {
			assertSame(action, PodcastAction.fromMenuId(action.menuId));
		}
		assertNull(PodcastAction.fromMenuId(me.aap.fermata.R.id.radio_fragment));
	}
}
