package me.aap.fermata.media.service;

import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;

/** Decides whether an unsolicited engine callback may acquire playback ownership. */
final class ExternalPlaybackAdmission {
	private final Decision decision;
	@Nullable
	private final PlaybackOwnership.Token pendingOwner;
	@Nullable
	private final PlayableItem source;

	private ExternalPlaybackAdmission(Decision decision,
			@Nullable PlaybackOwnership.Token pendingOwner, @Nullable PlayableItem source) {
		this.decision = decision;
		this.pendingOwner = pendingOwner;
		this.source = source;
	}

	static ExternalPlaybackAdmission evaluate(@Nullable PlaybackOwnership.Token pendingOwner,
			PlaybackTransition transition, @Nullable PlayableItem source) {
		source = (source == null) ? null : PlayableItemResolver.unwrap(source);
		boolean transitionPending = transition.hasPending();
		boolean sourceMatches = (pendingOwner != null) && (source != null) &&
				(pendingOwner.itemIdentity() == source) && transition.isPending(source);
		return new ExternalPlaybackAdmission(decide(pendingOwner != null, transitionPending,
				sourceMatches), pendingOwner, source);
	}

	static Decision decide(boolean ownershipPending, boolean transitionPending,
			boolean sourceMatchesPending) {
		if (!ownershipPending) {
			return transitionPending ? Decision.REJECT_TRANSITION_WITHOUT_OWNER : Decision.OPEN;
		}
		if (!transitionPending) return Decision.REJECT_REQUEST_NOT_PUBLISHED;
		return sourceMatchesPending ? Decision.COMPLETE_PENDING :
				Decision.REJECT_PENDING_SOURCE_MISMATCH;
	}

	boolean accepted() {
		return decision.accepted;
	}

	String rejectionReason() {
		return decision.reason;
	}

	@Nullable
	PlaybackOwnership.Token pendingOwner() {
		return pendingOwner;
	}

	@Nullable
	PlayableItem source() {
		return source;
	}

	enum Decision {
		OPEN(true, ""),
		COMPLETE_PENDING(true, ""),
		REJECT_TRANSITION_WITHOUT_OWNER(false, "transition_without_owner"),
		REJECT_REQUEST_NOT_PUBLISHED(false, "request_not_published"),
		REJECT_PENDING_SOURCE_MISMATCH(false, "pending_source_mismatch");

		final boolean accepted;
		final String reason;

		Decision(boolean accepted, String reason) {
			this.accepted = accepted;
			this.reason = reason;
		}
	}
}
