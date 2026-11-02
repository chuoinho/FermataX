package me.app.fermatax.auto.carapps.server;

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

import me.app.fermatax.auto.carapps.CallerPolicy;
import me.app.fermatax.auto.carapps.ICarDisplayServer;
import me.app.fermatax.auto.carapps.IdleExitPolicy;
import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * Concrete implementation of ICarDisplayServer running as root (app_process).
 */
@SuppressWarnings("deprecation")
public class CarDisplayServerImpl extends ICarDisplayServer.Stub {

	private static final String DISPLAY_NAME = "fermatax-car";
	private static final String WAKELOCK_TAG = "fermatax:cardisplay";

	private final int appUid;
	private final int versionCode;
	private final String appPackage;
	private final PowerManager.WakeLock wakeLock;

	private volatile VirtualDisplay virtualDisplay;
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
		this.appUid = appUid;
		this.versionCode = versionCode;
		this.appPackage = appPackage;
		this.wakeLock = Hidden.createWakeLock(WAKELOCK_TAG);
		scheduleIdleExit();
	}

	@Override
	public int capabilities() {
		return Hidden.getCapabilities();
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

		if (virtualDisplay != null) {
			try {
				virtualDisplay.release();
			} catch (Throwable ignored) {
			}
			virtualDisplay = null;
		}

		DisplayManager dm = Hidden.getDisplayManager();
		if (dm == null) {
			return -1;
		}

		int flags = VirtualDisplayFlags.forSdk(Build.VERSION.SDK_INT);
		if (!Hidden.hasCapability(ICarDisplayServer.CAP_TRUSTED)) {
			flags &= ~VirtualDisplayFlags.FLAG_TRUSTED;
		}

		try {
			virtualDisplay = dm.createVirtualDisplay(DISPLAY_NAME, w, h, dpi, s, flags);
		} catch (SecurityException se) {
			Hidden.clearCapability(ICarDisplayServer.CAP_TRUSTED);
			flags &= ~VirtualDisplayFlags.FLAG_TRUSTED;
			try {
				virtualDisplay = dm.createVirtualDisplay(DISPLAY_NAME, w, h, dpi, s, flags);
			} catch (Throwable t) {
				virtualDisplay = null;
			}
		} catch (Throwable t) {
			virtualDisplay = null;
		}

		if (virtualDisplay == null || virtualDisplay.getDisplay() == null) {
			displayId = -1;
			return -1;
		}

		displayId = virtualDisplay.getDisplay().getDisplayId();
		Hidden.setDisplayImePolicy(displayId, 0); // 0 = LOCAL
		bindClient(client);

		return displayId;
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
				virtualDisplay.resize(w, h, dpi);
			} catch (Throwable ignored) {
			}
		}
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
	public void setPanelPower(boolean on) {
		if (!CallerPolicy.serverAccepts(Binder.getCallingUid(), appUid)) {
			return;
		}
		setPanelPowerInternal(on);
	}

	public synchronized void setPanelPowerInternal(boolean on) {
		if (!on) {
			Hidden.setPhysicalDisplayPowerMode(0); // 0 = OFF
			if (wakeLock != null && !wakeLock.isHeld()) {
				try {
					wakeLock.acquire();
				} catch (Throwable ignored) {
				}
			}
		} else {
			Hidden.setPhysicalDisplayPowerMode(2); // 2 = NORMAL
			if (wakeLock != null && wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@Override
	public void release() {
		int callingUid = Binder.getCallingUid();
		if (callingUid != android.os.Process.myUid() && !CallerPolicy.serverAccepts(callingUid, appUid)) {
			return;
		}
		releaseInternal();
	}

	public synchronized void releaseInternal() {
		setPanelPowerInternal(true);
		if (wakeLock != null && wakeLock.isHeld()) {
			try {
				wakeLock.release();
			} catch (Throwable ignored) {
			}
		}
		if (clientBinder != null) {
			try {
				clientBinder.unlinkToDeath(deathRecipient, 0);
			} catch (Throwable ignored) {
			}
			clientBinder = null;
		}
		if (virtualDisplay != null) {
			try {
				virtualDisplay.release();
			} catch (Throwable ignored) {
			}
			virtualDisplay = null;
		}
		displayId = -1;
		lastDetachMs = SystemClock.elapsedRealtime();
		scheduleIdleExit();
	}

	/** One timer from the last detach (or process start): exit only if still without a client. */
	private void scheduleIdleExit() {
		Looper l = Looper.getMainLooper();
		if (l == null) return;
		Handler h = new Handler(l);
		h.removeCallbacks(idleExit);
		h.postDelayed(idleExit, IdleExitPolicy.IDLE_LIMIT_MS);
	}

	private synchronized void onIdleTimer() {
		long now = SystemClock.elapsedRealtime();
		if (IdleExitPolicy.shouldExit(now, lastDetachMs, virtualDisplay != null)) {
			setPanelPowerInternal(true);
			if (wakeLock != null && wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (Throwable ignored) {
				}
			}
			System.exit(0);
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

	public boolean isWakeLockHeld() {
		return wakeLock != null && wakeLock.isHeld();
	}

	public int getDisplayId() {
		return displayId;
	}
}
