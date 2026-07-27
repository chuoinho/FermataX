package me.aap.fermata.addon.stremio.failure;

/** Safe user action associated with a typed Stremio failure. */
public enum StremioRecovery {
	NONE,
	RETRY,
	SELECT_SOURCE,
	MANAGE_ADDON,
	FREE_STORAGE,
	CANCEL
}
