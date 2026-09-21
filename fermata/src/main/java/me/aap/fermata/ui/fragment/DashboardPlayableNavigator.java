package me.aap.fermata.ui.fragment;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.Consumer;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoPresentationController;

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
		if (play) {
			var binder = activity.getMediaServiceBinder();
			var selection = binder.captureUserSelection();
			binder.routeUserSelection(selection, item, -1, admission -> {
				PlayableItem canonical = PlayableItemResolver.unwrap(item);
				if (!activity.goToItem(canonical)) return me.aap.utils.async.Completed.completed(
						me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY);
				var result = new me.aap.utils.async.Promise<me.aap.fermata.auto.AutomotiveNavigationController.OpenResult>();
				activity.post(() -> {
					if (!admission.isCurrent()) { result.complete(
							me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.CANCELLED); return; }
					playIfNeeded(activity, item, admission);
					if (onOpened != null) activity.post(() -> { if (admission.isCurrent()) onOpened.accept(canonical); });
					result.complete(me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.LOAD_DISPATCHED);
				});
				return result;
			});
			return;
		}
		PlayableItem presented = item;
		PlayableItem canonical = PlayableItemResolver.unwrap(item);
		if (!activity.goToItem(canonical)) return;
		if (!play) {
			if (onOpened != null) activity.post(() -> onOpened.accept(canonical));
			return;
		}

	}

	private static void playIfNeeded(MainActivityDelegate activity, PlayableItem item,
			me.aap.fermata.auto.OpenOnCarMediaRouting.Admission admission) {
		PlayableItem current = activity.getMediaServiceBinder().getCurrentItem();
		if ((current == null) || !isSamePlayable(current, item) ||
				!activity.getMediaServiceBinder().isPlaying() ||
				activity.getMediaSessionCallback().getVideoOutputCoordinator().getHost() != admission.target()) {
			activity.getMediaServiceBinder().playRoutedItem(item, -1, admission);
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
					VideoPresentationController.enterFullscreen(activity);
				return;
			}

			if (attempt < 3) enterCurrentVideoFullscreen(activity, item, attempt + 1);
		}, attempt == 0 ? 0L : 200L);
	}
}
