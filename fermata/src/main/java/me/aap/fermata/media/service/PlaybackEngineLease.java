package me.aap.fermata.media.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

/**
 * Single-use ownership lease for an atomic engine-selection transaction.
 *
 * <p>It separates the pre-creation, selected, accepted and failure-claimed phases so a stale or
 * reentrant selection cannot replace the shared engine slot, retire its predecessor, or dispose
 * an addon-owned candidate without proving that the captured request is still current.</p>
 */
final class PlaybackEngineLease {
	private PlaybackEngineLease() {
	}

	enum Mode { REGULAR, TRANSPORT }

	enum CandidateDisposition { PREEXISTING_SLOT, BORROWED_ADDON_ENGINE, LEASE_OWNED_NEW }

	/** Live capabilities that must be re-read whenever a lease authorizes a side effect. */
	interface LiveState {
		boolean terminal();

		long requestRevision();

		@Nullable
		MediaEngine engineSlot();

		@Nullable
		PlaybackOwnership.Token activeOwner();

		@Nullable
		PlaybackOwnership.Token pendingOwner();

		PlaybackOwnership.StateToken captureState();

		boolean owns(PlaybackOwnership.StateToken state);

		boolean referencesEngine(MediaEngine engine);
	}

	record Captured(long requestRevision, @NonNull PlayableItem target,
			@Nullable MediaEngine preCreationSlot,
			@NonNull PlaybackOwnership.Token preCreationOwner,
			@NonNull PlaybackOwnership.StateToken preCreationState,
			@NonNull Mode mode) {
		Captured {
			target = PlayableItemResolver.unwrap(Objects.requireNonNull(target));
			Objects.requireNonNull(preCreationOwner);
			Objects.requireNonNull(preCreationState);
			Objects.requireNonNull(mode);
		}
	}

	record Selected(@NonNull Captured captured, @Nullable MediaEngine candidate,
			@NonNull CandidateDisposition disposition) {
		Selected {
			Objects.requireNonNull(captured);
			Objects.requireNonNull(disposition);
		}
	}

	record Accepted(@NonNull Selected selected,
			@NonNull PlaybackOwnership.Token acceptedOwner,
			@NonNull PlaybackOwnership.StateToken acceptedState) {
		Accepted {
			Objects.requireNonNull(selected);
			Objects.requireNonNull(acceptedOwner);
			Objects.requireNonNull(acceptedState);
			if (selected.candidate() == null) {
				throw new IllegalArgumentException("Accepted lease requires an engine candidate");
			}
		}

		long requestRevision() {
			return selected.captured().requestRevision();
		}

		@NonNull
		PlayableItem target() {
			return selected.captured().target();
		}

		@NonNull
		MediaEngine candidate() {
			return Objects.requireNonNull(selected.candidate());
		}

		Mode mode() {
			return selected.captured().mode();
		}
	}

	record FailureClaim(long requestRevision, @NonNull PlayableItem target,
			@Nullable MediaEngine detachedCandidate,
			@NonNull PlaybackOwnership.Token expectedPendingOwner,
			@NonNull AtomicBoolean consumed) {
		FailureClaim(long requestRevision, @NonNull PlayableItem target,
				@Nullable MediaEngine detachedCandidate,
				@NonNull PlaybackOwnership.Token expectedPendingOwner) {
			this(requestRevision, target, detachedCandidate, expectedPendingOwner,
					new AtomicBoolean());
		}

		FailureClaim {
			target = PlayableItemResolver.unwrap(Objects.requireNonNull(target));
			Objects.requireNonNull(expectedPendingOwner);
			Objects.requireNonNull(consumed);
		}

		boolean consume() {
			return consumed.compareAndSet(false, true);
		}
	}

	@Nullable
	static Captured capture(long requestRevision, @NonNull PlayableItem target,
			@Nullable MediaEngine preCreationSlot, @NonNull Mode mode,
			@NonNull LiveState live) {
		target = PlayableItemResolver.unwrap(Objects.requireNonNull(target));
		Objects.requireNonNull(mode);
		Objects.requireNonNull(live);
		if (live.terminal() || (live.requestRevision() != requestRevision) ||
				(live.engineSlot() != preCreationSlot)) return null;

		PlaybackOwnership.Token owner = (mode == Mode.REGULAR) ?
				live.pendingOwner() : live.activeOwner();
		if ((owner == null) || (owner.generation() != requestRevision) ||
				(owner.engineIdentity() != preCreationSlot)) return null;
		if (mode == Mode.REGULAR) {
			if (target.isPlaybackTransportCommand() || (owner.itemIdentity() != target)) return null;
		} else if (!target.isPlaybackTransportCommand()) {
			return null;
		}

		PlaybackOwnership.StateToken state = live.captureState();
		if ((state.owner() != owner) || !live.owns(state)) return null;
		Captured captured = new Captured(requestRevision, target, preCreationSlot, owner, state, mode);
		return ownsCapturedRequest(captured, live) ? captured : null;
	}

	static Selected select(@NonNull Captured captured, @Nullable MediaEngine candidate,
			@NonNull CandidateDisposition disposition) {
		Objects.requireNonNull(captured);
		Objects.requireNonNull(disposition);
		if ((candidate != null) && (candidate == captured.preCreationSlot()) &&
				(disposition != CandidateDisposition.PREEXISTING_SLOT)) {
			throw new IllegalArgumentException("Reused candidate must be marked PREEXISTING_SLOT");
		}
		if ((disposition == CandidateDisposition.PREEXISTING_SLOT) &&
				(candidate != captured.preCreationSlot())) {
			throw new IllegalArgumentException("PREEXISTING_SLOT must reference the captured slot");
		}
		return new Selected(captured, candidate, disposition);
	}

	static boolean canInstall(@NonNull Selected selected, @NonNull LiveState live) {
		Objects.requireNonNull(selected);
		Objects.requireNonNull(live);
		Captured captured = selected.captured();
		if (!ownsCapturedRequest(captured, live) || (selected.candidate() == null)) return false;
		if (captured.mode() == Mode.TRANSPORT) {
			MediaEngine candidate = selected.candidate();
			return (candidate == captured.preCreationSlot()) &&
					(captured.preCreationOwner().engineIdentity() == candidate);
		}
		return true;
	}

	@Nullable
	static Accepted accept(@NonNull Selected selected,
			@NonNull PlaybackOwnership.Token acceptedOwner,
			@NonNull PlaybackOwnership.StateToken acceptedState,
			@NonNull LiveState live) {
		Objects.requireNonNull(selected);
		Objects.requireNonNull(acceptedOwner);
		Objects.requireNonNull(acceptedState);
		Objects.requireNonNull(live);
		MediaEngine candidate = selected.candidate();
		if ((candidate == null) || (acceptedState.owner() != acceptedOwner) || live.terminal() ||
				(live.requestRevision() != selected.captured().requestRevision()) ||
				(live.engineSlot() != candidate) || !live.owns(acceptedState)) return null;

		Captured captured = selected.captured();
		if ((acceptedOwner.generation() != captured.requestRevision()) ||
				(acceptedOwner.engineIdentity() != candidate)) return null;
		if (captured.mode() == Mode.REGULAR) {
			if ((live.pendingOwner() != acceptedOwner) ||
					(acceptedOwner.itemIdentity() != captured.target())) return null;
		} else if ((live.activeOwner() != acceptedOwner) ||
				(acceptedOwner != captured.preCreationOwner())) {
			return null;
		}
		return new Accepted(selected, acceptedOwner, acceptedState);
	}

	static boolean isCurrent(@NonNull Accepted accepted, @NonNull LiveState live) {
		Objects.requireNonNull(accepted);
		Objects.requireNonNull(live);
		if (live.terminal() || (live.requestRevision() != accepted.requestRevision()) ||
				(live.engineSlot() != accepted.candidate()) || !live.owns(accepted.acceptedState())) {
			return false;
		}
		PlaybackOwnership.Token owner = (accepted.mode() == Mode.REGULAR) ?
				live.pendingOwner() : live.activeOwner();
		return owner == accepted.acceptedOwner();
	}

	@Nullable
	static FailureClaim claimFailure(@NonNull Accepted accepted,
			@NonNull MediaEngine detachedCandidate, @NonNull LiveState live) {
		Objects.requireNonNull(accepted);
		Objects.requireNonNull(detachedCandidate);
		Objects.requireNonNull(live);
		if ((accepted.mode() != Mode.REGULAR) ||
				(detachedCandidate != accepted.candidate()) || live.terminal() ||
				(live.requestRevision() != accepted.requestRevision()) ||
				(live.engineSlot() != null) ||
				(live.pendingOwner() != accepted.acceptedOwner()) ||
				!live.owns(accepted.acceptedState())) return null;
		return new FailureClaim(accepted.requestRevision(), accepted.target(), detachedCandidate,
				accepted.acceptedOwner());
	}

	@Nullable
	static FailureClaim claimUnsupported(@NonNull Selected selected, @NonNull LiveState live) {
		Objects.requireNonNull(selected);
		Objects.requireNonNull(live);
		Captured captured = selected.captured();
		if ((selected.candidate() != null) || (captured.mode() != Mode.REGULAR) ||
				live.terminal() || (live.requestRevision() != captured.requestRevision()) ||
				(live.engineSlot() != null) ||
				(live.pendingOwner() != captured.preCreationOwner()) ||
				!live.owns(captured.preCreationState())) return null;
		return new FailureClaim(captured.requestRevision(), captured.target(), null,
				captured.preCreationOwner());
	}

	static boolean canDisposeRejected(@NonNull Selected selected, @NonNull LiveState live) {
		Objects.requireNonNull(selected);
		Objects.requireNonNull(live);
		MediaEngine candidate = selected.candidate();
		return (candidate != null) &&
				(selected.disposition() == CandidateDisposition.LEASE_OWNED_NEW) &&
				(candidate != selected.captured().preCreationSlot()) &&
				(live.engineSlot() != candidate) && !live.referencesEngine(candidate);
	}

	static long preparationMetadataRevision(@NonNull Captured captured) {
		return Objects.requireNonNull(captured).requestRevision();
	}

	private static boolean ownsCapturedRequest(Captured captured, LiveState live) {
		if (live.terminal() || (live.requestRevision() != captured.requestRevision()) ||
				(live.engineSlot() != captured.preCreationSlot()) ||
				!live.owns(captured.preCreationState())) return false;
		PlaybackOwnership.Token owner = (captured.mode() == Mode.REGULAR) ?
				live.pendingOwner() : live.activeOwner();
		return owner == captured.preCreationOwner();
	}
}
