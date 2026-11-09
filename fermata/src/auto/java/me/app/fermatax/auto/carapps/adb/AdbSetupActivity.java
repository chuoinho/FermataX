package me.app.fermatax.auto.carapps.adb;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
		column.addView(primary);

		ScrollView scroll = new ScrollView(this);
		scroll.addView(column);
		setContentView(scroll);
	}

	@Override
	protected void onStart() {
		super.onStart();
		AdbPairing.addListener(stateListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		AdbPairing.State state = AdbPairing.state();
		boolean again = (state == AdbPairing.State.IDLE) || (state == AdbPairing.State.NOTIFICATIONS_OFF)
				|| (state == AdbPairing.State.DONE);
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
