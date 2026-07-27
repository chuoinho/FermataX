package me.aap.fermata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Characterizes the common progress ownership path required by ST-PROG-004/005/006. */
public class PlaybackProgressOwnershipCharacterizationTest {
	private static final long DURATION = 300_000;
	private static final long RANDOM_SEED = 0x5A17E0L;

	@Test
	public void itemSwitchWritesTheOutgoingPositionOnly() {
		ProgressProbe outgoing = new ProgressProbe("A");
		ProgressProbe target = new ProgressProbe("B");
		PlaybackTransition transition = new PlaybackTransition();
		Promise<Long> duration = new Promise<>();

		transition.begin(target.item, snapshot(1, outgoing.item, 42_000));
		outgoing.expect(42_000, false);
		FutureSupplier<Void> completion = duration.then(dur ->
				MediaSessionCallback.persistResolvedPlaybackProgress(outgoing.item,
				42_000, dur, transition.isPreviousItem(outgoing.item), true));
		transition.complete(engine(target.item), target.item);
		duration.complete(DURATION);
		assertFalse(completion.isDone());
		outgoing.completePendingWrites();

		assertCompleted(completion);
		outgoing.assertSatisfied();
		target.assertNoWrites();
	}

	@Test
	public void delayedStaleFutureCannotWriteAfterTheTargetOwnsPlayback() {
		ProgressProbe stale = new ProgressProbe("A");
		ProgressProbe target = new ProgressProbe("B");
		PlaybackTransition transition = new PlaybackTransition();
		Promise<Long> delayedDuration = new Promise<>();

		transition.begin(target.item, snapshot(1, stale.item, 42_000));
		transition.complete(engine(target.item), target.item);
		assertFalse(transition.isPreviousItem(stale.item));
		FutureSupplier<Void> completion = delayedDuration.then(dur ->
				MediaSessionCallback.persistResolvedPlaybackProgress(stale.item,
				42_000, dur, false, false));
		delayedDuration.complete(DURATION);

		assertCompleted(completion);
		stale.assertNoWrites();
		target.assertNoWrites();
	}

	@Test
	public void randomizedSwitchesNeverWriteAnotherItemOrNegativePosition() {
		Random random = new Random(RANDOM_SEED);
		ProgressProbe[] probes = {
				new ProgressProbe("A"),
				new ProgressProbe("B"),
				new ProgressProbe("C")
		};
		PlaybackTransition transition = new PlaybackTransition();
		List<Promise<Long>> delayedDurations = new ArrayList<>(200);
		List<FutureSupplier<Void>> completions = new ArrayList<>(200);
		int currentIndex = 0;

		for (int switchNumber = 0; switchNumber < 100; switchNumber++) {
			int generation = switchNumber;
			int targetIndex = random.nextInt(probes.length - 1);
			if (targetIndex >= currentIndex) targetIndex++;
			ProgressProbe outgoing = probes[currentIndex];
			ProgressProbe target = probes[targetIndex];
			long requestedPosition = ((switchNumber % 11) == 0) ?
					-(switchNumber + 1L) : 1_000L + (switchNumber * 1_003L) + currentIndex;
			long persistedPosition = Math.max(requestedPosition, 0);
			Promise<Long> outgoingDuration = new Promise<>();
			Promise<Long> staleDuration = new Promise<>();

			transition.begin(target.item,
					snapshot(switchNumber + 1L, outgoing.item, persistedPosition));
			outgoing.expect(persistedPosition, false);
			completions.add(outgoingDuration.then(dur ->
					MediaSessionCallback.persistResolvedPlaybackProgress(
					outgoing.item, requestedPosition, dur,
					transition.isPreviousItem(outgoing.item), true)));

			// A non-owner callback from the superseded generation must remain a no-op.
			completions.add(staleDuration.then(dur ->
					MediaSessionCallback.persistResolvedPlaybackProgress(
					target.item, 200_000L + generation, dur, false, false)));

			transition.complete(engine(target.item), target.item);
			delayedDurations.add(outgoingDuration);
			delayedDurations.add(staleDuration);
			currentIndex = targetIndex;
		}

		Collections.shuffle(delayedDurations, random);
		for (Promise<Long> duration : delayedDurations) duration.complete(DURATION);
		List<Promise<Void>> pendingWrites = new ArrayList<>(100);
		for (ProgressProbe probe : probes) probe.movePendingWritesTo(pendingWrites);
		assertEquals(100, pendingWrites.size());
		Collections.shuffle(pendingWrites, random);
		for (Promise<Void> write : pendingWrites) write.complete(null);
		for (FutureSupplier<Void> completion : completions) assertCompleted(completion);

		int totalWrites = 0;
		for (ProgressProbe probe : probes) {
			probe.assertSatisfied();
			totalWrites += probe.writeCount;
		}
		assertEquals(100, totalWrites);
	}

	private static void assertCompleted(FutureSupplier<Void> completion) {
		assertTrue("Delayed callback failed: " + completion.getFailure(),
				completion.isDoneNotFailed());
	}

	private static PlaybackSnapshot snapshot(long revision, PlayableItem item, long position) {
		return new PlaybackSnapshot(revision, item, new PlaybackStateCompat.Builder()
				.setState(STATE_PLAYING, position, 1F).build(), null);
	}

	private static MediaEngine engine(PlayableItem source) {
		AtomicReference<PlayableItem> current = new AtomicReference<>(source);
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getSource" -> current.get();
					case "toString" -> "engine(" + source.getId() + ")";
					default -> null;
				});
	}

	private static final class ProgressProbe {
		private final String id;
		private final PlayableItem item;
		private final Map<ExpectedWrite, Integer> expectedWrites = new HashMap<>();
		private final List<Promise<Void>> pendingWrites = new ArrayList<>();
		private int writeCount;

		private ProgressProbe(String id) {
			this.id = id;
			item = (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
					new Class<?>[]{PlayableItem.class, PlaybackProgressItem.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "savePlaybackProgress" -> {
							long position = (long) args[0];
							boolean completed = (boolean) args[1];
							assertTrue("Negative progress written to " + id, position >= 0);
							ExpectedWrite write = new ExpectedWrite(position, completed);
							Integer remaining = expectedWrites.get(write);
							assertTrue("Unexpected/wrong-item write on " + id + ": " + write,
									(remaining != null) && (remaining > 0));
							if (remaining == 1) expectedWrites.remove(write);
							else expectedWrites.put(write, remaining - 1);
							writeCount++;
							Promise<Void> pending = new Promise<>();
							pendingWrites.add(pending);
							yield pending;
						}
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						case "toString", "getName", "getId" -> id;
						default -> null;
					});
		}

		private void expect(long position, boolean completed) {
			ExpectedWrite write = new ExpectedWrite(position, completed);
			expectedWrites.merge(write, 1, Integer::sum);
		}

		private void assertSatisfied() {
			assertTrue("Missing writes for " + id + ": " + expectedWrites,
					expectedWrites.isEmpty());
		}

		private void assertNoWrites() {
			assertEquals("Unexpected writes for " + id, 0, writeCount);
			assertSatisfied();
		}

		private void completePendingWrites() {
			for (Promise<Void> write : pendingWrites) write.complete(null);
			pendingWrites.clear();
		}

		private void movePendingWritesTo(List<Promise<Void>> writes) {
			writes.addAll(pendingWrites);
			pendingWrites.clear();
		}
	}

	private static final class ExpectedWrite {
		private final long position;
		private final boolean completed;

		private ExpectedWrite(long position, boolean completed) {
			this.position = position;
			this.completed = completed;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof ExpectedWrite other)) return false;
			return (position == other.position) && (completed == other.completed);
		}

		@Override
		public int hashCode() {
			return (Long.hashCode(position) * 31) + Boolean.hashCode(completed);
		}

		@Override
		public String toString() {
			return "ExpectedWrite{" + position + ", completed=" + completed + '}';
		}
	}
}
