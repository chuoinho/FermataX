package me.aap.fermata.addon.stremio.session;

public enum StremioAdjacentDirection {
	PREVIOUS(-1),
	NEXT(1);

	final int offset;

	StremioAdjacentDirection(int offset) {
		this.offset = offset;
	}
}
