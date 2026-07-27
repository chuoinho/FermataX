package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.aap.utils.async.Completed.completedVoid;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.LongSupplier;

public class PlaybackProgressPolicyTest {
	private static final long DURATION = 600_000L;

	@Test
	public void legacyItemsKeepTheOneSecondCompletionRule() {
		ProgressProbe probe = new ProgressProbe("legacy", false, false);

		PlaybackProgressPolicy.ProgressValue before =
				PlaybackProgressPolicy.normalize(probe.progress, 598_999L, DURATION);
		PlaybackProgressPolicy.ProgressValue complete =
				PlaybackProgressPolicy.normalize(probe.progress, 599_000L, DURATION);

		assertFalse(before.completed());
		assertEquals(598_999L, before.position());
		assertTrue(complete.completed());
		assertEquals(0L, complete.position());
	}

	@Test
	public void managedItemsUseSixtySecondsOrNinetyFivePercent() {
		ProgressProbe probe = new ProgressProbe("managed", true, false);
		long threshold = PlaybackProgressPolicy.completionThreshold(DURATION);

		assertEquals(570_000L, threshold);
		assertFalse(PlaybackProgressPolicy.normalize(probe.progress, threshold - 1L, DURATION)
				.completed());
		PlaybackProgressPolicy.ProgressValue complete =
				PlaybackProgressPolicy.normalize(probe.progress, threshold, DURATION);
		assertTrue(complete.completed());
		assertEquals(0L, complete.position());
		assertFalse(PlaybackProgressPolicy.normalize(probe.progress, 590_000L, -1L)
				.completed());
	}

	@Test
	public void fakeClockEnforcesFifteenSecondCheckpoints() {
		FakeClock clock = new FakeClock(1_000L);
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		ProgressProbe probe = new ProgressProbe("A", true, false);

		assertTrue(policy.bind(probe.item, 7L, true));
		assertEquals(15_000L, policy.getCheckpointDelay(probe.item, 7L));
		policy.checkpoint(probe.item, 7L, 10_000L, DURATION);
		assertEquals(0, probe.writes.size());

		clock.advance(14_999L);
		assertEquals(1L, policy.getCheckpointDelay(probe.item, 7L));
		clock.advance(1L);
		policy.checkpoint(probe.item, 7L, 25_000L, DURATION);
		assertEquals(List.of(new Write(25_000L, false)), probe.writes);

		policy.checkpoint(probe.item, 7L, 26_000L, DURATION);
		assertEquals(1, probe.writes.size());
		clock.advance(15_000L);
		policy.checkpoint(probe.item, 7L, 40_000L, DURATION);
		assertEquals(List.of(new Write(25_000L, false), new Write(40_000L, false)),
				probe.writes);
	}

	@Test
	public void staleItemAndGenerationCannotCheckpointNewOwner() {
		FakeClock clock = new FakeClock(0L);
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		ProgressProbe a = new ProgressProbe("A", true, false);
		ProgressProbe b = new ProgressProbe("B", true, false);

		policy.bind(a.item, 1L, true);
		clock.advance(15_000L);
		policy.bind(b.item, 2L, true);
		clock.advance(15_000L);
		policy.checkpoint(a.item, 1L, 20_000L, DURATION);
		policy.checkpoint(b.item, 1L, 21_000L, DURATION);
		assertTrue(a.writes.isEmpty());
		assertTrue(b.writes.isEmpty());

		policy.checkpoint(b.item, 2L, 22_000L, DURATION);
		assertEquals(List.of(new Write(22_000L, false)), b.writes);
		assertEquals(List.of(2L), b.generations);
	}

	@Test
	public void lifecycleFlushQueuesBehindCheckpointWithoutParallelWriter() {
		FakeClock clock = new FakeClock(0L);
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		ProgressProbe probe = new ProgressProbe("A", true, true);

		policy.bind(probe.item, 1L, true);
		clock.advance(15_000L);
		FutureSupplier<Void> checkpoint =
				policy.checkpoint(probe.item, 1L, 20_000L, DURATION);
		FutureSupplier<Void> lifecycle =
				policy.lifecycle(probe.item, 2L, 35_000L, DURATION, true, false);

		assertEquals(List.of(new Write(20_000L, false)), probe.writes);
		assertFalse(checkpoint.isDone());
		assertFalse(lifecycle.isDone());
		probe.completeNext();
		assertTrue(checkpoint.isDoneNotFailed());
		assertEquals(List.of(new Write(20_000L, false), new Write(35_000L, false)),
				probe.writes);
		assertFalse(lifecycle.isDone());
		probe.completeNext();
		assertTrue(lifecycle.isDoneNotFailed());
	}

	@Test
	public void replayedItemRejectsOlderQueuedCommittedOutgoingWrite() {
		FakeClock clock = new FakeClock(0L);
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		ProgressProbe a = new ProgressProbe("A", true, true);
		ProgressProbe b = new ProgressProbe("B", true, false);
		PlayableItem replayedA = a.recreateItem();

		policy.bind(a.item, 1L, true);
		clock.advance(15_000L);
		FutureSupplier<Void> first = policy.checkpoint(a.item, 1L, 10_000L, DURATION);
		policy.bind(b.item, 2L, true);
		FutureSupplier<Void> staleOutgoing =
				policy.lifecycle(a.item, 2L, 20_000L, DURATION, false, true);
		policy.bind(replayedA, 3L, true);
		FutureSupplier<Void> replayed =
				policy.lifecycle(replayedA, 3L, 30_000L, DURATION, true, false);

		assertEquals(List.of(new Write(10_000L, false)), a.writes);
		a.completeNext();
		assertTrue(first.isDoneNotFailed());
		assertTrue(staleOutgoing.isDoneNotFailed());
		assertEquals(List.of(new Write(10_000L, false), new Write(30_000L, false)), a.writes);
		assertFalse(replayed.isDone());
		a.completeNext();
		assertTrue(replayed.isDoneNotFailed());
		assertEquals(new Write(30_000L, false), a.writes.get(a.writes.size() - 1));
		assertEquals(List.of(new Write(10_000L, false), new Write(30_000L, false)),
				a.persistedWrites);
		assertEquals(new Write(30_000L, false), a.persisted);
		assertTrue(b.writes.isEmpty());
	}

	@Test
	public void clearPreventsQueuedWriteFromBecomingValidAgain() {
		FakeClock clock = new FakeClock(0L);
		PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		ProgressProbe probe = new ProgressProbe("A", true, true);

		policy.bind(probe.item, 1L, true);
		clock.advance(15_000L);
		FutureSupplier<Void> checkpoint =
				policy.checkpoint(probe.item, 1L, 10_000L, DURATION);
		FutureSupplier<Void> queued =
				policy.lifecycle(probe.item, 1L, 20_000L, DURATION, false, true);

		policy.clear();
		policy.bind(probe.item, 1L, true);
		probe.completeNext();

		assertTrue(checkpoint.isDoneNotFailed());
		assertTrue(queued.isDoneNotFailed());
		assertEquals(List.of(new Write(10_000L, false)), probe.writes);
		assertEquals(List.of(new Write(10_000L, false)), probe.persistedWrites);
	}

	private static final class FakeClock implements LongSupplier {
		private long now;

		private FakeClock(long now) {
			this.now = now;
		}

		@Override
		public long getAsLong() {
			return now;
		}

		private void advance(long millis) {
			now += millis;
		}
	}

	private static final class ProgressProbe {
		private final String id;
		private final boolean managed;
		private final boolean delayed;
		private final PlaybackProgressItem progress;
		private final PlayableItem item;
		private final List<Write> writes = new ArrayList<>();
		private final List<Write> persistedWrites = new ArrayList<>();
		private final List<Long> generations = new ArrayList<>();
		private final List<PendingWrite> pending = new ArrayList<>();
		private Write persisted;

		private ProgressProbe(String id, boolean managed, boolean delayed) {
			this.id = id;
			this.managed = managed;
			this.delayed = delayed;
			item = createItem();
			progress = (PlaybackProgressItem) item;
		}

		private PlayableItem recreateItem() {
			return createItem();
		}

		private PlayableItem createItem() {
			return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
					new Class<?>[]{PlayableItem.class, PlaybackProgressItem.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getPlaybackProgressMode" -> managed ?
								PlaybackProgressItem.ProgressMode.MANAGED :
								PlaybackProgressItem.ProgressMode.LEGACY;
						case "savePlaybackProgress" -> {
							if (args.length == 3) generations.add((long) args[2]);
							writes.add(new Write((long) args[0], (boolean) args[1]));
							Write value = writes.get(writes.size() - 1);
							if (!delayed) {
								persist(value);
								yield completedVoid();
							}
							Promise<Void> write = new Promise<>();
							pending.add(new PendingWrite(value, write));
							yield write;
						}
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						case "toString", "getName", "getId" -> id;
						default -> null;
					});
		}

		private void completeNext() {
			PendingWrite write = pending.remove(0);
			persist(write.value());
			write.completion().complete(null);
		}

		private void persist(Write value) {
			persisted = value;
			persistedWrites.add(value);
		}
	}

	private record Write(long position, boolean completed) {}

	private record PendingWrite(Write value, Promise<Void> completion) {}
}
