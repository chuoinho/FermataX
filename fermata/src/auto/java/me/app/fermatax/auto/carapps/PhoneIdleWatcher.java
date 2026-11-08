package me.app.fermatax.auto.carapps;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.function.BooleanSupplier;

import me.aap.utils.log.Log;

/**
 * The car session keeps the phone awake, so the phone's own screen timeout never fires.
 * This counts it instead: a 0x0 overlay on the phone hears every touch there, and after
 * screen_off_timeout without one, while the phone screen is lit, onIdle runs.
 */
final class PhoneIdleWatcher {
	static final long TICK_MS = 1000;
	private final Handler handler;
	private final BooleanSupplier dark;
	private final Runnable onIdle;
	private final Runnable tick = this::tick;
	private Context ctx;
	private View overlay;
	private WindowManager wm;
	private long lastActivity;

	PhoneIdleWatcher(Handler handler, BooleanSupplier dark, Runnable onIdle) {
		this.handler = handler;
		this.dark = dark;
		this.onIdle = onIdle;
	}

	static boolean isIdle(long now, long lastActivity, long timeout) {
		return (timeout > 0) && (now - lastActivity >= timeout);
	}

	void start(Context c) {
		if (overlay != null) return;
		ctx = c.getApplicationContext();
		lastActivity = SystemClock.uptimeMillis();
		if (!Settings.canDrawOverlays(ctx)) {
			Log.i("PhoneIdleWatcher: no overlay permission, phone screen timeout is not counted");
			return;
		}
		try {
			DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
			Display phone = dm.getDisplay(Display.DEFAULT_DISPLAY);
			wm = (WindowManager) ctx.createDisplayContext(phone).getSystemService(Context.WINDOW_SERVICE);
			WindowManager.LayoutParams lp = new WindowManager.LayoutParams(0, 0,
					WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
							| WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
					PixelFormat.TRANSLUCENT);
			View v = new View(ctx);
			v.setOnTouchListener((view, e) -> {
				if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) lastActivity = SystemClock.uptimeMillis();
				return false;
			});
			wm.addView(v, lp);
			overlay = v;
			handler.postDelayed(tick, TICK_MS);
		} catch (Throwable e) {
			Log.e(e, "PhoneIdleWatcher: overlay failed");
		}
	}

	void stop() {
		handler.removeCallbacks(tick);
		View v = overlay;
		overlay = null;
		if (v == null) return;
		try {
			wm.removeView(v);
		} catch (Throwable e) {
			Log.e(e, "PhoneIdleWatcher: overlay removal failed");
		}
	}

	private void tick() {
		long now = SystemClock.uptimeMillis();
		if (dark.getAsBoolean()) {
			lastActivity = now; // the count starts when the screen comes on
		} else if (isIdle(now, lastActivity, Settings.System.getInt(ctx.getContentResolver(),
				Settings.System.SCREEN_OFF_TIMEOUT, 30_000))) {
			lastActivity = now;
			onIdle.run();
		}
		if (overlay != null) handler.postDelayed(tick, TICK_MS);
	}
}
