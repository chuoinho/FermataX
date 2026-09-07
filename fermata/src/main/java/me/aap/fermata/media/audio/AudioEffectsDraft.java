package me.aap.fermata.media.audio;

import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;

import java.util.ArrayList;
import java.util.List;

/** Settings-lifetime audio profile draft; it never writes through while being edited. */
public final class AudioEffectsDraft {
	public enum State { CLEAN, DRAFT, WORKING, APPLIED, FAILED }

	public interface Listener {
		void onStateChanged(State state);
	}

	private final AudioEffectsProfileRepository repository;
	private final BasicPreferenceStore store = new BasicPreferenceStore();
	private final List<Listener> listeners = new ArrayList<>();
	private final PreferenceStore.Listener storeListener;
	private AudioEffectsProfile committed;
	private State state = State.CLEAN;
	private boolean applying;

	public AudioEffectsDraft(AudioEffectsProfileRepository repository) {
		this.repository = repository;
		committed = repository.load();
		write(store, committed);
		storeListener = (ignored, changed) -> {
			if (!applying) {
				state = isDirty() ? State.DRAFT : State.CLEAN;
				notifyStateChanged();
			}
		};
		store.addBroadcastListener(storeListener);
	}

	public PreferenceStore getStore() {
		return store;
	}

	public AudioEffectsProfile snapshot() {
		return AudioEffectsProfileRepository.loadProfile(store);
	}

	public boolean isDirty() {
		return !committed.equals(snapshot());
	}

	public State getState() {
		return state;
	}

	public boolean isApplying() {
		return applying;
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
		listener.onStateChanged(state);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void discard() {
		write(store, committed);
		if (!applying) {
			state = State.CLEAN;
			notifyStateChanged();
		}
	}

	public AudioEffectsProfile commit() {
		if (applying) throw new IllegalStateException("Audio-effects Apply is already running");
		AudioEffectsProfile next = snapshot();
		repository.save(next);
		committed = next;
		state = State.CLEAN;
		notifyStateChanged();
		return next;
	}

	/** Saves the latest draft and reserves it for one explicit runtime application. */
	public AudioEffectsProfile beginApply() {
		if (applying) return null;
		AudioEffectsProfile next = snapshot();
		try {
			repository.save(next);
		} catch (RuntimeException error) {
			state = State.FAILED;
			notifyStateChanged();
			throw error;
		}
		committed = next;
		applying = true;
		state = State.WORKING;
		notifyStateChanged();
		return next;
	}

	public void finishApply(boolean success) {
		if (!applying) return;
		applying = false;
		state = success ? (isDirty() ? State.DRAFT : State.APPLIED) : State.FAILED;
		notifyStateChanged();
	}

	public void close() {
		store.removeBroadcastListener(storeListener);
		listeners.clear();
	}

	private void notifyStateChanged() {
		for (Listener listener : new ArrayList<>(listeners)) listener.onStateChanged(state);
	}

	private static void write(PreferenceStore target, AudioEffectsProfile profile) {
		try (PreferenceStore.Edit edit = target.editPreferenceStore(false)) {
			AudioEffectsProfileRepository.writeProfile(edit, profile);
		}
	}
}
