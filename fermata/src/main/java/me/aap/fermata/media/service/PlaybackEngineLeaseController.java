package me.aap.fermata.media.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.EngineSelection;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** Applies {@link PlaybackEngineLease} against the callback's live mutable state. */
final class PlaybackEngineLeaseController {
	interface Access {
		boolean terminal();

		long requestRevision();

		void requestRevision(long revision);

		@Nullable
		MediaEngine engineSlot();

		void engineSlot(@Nullable MediaEngine engine);
	}

	private final PlaybackOwnership ownership;
	private final Access access;
	private final PlaybackEngineLease.LiveState live = new PlaybackEngineLease.LiveState() {
		@Override public boolean terminal() { return access.terminal(); }
		@Override public long requestRevision() { return access.requestRevision(); }
		@Override public MediaEngine engineSlot() { return access.engineSlot(); }
		@Override public PlaybackOwnership.Token activeOwner() { return ownership.getActive(); }
		@Override public PlaybackOwnership.Token pendingOwner() { return ownership.getPending(); }
		@Override public PlaybackOwnership.StateToken captureState() {
			return ownership.captureState();
		}
		@Override public boolean owns(PlaybackOwnership.StateToken state) {
			return ownership.owns(state);
		}
		@Override public boolean referencesEngine(MediaEngine engine) {
			return ownership.referencesEngine(engine);
		}
	};

	PlaybackEngineLeaseController(@NonNull PlaybackOwnership ownership,
			@NonNull Access access) {
		this.ownership = ownership;
		this.access = access;
	}

	@Nullable
	PlaybackEngineLease.Captured capture(long requestRevision, @NonNull PlayableItem target,
			@Nullable MediaEngine preCreationSlot) {
		PlaybackEngineLease.Mode mode = target.isPlaybackTransportCommand() ?
				PlaybackEngineLease.Mode.TRANSPORT : PlaybackEngineLease.Mode.REGULAR;
		return PlaybackEngineLease.capture(requestRevision, target, preCreationSlot, mode, live);
	}

	PlaybackEngineLease.Selected select(@NonNull PlaybackEngineLease.Captured captured,
			@NonNull EngineSelection selection) {
		return PlaybackEngineLease.select(captured, selection.candidate(),
				mapDisposition(selection.ownership()));
	}

	@Nullable
	PlaybackEngineLease.Accepted tryAccept(@NonNull PlaybackEngineLease.Selected selected) {
		if (!PlaybackEngineLease.canInstall(selected, live)) {
			disposeRejected(selected);
			return null;
		}
		PlaybackEngineLease.Captured captured = selected.captured();
		MediaEngine candidate = selected.candidate();
		if (candidate == null) return null;
		PlaybackOwnership.Token acceptedOwner;
		if (captured.mode() == PlaybackEngineLease.Mode.REGULAR) {
			acceptedOwner = ownership.bindEngine(captured.preCreationOwner(), candidate);
			if (acceptedOwner == null) {
				disposeRejected(selected);
				return null;
			}
			if (access.engineSlot() != captured.preCreationSlot()) {
				ownership.rollback(acceptedOwner);
				disposeRejected(selected);
				return null;
			}
			access.engineSlot(candidate);
		} else {
			acceptedOwner = captured.preCreationOwner();
		}
		PlaybackEngineLease.Accepted accepted = PlaybackEngineLease.accept(selected, acceptedOwner,
				ownership.captureState(), live);
		if (accepted != null) return accepted;
		throw new IllegalStateException("Engine lease changed without a reentrant boundary");
	}

	boolean isCurrent(@NonNull PlaybackEngineLease.Accepted accepted) {
		return PlaybackEngineLease.isCurrent(accepted, live);
	}

	@Nullable
	PlaybackEngineLease.FailureClaim tryClaimUnsupported(
			@NonNull PlaybackEngineLease.Selected selected) {
		PlaybackEngineLease.Captured captured = selected.captured();
		if ((selected.candidate() != null) ||
				(captured.mode() != PlaybackEngineLease.Mode.REGULAR) || access.terminal() ||
				(access.requestRevision() != captured.requestRevision()) ||
				(access.engineSlot() != captured.preCreationSlot()) ||
				(ownership.getPending() != captured.preCreationOwner()) ||
				!ownership.owns(captured.preCreationState())) return null;
		access.engineSlot(null);
		return PlaybackEngineLease.claimUnsupported(selected, live);
	}

	@Nullable
	PlaybackEngineLease.FailureClaim tryClaimFailure(
			@NonNull PlaybackEngineLease.Accepted accepted) {
		if (!isCurrent(accepted) || (access.engineSlot() != accepted.candidate())) return null;
		access.engineSlot(null);
		return PlaybackEngineLease.claimFailure(accepted, accepted.candidate(), live);
	}

	@Nullable
	PlaybackOwnership.RollbackResult rollbackFailure(
			@NonNull PlaybackEngineLease.FailureClaim claim) {
		PlaybackOwnership.RollbackResult result = ownership.rollback(claim.expectedPendingOwner());
		if (result != null) access.requestRevision(result.restoredRevision());
		return result;
	}

	/**
	 * Releases a failed candidate only after its failure claim has rolled it back out of live
	 * ownership. Item/custom-provider engines are borrowed and must stay available to their owner.
	 */
	void disposeFailed(@NonNull PlaybackEngineLease.Accepted accepted) {
		PlaybackEngineLease.Selected selected = accepted.selected();
		if (PlaybackEngineLease.canDisposeRejected(selected, live)) accepted.candidate().close();
	}

	private void disposeRejected(PlaybackEngineLease.Selected selected) {
		MediaEngine candidate = selected.candidate();
		if ((candidate != null) && PlaybackEngineLease.canDisposeRejected(selected, live)) {
			candidate.close();
		}
	}

	static PlaybackEngineLease.CandidateDisposition mapDisposition(
			EngineSelection.Ownership ownership) {
		return switch (ownership) {
			case PREEXISTING -> PlaybackEngineLease.CandidateDisposition.PREEXISTING_SLOT;
			case BORROWED -> PlaybackEngineLease.CandidateDisposition.BORROWED_ADDON_ENGINE;
			case OWNED_NEW, NO_CANDIDATE ->
					PlaybackEngineLease.CandidateDisposition.LEASE_OWNED_NEW;
		};
	}
}
