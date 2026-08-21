package me.aap.fermata.ui.smarttop;

/** Strict priority selector. Recovery is action-driven and therefore not an input tier. */
public final class SmartTopSelectionPolicy {
	private SmartTopSelectionPolicy() {
	}

	public static SmartTopMode select(boolean hasCanonicalCurrent, boolean hasResume,
			boolean hasRecent, boolean hasRecommendation) {
		if (hasCanonicalCurrent) return SmartTopMode.CURRENT;
		if (hasResume) return SmartTopMode.RESUME;
		if (hasRecent) return SmartTopMode.RECENT;
		// Keep the recommendation input for compatibility, but it is no longer a display tier.
		return SmartTopMode.EMPTY;
	}
}
