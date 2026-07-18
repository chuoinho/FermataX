package me.app.fermatax.auto;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

final class CarTextInputSession {
	private final Promise<String> result = new Promise<>();

	FutureSupplier<String> getResult() {
		return result;
	}

	boolean submit(CharSequence value) {
		return result.complete(value == null ? "" : value.toString());
	}

	boolean cancel() {
		return result.cancel();
	}

	boolean isDone() {
		return result.isDone();
	}
}
