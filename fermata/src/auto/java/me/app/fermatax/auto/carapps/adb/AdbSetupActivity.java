package me.app.fermatax.auto.carapps.adb;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
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
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.PhoneAppsGuide;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Channel;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.Page;
import me.aap.fermata.ui.carapps.PhoneAppsGuide.StepState;
import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * The Phone Apps guide on the phone: the setup steps of the channel in use (root, or without root Developer options,
 * Wireless debugging, then the pairing code typed into the notification, see AdbPairing), and "How to use".
 * Asks for no permission; with notifications off it only explains how to turn them on.
 */
public final class AdbSetupActivity extends Activity {
	public static final String EXTRA_PAGE = "page";
	private static final String ADB_WIFI = "adb_wifi_enabled";
	private final Runnable stateListener = () -> runOnUiThread(this::render);
	private Page page;
	private TextView title;
	private LinearLayout steps;
	private TextView body;
	private LinearLayout sections;
	private Button primary;

	/** Set up again is the setup page; the caller forgets the pairing first. */
	public static Intent intent(Context ctx, Page page) {
		Page p = (page == Page.SETUP_AGAIN) ? Page.SETUP : page;
		Intent i = new Intent(ctx, AdbSetupActivity.class).putExtra(EXTRA_PAGE, p.name());
		if (!(ctx instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return i;
	}

	static Page page(Intent intent) {
		String name = (intent == null) ? null : intent.getStringExtra(EXTRA_PAGE);
		try {
			return (name == null) ? Page.SETUP : Page.valueOf(name);
		} catch (IllegalArgumentException ex) {
			return Page.SETUP;
		}
	}

	/** Title and body string pairs; the phone screen text follows what the screen does on this Android. */
	static int[] usageSections(Channel channel, int sdk) {
		int[] common = {R.string.car_apps_guide_open_title, R.string.car_apps_guide_open_body,
				R.string.car_apps_guide_drawer_title, R.string.car_apps_guide_drawer_body,
				R.string.car_apps_guide_keyboard_title, R.string.car_apps_guide_keyboard_body,
				R.string.car_apps_guide_buttons_title, R.string.car_apps_guide_buttons_body,
				R.string.car_apps_guide_screen_title, VirtualDisplayFlags.usesVirtualDevice(sdk) ?
				R.string.car_apps_guide_screen_body_sleep : R.string.car_apps_guide_screen_body_dark};
		int[] wifi = (channel == Channel.ADB) ? new int[]{R.string.car_apps_guide_wifi_title,
				R.string.car_apps_guide_wifi_body} : new int[0];
		int[] end = {R.string.car_apps_guide_off_title, R.string.car_apps_guide_off_body,
				R.string.car_apps_guide_report_title, R.string.car_apps_guide_report_body};
		int[] all = new int[common.length + wifi.length + end.length];
		System.arraycopy(common, 0, all, 0, common.length);
		System.arraycopy(wifi, 0, all, common.length, wifi.length);
		System.arraycopy(end, 0, all, common.length + wifi.length, end.length);
		return all;
	}

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		page = page(getIntent());
		float dp = getResources().getDisplayMetrics().density;
		int pad = Math.round(24 * dp);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setPadding(pad, pad, pad, pad);

		title = new TextView(this);
		title.setTextSize(22f);
		title.setAccessibilityHeading(true);
		column.addView(title);

		steps = new LinearLayout(this);
		steps.setOrientation(LinearLayout.VERTICAL);
		steps.setPadding(0, pad / 2, 0, 0);
		column.addView(steps);

		body = new TextView(this);
		body.setTextSize(16f);
		body.setPadding(0, pad / 2, 0, pad / 2);
		body.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		column.addView(body);

		sections = new LinearLayout(this);
		sections.setOrientation(LinearLayout.VERTICAL);
		sections.setVisibility(View.GONE);
		column.addView(sections);

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
		CarAppsFeature.addListener(stateListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The reply notification times out and can be swiped away: post it again (it replaces the old one)
		boolean again = AdbPairing.state() != AdbPairing.State.WORKING;
		if ((page != Page.USAGE) && (channel() == Channel.ADB) && again && !CarAppsFeature.isAdbPaired()
				&& developerOptionsOn()) {
			AdbPairing.start(this);
		}
		render();
	}

	@Override
	protected void onStop() {
		AdbPairing.removeListener(stateListener);
		CarAppsFeature.removeListener(stateListener);
		super.onStop();
	}

	private static Channel channel() {
		return PhoneAppsGuide.channel(CarAppsFeature.state(), Build.VERSION.SDK_INT);
	}

	private boolean developerOptionsOn() {
		return Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
	}

	private void render() {
		if (page == Page.USAGE) {
			renderUsage();
			return;
		}
		title.setText(R.string.car_apps_setup_title);
		sections.setVisibility(View.GONE);
		body.setVisibility(View.VISIBLE);
		switch (channel()) {
			case ROOT -> renderRoot();
			case ADB -> renderAdb();
			case NONE -> {
				steps.setVisibility(View.GONE);
				show(getString(R.string.car_apps_status_unsupported), getString(android.R.string.ok), this::finish);
			}
		}
	}

	private void renderRoot() {
		boolean granted = CarAppsFeature.usable(CarAppsFeature.state());
		AutomotiveConnectionState car = AutomotiveConnectionState.get();
		boolean connected = car.hasConnectionObservation()
				&& (car.state() != AutomotiveConnectionState.State.DISCONNECTED);
		// a server reported its capabilities: it has run on the car before
		boolean opened = (CarAppsFeature.capabilities() & CarAppsChecks.CAP_VD) != 0;
		showSteps(new int[]{R.string.car_apps_step_allow_root, R.string.car_apps_step_connect_car,
				R.string.car_apps_step_open_on_car}, PhoneAppsGuide.progress(granted, granted && connected, granted && opened));
		if (granted) showDone();
		else show(getString(R.string.car_apps_setup_root_note), getString(R.string.car_apps_grant_root), this::requestRoot);
	}

	private void requestRoot() {
		CarAppsFeature.Backend b = CarAppsFeature.backend();
		if (b == null) return;
		primary.setEnabled(false); // one su prompt at a time
		b.requestRoot().onCompletion((granted, err) -> runOnUiThread(() -> {
			if (!Boolean.TRUE.equals(granted) && !isFinishing()) {
				Toast.makeText(this, R.string.car_apps_grant_root_blocked, Toast.LENGTH_LONG).show();
			}
			render();
		}));
	}

	private void renderAdb() {
		AdbPairing.State state = AdbPairing.state();
		boolean paired = CarAppsFeature.isAdbPaired();
		boolean dev = developerOptionsOn();
		boolean typed = (state == AdbPairing.State.WORKING) || (state == AdbPairing.State.WRONG_CODE)
				|| (state == AdbPairing.State.FAILED);
		boolean wireless = Settings.Global.getInt(getContentResolver(), ADB_WIFI, 0) == 1;
		showSteps(new int[]{R.string.car_apps_step_dev_options, R.string.car_apps_step_wireless_debugging,
						R.string.car_apps_step_pair_dialog, R.string.car_apps_step_type_code},
				PhoneAppsGuide.progress(dev, wireless || typed, typed, paired));
		if (paired) {
			showDone();
		} else if (!dev) {
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
			String note = getString(R.string.car_apps_setup_adb_note);
			show((error == 0) ? note : getString(error) + "\n\n" + note, getString(R.string.car_apps_setup_open_dev),
					() -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
		}
	}

	private void showDone() {
		show(getString(R.string.car_apps_setup_done), getString(R.string.car_apps_settings_how_to), () -> {
			page = Page.USAGE;
			render();
		});
	}

	private void renderUsage() {
		title.setText(R.string.car_apps_guide_title);
		steps.setVisibility(View.GONE);
		body.setVisibility(View.GONE);
		if (sections.getChildCount() == 0) {
			int[] s = usageSections(channel(), Build.VERSION.SDK_INT);
			float dp = getResources().getDisplayMetrics().density;
			for (int i = 0; i < s.length; i += 2) {
				TextView heading = new TextView(this);
				heading.setText(s[i]);
				heading.setTextSize(18f);
				heading.setTypeface(Typeface.DEFAULT_BOLD);
				heading.setAccessibilityHeading(true);
				heading.setPadding(0, Math.round(16 * dp), 0, Math.round(4 * dp));
				sections.addView(heading);
				TextView text = new TextView(this);
				text.setText(s[i + 1]);
				text.setTextSize(16f);
				sections.addView(text);
			}
			sections.setPadding(0, 0, 0, Math.round(24 * dp));
		}
		sections.setVisibility(View.VISIBLE);
		show(null, getString(R.string.car_apps_guide_got_it), this::finish);
	}

	/** Each step says whether it is done, the one to do now, or still to come, also to TalkBack. */
	private void showSteps(int[] labels, StepState[] states) {
		steps.setVisibility(View.VISIBLE);
		steps.removeAllViews();
		float dp = getResources().getDisplayMetrics().density;
		for (int i = 0; i < labels.length; i++) {
			String label = getString(labels[i]);
			TextView row = new TextView(this);
			row.setTextSize(16f);
			row.setPadding(0, Math.round(6 * dp), 0, Math.round(6 * dp));
			switch (states[i]) {
				case DONE -> {
					row.setText("✓  " + label);
					row.setContentDescription(getString(R.string.car_apps_step_done, label));
				}
				case CURRENT -> {
					row.setText("▶  " + label);
					row.setTypeface(Typeface.DEFAULT_BOLD);
					row.setContentDescription(getString(R.string.car_apps_step_current, label));
				}
				case TODO -> {
					row.setText("○  " + label);
					row.setContentDescription(getString(R.string.car_apps_step_todo, label));
				}
			}
			steps.addView(row);
		}
	}

	/** A null label hides the button while the work runs: nothing can be pressed twice. */
	private void show(String text, String label, Runnable action) {
		if (text != null) body.setText(text);
		primary.setEnabled(true);
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
