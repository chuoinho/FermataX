package me.aap.fermata.ui.view;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Canonical command boundary for UI-triggered FRAME/BOTH/VIDEO transitions. BodyLayout remains the
 * layout state owner; chrome/navigation surfaces emit presentation intents through this controller
 * instead of mutating BodyLayout directly.
 */
public final class VideoPresentationController {
	private VideoPresentationController() {
	}

	public static boolean enterFullscreen(MainActivityDelegate activity) {
		BodyLayout body = activity.getBody();
		if ((body == null) || body.isVideoMode()) return false;
		body.setMode(BodyLayout.Mode.VIDEO);
		return true;
	}

	public static boolean enterFullscreenFromSplit(MainActivityDelegate activity) {
		BodyLayout body = activity.getBody();
		return (body != null) && body.isBothMode() && enterFullscreen(activity);
	}

	public static boolean leaveFullscreen(MainActivityDelegate activity, BodyLayout.Mode targetMode) {
		BodyLayout body = activity.getBody();
		if ((body == null) || !body.isVideoMode()) return false;
		body.setMode(targetMode);
		activity.setBarsHidden(false);
		activity.post(() -> {
			if (activity.getRuntimeHostMode().usesAutomotivePresentation())
				MediaItemListView.focusActive(activity.getContext(), null);
			TopBarController.refresh(activity);
		});
		return true;
	}

	public static void showFrame(MainActivityDelegate activity) {
		BodyLayout body = activity.getBody();
		if ((body != null) && !body.isFrameMode()) body.setMode(BodyLayout.Mode.FRAME);
	}
}
