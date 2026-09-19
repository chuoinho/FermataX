package me.aap.fermata.auto;

/** Process-local, non-persistent opt-in state for opening the next request on the car. */
public final class OpenOnCarMode {
	private boolean available;
	private boolean enabled;
	private long epoch = -1;
	private long revision;

	public synchronized void setAvailable(boolean next, long nextEpoch) {
		if ((available == next) && (epoch == nextEpoch)) return;
		boolean changedEpoch = epoch != nextEpoch;
		available = next;
		epoch = nextEpoch;
		if (!next || changedEpoch) enabled = false;
		revision++;
	}

	public synchronized void setEnabled(boolean next) {
		next &= available;
		if (next == enabled) return;
		enabled = next;
		revision++;
	}

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
