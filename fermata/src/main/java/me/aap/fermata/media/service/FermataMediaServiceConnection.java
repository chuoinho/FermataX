package me.aap.fermata.media.service;

import static me.aap.fermata.media.service.FermataMediaService.ACTION_MEDIA_SERVICE;
import static me.aap.fermata.media.service.FermataMediaService.DEFAULT_NOTIF_COLOR;
import static me.aap.fermata.media.service.FermataMediaService.INTENT_ATTR_NOTIF_COLOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.IBinder;
import android.os.OperationCanceledException;

import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.AppActivity;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Pavlenko
 */
public class FermataMediaServiceConnection implements ServiceConnection {
	private static final long CONNECT_TIMEOUT_SECONDS = 5;
	private static final String AUTO_SERVICE_CLASS =
			"me.app.fermatax.auto.AutoFermataMediaService";
	private Promise<FermataMediaServiceConnection> promise;
	private FermataMediaService.ServiceBinder binder;
	private Future<?> timeout;
	private boolean bound;

	public static FutureSupplier<FermataMediaServiceConnection> connect(@Nullable AppActivity a) {
		return connect(resolveNotificationColor(a));
	}

	public static int resolveNotificationColor(@Nullable AppActivity a) {
		int notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);

		if (a != null) {
			TypedArray typedArray = a.getTheme().obtainStyledAttributes(new int[]{android.R.attr.statusBarColor});
			notifColor = typedArray.getColor(0, notifColor);
			typedArray.recycle();
		}

		return notifColor;
	}

	public static FutureSupplier<FermataMediaServiceConnection> connect(int notifColor) {
		Context ctx = FermataApplication.get();
		FermataMediaServiceConnection con = new FermataMediaServiceConnection();
		Promise<FermataMediaServiceConnection> p = con.promise = new Promise<>();
		p.onCancel(con::cancelPendingBinding);
		Intent i = new Intent();
		if (BuildConfig.AUTO) i.setClassName(ctx, AUTO_SERVICE_CLASS);
		else i.setClass(ctx, FermataMediaService.class);
		i.setAction(ACTION_MEDIA_SERVICE);
		i.putExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
		Log.d("Binding service to context ", ctx);

		if (!ctx.bindService(i, con, Context.BIND_AUTO_CREATE)) {
			Exception ex = new IllegalStateException("Failed to bind to FermataMediaService");
			Log.e(ex, "Service connection failed");
			con.fail(ex);
		} else {
			con.bound = true;
			con.timeout = FermataApplication.get().getScheduler().schedule(
					() -> con.fail(new IllegalStateException("FermataMediaService connection timed out")),
					CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}

		return p;
	}

	public FermataServiceUiBinder createBinder() {
		return new FermataServiceUiBinder(this);
	}

	public MediaSessionCallback getMediaSessionCallback() {
		FermataMediaService.ServiceBinder b = binder;
		return (b == null) ? null : b.getMediaSessionCallback();
	}

	public boolean isConnected() {
		FermataMediaService.ServiceBinder b = binder;
		return (b != null) && b.isBinderAlive();
	}

	public void disconnect() {
		Promise<FermataMediaServiceConnection> pending;
		boolean unbind;
		synchronized (this) {
			if (!bound && (binder == null) && (promise == null)) return;
			pending = promise;
			promise = null;
			binder = null;
			unbind = bound;
			bound = false;
			cancelTimeoutLocked();
		}
		if (unbind) unbind();
		if (pending != null) {
			pending.completeExceptionally(new OperationCanceledException("Service connection cancelled"));
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Promise<FermataMediaServiceConnection> p;
		if (!(service instanceof FermataMediaService.ServiceBinder serviceBinder)) {
			fail(new IllegalStateException("Unexpected FermataMediaService binder: " + service));
			return;
		}
		synchronized (this) {
			p = promise;
			if (p == null) return;
			promise = null;
			binder = serviceBinder;
			cancelTimeoutLocked();
		}
		p.complete(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d("Service disconnected");
		synchronized (this) {
			binder = null;
		}
	}

	@Override
	public void onBindingDied(ComponentName name) {
		fail(new IllegalStateException("FermataMediaService binding died"));
	}

	@Override
	public void onNullBinding(ComponentName name) {
		fail(new IllegalStateException("FermataMediaService returned a null binding"));
	}

	private void fail(Throwable failure) {
		Promise<FermataMediaServiceConnection> p;
		boolean unbind;
		synchronized (this) {
			p = promise;
			if ((p == null) && !bound && (binder == null)) return;
			promise = null;
			binder = null;
			unbind = bound;
			bound = false;
			cancelTimeoutLocked();
		}
		if (unbind) unbind();
		if (p != null) p.completeExceptionally(failure);
	}

	private void cancelPendingBinding() {
		boolean unbind;
		synchronized (this) {
			if (promise == null) return;
			promise = null;
			binder = null;
			unbind = bound;
			bound = false;
			cancelTimeoutLocked();
		}
		if (unbind) unbind();
	}

	private void cancelTimeoutLocked() {
		Future<?> t = timeout;
		timeout = null;
		if (t != null) t.cancel(false);
	}

	private void unbind() {
		Log.d("Unbinding service from context ", FermataApplication.get());
		try {
			FermataApplication.get().unbindService(this);
		} catch (IllegalArgumentException ex) {
			Log.d("Service connection was already unbound");
		}
	}
}
