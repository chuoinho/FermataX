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
	@Nullable
	private MediaEngine engine;
	private int sessionId = ERROR;
	@Nullable
	private AudioEffectsBackend backend;

	public AudioEffectsController(PreferenceStore store) {
		this(new AudioEffectsProfileRepository(store));
	}

	public AudioEffectsController(AudioEffectsProfileRepository profiles) {
		this(profiles, NativeSessionAudioEffectsBackend::create);
	}

	AudioEffectsController(AudioEffectsProfileRepository profiles, BackendFactory backendFactory) {
		this.profiles = profiles;
		this.backendFactory = backendFactory;
		store = profiles.getStore();
		store.addBroadcastListener(this);
	}

	public synchronized void bind(MediaEngine nextEngine) {
		int nextSessionId = nextEngine.getAudioSessionId();
		if (!NativeSessionAudioEffectsBackend.isValidSessionId(nextSessionId)) {
			releaseBoundBackend();
			return;
		}
		if ((engine == nextEngine) && (sessionId == nextSessionId) && (backend != null)) return;

		releaseBoundBackend();
		engine = nextEngine;
		sessionId = nextSessionId;
		try {
			backend = backendFactory.create(nextSessionId);
			applyCurrentProfile();
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
		if (AudioEffectsProfileRepository.containsProfilePreference(changed)) applyCurrentProfile();
	}

	@Override
	public synchronized void close() {
		store.removeBroadcastListener(this);
		releaseBoundBackend();
	}

	private void applyCurrentProfile() {
		AudioEffectsBackend current = backend;
		if (current == null) return;
		AudioEffectsProfile profile = profiles.load();
		if (profile.enabled()) current.apply(profile);
		else current.bypass();
	}

	private void releaseBoundBackend() {
		AudioEffectsBackend current = backend;
		backend = null;
		engine = null;
		sessionId = ERROR;
		if (current != null) current.release();
	}
}
