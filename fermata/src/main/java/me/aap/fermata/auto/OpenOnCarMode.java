package me.aap.fermata.auto;

/** Process-local, non-persistent opt-in state for opening the next request on the car. */
public final class OpenOnCarMode {
	private boolean available;
	private boolean enabled;
	private long epoch = -1;
	private long revision;

	public void setAvailable(boolean next, long nextEpoch) {
		if ((available == next) && (epoch == nextEpoch)) return;
		boolean changedEpoch = epoch != nextEpoch;
		available = next;
		epoch = nextEpoch;
		if (!next || changedEpoch) enabled = false;
		revision++;
	}

	public void setEnabled(boolean next) {
		next &= available;
		if (next == enabled) return;
		enabled = next;
		revision++;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isAvailable() {
		return available;
	}

	public long revision() {
		return revision;
	}
}
