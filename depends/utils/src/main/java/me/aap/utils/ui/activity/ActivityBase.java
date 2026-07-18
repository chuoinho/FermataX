package me.aap.utils.ui.activity;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.CancellationException;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public abstract class ActivityBase extends AppCompatActivity implements AppActivity {
	private static final int GRANT_PERM_REQ = 1;
	private static ActivityBase instance;
	private static final PendingActivityBroker<AppActivity> pendingActivities =
			new PendingActivityBroker<>();
	private Promise<int[]> checkPermissions;
	@Nullable
	private ActivityResultLauncher<StartActivityPromise> activityLauncher;
	@Nullable
	private StartActivityContract activityContract;
	private final ActivityLifecycleGuard lifecycleGuard = new ActivityLifecycleGuard();
	private long lifecycleGeneration;
	private long pendingActivityGeneration;
	@Nullable
	private ActivityDelegate initializedDelegate;
	@NonNull
	private FutureSupplier<? extends ActivityDelegate> delegate = NO_DELEGATE;

	protected abstract FutureSupplier<? extends ActivityDelegate> createDelegate(AppActivity a);

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <A extends ActivityBase> FutureSupplier<A> create(
			Context ctx, String channelId, String channelName, @DrawableRes int icon,
			String title, String text, Class<A> c) {
		if (SDK_INT >= VERSION_CODES.O) {
			NotificationChannel nc = new NotificationChannel(channelId, channelName, IMPORTANCE_LOW);
			NotificationManager nmgr = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
			if (nmgr != null) nmgr.createNotificationChannel(nc);
		}

		PendingActivityBroker.Request<AppActivity> request;
		synchronized (ActivityBase.class) {
			ActivityBase current = instance;
			if (current != null) return completed((A) current).main();
			request = pendingActivities.acquire();
		}

		if (request.first()) {
			NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channelId);
			b.setSmallIcon(icon).setContentTitle(title).setContentText(text);
			Intent intent = new Intent(ctx, c);
			b.setFullScreenIntent(PendingIntent.getActivity(ctx, 0, intent, FLAG_IMMUTABLE), true);
			NotificationManagerCompat.from(ctx).notify(0, b.build());
		}
		return ((FutureSupplier<A>) (FutureSupplier<?>) request.future()).main();
	}

	@NonNull
	@Override
	public FutureSupplier<? extends ActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@CallSuper
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		assert delegate == NO_DELEGATE;
		lifecycleGeneration = lifecycleGuard.begin();
		// Activity lifecycle and delegate initialization must be serialized on the UI thread.
		delegate = createDelegate(this).main();
		StartActivityContract ac = new StartActivityContract();
		activityContract = ac;
		activityLauncher = registerForActivityResult(ac, ac);
		if (delegate == NO_DELEGATE) return;
		pendingActivityGeneration = pendingActivities.beginActivity();
		delegate.onCompletion((d, err) -> {
			lifecycleGuard.runIfCurrent(lifecycleGeneration, () -> {
				Throwable failure = err;
				if (failure == null) {
					try {
						d.onActivityCreate(savedInstanceState);
						initializedDelegate = d;
						delegate = completed(d);
						synchronized (ActivityBase.class) {
							instance = this;
						}
					} catch (Throwable ex) {
						failure = ex;
						try {
							d.onActivityDestroy();
						} catch (Throwable cleanupError) {
							ex.addSuppressed(cleanupError);
						}
					}
				}

				if (failure != null) {
					Log.e(failure, "Failed to create activity delegate");
					delegate = failed(failure);
				}
				pendingActivities.complete(pendingActivityGeneration, this, failure);
			});
		});
	}

	@CallSuper
	@Override
	protected void onStart() {
		super.onStart();
		withLiveDelegate(ActivityDelegate::onActivityStart);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		withLiveDelegate(d -> d.onActivityNewIntent(intent));
	}

	@CallSuper
	@Override
	protected void onResume() {
		super.onResume();
		withLiveDelegate(ActivityDelegate::onActivityResume);
	}


	@CallSuper
	@Override
	protected void onPause() {
		withLiveDelegate(ActivityDelegate::onActivityPause);
		super.onPause();
	}

	@CallSuper
	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		withLiveDelegate(d -> d.onActivitySaveInstanceState(outState));
	}

	@CallSuper
	@Override
	protected void onStop() {
		withLiveDelegate(ActivityDelegate::onActivityStop);
		super.onStop();
	}

	@CallSuper
	@Override
	protected void onDestroy() {
		lifecycleGuard.cancel();
		FutureSupplier<? extends ActivityDelegate> currentDelegate = delegate;
		ActivityDelegate d = initializedDelegate;
		try {
			if (d != null) d.onActivityDestroy();
			else if (currentDelegate != NO_DELEGATE) currentDelegate.cancel();
		} finally {
			cancelPendingActivityResults();
			long generation = pendingActivityGeneration;
			pendingActivityGeneration = 0;
			if (generation != 0) {
				pendingActivities.cancel(generation,
						new CancellationException("Activity destroyed before initialization completed"));
			}
			try {
				super.onDestroy();
			} finally {
				initializedDelegate = null;
				delegate = NO_DELEGATE;
				synchronized (ActivityBase.class) {
					if (instance == this) instance = null;
				}
			}
		}
	}

	@CallSuper
	@Override
	public void finish() {
		withLiveDelegate(ActivityDelegate::onActivityFinish);
		super.finish();
	}

	private void withLiveDelegate(Consumer<ActivityDelegate> action) {
		long generation = lifecycleGeneration;
		delegate.onSuccess(d -> lifecycleGuard.runIfCurrent(generation, () -> {
			if (initializedDelegate == d) action.accept(d);
		}));
	}

	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		StartActivityPromise p = new StartActivityPromise(intent);
		try {
			ActivityResultLauncher<StartActivityPromise> launcher = activityLauncher;
			if (launcher == null) throw new ActivityDestroyedException();
			launcher.launch(p);
		} catch (Exception ex) {
			p.completeExceptionally(ex);
		}
		return p;
	}

	private void cancelPendingActivityResults() {
		Promise<int[]> permissions = checkPermissions;
		checkPermissions = null;
		if (permissions != null) permissions.cancel();

		StartActivityContract contract = activityContract;
		activityContract = null;
		if (contract != null) contract.cancelPending();

		ActivityResultLauncher<StartActivityPromise> launcher = activityLauncher;
		activityLauncher = null;
		if (launcher != null) {
			try {
				launcher.unregister();
			} catch (RuntimeException ex) {
				Log.d(ex, "Failed to unregister activity result launcher");
			}
		}
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		if (checkPermissions != null) {
			checkPermissions.cancel();
			checkPermissions = null;
		}

		int[] result = new int[perms.length];

		for (int i = 0; i < perms.length; i++) {
			if (ContextCompat.checkSelfPermission(this, perms[i]) != PERMISSION_GRANTED) {
				Promise<int[]> p = checkPermissions = new Promise<>();
				ActivityCompat.requestPermissions(this, perms, GRANT_PERM_REQ);
				return p;
			} else {
				result[i] = PERMISSION_GRANTED;
			}
		}

		return completed(result);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		Promise<int[]> p = checkPermissions;

		if (p != null) {
			checkPermissions = null;
			p.complete(grantResults);
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		ActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyUp(keyCode, keyEvent, super::onKeyUp)
				: super.onKeyUp(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		ActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyDown(keyCode, keyEvent, super::onKeyDown)
				: super.onKeyDown(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		ActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress)
				: super.onKeyLongPress(keyCode, keyEvent);
	}

	public void installApk(Uri u, boolean known) {
		checkPermissions(Manifest.permission.REQUEST_INSTALL_PACKAGES).onSuccess(perms -> {
			Intent i = new Intent("android.intent.action.INSTALL_PACKAGE");
			int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
			if (SDK_INT >= VERSION_CODES.N) flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
			i.setData(u);
			i.setFlags(flags);
			if (known) {
				i.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
				i.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
			}
			startActivity(i);
		});
	}

	private static final class StartActivityPromise extends Promise<Intent> {
		final Supplier<Intent> supplier;

		StartActivityPromise(Supplier<Intent> supplier) {
			this.supplier = supplier;
		}
	}

	private static final class StartActivityContract
			extends ActivityResultContract<StartActivityPromise, Intent>
			implements ActivityResultCallback<Intent> {
		private StartActivityPromise promise;


		@NonNull
		@Override
		public Intent createIntent(@NonNull Context context, StartActivityPromise input) {
			StartActivityPromise p = promise;
			if (p != null) p.cancel();
			promise = input;
			return input.supplier.get();
		}

		@Override
		public Intent parseResult(int resultCode, @Nullable Intent intent) {
			return intent;
		}

		@Override
		public void onActivityResult(Intent result) {
			StartActivityPromise p = promise;
			promise = null;
			if (p != null) p.complete(result);
		}

		void cancelPending() {
			StartActivityPromise p = promise;
			promise = null;
			if (p != null) p.cancel();
		}
	}
}

final class ActivityLifecycleGuard {
	private long generation;
	private boolean active;
	private int runningCallbacks;

	synchronized long begin() {
		active = true;
		return ++generation;
	}

	boolean runIfCurrent(long expectedGeneration, Runnable action) {
		synchronized (this) {
			if (!active || (generation != expectedGeneration)) return false;
			runningCallbacks++;
		}
		try {
			action.run();
			return true;
		} finally {
			synchronized (this) {
				runningCallbacks--;
				if (runningCallbacks == 0) notifyAll();
			}
		}
	}

	synchronized void cancel() {
		active = false;
		generation++;
		boolean interrupted = false;
		while (runningCallbacks != 0) {
			try {
				wait();
			} catch (InterruptedException ex) {
				interrupted = true;
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
	}
}
