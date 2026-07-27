package me.aap.fermata.addon.stremio.source;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;

/** Secret-free result returned for every source operation. */
public record StremioSourceOutcome(
		Action action,
		Status status,
		String sourceUuid,
		StremioSourceSnapshot snapshot,
		Code errorCode) {

	public StremioSourceRecord source() {
		return ((snapshot == null) || (sourceUuid == null)) ? null : snapshot.source(sourceUuid);
	}

	public boolean changed() {
		return status == Status.CHANGED;
	}

	public enum Action {
		ADD, EDIT, ENABLE, DISABLE, REFRESH, REMOVE, REORDER, INITIALIZE_DEFAULT
	}

	public enum Status {
		CHANGED, UNCHANGED, FAILED, CANCELLED
	}
}
