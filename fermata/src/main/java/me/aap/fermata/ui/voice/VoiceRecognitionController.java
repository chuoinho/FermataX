package me.aap.fermata.ui.voice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.utils.function.Cancellable;

/** Owns callback ordering and endpoint watchdogs for online speech recognition. */
public final class VoiceRecognitionController {
	public enum State {
		IDLE, READY, LISTENING, SPEECH_STARTED, FINALIZING,
		COMPLETED, ERROR, CANCELLED
	}

	public enum Failure {
		NO_SPEECH_TIMEOUT, FINAL_RESULT_TIMEOUT, SESSION_TIMEOUT, PROVIDER_ERROR
	}

	public interface Host {
		Cancellable schedule(Runnable task, long delayMs);

		void requestStopListening();

		void cancelRecognition();

		void onFinalResults(List<String> results);

		void onFailure(Failure failure, int providerError);
	}

	private final VoiceEndpointPolicy policy;
	private final Host host;
	private State state = State.IDLE;
	private long generation;
	private boolean adaptiveAllowed;
	private String stablePartial;
	private int stablePartialCount;
	private int resultCount;
	private Cancellable noSpeechTask = Cancellable.CANCELED;
	private Cancellable finalResultTask = Cancellable.CANCELED;
	private Cancellable hardSessionTask = Cancellable.CANCELED;
	private Cancellable stablePartialTask = Cancellable.CANCELED;

	public VoiceRecognitionController(VoiceEndpointPolicy policy, Host host) {
		this.policy = policy;
		this.host = host;
	}

	/** Starts a generation and returns the token required by all subsequent callbacks. */
	public long start(boolean adaptiveAllowed) {
		if (isActive()) cancel(generation);
		generation++;
		this.adaptiveAllowed = adaptiveAllowed;
		stablePartial = null;
		stablePartialCount = 0;
		resultCount = 0;
		transition(State.LISTENING, Collections.singletonMap("adaptive_allowed", adaptiveAllowed));
		long current = generation;
		noSpeechTask = host.schedule(() -> fail(current, Failure.NO_SPEECH_TIMEOUT, 0),
				policy.getNoSpeechTimeoutMs());
		hardSessionTask = host.schedule(() -> fail(current, Failure.SESSION_TIMEOUT, 0),
				policy.getHardSessionTimeoutMs());
		return current;
	}

	public State getState() {
		return state;
	}

	public long getGeneration() {
		return generation;
	}

	public void onReadyForSpeech(long callbackGeneration) {
		if (!isCurrentActive(callbackGeneration)) return;
		if ((state == State.LISTENING) || (state == State.READY)) transition(State.READY,
				Collections.emptyMap());
	}

	public void onBeginningOfSpeech(long callbackGeneration) {
		if (!isCurrentActive(callbackGeneration) || (state == State.FINALIZING)) return;
		markSpeechStarted();
	}

	/**
	 * Records a partial transcript. A stable complete command may ask the provider to finalize,
	 * but partial text is never delivered as a successful result.
	 */
	public void onPartialResult(long callbackGeneration, String partial,
			boolean syntacticallyComplete) {
		if (!isCurrentActive(callbackGeneration) || (state == State.FINALIZING) ||
				(partial == null) || partial.isBlank()) return;
		markSpeechStarted();

		if (partial.equals(stablePartial)) stablePartialCount++;
		else {
			stablePartial = partial;
			stablePartialCount = 1;
		}

		cancelStablePartialTask();
		if (!adaptiveAllowed || !syntacticallyComplete ||
				(stablePartialCount < policy.getStablePartialRepetitions())) return;

		String candidate = stablePartial;
		stablePartialTask = host.schedule(() -> {
			if (!isCurrentActive(callbackGeneration) || (state == State.FINALIZING) ||
					!candidate.equals(stablePartial) ||
					(stablePartialCount < policy.getStablePartialRepetitions())) return;
			beginFinalizing(callbackGeneration);
			host.requestStopListening();
		}, policy.getStablePartialDelayMs());
	}

	public void onEndOfSpeech(long callbackGeneration) {
		beginFinalizing(callbackGeneration);
	}

	public void onResults(long callbackGeneration, List<String> results) {
		if (!isCurrentActive(callbackGeneration)) return;
		resultCount = (results == null) ? 0 : results.size();
		transition(State.COMPLETED, Collections.singletonMap("result_count", resultCount));
		cancelTasks();
		host.onFinalResults((results == null) ? List.of() : List.copyOf(results));
	}

	public void onError(long callbackGeneration, int providerError) {
		if (!isCurrentActive(callbackGeneration)) return;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("provider_error", providerError);
		attributes.put("result_count", resultCount);
		transition(State.ERROR, attributes);
		cancelTasks();
		host.onFailure(Failure.PROVIDER_ERROR, providerError);
	}

	public void cancel(long callbackGeneration) {
		if (!isCurrentActive(callbackGeneration)) return;
		transition(State.CANCELLED, Collections.singletonMap("result_count", resultCount));
		cancelTasks();
		host.cancelRecognition();
	}

	private void markSpeechStarted() {
		transition(State.SPEECH_STARTED, Collections.emptyMap());
		noSpeechTask.cancel();
		noSpeechTask = Cancellable.CANCELED;
	}

	private void beginFinalizing(long callbackGeneration) {
		if (!isCurrentActive(callbackGeneration) || (state == State.FINALIZING)) return;
		transition(State.FINALIZING, Collections.emptyMap());
		noSpeechTask.cancel();
		noSpeechTask = Cancellable.CANCELED;
		cancelStablePartialTask();
		finalResultTask = host.schedule(
				() -> fail(callbackGeneration, Failure.FINAL_RESULT_TIMEOUT, 0),
				policy.getFinalResultTimeoutMs());
	}

	private void fail(long callbackGeneration, Failure failure, int providerError) {
		if (!isCurrentActive(callbackGeneration)) return;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("failure", failure.name());
		attributes.put("provider_error", providerError);
		attributes.put("result_count", resultCount);
		transition(State.ERROR, attributes);
		cancelTasks();
		host.cancelRecognition();
		host.onFailure(failure, providerError);
	}

	private void transition(State next, Map<String, ?> extraAttributes) {
		if (state == next) return;
		state = next;
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("state", next.name());
		attributes.put("generation", generation);
		attributes.put("result_count", resultCount);
		if (extraAttributes != null) attributes.putAll(extraAttributes);
		AndroidDiagnosticsRuntime.get().recordEssential("voice", "session_state",
				DiagnosticPriority.STATE, "voice-" + generation, attributes);
	}

	private boolean isCurrentActive(long callbackGeneration) {
		return (callbackGeneration == generation) && isActive();
	}

	private boolean isActive() {
		return switch (state) {
			case READY, LISTENING, SPEECH_STARTED, FINALIZING -> true;
			default -> false;
		};
	}

	private void cancelStablePartialTask() {
		stablePartialTask.cancel();
		stablePartialTask = Cancellable.CANCELED;
	}

	private void cancelTasks() {
		noSpeechTask.cancel();
		finalResultTask.cancel();
		hardSessionTask.cancel();
		stablePartialTask.cancel();
		noSpeechTask = Cancellable.CANCELED;
		finalResultTask = Cancellable.CANCELED;
		hardSessionTask = Cancellable.CANCELED;
		stablePartialTask = Cancellable.CANCELED;
	}
}
