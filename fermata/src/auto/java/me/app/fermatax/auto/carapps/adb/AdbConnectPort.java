package me.app.fermatax.auto.carapps.adb;

import android.content.Context;

import io.github.muntashirakon.adb.android.AdbMdns;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Finds the port of this phone's own wireless-debugging connect service; it changes whenever the service restarts. */
public final class AdbConnectPort {

	private AdbConnectPort() {
	}

	/** AdbMdns reports only services whose host is a local interface. Fails with TimeoutException if there is none. */
	public static FutureSupplier<Integer> find(Context ctx, long timeoutMs) {
		return find(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT, timeoutMs);
	}

	/** The same lookup for another service type, e.g. AdbMdns.SERVICE_TYPE_TLS_PAIRING while a pairing dialog is open. */
	public static FutureSupplier<Integer> find(Context ctx, @AdbMdns.ServiceType String serviceType, long timeoutMs) {
		Promise<Integer> port = new Promise<>();
		AdbMdns mdns = new AdbMdns(ctx.getApplicationContext(), serviceType, (host, p) -> {
			if (p > 0) port.complete(p);
		});
		FutureSupplier<Integer> result = port.timeout(timeoutMs);
		result.onCompletion((r, err) -> mdns.stop());
		mdns.start();
		return result;
	}
}
