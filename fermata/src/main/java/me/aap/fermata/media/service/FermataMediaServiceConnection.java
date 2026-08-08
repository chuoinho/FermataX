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
import me.aap.fermata.diagnostics.DiagnosticEvent;
import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticScope;
import me.aap.utils.async.Completed;
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
		if (BuildConfig.AUTO && !AutomotiveRuntimeGate.allowsNewWork()) {
			OperationCanceledException error = new OperationCanceledException(
					"Automotive media generation is quiescent");
			recordConnectionDiagnostic("service_connect_rejected", DiagnosticPriority.WARN,
					false, false, error.getClass().getSimpleName());
			return Completed.failed(error);
		}
		Context ctx = FermataApplication.get();
		FermataMediaServiceConnection con = new FermataMediaServiceConnection();
		Promise<FermataMediaServiceConnection> p = con.promise = new Promise<>();
		p.onCancel(con::cancelPendingBinding);
		Intent i = new Intent();
		if (BuildConfig.AUTO) i.setClassName(ctx, AUTO_SERVICE_CLASS);
		else i.setClass(ctx, FermataMediaService.class);
		i.setAction(ACTION_MEDIA_SERVICE);
		i.putExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
		recordConnectionDiagnostic("service_connect_started", DiagnosticPriority.STATE, false,
				false, null);
		Log.d("Binding service to context ", ctx);

		if (!ctx.bindService(i, con, Context.BIND_AUTO_CREATE)) {
			Exception ex = new IllegalStateException("Failed to bind to FermataMediaService");
			recordConnectionDiagnostic("service_connect_failed", DiagnosticPriority.ERROR, false,
				false, ex.getClass().getSimpleName());
			Log.e(ex, "Service connection failed");
			con.fail(ex);
		} else {
			con.bound = true;
			recordConnectionDiagnostic("service_bind_requested", DiagnosticPriority.STATE, true,
				false, null);
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
		recordConnectionDiagnostic("service_connect_cancelled", DiagnosticPriority.STATE, false,
				false, null);
		if (pending != null) {
			pending.completeExceptionally(new OperationCanceledException("Service connection cancelled"));
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Promise<FermataMediaServiceConnection> p;
		if (!(service instanceof FermataMediaService.ServiceBinder serviceBinder)) {
			IllegalStateException error =
					new IllegalStateException("Unexpected FermataMediaService binder");
			recordConnectionDiagnostic("service_connect_failed", DiagnosticPriority.ERROR, bound,
					false, error.getClass().getSimpleName());
			fail(error);
			return;
		}
		if (BuildConfig.AUTO && !AutomotiveRuntimeGate.allowsNewWork()) {
			fail(new OperationCanceledException("Automotive media generation is quiescent"));
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
		recordConnectionDiagnostic("service_connected", DiagnosticPriority.STATE, bound, true,
				null);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d("Service disconnected");
		synchronized (this) {
			binder = null;
		}
		recordConnectionDiagnostic("service_disconnected", DiagnosticPriority.WARN, bound, false,
				null);
	}

	@Override
	public void onBindingDied(ComponentName name) {
		recordConnectionDiagnostic("service_binding_died", DiagnosticPriority.ERROR, bound, false,
				IllegalStateException.class.getSimpleName());
		fail(new IllegalStateException("FermataMediaService binding died"));
	}

	@Override
	public void onNullBinding(ComponentName name) {
		recordConnectionDiagnostic("service_null_binding", DiagnosticPriority.ERROR, bound, false,
				IllegalStateException.class.getSimpleName());
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
		recordConnectionDiagnostic("service_connection_failed", DiagnosticPriority.ERROR, false,
				false, (failure == null) ? null : failure.getClass().getSimpleName());
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
		recordConnectionDiagnostic("service_connect_cancelled", DiagnosticPriority.STATE, false,
				false, null);
	}

	private static void recordConnectionDiagnostic(String name, DiagnosticPriority priority,
			boolean bound, boolean hasBinder, String errorClass) {
		try {
			DiagnosticEvent.Builder event = DiagnosticEvent.builder("service_connection", name)
					.scope(DiagnosticScope.ESSENTIAL).priority(priority)
					.put("bound", bound).put("has_binder", hasBinder);
			if (errorClass != null) event.put("error_class", errorClass);
			FermataApplication.get().getDiagnostics().record(event.build());
		} catch (Throwable ignored) {
			// Diagnostics must not affect service binding or cancellation.
		}
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
