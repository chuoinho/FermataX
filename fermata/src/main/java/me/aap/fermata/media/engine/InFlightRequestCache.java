package me.aap.fermata.media.engine;

import java.util.HashMap;
import java.util.Map;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedSupplier;

import static me.aap.utils.async.Completed.failed;

final class InFlightRequestCache<T> {
	private final Map<String, FutureSupplier<T>> requests = new HashMap<>();

	synchronized FutureSupplier<T> getOrLoad(String key,
															 CheckedSupplier<FutureSupplier<T>, Throwable> loader) {
		FutureSupplier<T> existing = requests.get(key);
		if (existing != null) return existing;

		FutureSupplier<T> request;
		try {
			request = loader.get();
		} catch (Throwable ex) {
			return failed(ex);
		}

		requests.put(key, request);
		request.onCompletion((result, error) -> remove(key, request));
		return request;
	}

	private synchronized void remove(String key, FutureSupplier<T> request) {
		if (requests.get(key) == request) requests.remove(key);
	}
}
