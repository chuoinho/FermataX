package me.aap.fermata.ui.voice;

import androidx.annotation.Nullable;

/** Pure generation/state gate for one explicitly initiated global voice interaction. */
public final class VoiceInteractionTransaction {
	public enum State {
		IDLE, LISTENING, ROUTING, RESOLVING, CLARIFYING, EXECUTING,
		COMPLETED, FAILED, CANCELLED
	}

	private long generation;
	private State state = State.IDLE;
	@Nullable private String target;

	public void begin(long generation) {
		this.generation = generation;
		state = State.LISTENING;
		target = null;
	}

	public void onOutcome(long requestId, @Nullable VoiceIntent intent,
			VoiceCommandOutcome outcome) {
		if (!isCurrent(requestId)) return;
		if ((intent != null) && ((intent.getKind() == VoiceIntent.Kind.ADDON_SEARCH) ||
				(intent.getKind() == VoiceIntent.Kind.OPEN_ADDON))) target = intent.getAddon();
		if ((outcome == null) || !outcome.isHandled()) {
			state = State.FAILED;
			return;
		}
		state = switch (outcome.getStatus()) {
			case ASYNC_PENDING -> State.RESOLVING;
			case AWAITING_SELECTION -> State.CLARIFYING;
			case COMPLETED -> State.COMPLETED;
			case UNHANDLED -> State.FAILED;
		};
	}

	public boolean beginClarification(long requestId, @Nullable String resultTarget) {
		if (!isCurrent(requestId) || (state != State.RESOLVING)) return false;
		if ((target != null) && (resultTarget != null) && !target.equals(resultTarget)) return false;
		state = State.CLARIFYING;
		return true;
	}

	public void beginExecuting(long requestId) {
		if (isCurrent(requestId)) state = State.EXECUTING;
	}

	public void complete(long requestId) {
		if (isCurrent(requestId)) state = State.COMPLETED;
	}

	public void cancel() {
		if (isActive()) state = State.CANCELLED;
	}

	public boolean isCurrent(long requestId) {
		return (requestId != 0L) && (requestId == generation) && isActive();
	}

	public boolean isActive() {
		return switch (state) {
			case LISTENING, ROUTING, RESOLVING, CLARIFYING, EXECUTING -> true;
			default -> false;
		};
	}

	public boolean isClarifying() {
		return state == State.CLARIFYING;
	}

	public long getGeneration() {
		return generation;
	}

	public State getState() {
		return state;
	}

	@Nullable
	public String getTarget() {
		return target;
	}
}
