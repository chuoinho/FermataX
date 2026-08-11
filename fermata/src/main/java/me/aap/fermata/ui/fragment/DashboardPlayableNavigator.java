package me.aap.fermata.ui.fragment;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.Consumer;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.BodyLayout;

final class DashboardPlayableNavigator {
	private DashboardPlayableNavigator() {
	}

	static boolean isSamePlayable(PlayableItem first, PlayableItem second) {
		first = PlayableItemResolver.unwrap(first);
		second = PlayableItemResolver.unwrap(second);
		return TextUtils.equals(first.getOrigId(), second.getOrigId()) ||
				TextUtils.equals(first.getId(), second.getId());
	}

	static void goToPlayable(MainActivityDelegate activity, PlayableItem item) {
		route(activity, item, null, false);
	}

	static void openSmartTop(MainActivityDelegate activity, PlayableItem item) {
		route(activity, item,
				resolved -> onSmartTopTargetOpened(activity, resolved), true);
	}

	static void playAndGoToPlayable(MainActivityDelegate activity, PlayableItem item) {
		route(activity, item, null, true);
	}

	static void togglePlayback(MainActivityDelegate activity, PlayableItem item) {
		activity.getMediaServiceBinder().togglePlayback(item);
	}

	private static void route(MainActivityDelegate activity, PlayableItem item,
			@Nullable Consumer<PlayableItem> onOpened, boolean play) {
		PlayableItem presented = item;
		PlayableItem canonical = PlayableItemResolver.unwrap(item);
		if (!activity.goToItem(canonical)) return;
		if (!play) {
			if (onOpened != null) activity.post(() -> onOpened.accept(canonical));
			return;
		}

		// Let the destination fragment attach its playback surface before selecting the engine.
		// This is especially important for immutable WebView items opened from Recent/Favorites:
		// selecting an engine first can hand the request to the WebView for the previous item.
		activity.post(() -> {
			playIfNeeded(activity, presented);
			if (onOpened != null) activity.post(() -> onOpened.accept(canonical));
		});
	}

	private static void playIfNeeded(MainActivityDelegate activity, PlayableItem item) {
		PlayableItem current = activity.getCurrentPlayable();
		if ((current == null) || !isSamePlayable(current, item) ||
				!activity.getMediaServiceBinder().isPlaying()) {
			activity.getMediaServiceBinder().playItem(item);
		}
	}

	private static void onSmartTopTargetOpened(MainActivityDelegate activity, PlayableItem item) {
		if (!item.isVideo()) {
			activity.post(() -> activity.getControlPanel().setVisibility(View.VISIBLE));
			return;
		}

		enterCurrentVideoFullscreen(activity, item, 0);
	}

	private static void enterCurrentVideoFullscreen(MainActivityDelegate activity, PlayableItem item,
																			 int attempt) {
		activity.postDelayed(() -> {
			PlayableItem current = activity.getCurrentPlayable();
			MediaEngine engine = activity.getMediaServiceBinder().getCurrentEngine();
			if ((current != null) && isSamePlayable(current, item) && (engine != null)) {
				// Web-based engines own their fullscreen surface and restore it from their fragment.
				if (engine.isSplitModeSupported() && engine.isVideoModeRequired())
					activity.getBody().setMode(BodyLayout.Mode.VIDEO);
				return;
			}

			if (attempt < 3) enterCurrentVideoFullscreen(activity, item, attempt + 1);
		}, attempt == 0 ? 0L : 200L);
	}
}
