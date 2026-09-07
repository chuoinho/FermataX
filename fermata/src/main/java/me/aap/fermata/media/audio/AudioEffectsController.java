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
	private boolean equalizerPendingForNextSession;

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

	public synchronized void bind(MediaEngine nextEngine) {
		bind(nextEngine, nextEngine.getAudioSessionId());
	}

	public synchronized void bind(MediaEngine nextEngine, int nextSessionId) {
		if (!NativeSessionAudioEffectsBackend.isValidSessionId(nextSessionId)) {
			releaseBoundBackend();
			return;
		}
		if ((engine == nextEngine) && (sessionId == nextSessionId) && (backend != null)) return;

		releaseBoundBackend();
		engine = nextEngine;
		sessionId = nextSessionId;
		try {
			AudioEffectsBackend nextBackend = backendFactory.create(nextSessionId);
			if (nextBackend instanceof NativeEqualizerTopologyProvider topologyProvider) {
				profiles.migratePendingLegacyEqualizer(topologyProvider.getEqualizerTopology());
			}
			backend = nextBackend;
			boolean applied = applyCurrentProfile(true);
			if (applied) equalizerPendingForNextSession = false;
		} catch (RuntimeException error) {
			releaseBoundBackend();
		}
	}

	public synchronized void unbind(MediaEngine candidate) {
		if (engine == candidate) releaseBoundBackend();
	}

	@Override
	public synchronized void onPreferenceChanged(PreferenceStore ignored,
			List<PreferenceStore.Pref<?>> changed) {
		if (!AudioEffectsProfileRepository.containsProfilePreference(changed)) return;
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

	@Override
	public synchronized void close() {
		store.removeBroadcastListener(this);
		releaseBoundBackend();
	}

	private boolean applyCurrentProfile(boolean applyEqualizer) {
		AudioEffectsBackend current = backend;
		if (current == null) return false;
		AudioEffectsProfile profile = profiles.load();
		if (profile.enabled()) return current.apply(profile, applyEqualizer);
		current.bypass();
		return true;
	}

	private void releaseBoundBackend() {
		AudioEffectsBackend current = backend;
		backend = null;
		engine = null;
		sessionId = ERROR;
		if (current != null) current.release();
	}
}
