package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemWrapper;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.utils.async.Completed;
import me.aap.utils.async.Promise;

public class PlaybackTransitionTest {
	@Test
	public void pendingItemIsPublishedUntilEngineCommitsTarget() {
		PlayableItem oldItem = item("old");
		PlayableItem targetItem = item("target");
		AtomicReference<PlayableItem> engineSource = new AtomicReference<>(oldItem);
		MediaEngine engine = engine(engineSource);
		PlaybackTransition transition = new PlaybackTransition();

		assertSame(oldItem, transition.getCurrentItem(engine));
		transition.begin(targetItem, null);
		assertSame(targetItem, transition.getCurrentItem(engine));

		engineSource.set(targetItem);
		transition.complete(engine, targetItem);
		assertSame(targetItem, transition.getCurrentItem(engine));
	}

	@Test
	public void cancelledTransitionRestoresEngineIdentity() {
		PlayableItem oldItem = item("old");
		PlaybackTransition transition = new PlaybackTransition();
		MediaEngine engine = engine(new AtomicReference<>(oldItem));

		transition.begin(item("target"), null);
		transition.clear();

		assertSame(oldItem, transition.getCurrentItem(engine));
	}

	@Test
	public void failedTransitionCannotRemainPublished() {
		PlayableItem oldItem = item("old");
		PlayableItem targetItem = item("target");
		PlaybackTransition transition = new PlaybackTransition();
		MediaEngine engine = engine(new AtomicReference<>(oldItem));

		transition.begin(targetItem, null);
		assertSame(targetItem, transition.getCurrentItem(engine));
		assertEquals(true, transition.cancelIfPending(targetItem));
		assertSame(oldItem, transition.getCurrentItem(engine));
	}

	@Test
	public void transitionRetainsRollbackSnapshotUntilCommit() {
		PlayableItem oldItem = item("old");
		PlayableItem targetItem = item("target");
		PlaybackSnapshot previous = new PlaybackSnapshot(1, oldItem,
				new PlaybackStateCompat.Builder().setState(STATE_PLAYING, 42, 1f).build(), null);
		PlaybackTransition transition = new PlaybackTransition();

		transition.begin(targetItem, previous);

		assertSame(previous, transition.getPreviousSnapshot(targetItem));
		assertEquals(true, transition.cancelIfPending(targetItem));
		assertSame(null, transition.getPreviousSnapshot(targetItem));
	}

	@Test
	public void repeatedPublishAndRetargetKeepTheCommittedRollbackSnapshot() {
		PlayableItem oldItem = item("old");
		PlayableItem firstTarget = item("first");
		PlayableItem secondTarget = item("second");
		PlaybackSnapshot committed = new PlaybackSnapshot(1, oldItem,
				new PlaybackStateCompat.Builder().setState(STATE_PLAYING, 42, 1f).build(), null);
		PlaybackSnapshot connecting = new PlaybackSnapshot(2, firstTarget,
				new PlaybackStateCompat.Builder().setState(STATE_CONNECTING, 0, 1f).build(), null);
		PlaybackTransition transition = new PlaybackTransition();

		transition.begin(firstTarget, committed);
		transition.begin(firstTarget, connecting);
		transition.begin(secondTarget, connecting);

		assertSame(committed, transition.getPreviousSnapshot(secondTarget));
		assertEquals(true, transition.cancelIfPending(secondTarget));
	}

	@Test
	public void supersededTargetNeverBecomesTheCommittedOutgoingItem() {
		PlayableItem committed = item("committed");
		PlayableItem firstTarget = item("first");
		PlayableItem finalTarget = item("final");
		PlaybackSnapshot committedSnapshot = new PlaybackSnapshot(1, committed,
				new PlaybackStateCompat.Builder().setState(STATE_PLAYING, 36_000, 1f).build(), null);
		PlaybackSnapshot transientSnapshot = new PlaybackSnapshot(2, firstTarget,
				new PlaybackStateCompat.Builder().setState(STATE_CONNECTING, 0, 1f).build(), null);
		PlaybackTransition transition = new PlaybackTransition();

		transition.begin(firstTarget, committedSnapshot, 10_000);
		transition.begin(finalTarget, transientSnapshot, 20_000);

		assertTrue(transition.isPreviousItem(committed));
		assertFalse(transition.isPreviousItem(firstTarget));
		assertEquals(20_000, transition.getTargetPosition(finalTarget, -1));
		transition.complete(engine(new AtomicReference<>(firstTarget)), firstTarget);
		assertTrue(transition.hasPending());
		transition.complete(engine(new AtomicReference<>(finalTarget)), finalTarget);
		assertFalse(transition.hasPending());
	}

	@Test
	public void unspecifiedTargetPositionUsesResumeFallbackButExplicitZeroDoesNot() {
		PlayableItem target = item("target");
		PlaybackTransition transition = new PlaybackTransition();

		transition.begin(target, null);
		assertEquals(38_231, transition.getTargetPosition(target, 38_231));

		transition.begin(target, null, 0);
		assertEquals(0, transition.getTargetPosition(target, 38_231));
	}

	@Test
	public void exportedWrapperCommitsAgainstCanonicalEngineSource() {
		PlayableItem target = item("target");
		PlayableItem wrapper = new PlayableItemWrapper(target);
		AtomicReference<PlayableItem> engineSource = new AtomicReference<>(target);
		PlaybackTransition transition = new PlaybackTransition();

		transition.begin(wrapper, null);
		assertSame(target, transition.getCurrentItem(engine(engineSource)));
		transition.complete(engine(engineSource), wrapper);

		assertSame(target, transition.getCurrentItem(engine(engineSource)));
		assertEquals(false, transition.hasPending());
	}

	@Test
	public void transitionStateUsesTargetPositionWithoutChangingPlaybackSpeed() {
		PlaybackStateCompat previous = new PlaybackStateCompat.Builder()
				.setState(STATE_PLAYING, 81_000, 1.25f).build();

		PlaybackStateCompat skipping = MediaSessionCallback.createPlaybackTransitionState(previous,
				STATE_SKIPPING_TO_NEXT, 12_000);
		PlaybackStateCompat connecting = MediaSessionCallback.createPlaybackTransitionState(previous,
				STATE_CONNECTING, -1);

		assertEquals(STATE_SKIPPING_TO_NEXT, skipping.getState());
		assertEquals(12_000, skipping.getPosition());
		assertEquals(1.25f, skipping.getPlaybackSpeed(), 0f);
		assertEquals(STATE_CONNECTING, connecting.getState());
		assertEquals(0, connecting.getPosition());
		assertEquals(1f, connecting.getPlaybackSpeed(), 0f);
	}

	@Test
	public void transientTargetsCannotPersistPodcastOrAudiobookProgress() {
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_CONNECTING));
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_BUFFERING));
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_ERROR));
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_SKIPPING_TO_NEXT));
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_SKIPPING_TO_PREVIOUS));
		assertFalse(PlaybackSnapshot.canPersistProgress(STATE_SKIPPING_TO_QUEUE_ITEM));
		assertTrue(PlaybackSnapshot.canPersistProgress(STATE_PLAYING));
		assertTrue(PlaybackSnapshot.canPersistProgress(STATE_PAUSED));
		assertTrue(PlaybackSnapshot.canPersistProgress(STATE_STOPPED));
		PlaybackSnapshot transientSnapshot = new PlaybackSnapshot(1, item("target"),
				new PlaybackStateCompat.Builder().setState(STATE_CONNECTING, 23_000, 1f).build(), null);
		assertFalse(transientSnapshot.canPersistProgress());
	}

	@Test
	public void committedProgressItemPersistsNormalizedPositionWithoutAddonListener() {
		AtomicLong savedPosition = new AtomicLong(-1);
		AtomicBoolean savedCompleted = new AtomicBoolean();
		PlayableItem item = progressItem(savedPosition, savedCompleted);

		assertTrue(MediaSessionCallback.persistPlaybackProgress(item, 42_000, 100_000)
				.isDoneNotFailed());
		assertEquals(42_000, savedPosition.get());
		assertFalse(savedCompleted.get());

		assertTrue(MediaSessionCallback.persistPlaybackProgress(item, 99_500, 100_000)
				.isDoneNotFailed());
		assertEquals(0, savedPosition.get());
		assertTrue(savedCompleted.get());
	}

	@Test
	public void delayedOutgoingDurationPersistsAfterTheTargetCommits() {
		AtomicLong savedPosition = new AtomicLong(-1);
		AtomicBoolean savedCompleted = new AtomicBoolean();
		PlayableItem outgoing = progressItem(savedPosition, savedCompleted);
		PlayableItem target = item("target");
		PlaybackTransition transition = new PlaybackTransition();
		Promise<Long> duration = new Promise<>();

		transition.begin(target, new PlaybackSnapshot(1, outgoing,
				new PlaybackStateCompat.Builder().setState(STATE_PLAYING, 42_000, 1f).build(), null));
		duration.then(dur -> MediaSessionCallback.persistResolvedPlaybackProgress(outgoing,
				42_000, dur, transition.isPreviousItem(outgoing), true));
		transition.complete(engine(new AtomicReference<>(target)), target);
		assertFalse(transition.isPreviousItem(outgoing));

		duration.complete(100_000L);
		assertEquals(42_000, savedPosition.get());
		assertFalse(savedCompleted.get());
	}

	@Test
	public void staleNonOutgoingProgressCannotOverwriteTheCommittedItem() {
		AtomicLong savedPosition = new AtomicLong(-1);
		AtomicBoolean savedCompleted = new AtomicBoolean();
		PlayableItem stale = progressItem(savedPosition, savedCompleted);

		MediaSessionCallback.persistResolvedPlaybackProgress(stale, 42_000, 100_000,
				false, false);

		assertEquals(-1, savedPosition.get());
		assertFalse(savedCompleted.get());
	}

	private static PlayableItem item(String name) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString", "getName", "getId" -> name;
					default -> null;
				});
	}

	private static PlayableItem progressItem(AtomicLong position, AtomicBoolean completed) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class, PlaybackProgressItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "savePlaybackProgress" -> {
						position.set((long) args[0]);
						completed.set((boolean) args[1]);
						yield Completed.completedVoid();
					}
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString", "getName", "getId" -> "progress";
					default -> null;
				});
	}

	private static MediaEngine engine(AtomicReference<PlayableItem> source) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getSource" -> source.get();
					case "toString" -> "engine";
					default -> null;
				});
	}
}
