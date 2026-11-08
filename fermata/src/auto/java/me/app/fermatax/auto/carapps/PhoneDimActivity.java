package me.app.fermatax.auto.carapps;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import me.aap.fermata.R;

/**
 * Black, lowest-brightness phone screen shown while Car Apps runs on the car: the screen stays on
 * (so the car's virtual display keeps rendering) and tells the user why.
 * In panel mode: screen is turned off via root, touch is swallowed and does not end it.
 * In dim mode: touch or leaving the app ends it.
 */
public final class PhoneDimActivity extends Activity {
	private static WeakReference<PhoneDimActivity> current;

	public static boolean isShowing() {
		return current != null && current.get() != null;
	}

	public static void dismiss() {
		PhoneDimActivity a = (current == null) ? null : current.get();
		if (a != null) a.finish();
	}

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		current = new WeakReference<>(this);
		// Woken after a timeout, a swipe lock is up at once and would pause the car apps; a secure lock stays
		android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
		if ((km != null) && !km.isKeyguardSecure()) {
			setShowWhenLocked(true);
			if (km.isKeyguardLocked()) km.requestDismissKeyguard(this, null);
		}
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = 0.01f;
		getWindow().setAttributes(lp);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);

		boolean panelMode = CarDisplayClient.get().isPanelOffRequested();
		if (panelMode) {
			root.setOnTouchListener((v, e) -> true);
		} else {
			TextView tv = new TextView(this);
			tv.setText(R.string.car_apps_phone_dim_text);
			tv.setTextColor(0xFF6B7280);
			tv.setTextSize(16f);
			tv.setGravity(Gravity.CENTER);
			root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
			root.setOnClickListener((View v) -> finish());
		}
		setContentView(root);
	}

	@Override
	protected void onResume() {
		super.onResume();
		CarDisplayClient client = CarDisplayClient.get();
		if (client.isPanelOffRequested()) {
			client.setPanelOff(true);
			if (!client.isPanelOff()) finish();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		// Home / app switch: the phone goes back to normal behaviour. Rotating also stops it, but it comes back.
		if (isChangingConfigurations()) return;
		CarDisplayClient.get().setPanelOff(false);
		finish();
	}

	@Override
	protected void onDestroy() {
		if (current != null && current.get() == this) current = null;
		super.onDestroy();
	}
}
