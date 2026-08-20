package me.aap.fermata.ui.smarttop;

import java.util.ArrayList;
import java.util.List;

/** Pure semantic action availability. Measured presentation density belongs to SmartTopAdaptivePolicy. */
public final class SmartTopActionPolicy {
	private SmartTopActionPolicy() {
	}

	public static List<SmartTopAction> resolve(SmartTopMode mode,
			SmartTopCapabilities capabilities) {
		if (mode == SmartTopMode.EMPTY) return List.of(SmartTopAction.OPEN_ADDONS);
		if (mode == SmartTopMode.RECOVERY) return List.of(SmartTopAction.RETRY);
		if (mode == SmartTopMode.CURRENT) return current(capabilities);

		List<SmartTopAction> actions = new ArrayList<>(2);
		actions.add(SmartTopAction.PLAY);
		if (capabilities.canFavorite()) actions.add(SmartTopAction.FAVORITE);
		return List.copyOf(actions);
	}

	/** Compatibility bridge while the renderer migrates off layout-owned semantic state. */
	@Deprecated
	public static List<SmartTopAction> resolve(SmartTopMode mode,
			SmartTopLayoutMode ignoredLayout, SmartTopCapabilities capabilities) {
		return resolve(mode, capabilities);
	}

	private static List<SmartTopAction> current(SmartTopCapabilities capabilities) {
		List<SmartTopAction> actions = new ArrayList<>(3);
		if (capabilities.canSkipPrevious()) actions.add(SmartTopAction.PREVIOUS);
		actions.add(SmartTopAction.PLAY_PAUSE);
		if (capabilities.canFavorite()) actions.add(SmartTopAction.FAVORITE);
		return List.copyOf(actions);
	}
}
