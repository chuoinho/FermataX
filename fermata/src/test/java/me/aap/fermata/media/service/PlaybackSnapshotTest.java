package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.reflect.Proxy;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

public class PlaybackSnapshotTest {
	@Test
	public void exposesOneImmutablePlaybackRevision() {
		PlaybackStateCompat state = new PlaybackStateCompat.Builder()
				.setState(PlaybackStateCompat.STATE_PLAYING, 123L, 1F).build();
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Title").build();
		PlaybackSnapshot snapshot = new PlaybackSnapshot(7L, null, state, metadata);

		assertEquals(7L, snapshot.getRevision());
		assertNull(snapshot.getItem());
		assertSame(state, snapshot.getState());
		assertSame(metadata, snapshot.getMetadata());
	}

	@Test
	public void itemParityUsesTheSameSemanticsAsLegacyBinder() {
		PlaybackStateCompat state = new PlaybackStateCompat.Builder().build();
		PlayableItem item = item();
		PlaybackSnapshot first = new PlaybackSnapshot(1L, item, state, null);
		PlaybackSnapshot second = new PlaybackSnapshot(2L, item, state, null);
		PlaybackSnapshot different = new PlaybackSnapshot(3L, item(), state, null);
		PlaybackSnapshot empty = new PlaybackSnapshot(4L, null, state, null);

		assertTrue(second.hasSameItem(first));
		assertFalse(different.hasSameItem(first));
		assertFalse(second.hasSameItem(null));
		assertTrue(empty.hasSameItem(null));
	}

	@Test
	public void stableMetadataTitleOverridesTechnicalItemName() {
		assertEquals("Video title",
				PlaybackSnapshot.resolveDisplayTitle("Video title", "Display title",
						"https://m.youtube.com/watch?v=test"));
	}

	@Test
	public void displayTitleAndItemNameRemainOrderedFallbacks() {
		assertEquals("Display title",
				PlaybackSnapshot.resolveDisplayTitle(" ", "Display title", "Fallback name"));
		assertEquals("Fallback name",
				PlaybackSnapshot.resolveDisplayTitle(null, "", "Fallback name"));
	}

	@Test
	public void snapshotWithoutMetadataUsesItemName() {
		PlaybackStateCompat state = new PlaybackStateCompat.Builder().build();
		PlaybackSnapshot snapshot = new PlaybackSnapshot(1L, namedItem("Fallback name"), state, null);
		assertEquals("Fallback name", snapshot.getDisplayTitle());
	}

	private static PlayableItem item() {
		return namedItem(null);
	}

	private static PlayableItem namedItem(String name) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> "item";
					case "getName" -> name;
					default -> null;
				});
	}
}
