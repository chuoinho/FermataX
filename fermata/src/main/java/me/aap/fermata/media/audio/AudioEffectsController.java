package me.aap.fermata.media.audio;

import static android.media.AudioManager.ERROR;

import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.utils.pref.PreferenceStore;

/** The only runtime owner of native audio effects for one MediaSession callback. */
public final class AudioEffectsController implements PreferenceStore.Listener, AutoCloseable {
	interface BackendFactory {
		@Nullable AudioEffectsBackend create(int audioSessionId);
	}

	private final AudioEffectsProfileRepository profiles;
	private final PreferenceStore store;
	private final BackendFactory backendFactory;
	private final Runnable deferredEqualizerNotification;
	@Nullable
	private MediaEngine engine;
	private int sessionId = ERROR;
	@Nullable
	private AudioEffectsBackend backend;
	private boolean backendProfileApplied;
	private boolean equalizerPendingForNextSession;
	@Nullable
	private AudioEffectsProfile deferredExplicitProfile;

	public AudioEffectsController(PreferenceStore store) {
		this(new AudioEffectsProfileRepository(store));
	}

	public AudioEffectsController(PreferenceStore store, Runnable deferredEqualizerNotification) {
		this(new AudioEffectsProfileRepository(store), NativeSessionAudioEffectsBackend::create,
				deferredEqualizerNotification);
	}

	public AudioEffectsController(AudioEffectsProfileRepository profiles) {
		this(profiles, NativeSessionAudioEffectsBackend::create, () -> {
		});
	}

	AudioEffectsController(AudioEffectsProfileRepository profiles, BackendFactory backendFactory) {
		this(profiles, backendFactory, () -> {
		});
	}

	AudioEffectsController(AudioEffectsProfileRepository profiles, BackendFactory backendFactory,
			Runnable deferredEqualizerNotification) {
		this.profiles = profiles;
		this.backendFactory = backendFactory;
		this.deferredEqualizerNotification = deferredEqualizerNotification;
		store = profiles.getStore();
		store.addBroadcastListener(this);
	}

	public synchronized boolean bind(MediaEngine nextEngine) {
		return bind(nextEngine, nextEngine.getAudioSessionId());
	}

	public synchronized boolean bind(MediaEngine nextEngine, int nextSessionId) {
		if (!NativeSessionAudioEffectsBackend.isValidSessionId(nextSessionId)) {
			releaseBoundBackend();
			return false;
		}
		if ((engine == nextEngine) && (sessionId == nextSessionId) && (backend != null))
			return backendProfileApplied;

		releaseBoundBackend();
		engine = nextEngine;
		sessionId = nextSessionId;
		try {
			AudioEffectsBackend nextBackend = backendFactory.create(nextSessionId);
			if (nextBackend instanceof NativeEqualizerTopologyProvider topologyProvider) {
				LegacyEqualizerPresetResolver resolver = (nextBackend instanceof
						LegacyEqualizerPresetResolver presetResolver) ? presetResolver : null;
				profiles.migratePendingLegacyEqualizer(topologyProvider.getEqualizerTopology(), resolver);
			}
			backend = nextBackend;
			boolean applied = applyCurrentProfile(true);
			if (applied) equalizerPendingForNextSession = false;
			return applied;
		} catch (RuntimeException error) {
			releaseBoundBackend();
			return false;
		}
	}

	public synchronized void unbind(MediaEngine candidate) {
		if (engine == candidate) releaseBoundBackend();
	}

	@Override
	public synchronized void onPreferenceChanged(PreferenceStore ignored,
			List<PreferenceStore.Pref<?>> changed) {
		if (!AudioEffectsProfileRepository.containsProfilePreference(changed)) return;
		if (deferredExplicitProfile != null) return;
		AudioEffectsBackend current = backend;
		boolean deferEqualizer = (current != null) &&
				(current.getEqualizerUpdateMode() == EqualizerUpdateMode.INITIAL_ONLY) &&
				AudioEffectsProfileRepository.containsEqualizerPreference(changed);
		if (!deferEqualizer) {
			applyCurrentProfile(true);
			return;
		}

		if (!profiles.load().enabled()) {
			current.bypass();
			backendProfileApplied = true;
			equalizerPendingForNextSession = false;
			return;
		}

		applyCurrentProfile(false);
		if (!equalizerPendingForNextSession) {
			equalizerPendingForNextSession = true;
			deferredEqualizerNotification.run();
		}
	}

	boolean isEqualizerPendingForNextSession() {
		return equalizerPendingForNextSession;
	}

	/** Prevents the persistence broadcast from applying before the explicit runtime transaction. */
	public synchronized void deferExplicitProfileBroadcast(AudioEffectsProfile profile) {
		deferredExplicitProfile = profile;
	}

	public synchronized void cancelExplicitProfileBroadcast() {
		deferredExplicitProfile = null;
	}

	public synchronized boolean requiresFreshBackend(AudioEffectsProfile profile) {
		return (backend != null) && profile.enabled() &&
				(backend.getEqualizerUpdateMode() == EqualizerUpdateMode.INITIAL_ONLY);
	}

	public synchronized boolean applyExplicit(AudioEffectsProfile profile) {
		deferredExplicitProfile = null;
		AudioEffectsBackend current = backend;
		if (current == null) return true;
		if (!profile.enabled()) {
			current.bypass();
			backendProfileApplied = true;
			equalizerPendingForNextSession = false;
			return true;
		}
		backendProfileApplied = false;
		boolean applied = current.apply(profile, true);
		backendProfileApplied = applied;
		if (applied) equalizerPendingForNextSession = false;
		return applied;
	}

	/** Emergency bypass: persist only master-off, then silence the bound chain immediately. */
	public synchronized void emergencyDisable() {
		AudioEffectsProfile currentProfile = profiles.load();
		if (currentProfile.enabled()) {
			deferredExplicitProfile = currentProfile;
			try {
				profiles.save(new AudioEffectsProfile(currentProfile.schemaVersion(), false,
						currentProfile.equalizerEnabled(), currentProfile.canonicalCurveDb(),
						currentProfile.preampDb(), currentProfile.bassBoostEnabled(),
						currentProfile.bassBoostStrength(), currentProfile.loudnessEnabled(),
						currentProfile.loudnessGain(), currentProfile.virtualizerEnabled(),
						currentProfile.virtualizerStrength(), currentProfile.virtualizerMode()));
			} finally {
				deferredExplicitProfile = null;
			}
		}
		AudioEffectsBackend current = backend;
		if (current != null) current.bypass();
		equalizerPendingForNextSession = false;
	}

	@Override
	public synchronized void close() {
		store.removeBroadcastListener(this);
		releaseBoundBackend();
	}

	private boolean applyCurrentProfile(boolean applyEqualizer) {
		AudioEffectsBackend current = backend;
		if (current == null) {
			backendProfileApplied = false;
			return false;
		}
		AudioEffectsProfile profile = profiles.load();
		if (profile.enabled()) {
			backendProfileApplied = false;
			backendProfileApplied = current.apply(profile, applyEqualizer);
		}
		else {
			current.bypass();
			backendProfileApplied = true;
		}
		return backendProfileApplied;
	}

	private void releaseBoundBackend() {
		AudioEffectsBackend current = backend;
		backend = null;
		backendProfileApplied = false;
		engine = null;
		sessionId = ERROR;
		if (current != null) current.release();
	}
}
