package me.aap.fermata.ui.smarttop;

import java.util.ArrayList;
import java.util.List;

/** Pure action ordering and density policy shared by Auto and Mobile renderers. */
public final class SmartTopActionPolicy {
	private SmartTopActionPolicy() {
	}

	public static List<SmartTopAction> resolve(SmartTopMode mode,
			SmartTopLayoutMode layout, SmartTopCapabilities capabilities) {
		if (mode == SmartTopMode.EMPTY) return List.of(SmartTopAction.OPEN_ADDONS);
		if (mode == SmartTopMode.RECOVERY) {
			return capabilities.canOpenContext() ?
					List.of(SmartTopAction.RETRY, SmartTopAction.OPEN_CONTEXT) :
					List.of(SmartTopAction.RETRY);
		}

		boolean compact = layout == SmartTopLayoutMode.COMPACT;
		if (mode == SmartTopMode.CURRENT) return current(capabilities);

		List<SmartTopAction> actions = new ArrayList<>(4);
		actions.add(SmartTopAction.PLAY);
		if (capabilities.canOpenContext()) actions.add(SmartTopAction.OPEN_CONTEXT);
		if (!compact && capabilities.canFavorite()) actions.add(SmartTopAction.FAVORITE);
		if (compact && !capabilities.canOpenContext()) actions.add(SmartTopAction.HISTORY);
		return List.copyOf(actions);
	}

	private static List<SmartTopAction> current(SmartTopCapabilities capabilities) {
		List<SmartTopAction> actions = new ArrayList<>(3);
		actions.add(SmartTopAction.PLAY_PAUSE);
		if (capabilities.canOpenContext()) actions.add(SmartTopAction.OPEN_CONTEXT);
		if (capabilities.canFavorite()) actions.add(SmartTopAction.FAVORITE);
		return List.copyOf(actions);
	}
}
