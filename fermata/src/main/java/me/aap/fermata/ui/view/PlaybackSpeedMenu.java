package me.aap.fermata.ui.view;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.menu.OverlayMenu;

final class PlaybackSpeedMenu implements OverlayMenu.CloseHandler {
	private final MainActivityDelegate activity;
	private PrefStore store;

	PlaybackSpeedMenu(MainActivityDelegate activity) {
		this.activity = activity;
	}

	void build(OverlayMenu.Builder builder, Item item) {
		store = new PrefStore(item);
		PreferenceSet set = new PreferenceSet();

		set.addFloatPref(options -> {
			options.title = R.string.speed;
			options.store = store;
			options.pref = MediaPrefs.SPEED;
			options.scale = 0.1f;
			options.seekMin = 1;
			options.seekMax = 20;
		});
		set.addBooleanPref(options -> {
			options.title = R.string.current_track;
			options.store = store;
			options.pref = store.track;
		});
		set.addBooleanPref(options -> {
			options.title = R.string.current_folder;
			options.store = store;
			options.pref = store.folder;
		});

		set.addToMenu(builder, true);
		builder.setCloseHandlerHandler(this);
	}

	@Override
	public void menuClosed(OverlayMenu menu) {
		store.apply();
	}

	private final class PrefStore extends BasicPreferenceStore {
		private final Pref<BooleanSupplier> track = Pref.b("TRACK", false);
		private final Pref<BooleanSupplier> folder = Pref.b("FOLDER", false);
		private final MediaSessionCallback callback =
				activity.getMediaServiceBinder().getMediaSessionCallback();
		private final Item item;

		private PrefStore(Item item) {
			this.item = item;
			MediaPrefs prefs = item.getPrefs();
			BrowsableItem parent = item.getParent();
			boolean set = false;

			try (PreferenceStore.Edit edit = editPreferenceStore()) {
				if (prefs.hasPref(MediaPrefs.SPEED)) {
					edit.setBooleanPref(track, true);
					edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
					set = true;
				} else {
					edit.setBooleanPref(track, false);
				}

				if (parent != null) {
					prefs = parent.getPrefs();
					if (prefs.hasPref(MediaPrefs.SPEED)) {
						edit.setBooleanPref(folder, true);
						if (!set) {
							edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
							set = true;
						}
					} else {
						edit.setBooleanPref(folder, false);
					}
				} else {
					edit.setBooleanPref(folder, false);
				}

				if (!set) edit.setFloatPref(MediaPrefs.SPEED,
						callback.getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED));
			}
		}

		private void apply() {
			BrowsableItem parent = item.getParent();
			boolean set = false;

			if (getBooleanPref(track)) {
				item.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
				set = true;
			} else {
				item.getPrefs().removePref(MediaPrefs.SPEED);
			}

			if (parent != null) {
				if (getBooleanPref(folder)) {
					parent.getPrefs().applyFloatPref(MediaPrefs.SPEED,
							getFloatPref(MediaPrefs.SPEED));
					set = true;
				} else {
					parent.getPrefs().removePref(MediaPrefs.SPEED);
				}
			}

			if (!set) callback.getPlaybackControlPrefs()
					.applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
		}

		@Override
		public void applyFloatPref(boolean removeDefault, Pref<? extends DoubleSupplier> pref,
				float value) {
			if (value == 0.0f) value = 0.1f;
			super.applyFloatPref(removeDefault, pref, value);
			if (callback.isPlaying()) callback.onSetPlaybackSpeed(value);
		}
	}
}
