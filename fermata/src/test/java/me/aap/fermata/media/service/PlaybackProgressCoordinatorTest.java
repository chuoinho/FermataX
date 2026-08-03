package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.service.ProgressOwnership.LastPlayedLease;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.LongSupplier;

public class PlaybackProgressCoordinatorTest {
	private static final long DURATION = 100_000L;

	@Test
	public void revisionSupersedesResolvedProgressButCommittedOutgoingStillPersists() {
		Fixture staleCurrent = new Fixture(true, false);
		LastPlayedLease currentLease = staleCurrent.ownership.captureLastPlayed(staleCurrent.item.item);
		staleCurrent.ownership.owned = false;
		staleCurrent.coordinator.applyResolvedProgress(staleCurrent.item.item, 42_000L, DURATION,
				currentLease, false, 1L);

		assertTrue(staleCurrent.libPrefs.writes.isEmpty());
		assertTrue(staleCurrent.parentPrefs.writes.isEmpty());
		assertTrue(staleCurrent.item.progressWrites.isEmpty());

		Fixture outgoing = new Fixture(true, false);
		LastPlayedLease outgoingLease = outgoing.ownership.captureLastPlayed(outgoing.item.item);
		outgoing.ownership.owned = false;
		outgoing.coordinator.applyResolvedProgress(outgoing.item.item, 43_000L, DURATION,
				outgoingLease, true, 1L);

		assertTrue(outgoing.libPrefs.writes.isEmpty());
		assertEquals(List.of(43_000L), outgoing.item.progressWrites);
	}

	@Test
	public void nextPlayableCompletionCannotMutatePrefsAfterOwnershipChanges() {
		Fixture fixture = new Fixture(true, true);
		LastPlayedLease lease = fixture.ownership.captureLastPlayed(fixture.item.item);
		fixture.coordinator.applyResolvedProgress(fixture.item.item, 99_000L, DURATION,
				lease, false, 1L);

		fixture.ownership.owned = false;
		fixture.item.next.complete(fixture.next.item);

		assertTrue(fixture.libPrefs.writes.isEmpty());
		assertTrue(fixture.next.parentPrefs.writes.isEmpty());
		assertTrue(fixture.item.itemPrefs.writes.isEmpty());
	}

	@Test
	public void pendingTargetCanPersistWithoutBeingCurrentEngineSource() {
		Fixture fixture = new Fixture(false, false);
		fixture.ownership.pending = true;
		fixture.ownership.currentSource = false;

		fixture.coordinator.applyResolvedProgress(fixture.item.item, 42_000L, DURATION,
				fixture.ownership.captureLastPlayed(fixture.item.item), false, 1L);

		assertFalse(fixture.ownership.currentSource);
		assertFalse(fixture.libPrefs.writes.isEmpty());
	}

	@Test
	public void previousItemCanPersistWithoutBeingCurrentEngineSource() {
		Fixture fixture = new Fixture(false, false);
		fixture.ownership.previous = true;
		fixture.ownership.currentSource = false;

		fixture.coordinator.applyResolvedProgress(fixture.item.item, 42_000L, DURATION,
				fixture.ownership.captureLastPlayed(fixture.item.item), false, 1L);

		assertFalse(fixture.ownership.currentSource);
		assertFalse(fixture.libPrefs.writes.isEmpty());
	}

	@Test
	public void stalePositionReadDoesNotWriteOrReschedule() {
		Fixture fixture = new Fixture(true, false);
		Promise<Long> position = new Promise<>();
		fixture.position = position;
		fixture.policy.bind(fixture.item.item, 1L, true);
		fixture.clock.advance(PlaybackProgressPolicy.CHECKPOINT_INTERVAL_MS);
		fixture.coordinator.scheduleProgressCheckpoint(fixture.item.item, 1L, 0L);
		fixture.scheduler.tasks.get(0).task.run();

		fixture.policy.bind(fixture.next.item, 2L, true);
		fixture.ownership.owned = false;
		position.complete(42_000L);

		assertTrue(fixture.item.progressWrites.isEmpty());
		assertEquals(1, fixture.scheduler.tasks.size());
	}

	@Test
	public void staleCheckpointWriteDoesNotReschedule() {
		Fixture fixture = new Fixture(true, false);
		fixture.item.delayWrites = true;
		fixture.position = completed(42_000L);
		fixture.policy.bind(fixture.item.item, 1L, true);
		fixture.clock.advance(PlaybackProgressPolicy.CHECKPOINT_INTERVAL_MS);
		fixture.coordinator.scheduleProgressCheckpoint(fixture.item.item, 1L, 0L);
		fixture.scheduler.tasks.get(0).task.run();

		assertEquals(List.of(42_000L), fixture.item.progressWrites);
		fixture.policy.bind(fixture.next.item, 2L, true);
		fixture.item.completeWrite();

		assertEquals(1, fixture.scheduler.tasks.size());
	}

	@Test
	public void negativePositionUsesLeaseOwnershipGate() {
		Fixture fixture = new Fixture(false, false);
		LastPlayedLease lease = fixture.ownership.captureLastPlayed(fixture.item.item);
		fixture.ownership.owned = false;

		fixture.coordinator.applyNegativeProgress(fixture.item.item, lease);

		assertEquals(1, fixture.ownership.checks);
		assertTrue(fixture.libPrefs.writes.isEmpty());
		assertTrue(fixture.parentPrefs.writes.isEmpty());
	}

	private static final class Fixture {
		private final FakeClock clock = new FakeClock();
		private final PlaybackProgressPolicy policy = new PlaybackProgressPolicy(clock);
		private final FakeOwnership ownership = new FakeOwnership();
		private final FakeScheduler scheduler = new FakeScheduler();
		private final PrefProbe libPrefs = new PrefProbe(MediaLibPrefs.class);
		private final PrefProbe parentPrefs = new PrefProbe(BrowsableItemPrefs.class);
		private final ItemProbe item;
		private final ItemProbe next;
		private final PlaybackProgressCoordinator coordinator;
		private FutureSupplier<Long> position = completed(42_000L);

		private Fixture(boolean managed, boolean pendingNext) {
			MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
					new Class<?>[]{MediaLib.class}, (proxy, method, args) -> switch (method.getName()) {
						case "getPrefs" -> libPrefs.proxy;
						default -> defaultValue(method.getReturnType());
					});
			item = new ItemProbe("item", managed, parentPrefs, lib, pendingNext);
			next = new ItemProbe("next", false, new PrefProbe(BrowsableItemPrefs.class), lib, false);
			coordinator = new PlaybackProgressCoordinator(ownership, policy, scheduler,
					ignored -> position, ignored -> false, lib);
		}
	}

	private static final class FakeOwnership implements ProgressOwnership {
		private boolean owned = true;
		private boolean pending;
		private boolean previous;
		private boolean currentSource = true;
		private int checks;

		@Override
		public LastPlayedLease captureLastPlayed(PlayableItem item) {
			return new LastPlayedLease(item, 1L);
		}

		@Override
		public boolean isStillLastPlayedOwner(LastPlayedLease lease) {
			checks++;
			return owned && (pending || previous || currentSource);
		}

		@Override
		public boolean isCurrentEngineSource(PlayableItem item) {
			return currentSource;
		}
	}

	private static final class FakeScheduler implements PlaybackProgressCoordinator.Scheduler {
		private final List<ScheduledTask> tasks = new ArrayList<>();

		@Override
		public void postDelayed(Runnable task, long delayMillis) {
			tasks.add(new ScheduledTask(task, delayMillis));
		}
	}

	private static final class FakeClock implements LongSupplier {
		private long now;

		@Override
		public long getAsLong() {
			return now;
		}

		private void advance(long millis) {
			now += millis;
		}
	}

	private static final class ItemProbe {
		private final PlayableItem item;
		private final PrefProbe itemPrefs = new PrefProbe(PlayableItemPrefs.class);
		private final PrefProbe parentPrefs;
		private final Promise<PlayableItem> next = new Promise<>();
		private final List<Long> progressWrites = new ArrayList<>();
		private final List<Promise<Void>> pendingWrites = new ArrayList<>();
		private boolean delayWrites;

		private ItemProbe(String id, boolean managed, PrefProbe parentPrefs, MediaLib lib,
				boolean pendingNext) {
			this.parentPrefs = parentPrefs;
			BrowsableItem parent = (BrowsableItem) Proxy.newProxyInstance(
					BrowsableItem.class.getClassLoader(), new Class<?>[]{BrowsableItem.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getPrefs" -> parentPrefs.proxy;
						default -> defaultValue(method.getReturnType());
					});
			item = (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
					new Class<?>[]{PlayableItem.class, PlaybackProgressItem.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getId", "getName", "toString" -> id;
						case "getLib" -> lib;
						case "getParent" -> parent;
						case "getPrefs" -> itemPrefs.proxy;
						case "getDuration" -> completed(DURATION);
						case "getNextPlayable" -> pendingNext ? next : completed((PlayableItem) null);
						case "getPlaybackProgressMode" -> managed ?
								PlaybackProgressItem.ProgressMode.MANAGED :
								PlaybackProgressItem.ProgressMode.LEGACY;
						case "savePlaybackProgress" -> saveProgress((long) args[0]);
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						default -> defaultValue(method.getReturnType());
					});
		}

		private FutureSupplier<Void> saveProgress(long position) {
			progressWrites.add(position);
			if (!delayWrites) return completedVoid();
			Promise<Void> pending = new Promise<>();
			pendingWrites.add(pending);
			return pending;
		}

		private void completeWrite() {
			pendingWrites.remove(0).complete(null);
		}
	}

	private static final class PrefProbe {
		private final Object proxy;
		private final List<String> writes = new ArrayList<>();

		private PrefProbe(Class<?> type) {
			proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
					(ignored, method, args) -> {
						if (method.getName().startsWith("set") ||
								method.getName().startsWith("apply")) {
							writes.add(method.getName());
						}
						if (method.getName().equals("getWatchedThresholdPref")) return 0;
						return defaultValue(method.getReturnType());
					});
		}
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return '\0';
		return null;
	}

	private record ScheduledTask(Runnable task, long delayMillis) {}
}
