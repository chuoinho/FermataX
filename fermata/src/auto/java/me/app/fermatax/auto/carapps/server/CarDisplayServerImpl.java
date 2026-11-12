package me.app.fermatax.auto.carapps.server;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import me.app.fermatax.auto.carapps.CallerPolicy;
import me.app.fermatax.auto.carapps.ICarDisplayServer;
import me.app.fermatax.auto.carapps.IdleExitPolicy;
import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * Concrete implementation of ICarDisplayServer running as shell, or root if su cannot switch (app_process).
 */
@SuppressWarnings("deprecation")
public class CarDisplayServerImpl extends ICarDisplayServer.Stub {

	private static final String DISPLAY_NAME = "fermatax-car";

	private final int appUid;
	private final int versionCode;
	private final String appPackage;
	/** Started over ADB: nothing can start it again without Wi-Fi, so it never leaves on its own. */
	private final boolean persist;

	private volatile VirtualDisplay virtualDisplay;
	private volatile CarVirtualDevice carDevice;
	private volatile PowerManager.WakeLock displayWakeLock;
	private volatile int displayId = -1;
	private IBinder clientBinder;
	private final Runnable idleExit = this::onIdleTimer;
	private volatile long lastDetachMs = SystemClock.elapsedRealtime();

	private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
		@Override
		public void binderDied() {
			releaseInternal();
		}
	};

	public CarDisplayServerImpl(int appUid, int versionCode, String appPackage) {
		this(appUid, versionCode, appPackage, false);
	}

	public CarDisplayServerImpl(int appUid, int versionCode, String appPackage, boolean persist) {
		this.appUid = appUid;
		this.versionCode = versionCode;
		this.appPackage = appPackage;
		this.persist = persist;
		scheduleIdleExit();
	}

	@Override
	public int capabilities() {
		boolean own = (carDevice != null) || (displayWakeLock != null);
		return Hidden.getCapabilities() | (own ? ICarDisplayServer.CAP_OWN_POWER_GROUP : 0);
	}

	@Override
	public synchronized int attach(Surface s, int w, int h, int dpi, IBinder client) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return -1;
		}
		cancelIdleExit();

		if (virtualDisplay != null && virtualDisplay.getDisplay() != null && virtualDisplay.getDisplay().isValid()) {
			setSurfaceInternal(s, w, h, dpi);
			bindClient(client);
			return displayId;
		}

		releaseDisplay();

		int flags = VirtualDisplayFlags.forVirtualDevice(Build.VERSION.SDK_INT);
		carDevice = CarVirtualDevice.create(DISPLAY_NAME, w, h, dpi, s, flags);
		if (carDevice != null) {
			virtualDisplay = carDevice.display();
		} else {
			DisplayManager dm = Hidden.getDisplayManager();
			if (dm == null) {
				return -1;
			}

			flags = VirtualDisplayFlags.forSdk(Build.VERSION.SDK_INT);
			if (!Hidden.hasCapability(ICarDisplayServer.CAP_TRUSTED)) {
				flags = VirtualDisplayFlags.untrusted(flags);
			}

			try {
				virtualDisplay = dm.createVirtualDisplay(DISPLAY_NAME, w, h, dpi, s, flags);
			} catch (SecurityException se) {
				android.util.Log.w("CarDisplayServer", "createVirtualDisplay refused, retrying untrusted", se);
				Hidden.clearCapability(ICarDisplayServer.CAP_TRUSTED);
				flags = VirtualDisplayFlags.untrusted(flags);
				try {
					virtualDisplay = dm.createVirtualDisplay(DISPLAY_NAME, w, h, dpi, s, flags);
				} catch (Throwable t) {
					android.util.Log.w("CarDisplayServer", "createVirtualDisplay failed", t);
					virtualDisplay = null;
				}
			} catch (Throwable t) {
				android.util.Log.w("CarDisplayServer", "createVirtualDisplay failed", t);
				virtualDisplay = null;
			}
		}

		if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
			releaseDisplay();
			displayId = -1;
			return -1;
		}

		if ((carDevice == null) && VirtualDisplayFlags.trustedDropped(flags, virtualDisplay.getDisplay().getFlags())) {
			Hidden.clearCapability(ICarDisplayServer.CAP_TRUSTED);
		}
		if (VirtualDisplayFlags.focusDropped(flags, virtualDisplay.getDisplay().getFlags())) {
			Hidden.clearCapability(ICarDisplayServer.CAP_FOCUS);
		}

		displayId = virtualDisplay.getDisplay().getDisplayId();
		Hidden.setDisplayImePolicy(displayId, 0); // 0 = LOCAL
		if ((carDevice == null) && VirtualDisplayFlags.needsDisplayWakeLock(Build.VERSION.SDK_INT, Hidden.getDisplayGroupId(displayId))) {
			displayWakeLock = Hidden.acquireDisplayWakeLock(displayId, "fermatax:cardisplay");
		}
		verifyInputInjection(displayId);
		bindClient(client);

		return displayId;
	}

	private void verifyInputInjection(int displayId) {
		if (Hidden.hasCapability(ICarDisplayServer.CAP_INJECT)
				&& !injectionAccepted(displayId, e -> Hidden.injectInputEvent(e, 0))) {
			Hidden.clearCapability(ICarDisplayServer.CAP_INJECT);
		}
	}

	/**
	 * A harmless key press, with its release so the display keeps no key held down. Mode 0 does not wait for the app
	 * (InputManagerService.injectInputEventToTarget, android16-release): accepted says nothing about a touch arriving.
	 */
	static boolean injectionAccepted(int displayId, Predicate<KeyEvent> inject) {
		long now = SystemClock.uptimeMillis();
		boolean accepted = true;
		for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
			KeyEvent event = new KeyEvent(now, now, action, KeyEvent.KEYCODE_UNKNOWN, 0);
			Hidden.setDisplayId(event, displayId);
			accepted &= inject.test(event);
		}
		return accepted;
	}

	private void releaseDisplay() {
		if (displayWakeLock != null) {
			displayWakeLock.release();
			displayWakeLock = null;
		}
		if (carDevice != null) {
			carDevice.close();
			carDevice = null;
		} else if (virtualDisplay != null) {
			try {
				virtualDisplay.release();
			} catch (Throwable ignored) {
			}
		}
		virtualDisplay = null;
	}

	private void bindClient(IBinder client) {
		if (client != null && client != this.clientBinder) {
			if (this.clientBinder != null) {
				try {
					this.clientBinder.unlinkToDeath(deathRecipient, 0);
				} catch (Throwable ignored) {
				}
			}
			this.clientBinder = client;
			try {
				client.linkToDeath(deathRecipient, 0);
			} catch (RemoteException ignored) {
			}
		}
	}

	@Override
	public void setSurface(@Nullable Surface s, int w, int h, int dpi) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return;
		}
		setSurfaceInternal(s, w, h, dpi);
	}

	public synchronized void setSurfaceInternal(@Nullable Surface s, int w, int h, int dpi) {
		if (virtualDisplay != null) {
			try {
				virtualDisplay.setSurface(s);
				if (canResize(w, h, dpi)) virtualDisplay.resize(w, h, dpi);
			} catch (Throwable ignored) {
			}
		}
	}

	/** Android 10: a display resized to 0 kills system_server, divide by zero in configureDisplayPolicy. */
	static boolean canResize(int w, int h, int dpi) {
		return (w > 0) && (h > 0) && (dpi > 0);
	}

	@Override
	public void inject(MotionEvent e) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return;
		}

		if (displayId >= 0 && e != null) {
			Hidden.setDisplayId(e, displayId);
			boolean ok = Hidden.injectInputEvent(e, 0);
			if (!ok && e.getAction() == MotionEvent.ACTION_UP) {
				try {
					int x = Math.round(e.getX());
					int y = Math.round(e.getY());
					Runtime.getRuntime().exec(new String[]{"input", "-d", String.valueOf(displayId), "tap", String.valueOf(x), String.valueOf(y)}).waitFor();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@Override
	public void injectKey(int keyCode) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return;
		}

		if (displayId >= 0) {
			boolean ok = false;
			try {
				long now = SystemClock.uptimeMillis();
				KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
				Hidden.setDisplayId(down, displayId);
				boolean downOk = Hidden.injectInputEvent(down, 0);

				KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
				Hidden.setDisplayId(up, displayId);
				boolean upOk = Hidden.injectInputEvent(up, 0);
				ok = downOk && upOk;
			} catch (Throwable ignored) {
			}

			if (!ok) {
				try {
					Runtime.getRuntime().exec(new String[]{"input", "-d", String.valueOf(displayId), "keyevent", String.valueOf(keyCode)}).waitFor();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@Override
	public int launch(ComponentName c, int intentFlags) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return -1;
		}

		if (displayId < 0 || c == null) {
			return -1;
		}

		try {
			List<String> cmd = new ArrayList<>();
			cmd.add("am");
			cmd.add("start");
			cmd.add("--display");
			cmd.add(String.valueOf(displayId));
			cmd.add("-n");
			cmd.add(c.flattenToShortString());
			boolean isLauncher = c.getClassName() != null && c.getClassName().contains("LauncherActivity");
			if (!isLauncher) {
				cmd.add("--activity-multiple-task");
			}
			if (intentFlags != 0) {
				cmd.add("-f");
				cmd.add(String.valueOf(intentFlags));
			}
			Process p = Runtime.getRuntime().exec(cmd.toArray(new String[0]));
			return p.waitFor();
		} catch (Throwable t) {
			return -1;
		}
	}

	@Override
	public void launchOnPhone(ComponentName c, int intentFlags) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid) || c == null) {
			return;
		}

		try {
			Runtime.getRuntime().exec(new String[]{"am", "start", "--display", "0",
					"-n", c.flattenToShortString(), "-f", String.valueOf(intentFlags)}).waitFor();
		} catch (Throwable ignored) {
		}
	}

	@Override
	@SuppressLint("NewApi") // the server only runs on Android 11+ (CarAppsFeature.visible)
	public String closeTopApp(boolean force) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return "";
		}

		if (displayId < 0) {
			return "";
		}

		ActivityManager.RunningTaskInfo topTask = Hidden.getTopTask(displayId);
		if (topTask == null || topTask.topActivity == null) {
			return "";
		}

		if (topTask.topActivity.getClassName() != null &&
				topTask.topActivity.getClassName().contains("LauncherActivity")) {
			return "";
		}

		String pkg = topTask.topActivity.getPackageName();
		if (appPackage != null && appPackage.equals(pkg)) {
			return "";
		}

		if (!force) {
			boolean removed = Hidden.removeTask(topTask.taskId);
			if (!removed) {
				if (pkg != null && !pkg.isEmpty()) {
					try {
						Runtime.getRuntime().exec(new String[]{"am", "force-stop", pkg}).waitFor();
					} catch (Throwable ignored) {
					}
				}
			}
		} else {
			if (pkg != null && !pkg.isEmpty()) {
				try {
					Runtime.getRuntime().exec(new String[]{"am", "force-stop", pkg}).waitFor();
				} catch (Throwable ignored) {
				}
			}
		}

		return pkg != null ? pkg : "";
	}

	@Override
	public void release() {
		int callingUid = Binder.getCallingUid();
		if (callingUid != android.os.Process.myUid() && !CallerPolicy.serverAccepts(callingUid, appUid)) {
			return;
		}
		releaseInternal();
	}

	@Override
	public void setPanelPower(boolean on) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return;
		}
		Hidden.setPhysicalDisplayPowerMode(on ? 2 : 0); // 2 = NORMAL, 0 = OFF
	}

	@Override
	public void sleepPhone() {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) return;
		sendPowerIfScreenOn();
	}

	void sendPowerIfScreenOn() {
		sleepIfScreenOn(Hidden.isPhoneScreenOn(), () -> {
			try {
				Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"}).waitFor();
			} catch (Throwable ignored) {
			}
		});
	}

	static void sleepIfScreenOn(boolean screenOn, Runnable powerKey) {
		if (screenOn) powerKey.run();
	}

	public synchronized void releaseInternal() {
		Hidden.restorePanelIfForcedOff();
		if (clientBinder != null) {
			try {
				clientBinder.unlinkToDeath(deathRecipient, 0);
			} catch (Throwable ignored) {
			}
			clientBinder = null;
		}
		releaseDisplay();
		displayId = -1;
		lastDetachMs = SystemClock.elapsedRealtime();
		scheduleIdleExit();
	}

	/** One timer from the last detach (or process start): exit only if still without a client. */
	private void scheduleIdleExit() {
		if (persist) return;
		Looper l = Looper.getMainLooper();
		if (l == null) return;
		Handler h = new Handler(l);
		h.removeCallbacks(idleExit);
		h.postDelayed(idleExit, IdleExitPolicy.IDLE_LIMIT_MS);
	}

	private synchronized void onIdleTimer() {
		long now = SystemClock.elapsedRealtime();
		if (IdleExitPolicy.shouldExit(now, lastDetachMs, virtualDisplay != null)) {
			System.exit(0); // the shutdown hook restores the panel
		}
	}

	private void cancelIdleExit() {
		Looper l = Looper.getMainLooper();
		if (l != null) new Handler(l).removeCallbacks(idleExit);
	}

	@Override
	public int serverVersion() {
		return versionCode;
	}

	public Display getDisplay() {
		return virtualDisplay != null ? virtualDisplay.getDisplay() : null;
	}

	public int getDisplayId() {
		return displayId;
	}
}
