package me.aap.fermata.addon;

/** Current outcome of resolving an item owned by a dynamically delivered addon. */
public enum DeferredMediaItemState {
	NOT_HANDLED,
	LOADING,
	DISABLED,
	FAILED
}
