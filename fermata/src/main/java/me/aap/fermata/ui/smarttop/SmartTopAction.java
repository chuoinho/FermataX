package me.aap.fermata.ui.smarttop;

/**
 * Semantic actions. NEXT / OPEN_CONTEXT / HISTORY remain compatibility-only enum values while
 * legacy Dashboard holder code is still present; SmartTop V2 policy and renderer never expose
 * them. Active SmartTop controls are Previous, Play/Play-Pause, Favorite, and labeled setup/retry.
 */
public enum SmartTopAction {
	PREVIOUS,
	PLAY,
	PLAY_PAUSE,
	NEXT,
	FAVORITE,
	OPEN_CONTEXT,
	HISTORY,
	OPEN_ADDONS,
	RETRY
}
