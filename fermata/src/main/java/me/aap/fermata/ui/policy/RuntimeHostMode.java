package me.aap.fermata.ui.policy;

/** Runtime presentation host. The Auto build can still run as an ordinary phone activity. */
public enum RuntimeHostMode {
	PHONE,
	AA_PROJECTION,
	MIRROR;

	public boolean usesAutomotivePresentation() {
		return this != PHONE;
	}

	public boolean isProjection() {
		return this == AA_PROJECTION;
	}

	public boolean isMirror() {
		return this == MIRROR;
	}

	public static RuntimeHostMode resolve(boolean autoBuild, boolean carActivity,
			boolean mirroring) {
		if (!autoBuild) return PHONE;
		if (carActivity) return AA_PROJECTION;
		return mirroring ? MIRROR : PHONE;
	}
}
