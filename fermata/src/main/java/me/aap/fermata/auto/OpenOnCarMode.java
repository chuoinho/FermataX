package me.aap.fermata.auto;

import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local, non-persistent opt-in state for opening the next request on the car. */
public final class OpenOnCarMode {
	private boolean available;
	private boolean enabled;
	private long epoch = -1;
	private long revision;
	private final Set<Listener> listeners = new LinkedHashSet<>();

	public void setAvailable(boolean next, long nextEpoch) {
		Listener[] notify;
		synchronized (this) {
			if ((available == next) && (epoch == nextEpoch)) return;
			boolean changedEpoch = epoch != nextEpoch;
			available = next;
			epoch = nextEpoch;
			if (!next || changedEpoch) enabled = false;
			revision++;
			notify = listeners.toArray(new Listener[0]);
		}
		for (Listener listener : notify) listener.onChanged(isAvailable(), isEnabled(), revision());
	}

	public void setEnabled(boolean next) {
		Listener[] notify;
		synchronized (this) {
			next &= available;
			if (next == enabled) return;
			enabled = next;
			revision++;
			notify = listeners.toArray(new Listener[0]);
		}
		for (Listener listener : notify) listener.onChanged(isAvailable(), isEnabled(), revision());
	}

	public void addListener(Listener listener) {
		synchronized (this) { listeners.add(listener); }
		listener.onChanged(isAvailable(), isEnabled(), revision());
	}
	public synchronized void removeListener(Listener listener) { listeners.remove(listener); }

	@FunctionalInterface
	public interface Listener { void onChanged(boolean available, boolean enabled, long revision); }

	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized boolean isAvailable() {
		return available;
	}

	public synchronized long revision() {
		return revision;
	}
}
