package me.aap.fermata.media.service;

import static me.aap.utils.async.Completed.completed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;

import me.aap.fermata.R;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.media.engine.EngineSelection;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.ProgressiveResultConsumer;

@RunWith(RobolectricTestRunner.class)
public class PlaybackEngineLeaseControllerTest {
	@Test
	public void mapsEveryEngineSelectionOwnership() {
		assertEquals(PlaybackEngineLease.CandidateDisposition.PREEXISTING_SLOT,
				PlaybackEngineLeaseController.mapDisposition(EngineSelection.Ownership.PREEXISTING));
		assertEquals(PlaybackEngineLease.CandidateDisposition.BORROWED_ADDON_ENGINE,
				PlaybackEngineLeaseController.mapDisposition(EngineSelection.Ownership.BORROWED));
		assertEquals(PlaybackEngineLease.CandidateDisposition.LEASE_OWNED_NEW,
				PlaybackEngineLeaseController.mapDisposition(EngineSelection.Ownership.OWNED_NEW));
		assertEquals(PlaybackEngineLease.CandidateDisposition.LEASE_OWNED_NEW,
				PlaybackEngineLeaseController.mapDisposition(EngineSelection.Ownership.NO_CANDIDATE));
	}

	@Test
	public void normalNonReentrantSequencePreservesObservableEngineOperations() {
		Fixture fixture = fixture();
		AtomicInteger position = new AtomicInteger();
		AtomicInteger focus = new AtomicInteger();
		AtomicInteger video = new AtomicInteger();
		AtomicInteger prepare = new AtomicInteger();
		MediaEngine candidate = engine("candidate", new Counters(null, position, focus, video, prepare));
		PlaybackEngineLease.Captured captured = fixture.controller.capture(
				fixture.revision, fixture.target, fixture.slot);
		assertNotNull(captured);
		PlaybackEngineLease.Accepted accepted = fixture.controller.tryAccept(
				fixture.controller.select(captured,
						new EngineSelection(candidate, EngineSelection.Ownership.OWNED_NEW)));

		assertNotNull(accepted);
		assertSame(candidate, fixture.access.slot);
		assertTrue(fixture.controller.isCurrent(accepted));
		candidate.setPosition(42L);
		assertTrue(fixture.controller.isCurrent(accepted));
		candidate.setVideoView(null);
		assertTrue(candidate.requestAudioFocus(null, null));
		assertTrue(fixture.controller.isCurrent(accepted));
		candidate.prepare(accepted.target());
		assertEquals(1, position.get());
		assertEquals(1, video.get());
		assertEquals(1, focus.get());
		assertEquals(1, prepare.get());
		assertSame(candidate, fixture.ownership.getPending().engineIdentity());
	}

	@Test
	public void rejectedCandidateDisposalHonorsProvenanceAndLiveAdoption() {
		for (EngineSelection.Ownership provenance : new EngineSelection.Ownership[]{
				EngineSelection.Ownership.OWNED_NEW, EngineSelection.Ownership.BORROWED}) {
			Fixture fixture = fixture();
			AtomicInteger closes = new AtomicInteger();
			MediaEngine candidate = engine("candidate", new Counters(closes, null, null, null, null));
			PlaybackEngineLease.Captured captured = fixture.controller.capture(
					fixture.revision, fixture.target, fixture.slot);
			fixture.access.revision++;
			assertNull(fixture.controller.tryAccept(fixture.controller.select(captured,
					new EngineSelection(candidate, provenance))));
			assertEquals((provenance == EngineSelection.Ownership.OWNED_NEW) ? 1 : 0, closes.get());
			assertSame(fixture.slot, fixture.access.slot);
		}

		Fixture adopted = fixture();
		AtomicInteger closes = new AtomicInteger();
		MediaEngine candidate = engine("adopted", new Counters(closes, null, null, null, null));
		PlaybackEngineLease.Captured captured = adopted.controller.capture(
				adopted.revision, adopted.target, adopted.slot);
		adopted.ownership.replaceEngine(adopted.slot, candidate);
		adopted.access.slot = candidate;
		assertNull(adopted.controller.tryAccept(adopted.controller.select(captured,
				new EngineSelection(candidate, EngineSelection.Ownership.OWNED_NEW))));
		assertEquals(0, closes.get());
	}

	@Test
	public void failureClaimsDetachBeforeCleanupAndRestoreCommittedRevision() {
		Fixture fixture = fixtureWithCommittedOwner();
		MediaEngine candidate = engine("candidate", new Counters(null, null, null, null, null));
		PlaybackEngineLease.Captured captured = fixture.controller.capture(
				fixture.revision, fixture.target, fixture.slot);
		PlaybackEngineLease.Accepted accepted = fixture.controller.tryAccept(
				fixture.controller.select(captured,
						new EngineSelection(candidate, EngineSelection.Ownership.OWNED_NEW)));
		PlaybackEngineLease.FailureClaim claim = fixture.controller.tryClaimFailure(accepted);

		assertNotNull(claim);
		assertNull(fixture.access.slot);
		PlaybackOwnership.RollbackResult result = fixture.controller.rollbackFailure(claim);
		assertNotNull(result);
		assertSame(fixture.committed, result.restoredOwner());
		assertEquals(fixture.committed.generation(), fixture.access.revision);
		assertNull(fixture.controller.rollbackFailure(claim));
	}

	@Test
	public void sameGenerationHandoffDuringRealPlayPreparedItemPreservesWinner() throws Exception {
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlayableItem target = item("target");
		AtomicInteger oldCloses = new AtomicInteger();
		MediaEngine initial = engine("initial", new Counters(oldCloses, null, null, null, null));
		MediaEngine winner = engine("winner", new Counters(null, null, null, null, null));
		AtomicInteger closes = new AtomicInteger();
		MediaEngine outerCandidate = engine("outer", new Counters(closes, null, null, null, null));
		PlaybackOwnership.Token pending = ownership.begin("addon", target, initial);
		MutableAccess access = new MutableAccess(pending.generation(), initial);
		PlaybackEngineLeaseController controller = new PlaybackEngineLeaseController(ownership, access);
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		set(callback, "playbackOwnership", ownership);
		set(callback, "playbackEngineLease", controller);
		set(callback, "playbackTransition", new PlaybackTransition());
		set(callback, "playbackRequestRevision", pending.generation());
		set(callback, "engine", initial);

		SelectionManager manager = allocate(SelectionManager.class);
		manager.selection = new EngineSelection(outerCandidate, EngineSelection.Ownership.OWNED_NEW,
				EngineSelection.Retirement.RETIRE_AFTER_RESOLUTION);
		manager.onSelect = () -> {
			assertTrue(ownership.replaceEngine(initial, winner));
			access.slot = winner;
			try { set(callback, "engine", winner); } catch (Exception e) { throw new AssertionError(e); }
		};
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) ->
						method.getName().equals("getMediaEngineManager") ? manager : defaultValue(method.getReturnType()));
		set(callback, "lib", lib);

		Method play = MediaSessionCallback.class.getDeclaredMethod("playPreparedItem", MediaEngine.class,
				PlayableItem.class, long.class, PlayableItem.class, long.class, int.class, long.class);
		play.setAccessible(true);
		play.invoke(callback, initial, target, 0L, null, -1L, 8, pending.generation());

		assertEquals(pending.generation(), ownership.getActive().generation());
		assertSame(winner, ownership.getActive().engineIdentity());
		assertSame(winner, access.slot);
		assertSame(winner, callback.getEngine());
		assertEquals("A stale/reentrant selection must retain the old owner", 0, oldCloses.get());
		assertEquals(1, closes.get());
	}

	@Test
	public void acceptedReplacementDetachesThenRetiresThePreviousOutputOwner() throws Exception {
		List<String> events = runDeferredRetirementPlayPreparedItem();

		assertOrdered(events, "select", "initial:detach", "initial:close", "video");
		assertEquals(1, occurrences(events, "initial:detach"));
		assertEquals(1, occurrences(events, "initial:close"));
	}

	@Test
	public void acceptedRetainedReplacementDoesNotCloseThePreviousOutputOwner() throws Exception {
		List<String> events = runNormalPlayPreparedItem(false, false, true,
				EngineSelection.Retirement.RETAIN, false, null, true);

		assertOrdered(events, "select", "initial:detach", "video");
		assertEquals(1, occurrences(events, "initial:detach"));
		assertEquals(0, occurrences(events, "initial:close"));
	}

	@Test
	public void unsupportedReplacementDetachesThenRetiresThePreviousOutputOwner() throws Exception {
		UnsupportedFixture fixture = unsupportedFixture(
				EngineSelection.Retirement.RETIRE_AFTER_RESOLUTION);

		playPreparedItem(fixture.callback, fixture.initial, fixture.target, fixture.revision);

		assertOrdered(fixture.events, "select", "initial:detach", "initial:close");
		assertEquals(1, occurrences(fixture.events, "initial:detach"));
		assertEquals(1, occurrences(fixture.events, "initial:close"));
		assertFalse(fixture.videoOutput.isBound(fixture.initial));
		assertNull(fixture.access.slot);
		assertNull(fixture.callback.getEngine());
		assertEquals(PlaybackStateCompat.STATE_ERROR, fixture.session.state.getState());
	}

	@Test
	public void retainedUnsupportedReplacementDetachesWithoutClosingThePreviousOutputOwner()
			throws Exception {
		UnsupportedFixture fixture = unsupportedFixture(EngineSelection.Retirement.RETAIN);

		playPreparedItem(fixture.callback, fixture.initial, fixture.target, fixture.revision);

		assertOrdered(fixture.events, "select", "initial:detach");
		assertEquals(1, occurrences(fixture.events, "initial:detach"));
		assertEquals(0, occurrences(fixture.events, "initial:close"));
		assertFalse(fixture.videoOutput.isBound(fixture.initial));
		assertNull(fixture.access.slot);
		assertNull(fixture.callback.getEngine());
		assertEquals(PlaybackStateCompat.STATE_ERROR, fixture.session.state.getState());
	}

	@Test
	public void staleUnsupportedReplacementRetainsThePreviousOutputOwner() throws Exception {
		UnsupportedFixture fixture = unsupportedFixture(
				EngineSelection.Retirement.RETIRE_AFTER_RESOLUTION);
		MediaEngine winner = engine("winner", new Counters(null, null, null, null, null));
		fixture.manager.onSelect = () -> {
			fixture.events.add("select");
			assertTrue(fixture.ownership.replaceEngine(fixture.initial, winner));
			fixture.access.engineSlot(winner);
		};

		playPreparedItem(fixture.callback, fixture.initial, fixture.target, fixture.revision);

		assertSame(winner, fixture.access.slot);
		assertSame(winner, fixture.callback.getEngine());
		assertTrue(fixture.videoOutput.isBound(fixture.initial));
		assertEquals(0, occurrences(fixture.events, "initial:detach"));
		assertEquals(0, occurrences(fixture.events, "initial:close"));
	}

	@Test
	public void normalRealPlayPreparedItemPreservesGoldenSideEffectOrder() throws Exception {
		assertEquals(List.of("select", "position:42", "stage-source", "video", "foreground", "focus", "state:8", "prepare"),
				runNormalPlayPreparedItem(true));
		assertEquals(List.of("select", "clear-surfaces", "video", "foreground", "focus", "state:8",
					"prepare", "queue"), runNormalPlayPreparedItem(false));
		assertEquals(List.of("select", "clear-surfaces", "video", "foreground", "focus", "state:8",
					"prepare"), runNormalPlayPreparedItemKeepingQueue());
	}

	@Test
	public void audioFocusFailureClosesOnlyLeaseOwnedCandidate() throws Exception {
		List<String> borrowed = runAudioFocusFailurePlayPreparedItem(
				EngineSelection.Ownership.BORROWED);
		assertTrue(borrowed.contains("candidate:detach"));
		assertEquals(0, occurrences(borrowed, "candidate:close"));

		List<String> owned = runAudioFocusFailurePlayPreparedItem(
				EngineSelection.Ownership.OWNED_NEW);
		assertOrdered(owned, "candidate:detach", "candidate:close");
		assertEquals(1, occurrences(owned, "candidate:close"));
	}

	private static List<String> runNormalPlayPreparedItem(boolean sameItem) throws Exception {
		return runNormalPlayPreparedItem(sameItem, false, false,
				EngineSelection.Retirement.RETAIN, false, null, true);
	}

	private static List<String> runNormalPlayPreparedItemKeepingQueue() throws Exception {
		return runNormalPlayPreparedItem(false, true, false,
				EngineSelection.Retirement.RETAIN, false, null, true);
	}

	private static List<String> runDeferredRetirementPlayPreparedItem() throws Exception {
		return runNormalPlayPreparedItem(false, false, true,
				EngineSelection.Retirement.RETIRE_AFTER_RESOLUTION, true, null, true);
	}

	private static List<String> runAudioFocusFailurePlayPreparedItem(
			EngineSelection.Ownership candidateOwnership) throws Exception {
		return runNormalPlayPreparedItem(false, false, true,
				EngineSelection.Retirement.RETAIN, false, candidateOwnership, false);
	}

	private static UnsupportedFixture unsupportedFixture(EngineSelection.Retirement retirement)
			throws Exception {
		List<String> events = new ArrayList<>();
		AtomicReference<PlayableItem> source = new AtomicReference<>();
		PlayableItem target = item("target");
		FakeVideoView view = allocate(FakeVideoView.class);
		view.events = events;
		MediaEngine initial = observableEngine("initial", events, source, view);
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlaybackOwnership.Token pending = ownership.begin("addon", target, initial);
		MutableAccess access = new MutableAccess(pending.generation(), initial);
		PlaybackEngineLeaseController controller = new PlaybackEngineLeaseController(ownership, access);
		SelectionManager manager = allocate(SelectionManager.class);
		manager.selection = new EngineSelection(null, EngineSelection.Ownership.NO_CANDIDATE,
				retirement);
		manager.onSelect = () -> events.add("select");
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getMediaEngineManager" -> manager;
					case "getContext" -> new TestContext();
					default -> defaultValue(method.getReturnType());
				});
		FakeSession session = allocate(FakeSession.class);
		session.events = events;
		FakeService service = allocate(FakeService.class);
		service.events = events;
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		access.callback = callback;
		set(callback, "listeners", new LinkedList<>());
		set(callback, "lib", lib);
		set(callback, "service", service);
		set(callback, "session", session);
		set(callback, "playbackOwnership", ownership);
		set(callback, "playbackEngineLease", controller);
		set(callback, "playbackTransition", new PlaybackTransition());
		PlaybackProgressPolicy progressPolicy = new PlaybackProgressPolicy(() -> 0L);
		set(callback, "progressPolicy", progressPolicy);
		set(callback, "progressCoordinator", new PlaybackProgressCoordinator(callback, progressPolicy,
				(task, delay) -> {}, item -> completed(0L), item -> false, lib));
		set(callback, "playbackAdvanceWatchdog",
				new PlaybackAdvanceWatchdog((task, delay) -> {}, () -> {}));
		set(callback, "playbackRequestRevision", pending.generation());
		set(callback, "engine", initial);
		set(callback, "playbackSnapshot", new PlaybackSnapshot(1L, null,
				new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED,
						0L, 1F).build(), null));
		VideoOutputCoordinator videoOutput = new VideoOutputCoordinator();
		videoOutput.add(view, 0);
		videoOutput.bind(initial, true);
		events.clear();
		set(callback, "videoOutput", videoOutput);
		return new UnsupportedFixture(events, target, initial, ownership, access, manager, callback,
				session, videoOutput, pending.generation());
	}

	private static void playPreparedItem(MediaSessionCallback callback, MediaEngine engine,
			PlayableItem target, long requestRevision) throws Exception {
		Method play = MediaSessionCallback.class.getDeclaredMethod("playPreparedItem", MediaEngine.class,
				PlayableItem.class, long.class, PlayableItem.class, long.class, int.class, long.class);
		play.setAccessible(true);
		play.invoke(callback, engine, target, 0L, null, -1L,
				PlaybackStateCompat.STATE_CONNECTING, requestRevision);
	}

	private static List<String> runNormalPlayPreparedItem(boolean sameItem, boolean keepQueue,
			boolean bindInitialOutput, EngineSelection.Retirement retirement,
			boolean assertAcceptedBeforeRetirement,
			@Nullable EngineSelection.Ownership candidateOwnership, boolean grantAudioFocus)
			throws Exception {
		List<String> events = new ArrayList<>();
		AtomicReference<PlayableItem> candidateSource = new AtomicReference<>();
		AtomicReference<MediaEngine> selectedEngine = new AtomicReference<>();
		AtomicReference<MediaEngine> candidateReference = new AtomicReference<>();
		FutureSupplier<List<MediaSessionCompat.QueueItem>> queue = immediateFuture(List.of());
		BrowsableItem parent = (BrowsableItem) Proxy.newProxyInstance(
				BrowsableItem.class.getClassLoader(), new Class<?>[]{BrowsableItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getQueue" -> queue;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		@SuppressWarnings("unchecked")
		FutureSupplier<MediaMetadataCompat>[] pendingMetadata = new FutureSupplier[1];
		pendingMetadata[0] = (FutureSupplier<MediaMetadataCompat>) Proxy.newProxyInstance(
				FutureSupplier.class.getClassLoader(), new Class<?>[]{FutureSupplier.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "main", "onSuccess" -> pendingMetadata[0];
					case "isDone", "isCancelled" -> false;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		PlayableItem target = (PlayableItem) Proxy.newProxyInstance(
				PlayableItem.class.getClassLoader(), new Class<?>[]{PlayableItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "isVideo" -> true;
					case "isPlaybackTransportCommand", "isExternal", "isTimerRequired" -> false;
					case "getParent" -> parent;
					case "getMediaData" -> pendingMetadata[0];
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "getId", "getName", "toString" -> "target";
					default -> defaultValue(method.getReturnType());
				});
		PlayableItem current = sameItem ? target : keepQueue ? (PlayableItem) Proxy.newProxyInstance(
				PlayableItem.class.getClassLoader(), new Class<?>[]{PlayableItem.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "isVideo" -> true;
					case "isPlaybackTransportCommand", "isExternal", "isTimerRequired" -> false;
					case "getParent" -> parent;
					case "getMediaData" -> pendingMetadata[0];
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "getId", "getName", "toString" -> "current";
					default -> defaultValue(method.getReturnType());
				}) : null;
		candidateSource.set(current);

		FakeVideoView view = allocate(FakeVideoView.class);
		view.events = events;
		MediaEngine initial = observableEngine("initial", events, candidateSource, view, () -> {
			if (assertAcceptedBeforeRetirement)
				assertSame(candidateReference.get(), selectedEngine.get());
		});
		MediaEngine candidate = sameItem ? initial :
				observableEngine("candidate", events, candidateSource, view);
		candidateReference.set(candidate);
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlaybackOwnership.Token pending = ownership.begin("addon", target, initial);
		MutableAccess access = new MutableAccess(pending.generation(), initial, selectedEngine);
		PlaybackEngineLeaseController controller = new PlaybackEngineLeaseController(ownership, access);

		SelectionManager manager = allocate(SelectionManager.class);
		manager.selection = new EngineSelection(candidate, (candidateOwnership != null) ?
				candidateOwnership : sameItem ?
				EngineSelection.Ownership.PREEXISTING : EngineSelection.Ownership.OWNED_NEW,
				retirement);
		manager.onSelect = () -> events.add("select");
		MediaLib lib = (MediaLib) Proxy.newProxyInstance(MediaLib.class.getClassLoader(),
				new Class<?>[]{MediaLib.class}, (proxy, method, args) ->
						method.getName().equals("getMediaEngineManager") ? manager :
								defaultValue(method.getReturnType()));

		FakeSession session = allocate(FakeSession.class);
		session.events = events;
		FakeService service = allocate(FakeService.class);
		service.events = events;
		service.grantAudioFocus = grantAudioFocus;
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		access.callback = callback;
		set(callback, "listeners", new LinkedList<>());
		set(callback, "lib", lib);
		set(callback, "service", service);
		set(callback, "session", session);
		set(callback, "playbackOwnership", ownership);
		set(callback, "playbackEngineLease", controller);
		set(callback, "playbackTransition", new PlaybackTransition());
		set(callback, "preparationStatus", new PlaybackPreparationStatus());
		PlaybackProgressPolicy progressPolicy = new PlaybackProgressPolicy(() -> 0L);
		set(callback, "progressPolicy", progressPolicy);
		set(callback, "progressCoordinator", new PlaybackProgressCoordinator(callback, progressPolicy,
				(task, delay) -> {}, item -> completed(0L), item -> false, lib));
		set(callback, "playbackAdvanceWatchdog",
				new PlaybackAdvanceWatchdog((task, delay) -> {}, () -> {}));
		set(callback, "playbackRequestRevision", pending.generation());
		set(callback, "engine", initial);
		set(callback, "playbackSnapshot", new PlaybackSnapshot(1L, current,
				new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED,
						7L, 1F).build(), null));
		VideoOutputCoordinator videoOutput = new VideoOutputCoordinator();
		videoOutput.add(view, 0);
		if (bindInitialOutput) {
			videoOutput.bind(initial, true);
			events.clear();
		}
		set(callback, "videoOutput", videoOutput);

		Method play = MediaSessionCallback.class.getDeclaredMethod("playPreparedItem", MediaEngine.class,
				PlayableItem.class, long.class, PlayableItem.class, long.class, int.class, long.class);
		play.setAccessible(true);
		play.invoke(callback, initial, target, 42L, current,
				(current == null) ? -1L : 7L, PlaybackStateCompat.STATE_CONNECTING,
				pending.generation());

		if (grantAudioFocus) {
			assertSame(candidate, callback.getEngine());
			assertSame(candidate, ownership.getPending().engineIdentity());
			assertEquals(PlaybackStateCompat.STATE_CONNECTING, session.state.getState());
			assertEquals(!sameItem && !keepQueue, session.queuePublished);
			if (keepQueue) {
				assertFalse(session.queuePublished);
				assertFalse(events.contains("queue"));
			}
		} else {
			assertNull(callback.getEngine());
			assertNull(ownership.getPending());
			assertEquals(PlaybackStateCompat.STATE_ERROR, session.state.getState());
		}
		return events;
	}

	@SuppressWarnings("unchecked")
	private static <T> FutureSupplier<T> immediateFuture(T value) {
		FutureSupplier<T>[] result = new FutureSupplier[1];
		result[0] = (FutureSupplier<T>) Proxy.newProxyInstance(FutureSupplier.class.getClassLoader(),
				new Class<?>[]{FutureSupplier.class}, (proxy, method, args) -> switch (method.getName()) {
					case "main" -> result[0];
					case "onSuccess" -> {
						((ProgressiveResultConsumer.Success<T>) args[0]).accept(value);
						yield result[0];
					}
					case "isDone" -> true;
					case "isCancelled" -> false;
					case "get" -> value;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> defaultValue(method.getReturnType());
				});
		return result[0];
	}

	private static MediaEngine observableEngine(String name, List<String> events,
			AtomicReference<PlayableItem> source, VideoView expectedView) {
		return observableEngine(name, events, source, expectedView, () -> {
		});
	}

	private static MediaEngine observableEngine(String name, List<String> events,
			AtomicReference<PlayableItem> source, VideoView expectedView, Runnable onClose) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getSource" -> source.get();
					case "setPosition" -> { events.add("position:" + args[0]); yield null; }
					case "setVideoView" -> {
						if (args[0] == null) {
							events.add(name + ":detach");
						} else {
							assertSame(expectedView, args[0]);
							events.add("video");
						}
						yield null;
					}
					case "close" -> {
						events.add(name + ":close");
						onClose.run();
						yield null;
					}
					case "requestAudioFocus" -> { events.add("focus"); yield true; }
					case "prepare" -> {
						assertSame(args[0], source.updateAndGet(current -> (PlayableItem) args[0]));
						events.add("prepare");
						yield null;
					}
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static Fixture fixture() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlayableItem target = item("target");
		MediaEngine slot = engine("slot", new Counters(null, null, null, null, null));
		PlaybackOwnership.Token pending = ownership.begin("addon", target, slot);
		MutableAccess access = new MutableAccess(pending.generation(), slot);
		return new Fixture(ownership, new PlaybackEngineLeaseController(ownership, access), access,
				target, slot, pending.generation(), null);
	}

	private static Fixture fixtureWithCommittedOwner() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		PlaybackOwnership.Token committed = ownership.adopt("old", new Object(), new Object());
		PlayableItem target = item("target");
		MediaEngine slot = engine("slot", new Counters(null, null, null, null, null));
		PlaybackOwnership.Token pending = ownership.begin("addon", target, slot);
		MutableAccess access = new MutableAccess(pending.generation(), slot);
		return new Fixture(ownership, new PlaybackEngineLeaseController(ownership, access), access,
				target, slot, pending.generation(), committed);
	}

	private static PlayableItem item(String name) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "isPlaybackTransportCommand", "isVideo" -> false;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "getId", "getName", "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static MediaEngine engine(String name, Counters counters) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
					case "close" -> { if (counters.close != null) counters.close.incrementAndGet(); yield null; }
					case "setPosition" -> { if (counters.position != null) counters.position.incrementAndGet(); yield null; }
					case "requestAudioFocus" -> { if (counters.focus != null) counters.focus.incrementAndGet(); yield true; }
					case "setVideoView" -> { if (counters.video != null) counters.video.incrementAndGet(); yield null; }
					case "prepare" -> { if (counters.prepare != null) counters.prepare.incrementAndGet(); yield null; }
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
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

	private static void assertOrdered(List<String> events, String... ordered) {
		int index = -1;
		for (String event : ordered) {
			int relative = events.subList(index + 1, events.size()).indexOf(event);
			if (relative < 0) throw new AssertionError("Missing event " + event + " in " + events);
			index += relative + 1;
		}
	}

	private static int occurrences(List<String> events, String expected) {
		int count = 0;
		for (String event : events) if (expected.equals(event)) count++;
		return count;
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}

	private static void set(Object target, String name, Object value) throws Exception {
		Field field = MediaSessionCallback.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static class FakeSession extends MediaSessionCompat {
		private List<String> events;
		private PlaybackStateCompat state;
		private boolean queuePublished;

		private FakeSession() { super(null, "test"); }

		@Override
		public void setActive(boolean active) {
		}

		@Override
		public void setPlaybackState(PlaybackStateCompat state) {
			this.state = state;
			if (state.getState() == PlaybackStateCompat.STATE_CONNECTING) {
				events.add("state:" + state.getState());
			}
		}

		@Override
		public void setMetadata(MediaMetadataCompat metadata) {
		}

		@Override
		public void setQueue(List<QueueItem> queue) {
			queuePublished = true;
			events.add("queue");
		}
	}

	private static class TestContext extends android.content.ContextWrapper {
		private TestContext() { super(RuntimeEnvironment.getApplication()); }

		@Override public android.content.res.Resources getResources() {
			return new android.content.res.Resources(super.getResources().getAssets(),
					super.getResources().getDisplayMetrics(), super.getResources().getConfiguration()) {
				@Override public String getString(int id, Object... args) {
					return (id == R.string.err_unsupported_source_type) ? "unsupported" :
							super.getString(id, args);
				}
			};
		}

	}

	private static class FakeService extends FermataMediaService {
		private List<String> events;
		private boolean grantAudioFocus = true;

		@Override
		void updateNotification(int state, PlayableItem item) {
		}

		@Override
		boolean requestPlaybackAudioFocus(MediaEngine engine,
				android.media.AudioManager audioManager,
				androidx.media.AudioFocusRequestCompat request, int state, PlayableItem item) {
			events.add("foreground");
			return grantAudioFocus && engine.requestAudioFocus(audioManager, request);
		}
	}

	private static class FakeVideoView extends VideoView {
		private List<String> events;

		private FakeVideoView() { super(null); }

		@Override
		public void clearPlaybackSurfaces() {
			events.add("clear-surfaces");
		}

		@Override
		public void clearPlaybackSurfaces(MediaEngine expectedEngine) {
			events.add("clear-surfaces");
		}

		@Override
		public void beginVideoSource(MediaEngine expectedEngine) {
			events.add("stage-source");
		}
	}

	private static final class MutableAccess implements PlaybackEngineLeaseController.Access {
		private long revision;
		private MediaEngine slot;
		private MediaSessionCallback callback;
		private final AtomicReference<MediaEngine> observedSlot;

		private MutableAccess(long revision, MediaEngine slot) {
			this(revision, slot, null);
		}

		private MutableAccess(long revision, MediaEngine slot,
				AtomicReference<MediaEngine> observedSlot) {
			this.revision = revision;
			this.slot = slot;
			this.observedSlot = observedSlot;
			if (observedSlot != null) observedSlot.set(slot);
		}

		@Override public boolean terminal() { return false; }
		@Override public long requestRevision() { return revision; }
		@Override public void requestRevision(long revision) { this.revision = revision; }
		@Override public MediaEngine engineSlot() { return slot; }
		@Override public void engineSlot(MediaEngine engine) {
			slot = engine;
			if (observedSlot != null) observedSlot.set(engine);
			if (callback != null) {
				try {
					set(callback, "engine", engine);
				} catch (Exception ex) {
					throw new AssertionError(ex);
				}
			}
		}
	}

	private static final class SelectionManager extends MediaEngineManager {
		private EngineSelection selection;
		private Runnable onSelect;

		private SelectionManager() { super(null); }

		@Override
		public EngineSelection createEngineSelection(MediaEngine current, PlayableItem item,
				MediaEngine.Listener listener) {
			onSelect.run();
			return selection;
		}
	}

	private record Counters(AtomicInteger close, AtomicInteger position, AtomicInteger focus,
			AtomicInteger video, AtomicInteger prepare) {}

	private record Fixture(PlaybackOwnership ownership, PlaybackEngineLeaseController controller,
			MutableAccess access, PlayableItem target, MediaEngine slot, long revision,
			PlaybackOwnership.Token committed) {}

	private record UnsupportedFixture(List<String> events, PlayableItem target, MediaEngine initial,
			PlaybackOwnership ownership, MutableAccess access, SelectionManager manager,
			MediaSessionCallback callback, FakeSession session, VideoOutputCoordinator videoOutput,
			long revision) {}
}
