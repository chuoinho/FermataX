package me.app.fermatax.auto.carapps;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.log.Log;
import me.app.fermatax.auto.CarService;
import me.app.fermatax.auto.Su;

/**
 * Client coordinator for CarDisplayServer lifecycle, connection retry, and command proxying.
 */
public class CarDisplayClient {

	public enum State {
		IDLE,
		STARTING,
		READY,
		RETRYING,
		FAILED
	}

	private static final CarDisplayClient INSTANCE = new CarDisplayClient();

	private volatile State state = State.IDLE;
	private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();
	private DisplayManager.DisplayListener displayListener;
	private BroadcastReceiver unlockReceiver;
	private PowerManager.WakeLock keepAwakeLock;
	private volatile boolean phoneAsleep;
	private volatile boolean phoneLocked;
	private volatile boolean ownPowerGroup;
	private boolean sessionUnlocked;
	private Surface carSurface;
	private int carWidth;
	private int carHeight;
	private int carDpi;

	private volatile ICarDisplayServer server;
	private volatile IBinder serverBinder;
	private final IBinder clientToken = new Binder();

	private final TouchQueue touchQueue = new TouchQueue(64, 250_000_000L);
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
	private volatile Executor sendExecutor;

	private Context lastContext;
	private int retryAttempt = 0;
	private Consumer<String> testCommandRunner;
	private BiConsumer<String, Map<String, ?>> diagnosticsConsumerForTest;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
		@Override
		public void binderDied() {
			Log.w("CarDisplayServer binder died");
			onServerDied();
		}
	};

	CarDisplayClient() {
	}

	public static CarDisplayClient get() {
		return INSTANCE;
	}

	public State state() {
		return state;
	}

	public void addStateListener(Consumer<State> listener) {
		if (listener != null) {
			stateListeners.add(listener);
		}
	}

	public void removeStateListener(Consumer<State> listener) {
		if (listener != null) {
			stateListeners.remove(listener);
		}
	}

	private void setState(State newState) {
		if (this.state == newState) {
			return;
		}
		this.state = newState;
		notifyStateListeners();
	}

	private void notifyStateListeners() {
		State newState = this.state;
		for (Consumer<State> listener : stateListeners) {
			try {
				listener.accept(newState);
			} catch (Throwable t) {
				Log.e(t, "Error in CarDisplayClient state listener");
			}
		}
	}

	private void recordDiagnostic(String event, Map<String, ?> attributes) {
		if (diagnosticsConsumerForTest != null) {
			diagnosticsConsumerForTest.accept(event, attributes);
		}
		try {
			AndroidDiagnosticsRuntime.get().recordDetailed("carapps", event, attributes);
		} catch (Throwable ignored) {
		}
	}

	public void setDiagnosticsConsumerForTest(BiConsumer<String, Map<String, ?>> consumer) {
		this.diagnosticsConsumerForTest = consumer;
	}

	public int getExpectedVersion() {
		return BuildConfig.VERSION_CODE;
	}

	private final Runnable startTimeoutRunnable = () -> {
		synchronized (CarDisplayClient.this) {
			if (state == State.STARTING) {
				Log.w("CarDisplayServer startup timed out after 8000ms");
				recordDiagnostic("server_failed", Map.of("reason", "startup_timeout"));
				setState(State.RETRYING);
				scheduleRetry();
			}
		}
	};

	public synchronized void onServerBinderReceived(IBinder binder) {
		mainHandler.removeCallbacks(startTimeoutRunnable);
		if (binder == null) {
			recordDiagnostic("server_failed", Map.of("reason", "null_binder"));
			return;
		}
		ICarDisplayServer s = ICarDisplayServer.Stub.asInterface(binder);
		if (s == null) {
			recordDiagnostic("server_failed", Map.of("reason", "null_interface"));
			return;
		}

		int version = -1;
		try {
			version = s.serverVersion();
		} catch (RemoteException e) {
			Log.e(e, "Failed to query server version");
		}

		if (version != getExpectedVersion()) {
			Log.w("Server version mismatch: expected " + getExpectedVersion() + ", got " + version + "; releasing");
			recordDiagnostic("server_failed", Map.of("reason", "version_mismatch"));
			try {
				s.release();
			} catch (Throwable ignored) {
			}
			if (lastContext != null && state == State.STARTING) {
				startServer(lastContext);
			}
			return;
		}

		unlinkServerBinder();

		try {
			binder.linkToDeath(deathRecipient, 0);
		} catch (RemoteException e) {
			Log.e(e, "Failed to link to server binder death");
			recordDiagnostic("server_failed", Map.of("reason", "link_to_death_failed"));
			onServerDied();
			return;
		}

		int caps = 0;
		try {
			caps = s.capabilities();
		} catch (RemoteException e) {
			Log.e(e, "Failed to query server capabilities");
		}

		this.server = s;
		this.serverBinder = binder;
		this.retryAttempt = 0;
		setState(State.READY);
		Log.i("CarDisplayServer connected successfully (version " + version + ")");
		recordDiagnostic("server_connected", Map.of("version", version, "capabilities", caps));
		saveCapabilities(caps);
	}

	/** Last known capabilities for when no server runs: readiness reads them on the phone. */
	private void saveCapabilities(int caps) {
		try {
			Context ctx = (lastContext != null) ? lastContext : FermataApplication.get();
			if (ctx != null) {
				ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE)
						.edit().putInt("CAR_APPS_CAPABILITIES", caps)
						.putInt("CAR_APPS_CAPABILITIES_VERSION", BuildConfig.VERSION_CODE).apply();
			}
		} catch (Throwable ignored) {
		}
	}

	/** An older server knew fewer capabilities: what it saved would read as missing ones after an app update. */
	static int savedCapabilities(Context ctx, int unknown) {
		SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		return (prefs.getInt("CAR_APPS_CAPABILITIES_VERSION", -1) == BuildConfig.VERSION_CODE)
				? prefs.getInt("CAR_APPS_CAPABILITIES", unknown) : unknown;
	}

	private void recordChecks(int caps) {
		Map<String, String> statuses = new LinkedHashMap<>();
		for (CarAppsChecks.Check c : CarAppsChecks.from(CarAppsFeature.state(), caps, Build.VERSION.SDK_INT)) {
			statuses.put(c.id, c.status.name());
		}
		recordDiagnostic("carapps_checks", statuses);
	}

	private synchronized void onServerDied() {
		unlinkServerBinder();
		stopKeepingPhoneAwake();
		flushTouchQueueDiagnosticsAndClear();
		this.server = null;
		this.serverBinder = null;
		setState(State.RETRYING);
		scheduleRetry();
	}

	private void unlinkServerBinder() {
		if (serverBinder != null) {
			try {
				serverBinder.unlinkToDeath(deathRecipient, 0);
			} catch (Throwable ignored) {
			}
		}
	}

	private void scheduleRetry() {
		long delay = RetryPolicy.delayMs(retryAttempt);
		if (delay < 0) {
			Log.e("CarDisplayServer reconnection failed after max attempts");
			recordDiagnostic("server_failed", Map.of("reason", "max_retries_exceeded"));
			setState(State.FAILED);
			return;
		}

		recordDiagnostic("server_retry", Map.of("attempt", retryAttempt, "delayMs", delay));
		Log.i("Scheduling server reconnect attempt " + retryAttempt + " in " + delay + " ms");
		mainHandler.postDelayed(() -> {
			if (state == State.RETRYING && lastContext != null) {
				retryAttempt++;
				startServer(lastContext);
			}
		}, delay);
	}

	public synchronized void ensureRunning(Context ctx) {
		if (ctx != null) {
			this.lastContext = ctx.getApplicationContext();
		}
		if (state == State.READY && serverBinder != null && serverBinder.pingBinder()) {
			return;
		}
		if (state == State.STARTING) {
			return;
		}

		this.retryAttempt = 0;
		setState(State.STARTING);
		startServer(ctx != null ? ctx : lastContext);
	}

	private void startServer(@Nullable Context ctx) {
		if (ctx == null) {
			Log.w("Cannot start CarDisplayServer without context");
			recordDiagnostic("server_failed", Map.of("reason", "null_context"));
			return;
		}

		String apkPath = ctx.getPackageCodePath();
		int appUid = android.os.Process.myUid();
		int versionCode = getExpectedVersion();
		String pkg = ctx.getPackageName();
		String cmd = ServerCommand.build(apkPath, appUid, versionCode, pkg);

		recordDiagnostic("server_starting", Map.of("apkPath", apkPath != null ? apkPath : ""));
		mainHandler.removeCallbacks(startTimeoutRunnable);
		mainHandler.postDelayed(startTimeoutRunnable, 8000L);

		if (testCommandRunner != null) {
			testCommandRunner.accept(cmd);
			return;
		}

		Su.get().onSuccess(su -> {
			recordDiagnostic("root_grant", Map.of("result", "granted"));
			String rootCmd = ServerCommand.buildForRoot(apkPath, appUid, versionCode, pkg);
			Log.i("Starting CarDisplayServer via su: " + rootCmd);
			su.exec(rootCmd);
		}).onFailure(err -> {
			recordDiagnostic("root_grant", Map.of("result", "failed"));
			recordDiagnostic("server_failed", Map.of("reason", "root_failed"));
			Log.e(err, "Failed to obtain root shell to launch CarDisplayServer");
				// root was revoked since the grant was recorded: show the grant UI, not a generic failure
				RootProbe.recordGranted(ctx, false);
			setState(State.FAILED);
		});
	}

	public void onCarSessionStarted() {
		Context ctx = (lastContext != null) ? lastContext : FermataApplication.get();
		if (ctx != null) {
			onCarSessionStarted(ctx);
		}
	}

	public void onCarSessionStarted(Context ctx) {
		if (ctx == null) return;
		this.lastContext = ctx.getApplicationContext();

		SharedPreferences prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		boolean autoStartPref = prefs.getBoolean(AutoStartPolicy.PREF_AUTO_START, true);
		boolean carActive = CarService.isActive();
		RootState rootState = CarAppsFeature.state();

		if (AutoStartPolicy.shouldStart(carActive, rootState, autoStartPref)) {
			ensureRunning(ctx);
		}
	}

	// AIDL proxy methods

	public int attach(Surface s, int w, int h, int dpi) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return -1;
		try {
			boolean locked = (lastContext != null) && isKeyguardHoldingPhone() && !sessionUnlocked;
			int id = srv.attach(locked ? null : s, w, h, dpi, clientToken);
			if (id >= 0) {
				// Only now are they true: creating the display can lower CAP_TRUSTED and CAP_FOCUS
				int caps = srv.capabilities();
				ownPowerGroup = (caps & ICarDisplayServer.CAP_OWN_POWER_GROUP) != 0;
				saveCapabilities(caps);
				recordChecks(caps);
				rememberCarSurface(s, w, h, dpi);
				startKeepingPhoneAwake();
			}
			return id;
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.attach failed");
			return -1;
		}
	}

	public void setSurface(@Nullable Surface s, int w, int h, int dpi) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		rememberCarSurface(s, w, h, dpi);
		if (s == null) stopKeepingPhoneAwake();
		else startKeepingPhoneAwake();
		if ((s != null) && phoneLocked) return; // given to the display on unlock
		try {
			srv.setSurface(s, w, h, dpi);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.setSurface failed");
		}
	}

	public void inject(MotionEvent e) {
		if (e == null || this.server == null) return;
		long now = System.nanoTime();
		touchQueue.offer(e, now);
		scheduleDrain();
	}

	private void scheduleDrain() {
		if (drainScheduled.compareAndSet(false, true)) {
			try {
				getSendExecutor().execute(this::drainTouchQueue);
			} catch (Throwable t) {
				drainScheduled.set(false);
				Log.e(t, "Failed to schedule touch queue drain");
			}
		}
	}

	private void drainTouchQueue() {
		try {
			while (true) {
				MotionEvent ev;
				long now = System.nanoTime();
				ev = touchQueue.take(now);
				if (ev == null) {
					break;
				}
				ICarDisplayServer srv = this.server;
				if (srv != null) {
					try {
						srv.inject(ev);
					} catch (RemoteException ex) {
						Log.e(ex, "CarDisplayServer.inject failed");
						flushTouchQueueDiagnosticsAndClear();
						break;
					} catch (Throwable t) {
						Log.e(t, "Unexpected error injecting touch event");
					} finally {
						ev.recycle();
					}
				} else {
					ev.recycle();
				}
			}
		} finally {
			drainScheduled.set(false);
			if (!touchQueue.isEmpty()) {
				scheduleDrain();
			}
		}
	}

	private Executor getSendExecutor() {
		Executor ex = sendExecutor;
		if (ex == null) {
			synchronized (this) {
				ex = sendExecutor;
				if (ex == null) {
					ex = Executors.newSingleThreadExecutor(r -> {
						Thread t = new Thread(r, "fermatax-touch-send");
						t.setDaemon(true);
						return t;
					});
					sendExecutor = ex;
				}
			}
		}
		return ex;
	}

	private void flushTouchQueueDiagnosticsAndClear() {
		TouchQueue.Counters c = touchQueue.counters();
		if (c.merged > 0 || c.dropped > 0 || c.resetOverload > 0) {
			recordDiagnostic("touch_queue", Map.of(
					"merged", c.merged,
					"dropped", c.dropped,
					"resetOverload", c.resetOverload
			));
		}
		touchQueue.clear();
	}

	public void setSendExecutorForTest(Executor executor) {
		this.sendExecutor = executor;
	}

	public void injectKey(int keyCode) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.injectKey(keyCode);
		} catch (RemoteException ex) {
			Log.e(ex, "CarDisplayServer.injectKey failed");
		}
	}

	public int launch(ComponentName c, int intentFlags) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return -1;
		try {
			return srv.launch(c, intentFlags);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.launch failed");
			return -1;
		}
	}

	public void launchOnPhone(ComponentName c, int intentFlags) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.launchOnPhone(c, intentFlags);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.launchOnPhone failed");
		}
	}

	public String closeTopApp(boolean force) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return "";
		try {
			return srv.closeTopApp(force);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.closeTopApp failed");
			return "";
		}
	}

	public int capabilities() {
		ICarDisplayServer srv = this.server;
		if (srv == null) return 0;
		try {
			return srv.capabilities();
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.capabilities failed");
			return 0;
		}
	}

	public boolean hasCapability(int cap) {
		return (capabilities() & cap) == cap;
	}

	/**
	 * Apps on a display sharing the phone's power group stop if the phone sleeps. The dim lock makes the screen timeout
	 * dim the phone instead, in any app, and the power key still really sleeps it: that is reported, never undone.
	 * A locked phone is not kept awake, so its lock screen times out as usual.
	 * With its own power group the display needs none of this, and only the lock state is followed.
	 */
	private synchronized void startKeepingPhoneAwake() {
		if (displayListener != null || lastContext == null) return;
		DisplayManager dm = (DisplayManager) lastContext.getSystemService(Context.DISPLAY_SERVICE);
		if (dm != null) {
			displayListener = new DisplayManager.DisplayListener() {
				@Override
				public void onDisplayAdded(int displayId) {
				}

				@Override
				public void onDisplayRemoved(int displayId) {
				}

				@Override
				public void onDisplayChanged(int displayId) {
					if (displayId != Display.DEFAULT_DISPLAY) return;
					Display d = dm.getDisplay(displayId);
					setPhoneState((d != null) ? (d.getState() != Display.STATE_ON) : phoneAsleep, isKeyguardHoldingPhone());
				}
			};
			dm.registerDisplayListener(displayListener, mainHandler);
		}
		unlockReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				setPhoneState(phoneAsleep, false);
			}
		};
		// Exported because SystemUI, not the system uid, sends it; a protected broadcast, so no app can fake it.
		ContextCompat.registerReceiver(lastContext, unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT),
				ContextCompat.RECEIVER_EXPORTED);
		setPhoneState(phoneAsleep, isKeyguardHoldingPhone());
	}

	private synchronized void stopKeepingPhoneAwake() {
		releaseKeepAwakeLock();
		if (displayListener != null && lastContext != null) {
			DisplayManager dm = (DisplayManager) lastContext.getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null) dm.unregisterDisplayListener(displayListener);
			displayListener = null;
		}
		if (unlockReceiver != null && lastContext != null) {
			lastContext.unregisterReceiver(unlockReceiver);
			unlockReceiver = null;
		}
		carSurface = null;
		setPhoneState(false, false);
	}

	private boolean isKeyguardHoldingPhone() {
		KeyguardManager km = (KeyguardManager) lastContext.getSystemService(Context.KEYGUARD_SERVICE);
		return (km != null) && km.isKeyguardSecure() && km.isKeyguardLocked();
	}

	/** Both change first and listeners hear once, so they never see a sleeping phone as awake or a locked one as open. */
	@SuppressWarnings("deprecation")
	private synchronized void setPhoneState(boolean asleep, boolean locked) {
		if (ownPowerGroup) {
			// The phone sleeping no longer touches the car. Unlocked once, the car stays open until the session ends.
			if (!locked && (unlockReceiver != null)) sessionUnlocked = true;
			asleep = false;
			locked = locked && !sessionUnlocked;
		}
		boolean changed = (phoneAsleep != asleep) || (phoneLocked != locked);
		phoneAsleep = asleep;
		setPhoneLocked(locked);
		if (locked || ownPowerGroup) {
			releaseKeepAwakeLock();
		} else if ((keepAwakeLock == null) && (unlockReceiver != null)) {
			PowerManager pm = (PowerManager) lastContext.getSystemService(Context.POWER_SERVICE);
			if (pm != null) {
				keepAwakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "fermatax:carapps");
				keepAwakeLock.acquire();
			}
		}
		if (changed) notifyStateListeners();
	}

	private synchronized void releaseKeepAwakeLock() {
		if (keepAwakeLock != null) {
			keepAwakeLock.release();
			keepAwakeLock = null;
		}
	}

	/**
	 * No surface turns the virtual display off while the phone is locked: its apps stop, nothing reaches the car,
	 * and their keep-screen-on windows no longer hold the lock screen on.
	 */
	private synchronized void setPhoneLocked(boolean locked) {
		if (phoneLocked == locked) return;
		phoneLocked = locked;
		ICarDisplayServer srv = this.server;
		if ((srv != null) && (locked || (carSurface != null))) {
			try {
				if (locked) srv.setSurface(null, 0, 0, 0);
				else srv.setSurface(carSurface, carWidth, carHeight, carDpi);
			} catch (RemoteException e) {
				Log.e(e, "CarDisplayServer.setSurface failed");
			}
		}
	}

	private synchronized void rememberCarSurface(@Nullable Surface s, int w, int h, int dpi) {
		carSurface = s;
		carWidth = w;
		carHeight = h;
		carDpi = dpi;
	}

	/** True when the phone sleeping has paused the apps on the car; state listeners are told when it changes. */
	public boolean isPhoneAsleep() {
		return phoneAsleep;
	}

	/** True when the car must not show or control the apps until the phone is unlocked; listeners are told. */
	public boolean isPhoneLocked() {
		return phoneLocked;
	}

	/** True when the phone dims, sleeps and locks on its own, so the dim screen has nothing to do. */
	public boolean hasOwnPowerGroup() {
		return ownPowerGroup;
	}

	public void wakeScreen() {
		if (lastContext == null) return;
		try {
			PowerManager pm = (PowerManager) lastContext.getSystemService(Context.POWER_SERVICE);
			if (pm != null) {
				@SuppressWarnings("deprecation")
				PowerManager.WakeLock wl = pm.newWakeLock(
						PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
						"fermatax:panelwake");
				wl.acquire(1000);
			}
		} catch (Throwable ignored) {
		}
	}

	public synchronized void onCarSessionStopped() {
		stopKeepingPhoneAwake();
	}

	public synchronized void release() {
		mainHandler.removeCallbacks(startTimeoutRunnable);
		unlinkServerBinder();
		stopKeepingPhoneAwake();
		flushTouchQueueDiagnosticsAndClear();
		ownPowerGroup = false;
		sessionUnlocked = false;
		ICarDisplayServer srv = this.server;
		this.server = null;
		this.serverBinder = null;
		if (srv != null) {
			try {
				srv.release();
			} catch (RemoteException e) {
				Log.e(e, "CarDisplayServer.release failed");
			}
		}
		setState(State.IDLE);
	}

	// Test hooks

	public void setCommandRunnerForTest(Consumer<String> runner) {
		this.testCommandRunner = runner;
	}

	void retryConnectionForTesting() {
		long delay = RetryPolicy.delayMs(retryAttempt);
		if (delay < 0) {
			recordDiagnostic("server_failed", Map.of("reason", "max_retries_exceeded"));
			setState(State.FAILED);
		} else {
			recordDiagnostic("server_retry", Map.of("attempt", retryAttempt, "delayMs", delay));
			retryAttempt++;
			if (RetryPolicy.delayMs(retryAttempt) < 0) {
				recordDiagnostic("server_failed", Map.of("reason", "max_retries_exceeded"));
				setState(State.FAILED);
			} else {
				startServer(lastContext);
			}
		}
	}

	int getRetryAttemptCountForTesting() {
		return retryAttempt;
	}

	public void resetForTesting() {
		stopKeepingPhoneAwake();
		this.ownPowerGroup = false;
		this.sessionUnlocked = false;
		unlinkServerBinder();
		this.server = null;
		this.serverBinder = null;
		this.state = State.IDLE;
		this.retryAttempt = 0;
		this.testCommandRunner = null;
		this.diagnosticsConsumerForTest = null;
		this.stateListeners.clear();
		this.lastContext = null;
		this.sendExecutor = Runnable::run;
		this.touchQueue.clear();
		this.drainScheduled.set(false);
	}
}
