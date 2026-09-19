package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;
import android.widget.CompoundButton;

import me.aap.fermata.auto.OpenOnCarMode;
import me.aap.fermata.ui.policy.RuntimeHostMode;

/** Renders the phone-shell opt-in without coupling it to dashboard or media surfaces. */
public final class PhoneOpenOnCarStripController {
	private final OpenOnCarMode mode;
	private final View strip;
	private final CompoundButton toggle;
	private final OpenOnCarMode.Listener listener = (available, enabled, revision) -> render();
	private boolean rendering;

	public PhoneOpenOnCarStripController(OpenOnCarMode mode, View strip, CompoundButton toggle) {
		this.mode = mode;
		this.strip = strip;
		this.toggle = toggle;
		toggle.setOnCheckedChangeListener((button, checked) -> {
			if (!rendering) mode.setEnabled(checked);
		});
		mode.addListener(listener);
	}

	public void setVisible(boolean visible) {
		strip.setVisibility(visible ? VISIBLE : GONE);
	}

	public void refresh(RuntimeHostMode hostMode, boolean barsHidden) {
		strip.setVisibility(resolveVisibility(hostMode, barsHidden));
	}

	static int resolveVisibility(RuntimeHostMode hostMode, boolean barsHidden) {
		return (!barsHidden && (hostMode == RuntimeHostMode.PHONE)) ? VISIBLE : GONE;
	}

	public void close() {
		mode.removeListener(listener);
	}

	private void render() {
		rendering = true;
		try {
			toggle.setEnabled(mode.isAvailable());
			toggle.setChecked(mode.isEnabled());
		} finally {
			rendering = false;
		}
	}
}
