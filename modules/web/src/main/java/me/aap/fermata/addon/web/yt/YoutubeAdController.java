package me.aap.fermata.addon.web.yt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure state machine for YouTube ad breaks.
 *
 * <p>The controller deliberately knows nothing about WebView, JavaScript, DOM, network, or
 * threads. An integration layer turns its effects into UI/player operations and feeds back
 * normalized events. Callers should keep all calls on one thread (the main thread in Android).
 */
public final class YoutubeAdController {
	public static final RetryPolicy DEFAULT_RETRY_POLICY = new RetryPolicy(2, 1_000L);

	public enum State {
		IDLE,
		CONTENT,
		PRE_ROLL,
		MID_ROLL,
		COOLDOWN
	}

	public enum BreakType {
		PRE_ROLL,
		MID_ROLL
	}

	public enum EffectType {
		PLAYBACK_STARTED,
		ENTER_AD_POD,
		START_AD,
		COMPLETE_AD,
		ABORT_AD_POD,
		RETRY_AD_POD,
		RESUME_CONTENT,
		PLAYBACK_ENDED
	}

	/** Stable identity for one content playback. The number rejects callbacks from older plays. */
	public record PlaybackGeneration(long number, String playbackId) {
		public boolean isValid() {
			return number > 0L && playbackId != null && !playbackId.isBlank();
		}
	}

	/** Ad pod metadata normalized by the integration layer. {@code expectedAds == -1} means unknown. */
	public record AdPod(String id, BreakType type, int expectedAds) {
		public static AdPod preRoll(String id) {
			return new AdPod(id, BreakType.PRE_ROLL, -1);
		}

		public static AdPod midRoll(String id) {
			return new AdPod(id, BreakType.MID_ROLL, -1);
		}

		public boolean isValid() {
			return id != null && !id.isBlank() && type != null && expectedAds >= -1;
		}
	}

	public record RetryPolicy(int maxAttempts, long cooldownMillis) {
		public RetryPolicy {
			if (maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
			if (cooldownMillis < 0L) throw new IllegalArgumentException("cooldownMillis must be >= 0");
		}
	}

	public record Effect(EffectType type, long generation, BreakType breakType,
							String podId, String adId, long atMillis) {
	}

	/** The result of one input. Effects are ordered and immutable for deterministic integration/tests. */
	public record Transition(long generation, State state, boolean accepted, List<Effect> effects) {
		public Transition {
			effects = List.copyOf(effects);
		}

		public boolean isNoOp() {
			return !accepted && effects.isEmpty();
		}
	}

	private final RetryPolicy retryPolicy;
	private long generation;
	private String playbackId;
	private State state = State.IDLE;
	private boolean active;
	private boolean contentStarted;
	private AdPod activePod;
	private String activeAdId;
	private final Set<String> completedAdIds = new HashSet<>();
	private int retryAttempts;
	private long cooldownUntil;

	public YoutubeAdController() {
		this(DEFAULT_RETRY_POLICY);
	}

	public YoutubeAdController(RetryPolicy retryPolicy) {
		if (retryPolicy == null) throw new NullPointerException("retryPolicy");
		this.retryPolicy = retryPolicy;
	}

	/** Starts a new generation, or returns a no-op for a duplicate callback for the same play. */
	public synchronized Transition beginPlayback(String playbackId) {
		if (playbackId == null || playbackId.isBlank()) return noOp();
		if (active && playbackId.equals(this.playbackId)) return noOp();

		generation++;
		this.playbackId = playbackId;
		active = true;
		contentStarted = false;
		state = State.CONTENT;
		clearPod();
		return accepted(EffectType.PLAYBACK_STARTED, null, null, null, 0L);
	}

	/** Marks the content stream as playing. This is also the point after which a break is mid-roll. */
	public synchronized Transition onContentStarted(long generation) {
		if (!isCurrent(generation)) return noOp();
		if (state == State.COOLDOWN) {
			BreakType type = activePod.type();
			String pod = activePod.id();
			clearPod();
			contentStarted = true;
			state = State.CONTENT;
			return accepted(EffectType.RESUME_CONTENT, type, pod, null, 0L);
		}
		if (state != State.CONTENT || contentStarted) return noOp();
		contentStarted = true;
		return accepted();
	}

	public synchronized Transition onAdPodStarted(long generation, AdPod pod) {
		if (!isCurrent(generation) || !valid(pod) || state != State.CONTENT) return noOp();
		if (pod.type() == BreakType.PRE_ROLL && contentStarted) return noOp();
		if (pod.type() == BreakType.MID_ROLL && !contentStarted) return noOp();

		activePod = pod;
		activeAdId = null;
		completedAdIds.clear();
		retryAttempts = 0;
		cooldownUntil = 0L;
		state = stateFor(pod.type());
		return accepted(EffectType.ENTER_AD_POD, pod.type(), pod.id(), null, 0L);
	}

	public synchronized Transition onAdStarted(long generation, String podId, String adId) {
		if (!isCurrent(generation) || !hasPod(podId) || !validId(adId) || !isAdState()) return noOp();
		if (activeAdId != null || completedAdIds.contains(adId)) return noOp();

		activeAdId = adId;
		return accepted(EffectType.START_AD, activePod.type(), activePod.id(), adId, 0L);
	}

	public synchronized Transition onAdCompleted(long generation, String podId, String adId) {
		if (!isCurrent(generation) || !hasPod(podId) || !validId(adId) ||
				!adId.equals(activeAdId)) return noOp();

		activeAdId = null;
		completedAdIds.add(adId);
		return accepted(EffectType.COMPLETE_AD, activePod.type(), activePod.id(), adId, 0L);
	}

	public synchronized Transition onAdPodCompleted(long generation, String podId) {
		if (!isCurrent(generation) || !hasPod(podId) ||
				(!isAdState() && state != State.COOLDOWN) || activeAdId != null ||
				(activePod.expectedAds() >= 0 &&
						completedAdIds.size() < activePod.expectedAds())) return noOp();

		BreakType type = activePod.type();
		String id = activePod.id();
		clearPod();
		contentStarted = true;
		state = State.CONTENT;
		return accepted(EffectType.RESUME_CONTENT, type, id, null, 0L);
	}

	/**
	 * Reports a known ad failure. Retryable failures enter cooldown; repeated failure callbacks
	 * during cooldown are ignored until {@link #onClock(long, long)} opens the next attempt.
	 */
	public synchronized Transition onAdError(long generation, String podId, String adId,
										 boolean retryable, long nowMillis) {
		if (!isCurrent(generation) || !hasPod(podId) || !validId(adId) ||
				!adId.equals(activeAdId) || !isAdState() || nowMillis < 0L) return noOp();

		BreakType type = activePod.type();
		String pod = activePod.id();
		if (retryable && retryAttempts < retryPolicy.maxAttempts()) {
			retryAttempts++;
			activeAdId = null;
			state = State.COOLDOWN;
			cooldownUntil = saturatingAdd(nowMillis, retryPolicy.cooldownMillis());
			List<Effect> effects = new ArrayList<>(2);
			effects.add(effect(EffectType.ABORT_AD_POD, type, pod, adId, nowMillis));
			return transition(true, effects);
		}

		clearPod();
		contentStarted = true;
		state = State.CONTENT;
		List<Effect> effects = new ArrayList<>(2);
		effects.add(effect(EffectType.ABORT_AD_POD, type, pod, adId, nowMillis));
		effects.add(effect(EffectType.RESUME_CONTENT, type, pod, null, nowMillis));
		return transition(true, effects);
	}

	/** Opens a retry only after the caller's monotonic clock reaches the cooldown deadline. */
	public synchronized Transition onClock(long generation, long nowMillis) {
		if (!isCurrent(generation) || state != State.COOLDOWN || nowMillis < cooldownUntil) return noOp();

		state = stateFor(activePod.type());
		cooldownUntil = 0L;
		return accepted(EffectType.RETRY_AD_POD, activePod.type(), activePod.id(), null, nowMillis);
	}

	public synchronized Transition endPlayback(long generation) {
		if (!isCurrent(generation)) return noOp();
		BreakType type = activePod == null ? null : activePod.type();
		String pod = activePod == null ? null : activePod.id();
		clearPod();
		active = false;
		contentStarted = false;
		state = State.IDLE;
		return accepted(EffectType.PLAYBACK_ENDED, type, pod, null, 0L);
	}

	public synchronized State getState() {
		return state;
	}

	public synchronized boolean isContentStarted() {
		return contentStarted;
	}

	public synchronized PlaybackGeneration getPlaybackGeneration() {
		return active ? new PlaybackGeneration(generation, playbackId) : null;
	}

	public synchronized long getCooldownUntil() {
		return cooldownUntil;
	}

	private boolean isCurrent(long value) {
		return active && value > 0L && value == generation;
	}

	private boolean hasPod(String podId) {
		return activePod != null && validId(podId) && activePod.id().equals(podId);
	}

	private boolean isAdState() {
		return state == State.PRE_ROLL || state == State.MID_ROLL;
	}

	private void clearPod() {
		activePod = null;
		activeAdId = null;
		completedAdIds.clear();
		retryAttempts = 0;
		cooldownUntil = 0L;
	}

	private static State stateFor(BreakType type) {
		return type == BreakType.PRE_ROLL ? State.PRE_ROLL : State.MID_ROLL;
	}

	private static boolean valid(AdPod pod) {
		return pod != null && pod.isValid();
	}

	private static boolean validId(String id) {
		return id != null && !id.isBlank();
	}

	private Transition accepted(EffectType type, BreakType breakType, String podId, String adId,
									long atMillis) {
		return transition(true, List.of(effect(type, breakType, podId, adId, atMillis)));
	}

	private Transition accepted() {
		return transition(true, List.of());
	}

	private Transition noOp() {
		return transition(false, List.of());
	}

	private Transition transition(boolean accepted, List<Effect> effects) {
		return new Transition(generation, state, accepted, effects);
	}

	private Effect effect(EffectType type, BreakType breakType, String podId, String adId,
							 long atMillis) {
		return new Effect(type, generation, breakType, podId, adId, atMillis);
	}

	private static long saturatingAdd(long value, long increment) {
		if (Long.MAX_VALUE - value < increment) return Long.MAX_VALUE;
		return value + increment;
	}
}
