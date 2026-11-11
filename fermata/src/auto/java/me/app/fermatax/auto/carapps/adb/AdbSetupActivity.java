package me.app.fermatax.auto.carapps.adb;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsFeature;

/**
 * One-time setup of Phone Apps without root: Developer options, Wireless debugging, then the pairing code typed into
 * the notification (see AdbPairing). Asks for no permission; with notifications off it only explains how to turn them on.
 */
public final class AdbSetupActivity extends Activity {
	private final Runnable stateListener = () -> runOnUiThread(this::render);
	private TextView body;
	private Button primary;

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		float dp = getResources().getDisplayMetrics().density;
		int pad = Math.round(24 * dp);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(this);
		title.setText(R.string.car_apps_setup_title);
		title.setTextSize(22f);
		title.setAccessibilityHeading(true);
		column.addView(title);

		body = new TextView(this);
		body.setTextSize(16f);
		body.setPadding(0, pad / 2, 0, pad / 2);
		body.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		column.addView(body);

		primary = new Button(this);
		primary.setMinHeight(Math.round(48 * dp));
		styleButton(primary);
		column.addView(primary);

		ScrollView scroll = new ScrollView(this);
		scroll.addView(column);
		// Edge-to-edge (forced from Android 15): keep the content clear of the status, navigation bars and cutout
		ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
		setContentView(scroll);
	}

	/** The default grey button with the theme's light text is unreadable: fill it with the theme accent and pick the text. */
	private void styleButton(Button button) {
		android.util.TypedValue accent = new android.util.TypedValue();
		if (!getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, accent, true)) return;
		int fill = accent.data | 0xFF000000;
		button.setBackgroundTintList(ColorStateList.valueOf(fill));
		button.setTextColor((ColorUtils.calculateContrast(Color.BLACK, fill) >= ColorUtils.calculateContrast(Color.WHITE, fill))
				? Color.BLACK : Color.WHITE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		AdbPairing.addListener(stateListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The reply notification times out and can be swiped away: post it again (it replaces the old one)
		boolean again = AdbPairing.state() != AdbPairing.State.WORKING;
		if (again && !CarAppsFeature.isAdbPaired() && developerOptionsOn()) AdbPairing.start(this);
		render();
	}

	@Override
	protected void onStop() {
		AdbPairing.removeListener(stateListener);
		super.onStop();
	}

	private boolean developerOptionsOn() {
		return Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
	}

	private void render() {
		AdbPairing.State state = AdbPairing.state();
		if (CarAppsFeature.isAdbPaired()) {
			show(getString(R.string.car_apps_setup_done), getString(android.R.string.ok), this::finish);
		} else if (!developerOptionsOn()) {
			show(getString(R.string.car_apps_setup_dev_off), getString(R.string.car_apps_setup_open_about),
					() -> open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
		} else if (state == AdbPairing.State.NOTIFICATIONS_OFF) {
			show(getString(R.string.car_apps_setup_notif_off), getString(R.string.car_apps_setup_open_notif),
					() -> open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
							.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
		} else if (state == AdbPairing.State.WORKING) {
			show(getString(R.string.car_apps_setup_working), null, null);
		} else {
			int error = (state == AdbPairing.State.WRONG_CODE) ? R.string.car_apps_setup_wrong_code
					: (state == AdbPairing.State.FAILED) ? R.string.car_apps_setup_failed : 0;
			String steps = getString(R.string.car_apps_setup_steps);
			show((error == 0) ? steps : getString(error) + "\n\n" + steps, getString(R.string.car_apps_setup_open_dev),
					() -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
		}
	}

	/** A null label hides the button while the work runs: nothing can be pressed twice. */
	private void show(String text, String label, Runnable action) {
		body.setText(text);
		primary.setVisibility((label == null) ? View.GONE : View.VISIBLE);
		primary.setText(label);
		primary.setContentDescription(label);
		primary.setOnClickListener((action == null) ? null : v -> action.run());
	}

	private void open(Intent intent) {
		try {
			startActivity(intent);
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(this, R.string.readiness_settings_unavailable, Toast.LENGTH_LONG).show();
		}
	}
}
