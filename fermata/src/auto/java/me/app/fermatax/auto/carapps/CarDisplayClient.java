package me.app.fermatax.auto.carapps;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.fermata.ui.carapps.CarAppsFeature;
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

	public static final String PREF_PANEL_OFF = PanelPowerPolicy.PREF_PANEL_OFF;

	private static final CarDisplayClient INSTANCE = new CarDisplayClient();

	private volatile State state = State.IDLE;
	private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();
	private final List<Consumer<Boolean>> panelListeners = new CopyOnWriteArrayList<>();

	private volatile boolean panelOff = false;
	private BroadcastReceiver screenOffReceiver;

	private volatile ICarDisplayServer server;
	private volatile IBinder serverBinder;
	private final IBinder clientToken = new Binder();

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

	public boolean isPanelOff() {
		return panelOff;
	}

	public void addPanelListener(Consumer<Boolean> listener) {
		if (listener != null) {
			panelListeners.add(listener);
		}
	}

	public void removePanelListener(Consumer<Boolean> listener) {
		if (listener != null) {
			panelListeners.remove(listener);
		}
	}

	private void notifyPanelListeners(boolean off) {
		for (Consumer<Boolean> listener : panelListeners) {
			try {
				listener.accept(off);
			} catch (Throwable t) {
				Log.e(t, "Error in CarDisplayClient panel listener");
			}
		}
	}

	private void setState(State newState) {
		if (this.state == newState) {
			return;
		}
		this.state = newState;
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
		try {
			Context ctx = (lastContext != null) ? lastContext : FermataApplication.get();
			if (ctx != null) {
				ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE)
						.edit().putInt("CAR_APPS_CAPABILITIES", caps).apply();
			}
		} catch (Throwable ignored) {
		}
	}

	private synchronized void onServerDied() {
		unlinkServerBinder();
		if (panelOff) {
			PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.SERVER_DIED);
			if (action == PanelPowerPolicy.PanelAction.PANEL_ON_AND_WAKE) {
				restorePanelAndWake();
			}
		} else {
			unregisterScreenOffReceiver();
		}
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
			Log.i("Starting CarDisplayServer via su: " + cmd);
			su.exec(cmd);
		}).onFailure(err -> {
			recordDiagnostic("root_grant", Map.of("result", "failed"));
			recordDiagnostic("server_failed", Map.of("reason", "root_failed"));
			Log.e(err, "Failed to obtain root shell to launch CarDisplayServer");
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
			return srv.attach(s, w, h, dpi, clientToken);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.attach failed");
			return -1;
		}
	}

	public void setSurface(@Nullable Surface s, int w, int h, int dpi) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.setSurface(s, w, h, dpi);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.setSurface failed");
		}
	}

	public void inject(MotionEvent e) {
		ICarDisplayServer srv = this.server;
		if (srv == null || e == null) return;
		try {
			srv.inject(e);
		} catch (RemoteException ex) {
			Log.e(ex, "CarDisplayServer.inject failed");
		}
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

	public void setPanelPower(boolean on) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.setPanelPower(on);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.setPanelPower failed");
		}
	}

	public synchronized void setPanelOff(boolean off) {
		if (off) {
			if (!hasCapability(ICarDisplayServer.CAP_PANEL_POWER)) {
				PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.CAPABILITY_MISSING);
				return;
			}
			PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.USER_OFF);
			if (action == PanelPowerPolicy.PanelAction.PANEL_OFF_AND_HOLD) {
				panelOff = true;
				setPanelPower(false);
				registerScreenOffReceiver();
				notifyPanelListeners(true);
			}
		} else {
			PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.USER_ON);
			if (action == PanelPowerPolicy.PanelAction.PANEL_ON_AND_WAKE) {
				restorePanelAndWake();
			}
		}
	}

	private synchronized void registerScreenOffReceiver() {
		if (screenOffReceiver != null) return;
		if (lastContext == null) return;
		screenOffReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
					Log.i("CarDisplayClient received ACTION_SCREEN_OFF, restoring phone panel");
					onScreenOffReceived();
				}
			}
		};
		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		lastContext.registerReceiver(screenOffReceiver, filter);
	}

	private synchronized void unregisterScreenOffReceiver() {
		if (screenOffReceiver != null && lastContext != null) {
			try {
				lastContext.unregisterReceiver(screenOffReceiver);
			} catch (Throwable ignored) {
			}
			screenOffReceiver = null;
		}
	}

	private synchronized void onScreenOffReceived() {
		PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.SCREEN_OFF_BROADCAST);
		if (action == PanelPowerPolicy.PanelAction.PANEL_ON_AND_WAKE) {
			restorePanelAndWake();
		}
	}

	private synchronized void restorePanelAndWake() {
		unregisterScreenOffReceiver();
		panelOff = false;
		setPanelPower(true);
		wakeScreen();
		notifyPanelListeners(false);
	}

	private void wakeScreen() {
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
		if (panelOff) {
			PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.CAR_DISCONNECTED);
			if (action == PanelPowerPolicy.PanelAction.PANEL_ON_AND_WAKE) {
				restorePanelAndWake();
			}
		} else {
			unregisterScreenOffReceiver();
		}
	}

	public synchronized void release() {
		mainHandler.removeCallbacks(startTimeoutRunnable);
		unlinkServerBinder();
		if (panelOff) {
			PanelPowerPolicy.PanelAction action = PanelPowerPolicy.next(panelOff, PanelPowerPolicy.PanelEvent.RELEASED);
			if (action == PanelPowerPolicy.PanelAction.PANEL_ON_AND_WAKE) {
				restorePanelAndWake();
			}
		} else {
			unregisterScreenOffReceiver();
		}
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
		unregisterScreenOffReceiver();
		panelOff = false;
		unlinkServerBinder();
		this.server = null;
		this.serverBinder = null;
		this.state = State.IDLE;
		this.retryAttempt = 0;
		this.testCommandRunner = null;
		this.diagnosticsConsumerForTest = null;
		this.stateListeners.clear();
		this.panelListeners.clear();
		this.lastContext = null;
	}
}
