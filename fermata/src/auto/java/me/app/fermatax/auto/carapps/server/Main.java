package me.app.fermatax.auto.carapps.server;

import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * Entry point for the CarDisplayServer process invoked via app_process.
 */
@SuppressWarnings("deprecation")
public class Main {

	public static void main(String[] args) {
		if (args.length > 0 && "--selftest".equals(args[0])) {
			runSelftest();
			return;
		}

		if (args.length < 3) {
			System.err.println("Usage: app_process / me.app.fermatax.auto.carapps.server.Main <appUid> <versionCode> <appPackage> | --selftest");
			System.exit(1);
			return;
		}

		try {
			int appUid = Integer.parseInt(args[0]);
			int versionCode = Integer.parseInt(args[1]);
			String appPackage = args[2];

			if (Looper.myLooper() == null) {
				Looper.prepareMainLooper();
			}

			Hidden.init();
			CarDisplayServerImpl server = new CarDisplayServerImpl(appUid, versionCode, appPackage);
			System.out.println("SERVER_READY uid=" + appUid + " version=" + versionCode + " pkg=" + appPackage);

			Looper.loop();
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(2);
		}
	}

	private static void runSelftest() {
		try {
			if (Looper.myLooper() == null) {
				Looper.prepareMainLooper();
			}

			Hidden.init();
			CarDisplayServerImpl server = new CarDisplayServerImpl(0, 1, "me.app.fermataX.auto.debug");

			int caps = server.capabilities();
			System.out.println("CAPABILITIES=0x" + Integer.toHexString(caps));

			ImageReader ir = ImageReader.newInstance(1280, 720, PixelFormat.RGBA_8888, 2);
			int id = server.attach(ir.getSurface(), 1280, 720, 160, new Binder());
			System.out.println("ATTACHED displayId=" + id);

			int sdkFlags = VirtualDisplayFlags.forSdk(Build.VERSION.SDK_INT);
			System.out.println("DISPLAY_FLAGS=0x" + Integer.toHexString(sdkFlags));

			// Verify display in dumpsys
			boolean dumpsysConfirmed = false;
			try {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(Runtime.getRuntime().exec(new String[]{"dumpsys", "display"}).getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.contains("fermatax-car") && line.contains("FLAG_TRUSTED")) {
						dumpsysConfirmed = true;
						break;
					}
				}
				reader.close();
			} catch (Throwable t) {
				dumpsysConfirmed = false;
			}
			System.out.println("DUMPSYS_DISPLAY_CONFIRMED=" + dumpsysConfirmed);

			boolean imeOk = Hidden.setDisplayImePolicy(id, 0);
			System.out.println("IME_POLICY_OK=" + imeOk);

			long now = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 640f, 360f, 0);
			server.inject(down);
			down.recycle();

			MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, 640f, 360f, 0);
			server.inject(up);
			up.recycle();
			System.out.println("INJECT_TOUCH_OK=true");

			server.injectKey(KeyEvent.KEYCODE_BACK);
			System.out.println("INJECT_KEY_OK=true");

			server.setPanelPower(false);
			System.out.println("PANEL_POWER_OFF_OK=true");
			System.out.println("WAKELOCK_HELD=" + server.isWakeLockHeld());

			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignored) {
			}

			server.setPanelPower(true);
			System.out.println("PANEL_POWER_ON_OK=true");

			server.release();
			ir.close();
			System.out.println("RELEASE_OK=true");

			System.out.println("SELFTEST_SUCCESS displayId=" + id);
			System.exit(0);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}
}
