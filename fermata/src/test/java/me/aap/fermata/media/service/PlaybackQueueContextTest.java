package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackQueueContextTest {
	@Test
	public void exportedSelectionKeepsPresentationOrderAcrossAdvance() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		Item favoriteSecond = new Item("favorite-second", second);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.select(favoriteFirst);
		assertSame(favoriteFirst, context.navigationItem(first));

		context.advance(first, favoriteSecond);
		assertSame(favoriteSecond, context.navigationItem(second));
	}

	@Test
	public void directSelectionClearsPreviousPresentationOrder() {
		Item first = new Item("first", null);
		Item favoriteFirst = new Item("favorite-first", first);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.select(favoriteFirst);
		context.select(first);

		assertSame(first, context.navigationItem(first));
	}

	@Test
	public void canonicalAdvanceLeavesPresentationOrder() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.select(favoriteFirst);
		context.advance(first, second);

		assertSame(second, context.navigationItem(second));
	}

	@Test
	public void unrelatedPlaybackSourceInvalidatesPresentationOrder() {
		Item first = new Item("first", null);
		Item unrelated = new Item("unrelated", null);
		Item favoriteFirst = new Item("favorite-first", first);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.select(favoriteFirst);

		assertSame(unrelated, context.navigationItem(unrelated));
	}

	@Test
	public void failedPreparationDoesNotAdvancePresentationOrder() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		Item favoriteSecond = new Item("favorite-second", second);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.select(favoriteFirst);
		var prepared = context.prepareAdvance(first, favoriteSecond,
				ignored -> failed(new IllegalStateException("unavailable")));

		assertTrue(prepared.isFailed());
		assertSame(favoriteFirst, context.navigationItem(first));
	}

	private record Item(String id, Item original) {
		Item canonical() {
			return (original == null) ? this : original;
		}
	}
}
