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
		if (mode == SmartTopMode.RECOVERY) {
			return capabilities.canOpenContext() ?
					List.of(SmartTopAction.RETRY, SmartTopAction.OPEN_CONTEXT) :
					List.of(SmartTopAction.RETRY);
		}
		if (mode == SmartTopMode.CURRENT) return current(capabilities);

		List<SmartTopAction> actions = new ArrayList<>(4);
		actions.add(SmartTopAction.PLAY);
		if (capabilities.canOpenContext()) actions.add(SmartTopAction.OPEN_CONTEXT);
		else actions.add(SmartTopAction.HISTORY);
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
		List<SmartTopAction> actions = new ArrayList<>(5);
		actions.add(SmartTopAction.PREVIOUS);
		actions.add(SmartTopAction.PLAY_PAUSE);
		actions.add(SmartTopAction.NEXT);
		if (capabilities.canOpenContext()) actions.add(SmartTopAction.OPEN_CONTEXT);
		if (capabilities.canFavorite()) actions.add(SmartTopAction.FAVORITE);
		return List.copyOf(actions);
	}
}
