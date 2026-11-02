package me.app.fermatax.auto.carapps;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import me.aap.fermata.R;

/**
 * Black, lowest-brightness phone screen shown while Car Apps runs on the car: the screen stays on
 * (so the car's virtual display keeps rendering) and tells the user why. Touch or leaving the app ends it.
 */
public final class PhoneDimActivity extends Activity {
	private static WeakReference<PhoneDimActivity> current;

	/** Shows the screen on the phone display even when called from the car-side activity. */
	public static void show(Context ctx) {
		Intent i = new Intent(ctx.getApplicationContext(), PhoneDimActivity.class)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		ActivityOptions o = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY);
		ctx.getApplicationContext().startActivity(i, o.toBundle());
	}

	public static void dismiss() {
		PhoneDimActivity a = (current == null) ? null : current.get();
		if (a != null) a.finish();
	}

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		current = new WeakReference<>(this);
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = 0.01f;
		getWindow().setAttributes(lp);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		TextView tv = new TextView(this);
		tv.setText(R.string.car_apps_phone_dim_text);
		tv.setTextColor(0xFF6B7280);
		tv.setTextSize(16f);
		tv.setGravity(Gravity.CENTER);
		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
		root.setOnClickListener((View v) -> finish());
		setContentView(root);
	}

	@Override
	protected void onStop() {
		super.onStop();
		finish(); // Home / app switch: the phone goes back to normal behaviour
	}

	@Override
	protected void onDestroy() {
		if (current != null && current.get() == this) current = null;
		super.onDestroy();
	}
}
