package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackRequest;

public class PlaybackAttemptSupervisorTest {
	@Test
	public void reachesPlayingOnlyAfterPlayerReadyFirstFrameAndStarted() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = supervisor(scheduler);
		long id = supervisor.begin(descriptor("a"), 10L, ignored -> {});
		supervisor.preparationStarted(id);
		supervisor.dataReady(id, request("a", new AtomicInteger()));
		supervisor.playerReady(10L);
		supervisor.started(10L);
		assertEquals(PlaybackAttemptState.PLAYER_READY, supervisor.current().state());
		supervisor.firstFrame(10L);
		assertEquals(PlaybackAttemptState.PLAYING, supervisor.current().state());
		assertTrue(scheduler.tasks.get(0).cancelled);
	}

	@Test
	public void latchesFirstFrameThatArrivesBeforePlayerReady() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = supervisor(scheduler);
		long id = supervisor.begin(descriptor("early-frame"), 11L, ignored -> {});
		supervisor.preparationStarted(id);
		supervisor.dataReady(id, request("early-frame", new AtomicInteger()));
		supervisor.started(11L);
		supervisor.firstFrame(11L);
		assertEquals(PlaybackAttemptState.DATA_READY, supervisor.current().state());
		supervisor.playerReady(11L);
		assertEquals(PlaybackAttemptState.PLAYING, supervisor.current().state());
		assertTrue(scheduler.tasks.isEmpty());
	}

	@Test
	public void replacingAttemptClosesOldRequestAndRejectsItsCallbacks() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = supervisor(scheduler);
		AtomicInteger releasedA = new AtomicInteger();
		long a = supervisor.begin(descriptor("a"), 1L, ignored -> {});
		supervisor.preparationStarted(a);
		supervisor.dataReady(a, request("a", releasedA));

		long b = supervisor.begin(descriptor("b"), 2L, ignored -> {});
		assertEquals(1, releasedA.get());
		supervisor.dataReady(a, request("late-a", new AtomicInteger()));
		supervisor.firstFrame(1L);

		assertEquals(b, supervisor.currentOperationId());
		assertEquals(PlaybackAttemptState.CREATED, supervisor.current().state());
		assertEquals(2L, supervisor.staleCallbackCount());
	}

	@Test
	public void permitsOnlyOneDecoderFallback() {
		PlaybackAttemptSupervisor supervisor = supervisor(new ManualScheduler());
		long id = supervisor.begin(descriptor("a"), 7L, ignored -> {});
		supervisor.preparationStarted(id);
		supervisor.dataReady(id, request("a", new AtomicInteger()));
		supervisor.playerReady(7L);

		assertTrue(supervisor.claimDecoderFallback(7L));
		assertFalse(supervisor.claimDecoderFallback(7L));
		assertFalse(supervisor.claimDecoderFallback(8L));
		assertEquals(1, supervisor.current().decoderFallbacks());
	}

	@Test
	public void firstFrameDeadlineOffersOneFallbackAndReleasesOldTransport() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = supervisor(scheduler);
		AtomicReference<Throwable> reported = new AtomicReference<>();
		AtomicInteger releases = new AtomicInteger();
		long id = supervisor.begin(descriptor("a"), 3L, reported::set);
		supervisor.preparationStarted(id);
		supervisor.dataReady(id, request("a", releases));
		supervisor.playerReady(3L);

		scheduler.runPending();
		assertEquals(PlaybackAttemptState.PLAYER_READY, supervisor.current().state());
		assertNotNull(reported.get());
		assertTrue(supervisor.claimDecoderFallback(3L));
		assertEquals(PlaybackAttemptState.CREATED, supervisor.current().state());
		assertEquals(1, releases.get());
		assertFalse(supervisor.claimDecoderFallback(3L));
	}

	@Test
	public void torrentGetsBoundedP2pFirstFrameWindow() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = new PlaybackAttemptSupervisor(
				PlaybackAttemptObserver.NONE, scheduler, 10L, 40L);
		AtomicReference<Throwable> reported = new AtomicReference<>();
		AtomicInteger releases = new AtomicInteger();
		long id = supervisor.begin(descriptor("p2p", PlaybackDescriptor.TargetKind.TORRENT),
				4L, reported::set);
		supervisor.preparationStarted(id);
		supervisor.dataReady(id, request("p2p", releases));
		supervisor.playerReady(4L);

		assertEquals(40L, scheduler.tasks.get(0).delayMillis);
		scheduler.runPending();
		assertNotNull(reported.get());
		assertTrue(supervisor.claimDecoderFallback(4L));
		assertEquals(1, releases.get());
	}

	@Test
	public void fallbackAttemptOwnsFreshPreparationAndRejectsOldOperation() {
		ManualScheduler scheduler = new ManualScheduler();
		PlaybackAttemptSupervisor supervisor = supervisor(scheduler);
		AtomicInteger oldRelease = new AtomicInteger();
		long oldOperation = supervisor.begin(descriptor("a"), 9L, ignored -> {});
		supervisor.preparationStarted(oldOperation);
		supervisor.dataReady(oldOperation, request("old", oldRelease));
		supervisor.playerReady(9L);
		assertTrue(supervisor.claimDecoderFallback(9L));
		long fallbackOperation = supervisor.currentOperationId();
		assertTrue(fallbackOperation != oldOperation);
		supervisor.preparationStarted(oldOperation);
		supervisor.preparationStarted(fallbackOperation);
		supervisor.dataReady(fallbackOperation, request("fallback", new AtomicInteger()));
		assertEquals(PlaybackAttemptState.DATA_READY, supervisor.current().state());
		assertEquals(1, oldRelease.get());
		assertEquals(1L, supervisor.staleCallbackCount());
	}

	@Test
	public void tenDeterministicAbcSwitchesNeverAcceptPriorAttemptCallbacks() {
		PlaybackAttemptSupervisor supervisor = supervisor(new ManualScheduler());
		long previousId = -1L;
		long previousRevision = -1L;
		for (int cycle = 0; cycle < 10; cycle++) {
			for (String name : List.of("a", "b", "c")) {
				long revision = (cycle * 3L) + name.charAt(0);
				long id = supervisor.begin(descriptor(name + cycle), revision, ignored -> {});
				if (previousId >= 0L) {
					supervisor.preparationStarted(previousId);
					supervisor.firstFrame(previousRevision);
					assertEquals(id, supervisor.currentOperationId());
					assertEquals(PlaybackAttemptState.CREATED, supervisor.current().state());
				}
				previousId = id;
				previousRevision = revision;
			}
		}
		assertEquals(58L, supervisor.staleCallbackCount());
	}

	private static PlaybackAttemptSupervisor supervisor(ManualScheduler scheduler) {
		return new PlaybackAttemptSupervisor(PlaybackAttemptObserver.NONE, scheduler, 10L);
	}

	private static RemotePlaybackRequest request(String id, AtomicInteger releases) {
		URI target = URI.create("https://example.invalid/" + id + ".mp4");
		PlaybackRequestProfile profile = PlaybackRequestProfile.builder(target, id).build();
		return new RemotePlaybackRequest(target, profile, null, null,
				releases::incrementAndGet);
	}

	private static PlaybackDescriptor descriptor(String id) {
		return descriptor(id, PlaybackDescriptor.TargetKind.DIRECT_HTTP);
	}

	private static PlaybackDescriptor descriptor(String id,
			PlaybackDescriptor.TargetKind targetKind) {
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				"source", "movie", id, id);
		StreamProvider provider = new StreamProvider(
				"source", "addon.test", "Provider", 0, true);
		return new PlaybackDescriptor("descriptor-" + id, "selection-" + id, identity,
				"source", "Provider", null, null,
				new StremioPlaybackMetadata("Movie " + id, null, 0L),
				targetKind,
				"https://example.invalid/" + id + ".mp4", null, null, null,
				0L, 1_000L, provider);
	}

	private static final class ManualScheduler
			implements PlaybackAttemptSupervisor.DeadlineScheduler {
		private final List<Task> tasks = new ArrayList<>();

		@Override
		public PlaybackAttemptSupervisor.Cancellable schedule(Runnable task, long delayMillis) {
			Task scheduled = new Task(task, delayMillis);
			tasks.add(scheduled);
			return () -> scheduled.cancelled = true;
		}

		void runPending() {
			for (Task task : List.copyOf(tasks)) {
				if (!task.cancelled) task.runnable.run();
			}
		}
	}

	private static final class Task {
		private final Runnable runnable;
		private final long delayMillis;
		private boolean cancelled;

		private Task(Runnable runnable, long delayMillis) {
			this.runnable = runnable;
			this.delayMillis = delayMillis;
		}
	}
}
