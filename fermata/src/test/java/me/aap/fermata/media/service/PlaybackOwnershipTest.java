package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.media.service.PlaybackOwnership.StateToken;
import me.aap.fermata.media.service.PlaybackOwnership.Token;

public class PlaybackOwnershipTest {
	private static final String[][] ADDON_GROUPS = {
			{"TV", "Radio", "TV-next"},
			{"YouTube", "Web", "YouTube-next"},
			{"Stremio", "Stremio-P2P", "Stremio-next"},
			{"Podcast", "Audiobook", "Podcast-next"},
			{"Files", "Cast", "Files-next"}
	};

	@Test
	public void pauseRevisionDoesNotRelinquishCommittedItem() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object addon = new Object();
		Object item = new Object();
		Object engine = new Object();
		Token token = ownership.adopt(addon, item, engine);
		StateToken playing = ownership.captureState();

		ownership.reviseState();

		assertSame(token, ownership.getActive());
		assertTrue(ownership.owns(engine, item));
		assertFalse(ownership.owns(playing));
	}

	@Test
	public void failedPrepareRollsBackToCommittedOwner() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object addon = new Object();
		Object firstItem = new Object();
		Object firstEngine = new Object();
		Token committed = ownership.adopt(addon, firstItem, firstEngine);
		Object target = new Object();
		Token pending = ownership.begin(addon, target, null);

		assertTrue(ownership.isRequestCurrent(pending.generation(), target));
		assertFalse(ownership.owns(firstEngine, firstItem));
		assertTrue(ownership.rollback(pending.generation(), target));
		assertSame(committed, ownership.getActive());
		assertTrue(ownership.owns(firstEngine, firstItem));
		assertFalse(ownership.isRequestCurrent(pending.generation(), target));
	}

	@Test
	public void engineHandoffKeepsGenerationAndRejectsOldEngine() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object item = new Object();
		Object provisional = new Object();
		Object replacement = new Object();
		Token pending = ownership.begin(new Object(), item, provisional);

		assertTrue(ownership.replaceEngine(provisional, replacement));
		assertFalse(ownership.owns(provisional, item));
		assertTrue(ownership.owns(replacement, item));
		assertEquals(pending.generation(), ownership.getActive().generation());
		assertTrue(ownership.commit(replacement, item));
	}

	@Test
	public void committedEngineReplacementKeepsItemGeneration() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object item = new Object();
		Object firstEngine = new Object();
		Object replacement = new Object();
		Token committed = ownership.adopt(new Object(), item, firstEngine);

		assertTrue(ownership.replaceEngine(firstEngine, replacement));
		assertFalse(ownership.ownsCommitted(firstEngine, item));
		assertTrue(ownership.ownsCommitted(replacement, item));
		assertEquals(committed.generation(),
				ownership.committedGeneration(replacement, item));
	}

	@Test
	public void transportStateChangeKeepsCurrentItemUntilRealItemArrives() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object addon = new Object();
		Object engine = new Object();
		Object current = new Object();
		Object next = new Object();
		ownership.adopt(addon, current, engine);

		ownership.reviseState();
		assertTrue(ownership.owns(engine, current));
		assertFalse(ownership.owns(engine, next));

		ownership.adopt(addon, next, engine);
		assertFalse(ownership.owns(engine, current));
		assertTrue(ownership.owns(engine, next));
	}

	@Test
	public void everyAddonGroupSurvivesOneHundredAbcRoundsWithZeroStaleWrites() {
		for (String[] group : ADDON_GROUPS) {
			PlaybackOwnership ownership = new PlaybackOwnership();
			Token previous = null;
			StateToken previousState = null;
			int staleWrites = 0;

			for (int round = 0; round < 100; round++) {
				for (String addon : group) {
					Object item = new GroupIdentity(addon, round);
					Object engine = new Object();
					Token next = ownership.begin(addon, item, null);
					assertTrue(ownership.bindEngine(next.generation(), item, engine) != null);
					assertTrue(ownership.commit(engine, item));
					if ((previous != null) && ownership.isRequestCurrent(previous.generation(),
							previous.itemIdentity())) staleWrites++;
					if ((previousState != null) && ownership.owns(previousState)) staleWrites++;
					assertTrue(ownership.owns(engine, item));
					previous = ownership.getActive();
					previousState = ownership.captureState();
				}
			}

			assertEquals("Stale ownership in " + group[0] + '/' + group[1], 0, staleWrites);
		}
	}

	@Test
	public void addonSwitchDuringPrepareRejectsSupersededEngineAndItem() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object committedItem = new Object();
		Object committedEngine = new Object();
		Token committed = ownership.adopt("TV", committedItem, committedEngine);
		Object youtubeItem = new Object();
		Token youtube = ownership.begin("YouTube", youtubeItem, null);
		Object stremioItem = new Object();
		Object stremioEngine = new Object();
		Token stremio = ownership.begin("Stremio", stremioItem, null);

		assertFalse(ownership.isRequestCurrent(youtube.generation(), youtubeItem));
		assertTrue(ownership.bindEngine(youtube.generation(), youtubeItem, new Object()) == null);
		assertFalse(ownership.commit(new Object(), youtubeItem));
		assertTrue(ownership.bindEngine(stremio.generation(), stremioItem, stremioEngine) != null);
		assertTrue(ownership.commit(stremioEngine, stremioItem));
		assertTrue(ownership.owns(stremioEngine, stremioItem));
		assertFalse(ownership.owns(committedEngine, committedItem));
		assertNotSame(committed, ownership.getCommitted());
	}

	@Test
	public void pendingPresentationDoesNotReplaceCommittedOwnerUntilCommit() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object oldItem = new Object();
		Object oldEngine = new Object();
		Token committed = ownership.adopt("Radio", oldItem, oldEngine);
		Object target = new Object();
		Object targetEngine = new Object();
		Token pending = ownership.begin("Podcast", target, targetEngine);

		assertSame(committed, ownership.getCommitted());
		assertSame(pending, ownership.getPending());
		assertTrue(ownership.ownsCommitted(oldEngine, oldItem));
		assertEquals(committed.generation(),
				ownership.committedGeneration(oldEngine, oldItem));
		assertEquals(-1L, ownership.committedGeneration(targetEngine, target));
		assertFalse(ownership.ownsCommitted(targetEngine, target));
		assertTrue(ownership.commit(targetEngine, target));
		assertSame(pending, ownership.getCommitted());
		assertTrue(ownership.ownsCommitted(targetEngine, target));
		assertEquals(pending.generation(),
				ownership.committedGeneration(targetEngine, target));
		assertSame(null, ownership.getPending());
	}

	@Test
	public void releaseInvalidatesPendingCommittedAndStateCallbacks() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object item = new Object();
		Object engine = new Object();
		Token owner = ownership.adopt("Files", item, engine);
		StateToken state = ownership.captureState();

		ownership.release();

		assertFalse(ownership.owns(engine, item));
		assertFalse(ownership.owns(state));
		assertFalse(ownership.isRequestCurrent(owner.generation(), item));
		assertSame(null, ownership.getCommitted());
	}

	@Test
	public void equalButDifferentItemsNeverShareOwnership() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		EqualIdentity first = new EqualIdentity();
		EqualIdentity second = new EqualIdentity();
		Object engine = new Object();
		ownership.adopt(new Object(), first, engine);

		assertFalse(ownership.owns(engine, second));
		assertTrue(ownership.owns(engine, first));
	}

	private static final class EqualIdentity {
		@Override
		public boolean equals(Object obj) {
			return obj instanceof EqualIdentity;
		}
	}

	private record GroupIdentity(String addon, int round) {
	}
}
