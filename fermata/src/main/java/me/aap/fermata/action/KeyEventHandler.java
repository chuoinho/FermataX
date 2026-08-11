package me.aap.fermata.action;

import android.view.KeyEvent;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntObjectFunction;

/**
 * @author Andrey Pavlenko
 */
public class KeyEventHandler {
	public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event,
														 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return cb.getHardwareInputRouter().handleMediaEvent(event, defaultHandler);
	}

	public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event,
														 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return activity.getMediaSessionCallback().getHardwareInputRouter()
				.handleActivityEvent(activity, event, defaultHandler);
	}
}
