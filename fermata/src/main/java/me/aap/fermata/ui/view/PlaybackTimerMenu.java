package me.aap.fermata.ui.view;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.menu.OverlayMenu;

final class PlaybackTimerMenu extends BasicPreferenceStore
		implements OverlayMenu.CloseHandler {
	private final Pref<IntSupplier> hours = Pref.i("H", 0);
	private final Pref<IntSupplier> minutes = Pref.i("M", 0);
	private final OverlayMenu menu;
	private final MainActivityDelegate activity;
	private final Runnable refresh;
	private boolean changed;
	private boolean closed;

	PlaybackTimerMenu(OverlayMenu menu, MainActivityDelegate activity, Runnable refresh) {
		this.menu = menu;
		this.activity = activity;
		this.refresh = refresh;
	}

	void build(OverlayMenu.Builder builder) {
		PreferenceSet set = new PreferenceSet();
		int time = activity.getMediaSessionCallback().getPlaybackTimer();

		if (time > 0) {
			int[] parts = splitSeconds(time);
			applyIntPref(hours, parts[0]);
			applyIntPref(minutes, parts[1]);
		}

		set.addIntPref(options -> {
			options.title = R.string.hours;
			options.store = this;
			options.pref = hours;
			options.seekMin = 0;
			options.seekMax = 12;
		});
		set.addIntPref(options -> {
			options.title = R.string.minutes;
			options.store = this;
			options.pref = minutes;
			options.seekMin = 0;
			options.seekMax = 60;
			options.seekScale = 5;
		});

		set.addToMenu(builder, true);
		builder.setCloseHandlerHandler(this);
		changed = false;
		startTimer();
	}

	@Override
	public void applyIntPref(boolean removeDefault, Pref<? extends IntSupplier> pref, int value) {
		super.applyIntPref(removeDefault, pref, value);
		changed = true;
		startTimer();
	}

	@Override
	public void menuClosed(OverlayMenu ignored) {
		closed = true;
		if (!changed) return;
		int h = getIntPref(hours);
		int m = getIntPref(minutes);
		activity.getMediaSessionCallback().setPlaybackTimer(toSeconds(h, m));
		refresh.run();
	}

	static int[] splitSeconds(int seconds) {
		int h = seconds / 3600;
		return new int[]{h, (seconds - h * 3600) / 60};
	}

	static int toSeconds(int hours, int minutes) {
		return hours * 3600 + minutes * 60;
	}

	private void startTimer() {
		activity.postDelayed(() -> {
			if (!closed) menu.hide();
		}, 60000);
	}
}
