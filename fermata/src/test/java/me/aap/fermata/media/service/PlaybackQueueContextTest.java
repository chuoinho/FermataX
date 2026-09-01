package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

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

		assertSame(first, context.selectAndCanonicalize(favoriteFirst));
		assertSame(favoriteFirst, context.navigationItem(first));

		context.advance(first, favoriteSecond);
		assertSame(favoriteSecond, context.navigationItem(second));
	}

	@Test
	public void exportedSelectionKeepsPresentationOrderAcrossPrevious() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		Item favoriteSecond = new Item("favorite-second", second);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(favoriteSecond);
		assertSame(favoriteSecond, context.navigationItem(second));

		context.advance(second, favoriteFirst);
		assertSame(favoriteFirst, context.navigationItem(first));
	}

	@Test
	public void directSelectionClearsPreviousPresentationOrder() {
		Item first = new Item("first", null);
		Item favoriteFirst = new Item("favorite-first", first);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(favoriteFirst);
		assertSame(first, context.selectAndCanonicalize(first));

		assertSame(first, context.navigationItem(first));
	}

	@Test
	public void canonicalAdvanceLeavesPresentationOrder() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(favoriteFirst);
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

		context.selectAndCanonicalize(favoriteFirst);

		assertSame(unrelated, context.navigationItem(unrelated));
	}

	@Test
	public void mediaBrowserSelectionReturnsCanonicalAndRetainsPresentedItem() {
		Item canonical = new Item("youtube-a", null);
		Item presented = new Item("favorite:youtube-a", canonical);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		assertSame(canonical, context.selectAndCanonicalize(presented));
		assertSame(presented, context.navigationItem(canonical));
	}

	@Test
	public void mixedCollectionAdvancesAcrossAddonOwnershipBoundaries() {
		Item iptv = new Item("iptv", null);
		Item radio = new Item("radio", null);
		Item podcast = new Item("podcast", null);
		Item favoriteIptv = new Item("favorite:iptv", iptv);
		Item favoriteRadio = new Item("favorite:radio", radio);
		Item favoritePodcast = new Item("favorite:podcast", podcast);
		List<Item> favorites = List.of(favoriteIptv, favoriteRadio, favoritePodcast);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(favoriteIptv);
		context.advance(iptv, favorites.get(1));
		assertSame(favoriteRadio, context.navigationItem(radio));
		context.advance(radio, favorites.get(2));
		assertSame(favoritePodcast, context.navigationItem(podcast));
		context.advance(podcast, favorites.get(1));
		assertSame(favoriteRadio, context.navigationItem(radio));
	}

	@Test
	public void clearRemovesPresentationContext() {
		Item canonical = new Item("item", null);
		Item presented = new Item("favorite:item", canonical);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(presented);
		context.clear();

		assertSame(canonical, context.navigationItem(canonical));
	}

	@Test
	public void failedPreparationDoesNotAdvancePresentationOrder() {
		Item first = new Item("first", null);
		Item second = new Item("second", null);
		Item favoriteFirst = new Item("favorite-first", first);
		Item favoriteSecond = new Item("favorite-second", second);
		PlaybackQueueContext<Item> context =
				new PlaybackQueueContext<>(Item::canonical);

		context.selectAndCanonicalize(favoriteFirst);
		var prepared = context.prepareAdvance(first, favoriteSecond,
				ignored -> failed(new IllegalStateException("unavailable")));

		assertTrue(prepared.isFailed());
		assertSame(favoriteFirst, context.navigationItem(first));
	}

	@Test
	public void staleCandidateIsSkippedBeforePreparingNextValidItem() throws Exception {
		Item first = new Item("first", null);
		Item stale = new Item("stale", null);
		Item valid = new Item("valid", null);
		List<Item> queue = List.of(first, stale, valid);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		Item prepared = context.prepareAdjacent(first, first,
				item -> completed(next(queue, item)),
				item -> item == stale ? completedNull() : completed(item), Item::id, 10).get();

		assertSame(valid, prepared);
	}

	@Test
	public void allStaleCandidatesStopWithoutLooping() throws Exception {
		Item first = new Item("first", null);
		Item stale = new Item("stale", null);
		List<Item> queue = List.of(first, stale);
		PlaybackQueueContext<Item> context = new PlaybackQueueContext<>(Item::canonical);

		Item prepared = context.prepareAdjacent(first, first,
				item -> completed(next(queue, item)), ignored -> completedNull(), Item::id, 10).get();

		assertNull(prepared);
	}

	private static Item next(List<Item> queue, Item item) {
		return queue.get((queue.indexOf(item) + 1) % queue.size());
	}

	private record Item(String id, Item original) {
		Item canonical() {
			return (original == null) ? this : original;
		}
	}
}
