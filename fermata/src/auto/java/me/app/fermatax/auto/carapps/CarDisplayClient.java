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
import android.os.SystemClock;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.fermata.ui.carapps.RootState;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.app.fermatax.auto.CarService;
import me.app.fermatax.auto.Su;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;
import me.app.fermatax.auto.carapps.server.AppPing;

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
	private volatile boolean panelOff;
	private volatile boolean panelOffRequested;
	private volatile boolean restorePanelOnReconnect;
	private volatile long nextPhoneSleepMs;
	private BroadcastReceiver screenOffReceiver;
	private DisplayManager.DisplayListener displayListener;
	private static final long LOCK_REREAD_DELAY_MS = 1000;
	private BroadcastReceiver unlockReceiver;
	private BroadcastReceiver screenOnReceiver;
	private final Runnable delayedLockCheck = this::rereadLockState;
	private PowerManager.WakeLock keepAwakeLock;
	private static final int REWAKE_MAX = 3;
	private static final long REWAKE_WINDOW_MS = 60_000;
	private final java.util.ArrayDeque<Long> rewakeTimes = new java.util.ArrayDeque<>();
	/** One power key press reports the screen off more than once; the later reports must not darken the panel it lit. */
	private static final long RESTORE_SETTLE_MS = 3000;
	private long panelRestoredMs = -RESTORE_SETTLE_MS;
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
	/** Counts connected servers: a display belongs to one of them, and a new one starts without any. */
	private volatile int serverEpoch;
	private volatile int serverUid = -1;
	/** The server of the last session, its display released: an ADB-started one still runs and is taken back. */
	private IBinder idleBinder;
	/** Covers the poll of the server (AppPing.POLL_MS) and its handoff. */
	private static final long SERVER_PING_WAIT_MS = 5000;
	private volatile Predicate<Context> serverPing = CarDisplayClient::pingServer;
	private volatile Predicate<Context> serverStop = CarDisplayClient::stopServer;
	private volatile boolean askingRunningServer;
	/** A ping was sent and not yet answered; a release in between does not clear it (the answer may still come). */
	private boolean pingUnanswered;
	private volatile boolean failedForWifi;
	private volatile String lastFailure = "none";
	/** When the current start began (uptime), for the time a server took to answer. */
	private volatile long startedAtMs;
	private final Runnable serverPingTimeout = () -> {
		synchronized (CarDisplayClient.this) {
			askingRunningServer = false;
			Context ctx = CarDisplayClient.this.lastContext;
			if ((state != State.STARTING) || (ctx == null)) return;
			recordDiagnostic("server_ping", Map.of("state", "unanswered"));
			startServer(ctx);
		}
	};
	private final IBinder clientToken = new Binder();

	private final TouchQueue touchQueue = new TouchQueue(64, 250_000_000L);
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
	private volatile Executor sendExecutor;

	private Context lastContext;
	private int retryAttempt = 0;
	private Consumer<String> testCommandRunner;
	private volatile BiConsumer<Context, String> rootStarter = this::startViaRoot;
	private volatile BiFunction<Context, String, FutureSupplier<Void>> adbStarter = ServerStarter::startViaAdb;
	private final AtomicReference<Object> adbAttempt = new AtomicReference<>();
	private BiConsumer<String, Map<String, ?>> diagnosticsConsumerForTest;
	private static final long CALL_FAILURE_INTERVAL_MS = 10_000;
	private static final int SERVER_LOG_LINES = 20;
	private final Map<String, Long> lastCallFailure = new ConcurrentHashMap<>();
	private volatile Supplier<String> serverLogReader = CarDisplayClient::readServerLog;
	private volatile Executor logExecutor = r -> {
		Thread t = new Thread(r, "fermatax-server-log");
		t.setDaemon(true);
		t.start();
	};
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

	int serverEpoch() {
		return serverEpoch;
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

	public boolean isPanelOffRequested() {
		return panelOffRequested;
	}

	public void setPanelOffRequested(boolean requested) {
		this.panelOffRequested = requested;
	}

	public boolean isPanelOff() {
		return panelOff;
	}

	public void setPanelPower(boolean on) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return;
		try {
			srv.setPanelPower(on);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.setPanelPower failed");
			recordCallFailure("set_panel_power", e);
		}
	}

	public void sleepPhone() {
		ICarDisplayServer s = server;
		if (s == null) return;
		long now = SystemClock.elapsedRealtime();
		if (now < nextPhoneSleepMs) return;
		nextPhoneSleepMs = now + 2000;
		try {
			s.sleepPhone();
			recordDiagnostic("phone_sleep", Map.of("ok", true));
		} catch (RemoteException e) {
			Log.w("Failed to sleep phone", e);
			recordDiagnostic("phone_sleep", Map.of("ok", false));
		}
	}

	/** Panel mode swallows touch, so a panel that cannot turn off must not stay in it. */
	public synchronized void setPanelOff(boolean off) {
		if (off == panelOff) return;
		if (!off) {
			restorePanelAndWake("requested");
			return;
		}
		if (hasCapability(ICarDisplayServer.CAP_PANEL_POWER)) {
			registerScreenOffReceiver();
			setPanelPower(false);
			if (hasCapability(ICarDisplayServer.CAP_PANEL_POWER)) {
				panelOff = true;
				mainHandler.post(this::notifyStateListeners);
				updateKeepAwakeLock();
				recordPanel("off", "requested");
				return;
			}
			unregisterScreenOffReceiver();
		}
		panelOffRequested = false;
		recordPanel("unavailable", "no_panel_power");
	}

	private void recordPanel(String state, String reason) {
		recordDiagnostic("phone_panel", Map.of("state", state, "reason", reason));
	}

	/** The system sleeps on the first power press but the panel it thinks is on stays dark: bring both back. */
	private synchronized void registerScreenOffReceiver() {
		if (screenOffReceiver != null || lastContext == null) return;
		screenOffReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.i("CarDisplayClient received ACTION_SCREEN_OFF, restoring phone panel");
				onScreenOffReceived();
			}
		};
		lastContext.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
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
		if (panelOff) {
			restorePanelAndWake("screen_off");
		} else if (restorePanelOnReconnect) {
			restorePanelOnReconnect = false;
			unregisterScreenOffReceiver();
			wakeScreen();
			recordPanel("on", "screen_off_without_server");
		}
	}

	private synchronized void restorePanelAndWake(String reason) {
		recordPanel("on", reason);
		PhoneDimActivity.dismiss();
		panelOffRequested = false;
		unregisterScreenOffReceiver();
		panelOff = false;
		mainHandler.post(this::notifyStateListeners);
		panelRestoredMs = SystemClock.elapsedRealtime();
		setPanelPower(true);
		wakeScreen();
		updateKeepAwakeLock();
	}

	private void setState(State newState) {
		if (this.state == newState) {
			return;
		}
		if (newState != State.FAILED) failedForWifi = false;
		this.state = newState;
		// A start that ends here (a late binder, no channel any more, out of retries) has no command left to give the
		// setting back; between two retries nothing else would. A command in flight still does it itself.
		if (newState == State.READY || newState == State.FAILED || newState == State.IDLE) {
			Context ctx = lastContext;
			if (ctx != null) restoreAdbWifiIfIdle(ctx);
		}
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
		if ("server_failed".equals(event)) lastFailure = String.valueOf(attributes.get("reason"));
		PhoneAppsJournal.record(event, attributes); // always-on journal, and Detailed diagnostics while that is on
	}

	/** The reason of the last server_failed, for the Phone Apps report. */
	String lastFailure() {
		return lastFailure;
	}

	int serverUid() {
		return serverUid;
	}

	public void setDiagnosticsConsumerForTest(BiConsumer<String, Map<String, ?>> consumer) {
		this.diagnosticsConsumerForTest = consumer;
	}

	private void recordCallFailure(String call, Throwable error) {
		recordCallFailure(call, error.getClass().getSimpleName());
	}

	/** Once per call every 10 s: a dead server fails every touch. */
	private void recordCallFailure(String call, String error) {
		long now = SystemClock.elapsedRealtime();
		Long last = lastCallFailure.get(call);
		if ((last != null) && (now - last < CALL_FAILURE_INTERVAL_MS)) return;
		lastCallFailure.put(call, now);
		recordDiagnostic("server_call_failed", Map.of("call", call, "error", error));
	}

	/** The server logs only to logcat, which a tester without a computer cannot read: copy its tail. */
	private void recordServerLog(String reason) {
		Supplier<String> reader = serverLogReader;
		logExecutor.execute(() -> {
			String log;
			try {
				log = reader.get();
			} catch (Throwable t) {
				return;
			}
			if ((log != null) && !log.trim().isEmpty()) {
				recordDiagnostic("server_log", Map.of("reason", reason, "lines", lastLines(log, SERVER_LOG_LINES)));
			}
		});
	}

	static String lastLines(String text, int max) {
		String[] lines = text.trim().split("\n");
		int from = Math.max(0, lines.length - max);
		return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
	}

	/** The server runs under another uid, so reading its log needs root; asked only once root was granted. */
	@Nullable
	private static String readServerLog() {
		if (!CarAppsFeature.usable(CarAppsFeature.state())) return null;
		try {
			java.lang.Process p = new ProcessBuilder("su", "-c", "logcat -d -v time -s CarDisplayServer:*")
					.redirectErrorStream(true).start();
			StringBuilder out = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				for (String line; (line = r.readLine()) != null; ) out.append(line).append('\n');
			}
			p.waitFor(5, TimeUnit.SECONDS);
			return out.toString();
		} catch (Exception e) {
			return null;
		}
	}

	void setServerLogReaderForTest(Supplier<String> reader) {
		this.serverLogReader = reader;
	}

	public int getExpectedVersion() {
		return BuildConfig.VERSION_CODE;
	}

	private final Runnable startTimeoutRunnable = () -> {
		synchronized (CarDisplayClient.this) {
			if (state == State.STARTING) {
				Log.w("CarDisplayServer startup timed out after 8000ms");
				recordDiagnostic("server_failed", Map.of("reason", "startup_timeout"));
				recordServerLog("startup_timeout");
				setState(State.RETRYING);
				scheduleRetry();
			}
		}
	};

	public void onServerBinderReceived(IBinder binder) {
		onServerBinderReceived(binder, -1);
	}

	/** The uid shows on a test phone whether su switched user or the plain root fallback ran. */
	public synchronized void onServerBinderReceived(IBinder binder, int serverUid) {
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
		// a server answered the ping: no start, or a stale one is replaced by the one start below
		mainHandler.removeCallbacks(serverPingTimeout);
		boolean answeredPing = askingRunningServer;
		askingRunningServer = false;
		boolean lateAnswer = pingUnanswered && (state == State.IDLE);
		pingUnanswered = false;
		if (!CarAppsFeature.isEnabled()) {
			// started by a command in flight when Phone Apps was turned off: not adopted, told to exit
			recordDiagnostic("server_failed", Map.of("reason", "turned_off"));
			try {
				s.release();
			} catch (Throwable ignored) {
			}
			if (lastContext != null) recordDiagnostic("server_stop", Map.of("written", serverStop.test(lastContext)));
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
			recordDiagnostic("server_failed", Map.of("reason", "version_mismatch", "version", version));
			try {
				s.release();
			} catch (Throwable ignored) {
			}
			if (lastContext != null && state == State.STARTING) {
				startServer(lastContext);
			}
			return;
		}

		if (lateAnswer) {
			recordDiagnostic("server_adopted", Map.of("source", "late_ping_kept"));
			idleBinder = binder; // the session ended meanwhile: the next one takes it back, nothing is READY without one
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
		this.serverUid = serverUid;
		this.idleBinder = null;
		this.serverEpoch++;
		if (restorePanelOnReconnect) {
			try {
				s.setPanelPower(true);
			} catch (RemoteException e) {
				Log.e(e, "Failed to restore panel on reconnect");
			}
			restorePanelOnReconnect = false;
			unregisterScreenOffReceiver();
		}
		this.retryAttempt = 0;
		this.nextPhoneSleepMs = 0;
		setState(State.READY);
		Log.i("CarDisplayServer connected successfully (version " + version + ")");
		long elapsed = SystemClock.elapsedRealtime() - startedAtMs;
		if (answeredPing) {
			recordDiagnostic("server_ping", Map.of("state", "answered", "duration_ms", elapsed));
			recordDiagnostic("server_adopted", Map.of("source", "ping"));
		}
		recordDiagnostic("server_connected", Map.of("version", version, "capabilities", caps, "uid", serverUid,
				"sdk", Build.VERSION.SDK_INT, "duration_ms", elapsed));
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
		recordDiagnostic("server_died", Map.of("panel_off", panelOff));
		recordServerLog("server_died");
		unlinkServerBinder();
		stopKeepingPhoneAwake();
		flushTouchQueueDiagnosticsAndClear();
		this.server = null;
		this.serverBinder = null;
		if (panelOff) {
			restorePanelOnReconnect = true;
			restorePanelAndWake("server_died");
			// No server can light the panel, but the system does when it wakes: the power key or the timeout gets it there
			registerScreenOffReceiver();
		} else {
			PhoneDimActivity.dismiss();
		}
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

		recordDiagnostic("server_retry", Map.of("attempt", retryAttempt, "delay_ms", delay));
		Log.i("Scheduling server reconnect attempt " + retryAttempt + " in " + delay + " ms");
		mainHandler.postDelayed(() -> {
			synchronized (CarDisplayClient.this) {
				if (state == State.RETRYING && lastContext != null) {
					retryAttempt++;
					// the startup timeout acts only in STARTING: a retry left in RETRYING would never end
					setState(State.STARTING);
					startServer(lastContext);
				}
			}
		}, delay);
	}

	public void ensureRunning(Context ctx) {
		ensureRunning(ctx, "other");
	}

	/** The trigger (car_session, open, recovery_job, pairing, ...) only names the start in the journal. */
	public synchronized void ensureRunning(Context ctx, String trigger) {
		if (ctx != null) {
			this.lastContext = ctx.getApplicationContext();
		}
		if (!CarAppsFeature.isEnabled()) return; // turned off: no trigger starts a server
		if (state == State.READY && serverBinder != null && serverBinder.pingBinder()) {
			return;
		}
		if (state == State.STARTING) {
			return;
		}

		this.retryAttempt = 0;
		this.nextPhoneSleepMs = 0;
		recordDiagnostic("start_requested", Map.of("trigger", trigger));
		Context c = (ctx != null) ? ctx : lastContext;
		if (adoptRunningServer(c)) return;
		setState(State.STARTING);
		startServer(c);
	}

	/**
	 * An ADB-started server never exits and needs Wi-Fi to be started again, so it is taken back without a command:
	 * the binder of the last session in this process, else (the process was restarted) the server is pinged and hands
	 * it over again (AppPing). Only when none answers in time is a new one started.
	 */
	private boolean adoptRunningServer(@Nullable Context ctx) {
		if ((ctx == null) || (testCommandRunner != null) || (ServerStarter.choose(CarAppsFeature.state(),
				CarAppsFeature.isAdbPaired(), Build.VERSION.SDK_INT) != ServerStarter.Channel.ADB)) {
			return false;
		}
		IBinder idle = idleBinder;
		idleBinder = null;
		if ((idle != null) && idle.pingBinder()) {
			recordDiagnostic("server_adopted", Map.of("source", "idle_binder"));
			startedAtMs = SystemClock.elapsedRealtime();
			setState(State.STARTING);
			onServerBinderReceived(idle, serverUid); // a version mismatch there starts the replacement itself
			return true;
		}
		if (!serverPing.test(ctx)) {
			recordDiagnostic("server_ping", Map.of("state", "not_written"));
			return false;
		}
		recordDiagnostic("server_ping", Map.of("state", "sent"));
		startedAtMs = SystemClock.elapsedRealtime();
		pingUnanswered = true;
		askingRunningServer = true; // before STARTING: its listeners must not ask for Wi-Fi meanwhile
		setState(State.STARTING);
		mainHandler.removeCallbacks(serverPingTimeout);
		mainHandler.postDelayed(serverPingTimeout, SERVER_PING_WAIT_MS);
		return true;
	}

	/** Seen by an ADB-started server (AppPing), which then hands its binder over again. False when not written. */
	static boolean pingServer(Context ctx) {
		return touch(ctx, AppPing.FILE);
	}

	/**
	 * Phone Apps was turned off: an ADB-started server exits on its next poll (AppPing.STOP_FILE). A file of its own, so
	 * a stop never reads as a ping: it cannot make a server hand over again.
	 */
	static boolean stopServer(Context ctx) {
		return touch(ctx, AppPing.STOP_FILE);
	}

	/** The server watches the time only. */
	private static boolean touch(Context ctx, String name) {
		File dir = ctx.getExternalFilesDir(null);
		if (dir == null) return false;
		try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
			out.write('1'); // the write changes the time the server watches
			return true;
		} catch (IOException e) {
			Log.w("Cannot signal the car display server: ", e);
			return false;
		}
	}

	/**
	 * Phone Apps was turned off: the session is released as usual (a command in flight still gives wireless debugging
	 * back), the server is not kept to be taken back, and an ADB-started one is told to exit. A root-started one exits
	 * by itself once idle. Nothing starts one again while it is off (ensureRunning).
	 */
	public synchronized void stop(Context ctx) {
		release();
		idleBinder = null;
		recordDiagnostic("server_stop", Map.of("written", serverStop.test(ctx.getApplicationContext())));
	}

	/** True while a running server is asked for its binder: the car must wait, not ask for Wi-Fi. */
	public boolean isAskingRunningServer() {
		return askingRunningServer;
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
		ServerStarter.Channel channel = ServerStarter.choose(CarAppsFeature.state(), CarAppsFeature.isAdbPaired(),
				Build.VERSION.SDK_INT);
		if (retryAttempt == 0) startedAtMs = SystemClock.elapsedRealtime();
		recordDiagnostic("server_starting", Map.of("attempt", retryAttempt,
				"channel", channel.name().toLowerCase(java.util.Locale.ROOT)));

		if (testCommandRunner != null) {
			armStartTimeout();
			testCommandRunner.accept(ServerCommand.build(apkPath, appUid, versionCode, pkg));
			return;
		}
		if (channel == ServerStarter.Channel.ADB) {
			mainHandler.removeCallbacks(startTimeoutRunnable); // a stale timer from a root start must not cut this one short
			if (!AdbRecoveryJob.hasWifi(ctx)) {
				// no wireless debugging without Wi-Fi: do not wait for a port that cannot come; connecting Wi-Fi runs the job
				recordDiagnostic("server_failed", Map.of("reason", "no_wifi"));
				restoreAdbWifiIfIdle(ctx);
				AdbRecoveryJob.schedule(ctx);
				failedForWifi = true;
				setState(State.FAILED);
				return;
			}
			startViaAdb(ctx, ServerCommand.buildForAdb(apkPath, appUid, versionCode, pkg));
			return;
		}
		if (channel == ServerStarter.Channel.NONE) {
			// no root and no pairing: a background retry must not raise a root prompt
			recordDiagnostic("server_failed", Map.of("reason", "no_channel"));
			setState(State.FAILED);
			return;
		}

		restoreAdbWifiIfIdle(ctx); // the channel changed between two ADB retries
		armStartTimeout();
		rootStarter.accept(ctx, ServerCommand.buildForRoot(apkPath, appUid, versionCode, pkg, Build.VERSION.SDK_INT));
	}

	private void armStartTimeout() {
		mainHandler.removeCallbacks(startTimeoutRunnable);
		mainHandler.postDelayed(startTimeoutRunnable, 8000L);
	}

	private void startViaRoot(Context ctx, String rootCmd) {
		Su.get().onSuccess(su -> {
			recordDiagnostic("root_grant", Map.of("result", "granted"));
			Log.i("Starting CarDisplayServer via su: " + rootCmd);
			su.exec(rootCmd);
		}).onFailure(err -> {
			recordDiagnostic("root_grant", Map.of("result", "failed", "error", err.getClass().getSimpleName()));
			recordDiagnostic("server_failed", Map.of("reason", "root_failed"));
			Log.e(err, "Failed to obtain root shell to launch CarDisplayServer");
				// root was revoked since the grant was recorded: show the grant UI, not a generic failure
				RootProbe.recordGranted(ctx, false);
			setState(State.FAILED);
		});
	}

	/**
	 * The startup timer runs only once the command is delivered: finding the port can take longer than it, and a
	 * second attempt while this one is in flight would start a second server.
	 * The foreground server normally ends the session by itself: the peer closes the stream about a second after the
	 * start and the server keeps running. A TimeoutException (the 15 s watchdog) therefore says nothing about a server
	 * that is already up: it is ignored once the client is READY, and while still starting it takes the bounded retry.
	 */
	private void startViaAdb(Context ctx, String cmd) {
		Object attempt = new Object();
		if (!adbAttempt.compareAndSet(null, attempt)) return; // one in flight, also from before a release(), decides this start
		Log.i("Starting CarDisplayServer via local ADB");
		AdbRecoveryJob.ensureUsbDebugging(ctx); // never undone: it keeps adbd, and so the server, alive without wireless debugging
		boolean switchedOn = AdbRecoveryJob.enableAdbWifi(ctx); // undone when the command ends, whatever its result
		FutureSupplier<Void> run;
		try {
			run = adbStarter.apply(ctx, cmd);
		} catch (Throwable t) {
			run = Completed.failed(t);
		}
		run.onCompletion((r, err) -> {
			boolean current = adbAttempt.compareAndSet(attempt, null);
			synchronized (this) {
				// a revoked authorization is true whatever the client is doing now
				if ((err != null) && isPairingRequired(err)) {
					restoreAdbWifiIfIdle(ctx);
					RootProbe.recordAdbPaired(ctx, false);
					recordDiagnostic("server_failed", Map.of("reason", "adb_pairing_required"));
					if (current && (state == State.STARTING || state == State.RETRYING)) setState(State.FAILED);
					return;
				}
				if (!current || (state != State.STARTING && state != State.RETRYING)) {
					restoreAdbWifiIfIdle(ctx);
					return;
				}
				if (err == null) {
					restoreAdbWifiIfIdle(ctx); // delivered: the server runs on without the ADB session
					armStartTimeout();
					return;
				}
				Log.e(err, "Failed to start CarDisplayServer via local ADB");
				if (switchedOn && AdbRecoveryJob.refusedOnThisWifi(ctx)) {
					// a retry would switch it on again and show Android's dialog again: wait for another or a trusted Wi-Fi
					recordDiagnostic("server_failed", Map.of("reason", "adb_wifi_refused"));
					failedForWifi = true;
					setState(State.FAILED);
					return;
				}
				recordDiagnostic("server_failed", Map.of("reason", "adb_failed", "error", err.getClass().getSimpleName()));
				setState(State.RETRYING);
				scheduleRetry();
				// Between the retries of one start wireless debugging stays on, else every retry would switch it off and
				// on and wait for the announcement again; only the end of the start gives it back.
				if (state == State.FAILED) restoreAdbWifiIfIdle(ctx);
			}
		});
	}

	/** Never under a command in flight: that one owns the setting and gives it back itself. */
	private void restoreAdbWifiIfIdle(Context ctx) {
		if (adbAttempt.get() == null) AdbRecoveryJob.restoreAdbWifi(ctx);
	}

	private static boolean isPairingRequired(Throwable err) {
		for (int depth = 0; (err != null) && (depth < 8); depth++, err = err.getCause()) {
			if (err instanceof AdbPairingRequiredException) return true;
		}
		return false;
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

		boolean start = AutoStartPolicy.shouldStart(carActive, rootState, autoStartPref, Build.VERSION.SDK_INT);
		recordDiagnostic("car_session", Map.of("state", "started", "auto_start", start));
		if (start) ensureRunning(ctx, "car_session");
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
				recordDiagnostic("display_attach", Map.of("result", "ok", "epoch", serverEpoch));
				recordChecks(caps);
				rememberCarSurface(s, w, h, dpi);
				startKeepingPhoneAwake();
			}
			return id;
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.attach failed");
			recordCallFailure("attach", e);
			recordDiagnostic("display_attach", Map.of("result", "failed", "epoch", serverEpoch));

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
			recordCallFailure("set_surface", e);
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
						recordCallFailure("inject", ex);
						flushTouchQueueDiagnosticsAndClear();
						break;
					} catch (Throwable t) {
						Log.e(t, "Unexpected error injecting touch event");
						recordCallFailure("inject", t);
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
					"reset_overload", c.resetOverload
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
			recordCallFailure("inject_key", ex);
		}
	}

	public int launch(ComponentName c, int intentFlags) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return -1;
		try {
			int result = srv.launch(c, intentFlags);
			if (result != 0) recordCallFailure("launch", "exit_" + result); // the exit code of am start
			return result;
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.launch failed");
			recordCallFailure("launch", e);
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
			recordCallFailure("launch_on_phone", e);
		}
	}

	public String closeTopApp(boolean force) {
		ICarDisplayServer srv = this.server;
		if (srv == null) return "";
		try {
			return srv.closeTopApp(force);
		} catch (RemoteException e) {
			Log.e(e, "CarDisplayServer.closeTopApp failed");
			recordCallFailure("close_top_app", e);
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
			recordCallFailure("capabilities", e);
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
		panelRestoredMs = -RESTORE_SETTLE_MS; // a restore that ended the last session says nothing about this one
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
					if (d != null && d.getState() != Display.STATE_ON && panelOff) {
						onScreenOffReceived();
					} else if (d != null && d.getState() == Display.STATE_OFF) {
						onPhoneSlept();
					}
					setPhoneState((d != null) ? (d.getState() != Display.STATE_ON) : phoneAsleep, isKeyguardHoldingPhone());
				}
			};
			dm.registerDisplayListener(displayListener, mainHandler);
		}
		unlockReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mainHandler.removeCallbacks(delayedLockCheck);
				setPhoneState(phoneAsleep, false);
			}
		};
		// Exported because SystemUI, not the system uid, sends it; a protected broadcast, so no app can fake it.
		ContextCompat.registerReceiver(lastContext, unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT),
				ContextCompat.RECEIVER_EXPORTED);
		screenOnReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mainHandler.removeCallbacks(delayedLockCheck);
				rereadLockState();
				mainHandler.postDelayed(delayedLockCheck, LOCK_REREAD_DELAY_MS);
			}
		};
		ContextCompat.registerReceiver(lastContext, screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON),
				ContextCompat.RECEIVER_NOT_EXPORTED);
		setPhoneState(phoneAsleep, isKeyguardHoldingPhone());
	}

	private synchronized void rereadLockState() {
		if (unlockReceiver == null) return;
		setPhoneState(phoneAsleep, isKeyguardHoldingPhone());
	}

	private synchronized void stopKeepingPhoneAwake() {
		releaseKeepAwakeLock();
		rewakeTimes.clear();
		mainHandler.removeCallbacks(delayedLockCheck);
		if (displayListener != null && lastContext != null) {
			DisplayManager dm = (DisplayManager) lastContext.getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null) dm.unregisterDisplayListener(displayListener);
			displayListener = null;
		}
		if (unlockReceiver != null && lastContext != null) {
			lastContext.unregisterReceiver(unlockReceiver);
			unlockReceiver = null;
		}
		if (screenOnReceiver != null && lastContext != null) {
			lastContext.unregisterReceiver(screenOnReceiver);
			screenOnReceiver = null;
		}
		carSurface = null;
		setPhoneState(false, false);
	}

	public static boolean keyguardHoldsPhone(int sdk, boolean secure, boolean keyguardLocked) {
		return keyguardLocked && (secure || sdk < 33);
	}

	private boolean isKeyguardHoldingPhone() {
		KeyguardManager km = (KeyguardManager) lastContext.getSystemService(Context.KEYGUARD_SERVICE);
		// CarAppsFragment.applyPanelPref checks raw km.isKeyguardLocked(); here we decide if the car must block apps.
		return (km != null) && keyguardHoldsPhone(Build.VERSION.SDK_INT, km.isKeyguardSecure(), km.isKeyguardLocked());
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
		updateKeepAwakeLock();
		if (changed) {
			recordDiagnostic("phone_state", Map.of("asleep", asleep, "locked", locked, "own_power_group", ownPowerGroup));
			notifyStateListeners();
		}
	}

	/**
	 * A dark panel keeps the phone awake. A lit one lets the phone's own screen timeout run, and onPhoneSlept
	 * darkens it again; without panel power the phone is always kept awake.
	 */
	private synchronized void updateKeepAwakeLock() {
		boolean keep = (unlockReceiver != null) && !phoneLocked && !ownPowerGroup
				&& (panelOff || !hasCapability(ICarDisplayServer.CAP_PANEL_POWER));
		if (!keep) {
			releaseKeepAwakeLock();
		} else if (keepAwakeLock == null) {
			PowerManager pm = (PowerManager) lastContext.getSystemService(Context.POWER_SERVICE);
			if (pm != null) {
				keepAwakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "fermatax:carapps");
				keepAwakeLock.acquire();
			}
		}
	}

	/**
	 * The phone timed out or its power key was pressed with the panel lit: wake it at once, before a delayed lock,
	 * and darken the panel, so the car keeps running. A secure lock or repeated presses mean the user wants it asleep.
	 */
	private synchronized void onPhoneSlept() {
		if (unlockReceiver == null || ownPowerGroup || server == null) return;
		KeyguardManager km = (KeyguardManager) lastContext.getSystemService(Context.KEYGUARD_SERVICE);
		if ((km != null) && km.isKeyguardSecure() && km.isKeyguardLocked()) return;
		long now = SystemClock.elapsedRealtime();
		if (now - panelRestoredMs < RESTORE_SETTLE_MS) return;
		while (!rewakeTimes.isEmpty() && (now - rewakeTimes.peekFirst() >= REWAKE_WINDOW_MS)) rewakeTimes.pollFirst();
		if (rewakeTimes.size() >= REWAKE_MAX) {
			recordPanel("asleep", "turned_off_repeatedly");
			return;
		}
		PanelPowerPolicy.PanelMode mode = PanelPowerPolicy.forButton(capabilities(), Build.VERSION.SDK_INT, false, false);
		// Without panel power the dim lock holds the phone awake, and a power key press is the user's to keep
		if (mode != PanelPowerPolicy.PanelMode.PANEL_OFF) return;
		rewakeTimes.addLast(now);
		recordPanel("rewake", "panel_off");
		wakeScreen();
		setPanelOffRequested(true);
		launchOnPhone(new ComponentName(lastContext, PhoneDimActivity.class),
				Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
				recordCallFailure("set_surface", e);
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
		if (panelOff) {
			restorePanelAndWake("session_stopped");
		} else {
			PhoneDimActivity.dismiss();
		}
	}

	/**
	 * A command still in flight is not forgotten: it may yet start a server, so a new start waits for its outcome (at most
	 * the port wait plus the ADB watchdog) instead of running a second one, and it gives wireless debugging back itself.
	 */
	public synchronized void release() {
		if (lastContext != null) restoreAdbWifiIfIdle(lastContext); // a start between two retries has no completion left
		mainHandler.removeCallbacks(startTimeoutRunnable);
		mainHandler.removeCallbacks(serverPingTimeout);
		askingRunningServer = false;
		unlinkServerBinder();
		stopKeepingPhoneAwake();
		flushTouchQueueDiagnosticsAndClear();
		ownPowerGroup = false;
		sessionUnlocked = false;
		if (panelOff) {
			restorePanelAndWake("release");
		} else {
			PhoneDimActivity.dismiss();
		}
		ICarDisplayServer srv = this.server;
		if (srv != null) idleBinder = serverBinder; // unlinked: its death later is no failure of a session
		this.server = null;
		this.serverBinder = null;
		if (srv != null) {
			try {
				srv.release();
			} catch (RemoteException e) {
				Log.e(e, "CarDisplayServer.release failed");
				recordCallFailure("release", e);
			}
		}
		setState(State.IDLE);
	}

	// Test hooks

	public void setCommandRunnerForTest(Consumer<String> runner) {
		this.testCommandRunner = runner;
	}

	void setStartersForTest(BiConsumer<Context, String> root, BiFunction<Context, String, FutureSupplier<Void>> adb) {
		this.rootStarter = root;
		this.adbStarter = adb;
	}

	/** FAILED only for want of a (trusted) Wi-Fi: the pairing is fine, so setting it up again is no remedy. */
	public boolean failedForWifi() {
		return failedForWifi && (state == State.FAILED);
	}

	void setServerStopForTest(Predicate<Context> stop) {
		this.serverStop = stop;
	}

	void setServerPingForTest(Predicate<Context> ping) {
		this.serverPing = ping;
	}

	void retryConnectionForTesting() {
		long delay = RetryPolicy.delayMs(retryAttempt);
		if (delay < 0) {
			recordDiagnostic("server_failed", Map.of("reason", "max_retries_exceeded"));
			setState(State.FAILED);
		} else {
			recordDiagnostic("server_retry", Map.of("attempt", retryAttempt, "delay_ms", delay));
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
		panelOffRequested = false;
		restorePanelOnReconnect = false;
		stopKeepingPhoneAwake();
		this.ownPowerGroup = false;
		this.sessionUnlocked = false;
		unlinkServerBinder();
		this.server = null;
		this.serverBinder = null;
		this.idleBinder = null;
		this.serverUid = -1;
		mainHandler.removeCallbacks(serverPingTimeout);
		this.askingRunningServer = false;
		this.pingUnanswered = false;
		this.serverPing = c -> false; // tests that start over ADB expect the command at once; ping tests set their own
		this.serverStop = c -> false;
		this.state = State.IDLE;
		this.retryAttempt = 0;
		this.nextPhoneSleepMs = 0;
		this.testCommandRunner = null;
		this.rootStarter = this::startViaRoot;
		this.adbStarter = ServerStarter::startViaAdb;
		this.adbAttempt.set(null);
		this.diagnosticsConsumerForTest = null;
		this.lastCallFailure.clear();
		this.serverLogReader = () -> null;
		this.logExecutor = Runnable::run;
		this.stateListeners.clear();
		this.lastContext = null;
		this.sendExecutor = Runnable::run;
		this.touchQueue.clear();
		this.drainScheduled.set(false);
	}
}
