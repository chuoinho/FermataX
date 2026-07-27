package me.aap.fermata.addon.stremio;

import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome.Status;

/** One-time default-source marker policy, independent of Android persistence. */
final class StremioDefaultSourcePolicy {
	private StremioDefaultSourcePolicy() {
	}

	static boolean marksHandled(StremioSourceOutcome outcome) {
		if (outcome == null) return false;
		Status status = outcome.status();
		return (status == Status.CHANGED) || (status == Status.UNCHANGED) ||
				((status == Status.FAILED) &&
						(outcome.errorCode() == Code.DUPLICATE_TRANSPORT));
	}
}
