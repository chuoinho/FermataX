package me.app.fermatax.auto.carapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import me.aap.fermata.BuildConfig;
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

	private static final CarDisplayClient INSTANCE = new CarDisplayClient();

	private volatile State state = State.IDLE;
	private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();

	private volatile ICarDisplayServer server;
	private volatile IBinder serverBinder;
	private final IBinder clientToken = new Binder();

	private Context lastContext;
	private int retryAttempt = 0;
	private Consumer<String> testCommandRunner;
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
		for (Consumer<State> listener : stateListeners) {
			try {
				listener.accept(newState);
			} catch (Throwable t) {
				Log.e(t, "Error in CarDisplayClient state listener");
			}
		}
	}

	public int getExpectedVersion() {
		return BuildConfig.VERSION_CODE;
	}

	public synchronized void onServerBinderReceived(IBinder binder) {
		if (binder == null) {
			return;
		}
		ICarDisplayServer s = ICarDisplayServer.Stub.asInterface(binder);
		if (s == null) {
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
			onServerDied();
			return;
		}

		this.server = s;
		this.serverBinder = binder;
		this.retryAttempt = 0;
		setState(State.READY);
		Log.i("CarDisplayServer connected successfully (version " + version + ")");
	}

	private synchronized void onServerDied() {
		unlinkServerBinder();
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
			setState(State.FAILED);
			return;
		}

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
			return;
		}

		String apkPath = ctx.getPackageCodePath();
		int appUid = android.os.Process.myUid();
		int versionCode = getExpectedVersion();
		String pkg = ctx.getPackageName();
		String cmd = ServerCommand.build(apkPath, appUid, versionCode, pkg);

		if (testCommandRunner != null) {
			testCommandRunner.accept(cmd);
			return;
		}

		Su.get().onSuccess(su -> {
			Log.i("Starting CarDisplayServer via su: " + cmd);
			su.exec(cmd);
		}).onFailure(err -> {
			Log.e(err, "Failed to obtain root shell to launch CarDisplayServer");
			setState(State.FAILED);
		});
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

	public void setPanelPower(boolean on) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.setPanelPower(on);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.setPanelPower failed");
		}
	}

	public synchronized void release() {
		unlinkServerBinder();
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

	void setCommandRunnerForTest(Consumer<String> runner) {
		this.testCommandRunner = runner;
	}

	void retryConnectionForTesting() {
		long delay = RetryPolicy.delayMs(retryAttempt);
		if (delay < 0) {
			setState(State.FAILED);
		} else {
			retryAttempt++;
			if (RetryPolicy.delayMs(retryAttempt) < 0) {
				setState(State.FAILED);
			} else {
				startServer(lastContext);
			}
		}
	}

	int getRetryAttemptCountForTesting() {
		return retryAttempt;
	}

	void resetForTesting() {
		unlinkServerBinder();
		this.server = null;
		this.serverBinder = null;
		this.state = State.IDLE;
		this.retryAttempt = 0;
		this.testCommandRunner = null;
		this.stateListeners.clear();
		this.lastContext = null;
	}
}
