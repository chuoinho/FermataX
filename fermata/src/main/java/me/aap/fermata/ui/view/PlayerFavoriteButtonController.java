package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;

final class PlayerFavoriteButtonController {
	private final MainActivityDelegate activity;
	private final ImageView button;

	PlayerFavoriteButtonController(MainActivityDelegate activity, ImageView button) {
		this.activity = activity;
		this.button = button;
		button.setOnClickListener(this::toggle);
	}

	void refresh() {
		PlayableItem item = activity.getMediaServiceBinder().getCurrentItem();
		if (!isSupported(item)) {
			button.setVisibility(GONE);
			return;
		}

		boolean favorite = item.isFavoriteItem();
		button.setVisibility(VISIBLE);
		button.setImageResource(favorite ? R.drawable.favorite_filled : R.drawable.favorite);
		button.setContentDescription(button.getContext().getString(favorite ?
				R.string.favorites_remove : R.string.favorites_add));
	}

	private void toggle(View view) {
		PlayableItem item = activity.getMediaServiceBinder().getCurrentItem();
		if (!isSupported(item)) {
			refresh();
			return;
		}

		view.setEnabled(false);
		FutureSupplier<Void> done = item.isFavoriteItem() ?
				item.getLib().getFavorites().removeItem(item) :
				item.getLib().getFavorites().addItem(item);
		done.main().onCompletion((result, error) -> {
			view.setEnabled(true);
			refresh();
		});
	}

	private static boolean isSupported(@Nullable PlayableItem item) {
		return (item != null) && !item.isExternal();
	}
}
