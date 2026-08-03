package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemWrapper;
import me.aap.fermata.media.service.PlaybackEngineLease.Accepted;
import me.aap.fermata.media.service.PlaybackEngineLease.CandidateDisposition;
import me.aap.fermata.media.service.PlaybackEngineLease.Captured;
import me.aap.fermata.media.service.PlaybackEngineLease.FailureClaim;
import me.aap.fermata.media.service.PlaybackEngineLease.Mode;
import me.aap.fermata.media.service.PlaybackEngineLease.Selected;

public class PlaybackEngineLeaseTest {
	@Test
	public void sameGenerationHandoffDuringEngineSelectionPreservesWinnerAndRejectsOuterCandidate() {
		Fixture fixture = regularFixture();
		Captured captured = capture(fixture);
		EngineProbe winner = engine("handoff");
		EngineProbe outer = engine("outer-candidate");

		// Simulates replaceEngine() running synchronously inside createEngine().
		assertTrue(fixture.ownership.replaceEngine(fixture.initial.engine, winner.engine));
		fixture.live.engine = winner.engine;
		Selected selected = PlaybackEngineLease.select(captured, outer.engine,
				CandidateDisposition.LEASE_OWNED_NEW);

		assertEquals(captured.requestRevision(), fixture.live.revision);
		assertFalse(PlaybackEngineLease.canInstall(selected, fixture.live));
		assertSame(winner.engine, fixture.live.engine);
		assertTrue(fixture.ownership.owns(winner.engine, fixture.target));
		assertFalse(fixture.ownership.referencesEngine(outer.engine));
		if (PlaybackEngineLease.canDisposeRejected(selected, fixture.live)) outer.engine.close();

		assertEquals(1, outer.closes.get());
		assertEquals(0, winner.closes.get());
		assertEquals(0, outer.positions.get());
		assertEquals(0, outer.videoViews.get());
		assertEquals(0, outer.prepares.get());
	}

	@Test
	public void capturedLeaseRequiresCanonicalRequestSlotTokenAndState() {
		Fixture canonical = regularFixture();
		PlayableItem wrapped = new PlayableItemWrapper(canonical.target);
		assertNotNull(PlaybackEngineLease.capture(canonical.live.revision, wrapped,
				canonical.initial.engine, Mode.REGULAR, canonical.live));

		CaptureCase[] cases = {
				new CaptureCase("terminal", CaptureMutation.TERMINAL, false),
				new CaptureCase("stale revision", CaptureMutation.REVISION, false),
				new CaptureCase("different slot", CaptureMutation.SLOT, false),
				new CaptureCase("different target", CaptureMutation.TARGET, false),
				new CaptureCase("current state revision is captured", CaptureMutation.STATE, true),
				new CaptureCase("current canonical target", CaptureMutation.NONE, true)
		};

		for (CaptureCase test : cases) {
			Fixture fixture = regularFixture();
			PlayableItem target = fixture.target;
			switch (test.mutation) {
				case TERMINAL -> fixture.live.terminal = true;
				case REVISION -> fixture.live.revision++;
				case SLOT -> fixture.live.engine = engine("other-slot").engine;
				case TARGET -> target = item("different", false);
				case STATE -> fixture.ownership.reviseState();
				case NONE -> {
				}
			}
			assertEquals(test.name, test.accepted,
					PlaybackEngineLease.capture(fixture.live.revision, target,
							fixture.initial.engine, Mode.REGULAR, fixture.live) != null);
		}

		Fixture transport = transportFixture();
		Captured command = PlaybackEngineLease.capture(transport.live.revision, transport.target,
				transport.initial.engine, Mode.TRANSPORT, transport.live);
		assertNotNull(command);
		assertNull(PlaybackEngineLease.capture(transport.live.revision, transport.target,
				transport.initial.engine, Mode.REGULAR, transport.live));
		assertNull(PlaybackEngineLease.capture(canonical.live.revision, canonical.target,
				canonical.initial.engine, Mode.TRANSPORT, canonical.live));
	}

	@Test
	public void installDecisionRejectsEveryStaleSnapshotButKeepsHappyPaths() {
		InstallCase[] cases = {
				new InstallCase("reused current engine", InstallMutation.NONE, true, true),
				new InstallCase("new engine", InstallMutation.NONE, false, true),
				new InstallCase("newer generation", InstallMutation.NEWER_REQUEST, false, false),
				new InstallCase("slot changed", InstallMutation.SLOT_CHANGED, false, false),
				new InstallCase("same generation handoff", InstallMutation.HANDOFF, false, false),
				new InstallCase("state changed", InstallMutation.STATE_CHANGED, false, false),
				new InstallCase("pending committed", InstallMutation.COMMITTED, false, false)
		};

		for (InstallCase test : cases) {
			Fixture fixture = regularFixture();
			Captured captured = capture(fixture);
			EngineProbe candidate = test.reuse ? fixture.initial : engine("candidate");
			Selected selected = PlaybackEngineLease.select(captured, candidate.engine,
					test.reuse ? CandidateDisposition.PREEXISTING_SLOT :
							CandidateDisposition.LEASE_OWNED_NEW);
			switch (test.mutation) {
				case NEWER_REQUEST -> {
					PlaybackOwnership.Token newer = fixture.ownership.begin(new Object(),
							item("newer", false), fixture.initial.engine);
					fixture.live.revision = newer.generation();
				}
				case SLOT_CHANGED -> fixture.live.engine = engine("slot-winner").engine;
				case HANDOFF -> {
					MediaEngine handoff = engine("handoff").engine;
					assertTrue(fixture.ownership.replaceEngine(fixture.initial.engine, handoff));
					fixture.live.engine = handoff;
				}
				case STATE_CHANGED -> fixture.ownership.reviseState();
				case COMMITTED -> assertTrue(fixture.ownership.commit(
						fixture.initial.engine, fixture.target));
				case NONE -> {
				}
			}
			assertEquals(test.name, test.accepted,
					PlaybackEngineLease.canInstall(selected, fixture.live));
		}
	}

	@Test
	public void exactTokenBindRejectsEqualReplacementAndReferencesBothOwnershipSlots() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		Object committedItem = new Object();
		Object committedEngine = new Object();
		ownership.adopt(new Object(), committedItem, committedEngine);
		Object target = new Object();
		Object oldEngine = new Object();
		PlaybackOwnership.Token pending = ownership.begin(new Object(), target, oldEngine);
		PlaybackOwnership.Token equalButDifferent = new PlaybackOwnership.Token(
				pending.generation(), pending.addonIdentity(), pending.itemIdentity(),
				pending.engineIdentity());
		Object candidate = new Object();

		assertNull(ownership.bindEngine(equalButDifferent, candidate));
		PlaybackOwnership.Token bound = ownership.bindEngine(pending, candidate);
		assertNotNull(bound);
		assertSame(bound, ownership.getPending());
		assertTrue(ownership.referencesEngine(candidate));
		assertTrue(ownership.referencesEngine(committedEngine));
		assertFalse(ownership.referencesEngine(oldEngine));
		assertFalse(ownership.referencesEngine(new Object()));
		assertFalse(ownership.referencesEngine(null));
	}

	@Test
	public void transportAcceptanceRetainsTheActiveContentOwner() {
		Fixture fixture = transportFixture();
		Captured captured = capture(fixture, Mode.TRANSPORT);
		Selected retained = PlaybackEngineLease.select(captured, fixture.initial.engine,
				CandidateDisposition.PREEXISTING_SLOT);
		assertTrue(PlaybackEngineLease.canInstall(retained, fixture.live));
		PlaybackOwnership.Token owner = fixture.ownership.getActive();
		PlaybackOwnership.StateToken state = fixture.ownership.captureState();
		Accepted accepted = PlaybackEngineLease.accept(retained, owner, state, fixture.live);
		assertNotNull(accepted);
		assertTrue(PlaybackEngineLease.isCurrent(accepted, fixture.live));
		assertSame(owner, fixture.ownership.getActive());

		Selected replacement = PlaybackEngineLease.select(captured, engine("replacement").engine,
				CandidateDisposition.LEASE_OWNED_NEW);
		assertFalse(PlaybackEngineLease.canInstall(replacement, fixture.live));
	}

	@Test
	public void rejectedCandidateDisposalRequiresExclusiveUnadoptedOwnership() {
		DisposalCase[] cases = {
				new DisposalCase("exclusive unreferenced", DisposalMutation.NONE,
						CandidateDisposition.LEASE_OWNED_NEW, true),
				new DisposalCase("borrowed addon", DisposalMutation.NONE,
						CandidateDisposition.BORROWED_ADDON_ENGINE, false),
				new DisposalCase("candidate is live slot", DisposalMutation.LIVE_SLOT,
						CandidateDisposition.LEASE_OWNED_NEW, false),
				new DisposalCase("pending adopted candidate", DisposalMutation.PENDING_OWNER,
						CandidateDisposition.LEASE_OWNED_NEW, false),
				new DisposalCase("committed adopted candidate", DisposalMutation.COMMITTED_OWNER,
						CandidateDisposition.LEASE_OWNED_NEW, false)
		};

		for (DisposalCase test : cases) {
			Fixture fixture = regularFixture();
			Captured captured = capture(fixture);
			EngineProbe candidate = engine("candidate");
			Selected selected = PlaybackEngineLease.select(captured, candidate.engine,
					test.disposition);
			switch (test.mutation) {
				case LIVE_SLOT -> fixture.live.engine = candidate.engine;
				case PENDING_OWNER -> assertNotNull(fixture.ownership.bindEngine(
						captured.preCreationOwner(), candidate.engine));
				case COMMITTED_OWNER -> {
					PlaybackOwnership.Token committed = fixture.ownership.adopt(new Object(),
							fixture.target, candidate.engine);
					fixture.live.revision = committed.generation();
				}
				case NONE -> {
				}
			}
			assertEquals(test.name, test.disposable,
					PlaybackEngineLease.canDisposeRejected(selected, fixture.live));
		}

		Fixture reusedFixture = regularFixture();
		Selected reused = PlaybackEngineLease.select(capture(reusedFixture),
				reusedFixture.initial.engine, CandidateDisposition.PREEXISTING_SLOT);
		assertFalse(PlaybackEngineLease.canDisposeRejected(reused, reusedFixture.live));
	}

	@Test
	public void installedLeaseRejectsAllActionsAfterSameGenerationReplacement() {
		Fixture fixture = regularFixture();
		EngineProbe candidate = engine("candidate");
		Accepted accepted = accept(fixture, candidate);
		assertTrue(PlaybackEngineLease.isCurrent(accepted, fixture.live));

		EngineProbe winner = engine("winner");
		assertTrue(fixture.ownership.replaceEngine(candidate.engine, winner.engine));
		fixture.live.engine = winner.engine;
		assertFalse(PlaybackEngineLease.isCurrent(accepted, fixture.live));

		if (PlaybackEngineLease.isCurrent(accepted, fixture.live)) {
			candidate.engine.setPosition(1);
			candidate.engine.setVideoView(null);
			candidate.engine.prepare(fixture.target);
		}
		assertEquals(0, candidate.positions.get());
		assertEquals(0, candidate.videoViews.get());
		assertEquals(0, candidate.prepares.get());
	}

	@Test
	public void failureClaimRequiresDetachedCurrentLeaseAndIsSingleUse() {
		Fixture fixture = regularFixture();
		EngineProbe candidate = engine("candidate");
		Accepted accepted = accept(fixture, candidate);
		fixture.live.engine = null; // Future compare-before-close detachment.
		FailureClaim claim = PlaybackEngineLease.claimFailure(
				accepted, candidate.engine, fixture.live);
		assertNotNull(claim);
		assertSame(candidate.engine, claim.detachedCandidate());
		assertTrue(claim.consume());
		assertFalse(claim.consume());

		Fixture stale = regularFixture();
		EngineProbe shared = engine("shared");
		Accepted old = accept(stale, shared);
		PlaybackOwnership.Token newer = stale.ownership.begin(new Object(), stale.target, shared.engine);
		stale.live.revision = newer.generation();
		assertNull(PlaybackEngineLease.claimFailure(old, shared.engine, stale.live));
		assertEquals(0, shared.closes.get());
		assertSame(newer, stale.ownership.getPending());
	}

	@Test
	public void unsupportedFailureClaimCannotClearOrRollbackANewerSameItemRequest() {
		Fixture current = regularFixture();
		Captured captured = capture(current);
		Selected unsupported = PlaybackEngineLease.select(captured, null,
				CandidateDisposition.LEASE_OWNED_NEW);
		current.live.engine = null; // Future compare-clear succeeded.
		FailureClaim claim = PlaybackEngineLease.claimUnsupported(unsupported, current.live);
		assertNotNull(claim);
		assertSame(captured.preCreationOwner(), claim.expectedPendingOwner());

		Fixture stale = regularFixture();
		Captured old = capture(stale);
		Selected staleUnsupported = PlaybackEngineLease.select(old, null,
				CandidateDisposition.LEASE_OWNED_NEW);
		PlaybackOwnership.Token newer = stale.ownership.begin(new Object(), stale.target,
				stale.initial.engine);
		stale.live.revision = newer.generation();
		assertNull(PlaybackEngineLease.claimUnsupported(staleUnsupported, stale.live));
		assertSame(newer, stale.ownership.getPending());
	}

	@Test
	public void preparationMetadataKeepsTheOriginallyCapturedRevision() {
		Fixture fixture = regularFixture();
		Captured captured = capture(fixture);
		long original = captured.requestRevision();
		PlaybackOwnership.Token newer = fixture.ownership.begin(new Object(),
				item("newer", false), fixture.initial.engine);
		fixture.live.revision = newer.generation();

		assertFalse(original == fixture.live.revision);
		assertEquals(original, PlaybackEngineLease.preparationMetadataRevision(captured));
	}

	@Test
	public void selectedDispositionMustMatchAReusedCandidate() {
		Fixture fixture = regularFixture();
		Captured captured = capture(fixture);
		try {
			PlaybackEngineLease.select(captured, fixture.initial.engine,
					CandidateDisposition.LEASE_OWNED_NEW);
			fail("Expected reused-candidate disposition check");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		try {
			PlaybackEngineLease.select(captured, engine("new").engine,
					CandidateDisposition.PREEXISTING_SLOT);
			fail("Expected pre-existing disposition check");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static Accepted accept(Fixture fixture, EngineProbe candidate) {
		Captured captured = capture(fixture);
		Selected selected = PlaybackEngineLease.select(captured, candidate.engine,
				CandidateDisposition.LEASE_OWNED_NEW);
		assertTrue(PlaybackEngineLease.canInstall(selected, fixture.live));
		PlaybackOwnership.Token bound = fixture.ownership.bindEngine(
				captured.preCreationOwner(), candidate.engine);
		assertNotNull(bound);
		fixture.live.engine = candidate.engine;
		PlaybackOwnership.StateToken state = fixture.ownership.captureState();
		Accepted accepted = PlaybackEngineLease.accept(selected, bound, state, fixture.live);
		assertNotNull(accepted);
		return accepted;
	}

	private static Captured capture(Fixture fixture) {
		return capture(fixture, Mode.REGULAR);
	}

	private static Captured capture(Fixture fixture, Mode mode) {
		Captured captured = PlaybackEngineLease.capture(fixture.live.revision, fixture.target,
				fixture.initial.engine, mode, fixture.live);
		assertNotNull(captured);
		return captured;
	}

	private static Fixture regularFixture() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		EngineProbe initial = engine("initial");
		PlayableItem target = item("target", false);
		PlaybackOwnership.Token pending = ownership.begin(new Object(), target, initial.engine);
		MutableLiveState live = new MutableLiveState(ownership, pending.generation(), initial.engine);
		return new Fixture(ownership, live, initial, target);
	}

	private static Fixture transportFixture() {
		PlaybackOwnership ownership = new PlaybackOwnership();
		EngineProbe initial = engine("initial");
		PlayableItem content = item("content", false);
		PlaybackOwnership.Token active = ownership.adopt(new Object(), content, initial.engine);
		ownership.reviseState();
		MutableLiveState live = new MutableLiveState(ownership, active.generation(), initial.engine);
		return new Fixture(ownership, live, initial, item("transport", true));
	}

	private static PlayableItem item(String name, boolean transport) {
		return (PlayableItem) Proxy.newProxyInstance(PlayableItem.class.getClassLoader(),
				new Class<?>[]{PlayableItem.class}, (proxy, method, args) -> switch (method.getName()) {
					case "isPlaybackTransportCommand" -> transport;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "getId", "getName", "toString" -> name;
					default -> defaultValue(method.getReturnType());
				});
	}

	private static EngineProbe engine(String name) {
		return new EngineProbe(name);
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

	private static final class EngineProbe {
		final AtomicInteger closes = new AtomicInteger();
		final AtomicInteger positions = new AtomicInteger();
		final AtomicInteger videoViews = new AtomicInteger();
		final AtomicInteger prepares = new AtomicInteger();
		final MediaEngine engine;

		EngineProbe(String name) {
			engine = (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
					new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> switch (method.getName()) {
						case "close" -> closes.incrementAndGet();
						case "setPosition" -> positions.incrementAndGet();
						case "setVideoView" -> videoViews.incrementAndGet();
						case "prepare" -> prepares.incrementAndGet();
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						case "toString" -> name;
						default -> defaultValue(method.getReturnType());
					});
		}
	}

	private static final class MutableLiveState implements PlaybackEngineLease.LiveState {
		final PlaybackOwnership ownership;
		long revision;
		MediaEngine engine;
		boolean terminal;

		MutableLiveState(PlaybackOwnership ownership, long revision, MediaEngine engine) {
			this.ownership = ownership;
			this.revision = revision;
			this.engine = engine;
		}

		@Override
		public boolean terminal() {
			return terminal;
		}

		@Override
		public long requestRevision() {
			return revision;
		}

		@Override
		public MediaEngine engineSlot() {
			return engine;
		}

		@Override
		public PlaybackOwnership.Token activeOwner() {
			return ownership.getActive();
		}

		@Override
		public PlaybackOwnership.Token pendingOwner() {
			return ownership.getPending();
		}

		@Override
		public PlaybackOwnership.StateToken captureState() {
			return ownership.captureState();
		}

		@Override
		public boolean owns(PlaybackOwnership.StateToken state) {
			return ownership.owns(state);
		}

		@Override
		public boolean referencesEngine(MediaEngine engine) {
			return ownership.referencesEngine(engine);
		}
	}

	private record Fixture(PlaybackOwnership ownership, MutableLiveState live,
			EngineProbe initial, PlayableItem target) {}

	private enum CaptureMutation { NONE, TERMINAL, REVISION, SLOT, TARGET, STATE }

	private record CaptureCase(String name, CaptureMutation mutation, boolean accepted) {}

	private enum InstallMutation { NONE, NEWER_REQUEST, SLOT_CHANGED, HANDOFF, STATE_CHANGED,
		COMMITTED }

	private record InstallCase(String name, InstallMutation mutation, boolean reuse,
			boolean accepted) {}

	private enum DisposalMutation { NONE, LIVE_SLOT, PENDING_OWNER, COMMITTED_OWNER }

	private record DisposalCase(String name, DisposalMutation mutation,
			CandidateDisposition disposition, boolean disposable) {}
}
